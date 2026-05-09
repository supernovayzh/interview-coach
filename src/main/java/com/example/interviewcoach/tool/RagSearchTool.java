package com.example.interviewcoach.tool;

import com.example.interviewcoach.model.RagChunk;
import com.example.interviewcoach.service.RagKnowledgeService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RagSearchTool implements InterviewTool {

    private final RagKnowledgeService ragKnowledgeService;

    public RagSearchTool(RagKnowledgeService ragKnowledgeService) {
        this.ragKnowledgeService = ragKnowledgeService;
    }

    @Override
    public String name() {
        return "search";
    }

    @Override
    public String description() {
        return "按给定查询词检索本地 Markdown 知识库，返回相关片段";
    }

    @Override
    public InterviewToolResult execute(InterviewToolContext context) {
        String query = context.getAttribute("query", String.class);
        if (query == null || query.isBlank()) {
            query = context.getQuestion();
        }
        int topK = context.getAttribute("topK", Integer.class) == null ? 3 : context.getAttribute("topK", Integer.class);
        List<RagChunk> results = ragKnowledgeService.search(query, topK);

        List<Map<String, Object>> matches = new ArrayList<>();
        StringBuilder contextText = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            RagChunk chunk = results.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("title", chunk.getTitle());
            item.put("sourceFile", chunk.getSourceFile());
            item.put("score", chunk.getScore());
            String preview = chunk.preview(650);
            item.put("preview", preview);
            matches.add(item);

            contextText.append("【").append(i + 1).append("】");
            contextText.append(chunk.getTitle() == null ? "未命名" : chunk.getTitle());
            contextText.append(" (来源: ").append(chunk.getSourceFile()).append(", 分数: ")
                    .append(String.format(Locale.ROOT, "%.2f", chunk.getScore())).append(")\n");
            contextText.append(preview).append("\n");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("query", query);
        data.put("topK", topK);
        data.put("matches", matches);
        data.put("context", contextText.toString());
        return InterviewToolResult.of(name(), data);
    }
}
