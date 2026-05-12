package com.example.interviewcoach.service;

import com.example.interviewcoach.model.ChatAnswer;
import com.example.interviewcoach.model.ChatRequest;
import com.example.interviewcoach.model.InterviewSessionMemory;
import com.example.interviewcoach.tool.InterviewToolContext;
import com.example.interviewcoach.tool.InterviewToolResult;
import com.example.interviewcoach.tool.ToolRegistry;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class InterviewOrchestrator implements ChatService {

    private final LLMService llmService;
    private final InterviewMemoryService memoryService;
    private final ToolRegistry toolRegistry;
    private final RagSearchService ragSearchService;
    private final PlannerService plannerService;

    // thresholds can be moved to config later
    private final double followupThreshold = 60.0;
    private final double minorClarifyThreshold = 80.0;

    public InterviewOrchestrator(LLMService llmService,
                                  InterviewMemoryService memoryService,
                                  ToolRegistry toolRegistry,
                                  RagSearchService ragSearchService,
                                  PlannerService plannerService) {
        this.llmService = llmService;
        this.memoryService = memoryService;
        this.toolRegistry = toolRegistry;
        this.ragSearchService = ragSearchService;
        this.plannerService = plannerService;
    }

    @Override
    public ChatAnswer ask(ChatRequest request) {
        ChatAnswer answer = llmService.ask(request);
        if (answer == null) return null;

        String sessionId = request.getEffectiveSessionId();
        InterviewSessionMemory mem = memoryService.getOrCreate(sessionId);

        String ragContext = buildRagContext(request, sessionId);
        InterviewToolContext context = new InterviewToolContext(request, mem, sessionId, request.getQuestion(), answer.getAnswer(), ragContext);

        // Prefer LLM-driven planner when available
        try {
            InterviewToolResult planResult = plannerService.planAndExecute(context);
            Object err = planResult.get("error");
            if (err != null) {
                // fallback to rule-based
                applyRuleBasedDecision(answer, context);
            } else {
                double score = planResult.getDouble("score", -1.0);
                if (score >= 0) {
                    answer.setScore(score);
                }
                String feedback = planResult.getString("feedback");
                if (feedback != null) answer.setScoreFeedback(feedback);

                String follow = planResult.getString("followUpQuestion");
                String finalAction = planResult.getString("finalAction");
                if (follow != null && !follow.isBlank()) {
                    answer.setNextAction("ASK_FOLLOWUP");
                    answer.setFollowUpQuestion(follow);
                } else if ("SUGGEST_IMPROVEMENT".equalsIgnoreCase(finalAction)) {
                    answer.setNextAction("SUGGEST_IMPROVEMENT");
                    answer.setFollowUpQuestion(null);
                } else if ("END".equalsIgnoreCase(finalAction)) {
                    answer.setNextAction("END");
                } else {
                    answer.setNextAction("NEXT_QUESTION");
                    answer.setFollowUpQuestion(null);
                }
                // append summary if present
                String summary = planResult.getString("summary");
                appendSummaryFeedback(answer, summary);
            }
        } catch (Exception ex) {
            applyRuleBasedDecision(answer, context);
        }

        if (mem.getRecentTurns().size() >= 15) {
            InterviewToolResult summaryResult = toolRegistry.invoke("summarize", context);
            String summary = summaryResult.getString("summary");
            appendSummaryFeedback(answer, summary);
        }

        // persist evaluation if available
        try {
            double s = answer.getScore();
            String fb = answer.getScoreFeedback();
            // only persist when score or feedback present
            if (s != 0.0 || (fb != null && !fb.isBlank())) {
                memoryService.saveEvaluation(sessionId, request.getQuestion(), s, fb);
            }
        } catch (Exception ignored) {
        }

        return answer;
    }

    private String buildRagContext(ChatRequest request, String sessionId) {
        return ragSearchService.search(request, sessionId, null, 3);
    }

    private void applyRuleBasedDecision(ChatAnswer answer, InterviewToolContext context) {
        InterviewToolResult scoreResult = toolRegistry.invoke("score", context);
        double score = scoreResult.getDouble("score", 0.0);
        String feedback = scoreResult.getString("feedback");
        answer.setScore(score);
        answer.setScoreFeedback(feedback);

        if (score < followupThreshold) {
            InterviewToolResult clarifyResult = toolRegistry.invoke("clarify", context);
            String follow = clarifyResult.getString("followUpQuestion");
            answer.setNextAction("ASK_FOLLOWUP");
            answer.setFollowUpQuestion(follow == null ? "" : follow);
        } else if (score < minorClarifyThreshold) {
            answer.setNextAction("SUGGEST_IMPROVEMENT");
            answer.setFollowUpQuestion(null);
        } else {
            answer.setNextAction("NEXT_QUESTION");
            answer.setFollowUpQuestion(null);
        }
        if (context.getMemory().getRecentTurns().size() >= 15) {
            InterviewToolResult summaryResult = toolRegistry.invoke("summarize", context);
            String summary = summaryResult.getString("summary");
            appendSummaryFeedback(answer, summary);
        }
    }

    private void appendSummaryFeedback(ChatAnswer answer, String summary) {
        if (summary != null && !summary.isBlank()) {
            String fb = answer.getScoreFeedback() == null ? "" : answer.getScoreFeedback();
            answer.setScoreFeedback(fb + "\n会话复盘：\n" + summary);
        }
    }
}
