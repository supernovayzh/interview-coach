package com.example.interviewcoach.eval;

import com.example.interviewcoach.config.LlmOpenAiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LlmEvalJudgeService implements EvalJudgeService {

    private static final Pattern SCORE_PATTERN = Pattern.compile("(?i)(?:score|评分|得分|分数)\\D{0,12}([0-9]{1,3}(?:\\.[0-9]+)?)");
    private static final Logger logger = LoggerFactory.getLogger(LlmEvalJudgeService.class);

    private final WebClient webClient;
    private final LlmOpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public LlmEvalJudgeService(LlmOpenAiProperties properties,
                               WebClient.Builder webClientBuilder,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.baseUrl(properties.getEndpoint()).build();
    }

    @Override
    public JudgeResult judge(EvalCase evalCase, String answer, RuleEvaluationContext context) {
        if (evalCase == null) {
            return new JudgeResult(null, "[LLM Judge 未执行] case 为空", null, "skipped");
        }
        if (answer == null || answer.isBlank()) {
            return new JudgeResult(0.0, "空答案", null, "fallback");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return new JudgeResult(null, "[LLM Judge 未启用] 未配置 API Key", null, "disabled");
        }

        String prompt = buildJudgePrompt(evalCase, answer, context);
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", buildJudgeSystemPrompt()),
                        Map.of("role", "user", "content", prompt)
                ),
            "max_tokens", Math.min(768, properties.getMaxTokens()),
            "temperature", 0.0,
            "response_format", Map.of("type", "json_object")
        );

        try {
            Map resp = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null) {
                return new JudgeResult(null, "[LLM Judge 错误] 空响应", null, "error");
            }
            Object choicesObj = resp.get("choices");
            if (choicesObj instanceof List) {
                List choices = (List) choicesObj;
                if (!choices.isEmpty()) {
                    Object c0 = choices.get(0);
                    if (c0 instanceof Map) {
                        Object message = ((Map) c0).get("message");
                        if (message instanceof Map) {
                            Object content = ((Map) message).get("content");
                            Object reasoningContent = ((Map) message).get("reasoning_content");
                            String text = firstNonBlank(
                                    content == null ? null : content.toString(),
                                    reasoningContent == null ? null : reasoningContent.toString()
                            );
                            if (text != null) {
                                String normalizedText = text.trim();
                                Double score = parseScoreFromText(normalizedText);
                                if (score == null) {
                                    logger.warn("LLM judge parse_error, response={}, message={}, rawText={}", resp, message, normalizedText);
                                    return new JudgeResult(null, parseFeedbackFromText(normalizedText), normalizedText, "parse_error");
                                }
                                return new JudgeResult(score, parseFeedbackFromText(normalizedText), normalizedText, "ok");
                            }
                        }
                    }
                }
            }
            return new JudgeResult(null, "[LLM Judge 错误] 无候选结果", null, "error");
        } catch (Exception ex) {
            return new JudgeResult(null, "[LLM Judge 异常] " + ex.getMessage(), null, "error");
        }
    }

    private String buildJudgeSystemPrompt() {
        return "你是一个严格但务实的面试评测裁判。只基于 case、回答和规则评分给出最终分数和一句反馈，不要展开推理过程，不要使用 markdown。";
    }

    private String buildJudgePrompt(EvalCase evalCase, String answer, RuleEvaluationContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("请给出最终评测，只输出一行：SCORE: <0-100> FEEDBACK: <一句话中文反馈>。不要解释过程，不要列点。\n\n");

        sb.append("CASE ID: ").append(nullToEmpty(evalCase.getId())).append('\n');
        sb.append("TITLE: ").append(nullToEmpty(evalCase.getTitle())).append('\n');
        sb.append("EXPECTED BEHAVIOR: ").append(nullToEmpty(evalCase.getExpectedBehavior())).append('\n');
        sb.append("FOCUS POINTS: ").append(String.join(", ", safeList(evalCase.getFocusPoints()))).append('\n');
        sb.append("RULE SCORE: ").append(context == null ? "" : context.ruleScore()).append('\n');
        sb.append("RULE FEEDBACK: ").append(context == null ? "" : nullToEmpty(context.ruleFeedback())).append('\n');
        sb.append("QUESTION COUNT: ").append(context == null ? 0 : context.questionCount()).append('\n');
        sb.append("ANSWER:\n").append(nullToEmpty(answer)).append('\n');
        return sb.toString();
    }

    private Double parseScoreFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            String trimmed = text.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                Map parsed = objectMapper.readValue(trimmed, Map.class);
                Object scoreObj = parsed.get("score");
                if (scoreObj instanceof Number number) {
                    return normalizeScore(number.doubleValue());
                }
                if (scoreObj != null) {
                    return normalizeScore(Double.parseDouble(scoreObj.toString()));
                }
            }
        } catch (Exception ignored) {
        }
        Matcher matcher = SCORE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return normalizeScore(Double.parseDouble(matcher.group(1)));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String parseFeedbackFromText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        try {
            String trimmed = text.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                Map parsed = objectMapper.readValue(trimmed, Map.class);
                Object feedback = parsed.get("feedback");
                if (feedback != null) {
                    return feedback.toString().trim();
                }
            }
        } catch (Exception ignored) {
        }
        int idx = text.indexOf("FEEDBACK:");
        if (idx >= 0) {
            return text.substring(idx + 9).trim().replaceAll("\n", " ");
        }
        String[] lines = text.split("\n");
        return lines.length > 0 ? lines[0].trim() : text.trim();
    }

    private double normalizeScore(double score) {
        return Math.max(0.0, Math.min(100.0, score));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private List<String> safeList(List<String> list) {
        return list == null ? List.of() : list;
    }
}