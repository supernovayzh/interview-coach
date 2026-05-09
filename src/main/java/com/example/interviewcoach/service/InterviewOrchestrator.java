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
    private final RagKnowledgeService ragKnowledgeService;
    private final PlannerService plannerService;

    // thresholds can be moved to config later
    private final double followupThreshold = 60.0;
    private final double minorClarifyThreshold = 80.0;

    public InterviewOrchestrator(LLMService llmService,
                                  InterviewMemoryService memoryService,
                                  ToolRegistry toolRegistry,
                                  RagKnowledgeService ragKnowledgeService,
                                  PlannerService plannerService) {
        this.llmService = llmService;
        this.memoryService = memoryService;
        this.toolRegistry = toolRegistry;
        this.ragKnowledgeService = ragKnowledgeService;
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
                if (summary != null && !summary.isBlank()) {
                    String fb = answer.getScoreFeedback() == null ? "" : answer.getScoreFeedback();
                    answer.setScoreFeedback(fb + "\n会话复盘：\n" + summary);
                }
            }
        } catch (Exception ex) {
            applyRuleBasedDecision(answer, context);
        }

        if (mem.getRecentTurns().size() >= 15) {
            InterviewToolResult summaryResult = toolRegistry.invoke("summarize", context);
            String summary = summaryResult.getString("summary");
            String fb = answer.getScoreFeedback() == null ? "" : answer.getScoreFeedback();
            if (summary != null && !summary.isBlank()) {
                answer.setScoreFeedback(fb + "\n会话复盘：\n" + summary);
            }
        }

        return answer;
    }

    private String buildRagContext(ChatRequest request, String sessionId) {
        if (toolRegistry == null || ragKnowledgeService == null) {
            return "";
        }
        StringBuilder query = new StringBuilder();
        query.append(request.getQuestion()).append(' ');
        appendQueryIfNotBlank(query, request.getTargetCompany());
        appendQueryIfNotBlank(query, request.getCompanyTier());
        appendQueryIfNotBlank(query, request.getTargetRole());
        appendQueryIfNotBlank(query, request.getFocusAreas());
        appendQueryIfNotBlank(query, request.getInterviewGoal());
        InterviewToolContext searchContext = new InterviewToolContext(request, memoryService.getOrCreate(sessionId), sessionId, request.getQuestion(), null, null)
                .withAttribute("query", query.toString())
                .withAttribute("topK", 3);
        try {
            InterviewToolResult result = toolRegistry.invoke("search", searchContext);
            String context = result.getString("context");
            return context == null ? "" : context;
        } catch (Exception ex) {
            return "";
        }
    }

    private void appendQueryIfNotBlank(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(value).append(' ');
        }
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
            String fb = answer.getScoreFeedback() == null ? "" : answer.getScoreFeedback();
            if (summary != null && !summary.isBlank()) {
                answer.setScoreFeedback(fb + "\n会话复盘：\n" + summary);
            }
        }
    }
}
