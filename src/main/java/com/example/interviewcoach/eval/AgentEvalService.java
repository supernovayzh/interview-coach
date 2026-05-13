package com.example.interviewcoach.eval;

import com.example.interviewcoach.model.ChatAnswer;
import com.example.interviewcoach.model.ChatRequest;
import com.example.interviewcoach.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AgentEvalService {

    private static final String DEFAULT_SUITE = "mini-interview-agent";
    private static final String SUITE_PATH_PREFIX = "eval/";

    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final EvalJudgeService evalJudgeService;

    public AgentEvalService(ObjectMapper objectMapper,
                            ChatService chatService,
                            EvalJudgeService evalJudgeService) {
        this.objectMapper = objectMapper;
        this.chatService = chatService;
        this.evalJudgeService = evalJudgeService;
    }

    public List<EvalCase> listCases(String suiteName) {
        return loadSuite(normalizeSuiteName(suiteName)).getCases();
    }

    public EvalRunResponse run(EvalRunRequest request) {
        String suiteName = normalizeSuiteName(request == null ? null : request.getSuiteName());
        EvalSuiteDefinition suite = loadSuite(suiteName);
        List<EvalCase> selectedCases = selectCases(suite.getCases(), request == null ? null : request.getCaseIds());
        String runId = buildRunId(suiteName);
        double passThreshold = request == null ? 70.0 : request.getPassThreshold();
        long started = System.currentTimeMillis();

        List<EvalCaseResult> caseResults = new ArrayList<>();
        double scoreSum = 0.0;
        int passed = 0;

        for (EvalCase evalCase : selectedCases) {
            long caseStarted = System.currentTimeMillis();
            String caseSessionId = buildCaseSessionId(runId, evalCase);
            ChatAnswer answer = executeCase(evalCase, caseSessionId);
            RuleEvaluation ruleEvaluation = evaluate(evalCase, answer, passThreshold);
            EvalJudgeService.JudgeResult judgeResult = evalJudgeService.judge(
                    evalCase,
                    buildOutput(answer),
                    new EvalJudgeService.RuleEvaluationContext(
                            ruleEvaluation.score(),
                            ruleEvaluation.questionCount(),
                            ruleEvaluation.matchedFocusPointCount(),
                            ruleEvaluation.feedback()
                    )
            );
            double finalScore = mergeScores(ruleEvaluation.score(), judgeResult.score());

            EvalCaseResult caseResult = new EvalCaseResult();
            caseResult.setCaseId(evalCase.getId());
            caseResult.setTitle(evalCase.getTitle());
            caseResult.setSessionId(caseSessionId);
            caseResult.setDifficulty(evalCase.getDifficulty());
            caseResult.setAnswer(answer == null ? "" : answer.getAnswer());
            caseResult.setNextAction(answer == null ? null : answer.getNextAction());
            caseResult.setFollowUpQuestion(answer == null ? null : answer.getFollowUpQuestion());
            caseResult.setRuleScore(ruleEvaluation.score());
            caseResult.setJudgeScore(judgeResult.score());
            caseResult.setJudgeStatus(judgeResult.status());
            caseResult.setJudgeRawText(judgeResult.rawText());
            caseResult.setFinalScore(finalScore);
            caseResult.setPassed(finalScore >= passThreshold);
            caseResult.setQuestionCount(ruleEvaluation.questionCount());
            caseResult.setMatchedFocusPointCount(ruleEvaluation.matchedFocusPointCount());
            caseResult.setViolations(ruleEvaluation.violations());
            caseResult.setFeedback(buildFeedback(ruleEvaluation.feedback(), judgeResult.feedback(), judgeResult.status(), finalScore));
            caseResult.setExpectedBehavior(evalCase.getExpectedBehavior());
            caseResult.setDurationMs(System.currentTimeMillis() - caseStarted);

            caseResults.add(caseResult);
            scoreSum += finalScore;
            if (finalScore >= passThreshold) {
                passed++;
            }
        }

        EvalRunResponse response = new EvalRunResponse();
        response.setRunId(runId);
        response.setSuiteName(suite.getName() == null ? suiteName : suite.getName());
        response.setDescription(suite.getDescription());
        response.setStartedAt(Instant.ofEpochMilli(started).toString());
        response.setDurationMs(System.currentTimeMillis() - started);
        response.setTotalCases(caseResults.size());
        response.setPassedCases(passed);
        response.setFailedCases(caseResults.size() - passed);
        response.setAverageScore(caseResults.isEmpty() ? 0.0 : round(scoreSum / caseResults.size()));
        response.setCases(caseResults);
        return response;
    }

    private ChatAnswer executeCase(EvalCase evalCase, String caseSessionId) {
        ChatRequest request = new ChatRequest();
        request.setSessionId(caseSessionId);
        request.setQuestion(evalCase.getUserInput());
        request.setTargetCompany(evalCase.getTargetCompany());
        request.setCompanyTier(evalCase.getCompanyTier());
        request.setTargetRole(evalCase.getTargetRole());
        request.setFocusAreas(evalCase.getFocusAreas());
        request.setResumeSummary(evalCase.getResumeSummary());
        request.setInterviewGoal(evalCase.getInterviewGoal());
        return chatService.ask(request);
    }

    private RuleEvaluation evaluate(EvalCase evalCase, ChatAnswer answer, double passThreshold) {
        String output = buildOutput(answer);
        String normalized = normalize(output);
        List<String> violations = new ArrayList<>();
        double score = 100.0;

        int questionCount = countQuestionMarks(output);
        if (evalCase.isMustAskOneQuestion()) {
            if (questionCount == 0) {
                score -= 25.0;
                violations.add("未输出问题");
            }
            if (questionCount > evalCase.getMaxQuestionMarks()) {
                score -= 25.0 + Math.max(0, questionCount - evalCase.getMaxQuestionMarks()) * 8.0;
                violations.add("一次输出多个问题");
            }
        }

        if (containsAny(normalized, List.of("问题：", "回答：", "面试官：", "你："))) {
            score -= 20.0;
            violations.add("出现自问自答或角色混写");
        }

        for (String forbidden : safeList(evalCase.getForbiddenPhrases())) {
            if (!forbidden.isBlank() && normalized.contains(normalize(forbidden))) {
                score -= 15.0;
                violations.add("命中禁用短语: " + forbidden);
            }
        }

        int matchedFocus = countMatches(normalized, evalCase.getFocusPoints());
        if (!safeList(evalCase.getFocusPoints()).isEmpty() && matchedFocus == 0) {
            score -= 15.0;
            violations.add("未命中期望主题");
        }

        for (String required : safeList(evalCase.getRequiredPhrases())) {
            if (!required.isBlank() && !normalized.contains(normalize(required))) {
                score -= 5.0;
                violations.add("缺少期望短语: " + required);
            }
        }

        if (output.length() > 900) {
            score -= 5.0;
            violations.add("输出过长");
        }

        score = Math.max(0.0, Math.min(100.0, score));
        boolean passed = score >= passThreshold;

        StringBuilder feedback = new StringBuilder();
        feedback.append("规则评分=").append(round(score)).append("/100");
        if (evalCase.getExpectedBehavior() != null && !evalCase.getExpectedBehavior().isBlank()) {
            feedback.append("；期望行为：").append(evalCase.getExpectedBehavior());
        }
        if (!violations.isEmpty()) {
            feedback.append("；违规项：").append(String.join("；", violations));
        } else {
            feedback.append("；未发现明显规则违规");
        }
        feedback.append("；命中主题数=").append(matchedFocus);

        return new RuleEvaluation(score, passed, questionCount, matchedFocus, violations, feedback.toString());
    }

    private EvalSuiteDefinition loadSuite(String suiteName) {
        String normalized = normalizeSuiteName(suiteName);
        String path = SUITE_PATH_PREFIX + normalized + ".json";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                if (!DEFAULT_SUITE.equals(normalized)) {
                    return loadSuite(DEFAULT_SUITE);
                }
                throw new IllegalArgumentException("评测集不存在: " + path);
            }
            String json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            EvalSuiteDefinition suite = objectMapper.readValue(json, EvalSuiteDefinition.class);
            if (suite.getName() == null || suite.getName().isBlank()) {
                suite.setName(normalized);
            }
            if (suite.getCases() == null) {
                suite.setCases(List.of());
            }
            return suite;
        } catch (IOException e) {
            throw new IllegalStateException("读取评测集失败: " + path + ", " + e.getMessage(), e);
        }
    }

    private List<EvalCase> selectCases(List<EvalCase> cases, List<String> caseIds) {
        if (caseIds == null || caseIds.isEmpty()) {
            return cases == null ? List.of() : cases;
        }
        List<String> wanted = caseIds.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).toList();
        if (wanted.isEmpty()) {
            return cases == null ? List.of() : cases;
        }
        return cases == null ? List.of() : cases.stream()
                .filter(evalCase -> wanted.contains(evalCase.getId()))
                .collect(Collectors.toList());
    }

    private String buildRunId(String suiteName) {
        return "eval_" + normalizeSuiteName(suiteName) + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String buildCaseSessionId(String runId, EvalCase evalCase) {
        String caseId = evalCase.getId() == null || evalCase.getId().isBlank() ? "case" : evalCase.getId();
        return runId + "_" + caseId;
    }

    private String buildOutput(ChatAnswer answer) {
        if (answer == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (answer.getAnswer() != null) {
            sb.append(answer.getAnswer()).append('\n');
        }
        if (answer.getFollowUpQuestion() != null) {
            sb.append(answer.getFollowUpQuestion()).append('\n');
        }
        if (answer.getScoreFeedback() != null) {
            sb.append(answer.getScoreFeedback());
        }
        return sb.toString().trim();
    }

    private boolean containsAny(String text, Collection<String> needles) {
        if (text == null || text.isBlank() || needles == null || needles.isEmpty()) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && text.contains(normalize(needle))) {
                return true;
            }
        }
        return false;
    }

    private int countMatches(String normalizedText, Collection<String> keywords) {
        if (normalizedText == null || normalizedText.isBlank() || keywords == null || keywords.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && normalizedText.contains(normalize(keyword))) {
                count++;
            }
        }
        return count;
    }

    private int countQuestionMarks(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '?' || c == '？') {
                count++;
            }
        }
        return count;
    }

    private List<String> safeList(List<String> list) {
        return list == null ? List.of() : list;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeSuiteName(String suiteName) {
        if (suiteName == null || suiteName.isBlank()) {
            return DEFAULT_SUITE;
        }
        return suiteName.trim().replace(' ', '-');
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double mergeScores(double ruleScore, Double judgeScore) {
        if (judgeScore == null) {
            return round(ruleScore);
        }
        return round(ruleScore * 0.4 + judgeScore * 0.6);
    }

    private String buildFeedback(String ruleFeedback, String judgeFeedback, String judgeStatus, double finalScore) {
        StringBuilder sb = new StringBuilder();
        sb.append("最终分=").append(round(finalScore)).append("/100");
        sb.append("；规则评测：").append(ruleFeedback == null ? "" : ruleFeedback);
        if (judgeFeedback != null && !judgeFeedback.isBlank()) {
            sb.append("；LLM Judge(").append(judgeStatus == null ? "" : judgeStatus).append(")：").append(judgeFeedback);
        }
        return sb.toString();
    }

    private record RuleEvaluation(double score,
                                  boolean passed,
                                  int questionCount,
                                  int matchedFocusPointCount,
                                  List<String> violations,
                                  String feedback) {
    }
}