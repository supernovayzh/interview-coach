package com.example.interviewcoach.service;

import com.example.interviewcoach.model.ChatRequest;
import com.example.interviewcoach.tool.InterviewToolContext;
import com.example.interviewcoach.tool.InterviewToolResult;
import com.example.interviewcoach.tool.ToolRegistry;
import org.springframework.stereotype.Service;

@Service
public class RagSearchService {

    private final ToolRegistry toolRegistry;
    private final InterviewMemoryService memoryService;

    public RagSearchService(ToolRegistry toolRegistry, InterviewMemoryService memoryService) {
        this.toolRegistry = toolRegistry;
        this.memoryService = memoryService;
    }

    public String search(ChatRequest request, String sessionId, String extraContext, int topK) {
        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            return "";
        }

        String resolvedSessionId = (sessionId == null || sessionId.isBlank())
                ? request.getEffectiveSessionId()
                : sessionId;

        StringBuilder query = new StringBuilder();
        query.append(request.getQuestion()).append(' ');
        appendIfNotBlank(query, request.getTargetCompany());
        appendIfNotBlank(query, request.getCompanyTier());
        appendIfNotBlank(query, request.getTargetRole());
        appendIfNotBlank(query, request.getFocusAreas());
        appendIfNotBlank(query, request.getInterviewGoal());
        if (extraContext != null && !extraContext.isBlank()) {
            query.append(extraContext);
        }

        InterviewToolContext context = new InterviewToolContext(
                request,
                memoryService.getOrCreate(resolvedSessionId),
                resolvedSessionId,
                request.getQuestion(),
                null,
                null
        ).withAttribute("query", query.toString())
         .withAttribute("topK", topK <= 0 ? 3 : topK);

        try {
            InterviewToolResult result = toolRegistry.invoke("search", context);
            String ragContext = result.getString("context");
            return ragContext == null ? "" : ragContext;
        } catch (Exception ex) {
            return "";
        }
    }

    private void appendIfNotBlank(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(value).append(' ');
        }
    }
}
