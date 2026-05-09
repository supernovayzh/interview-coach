package com.example.interviewcoach.tool;

import com.example.interviewcoach.service.WebSearchService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WebSearchTool implements InterviewTool {

    private final WebSearchService webSearchService;

    public WebSearchTool(WebSearchService webSearchService) {
        this.webSearchService = webSearchService;
    }

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String description() {
        return "联网检索资料并生成带来源的参考答案，可用于出题前准备和面试回答校验";
    }

    @Override
    public InterviewToolResult execute(InterviewToolContext context) {
        String query = context.getAttribute("query", String.class);
        if (query == null || query.isBlank()) {
            query = context.getQuestion();
        }

        Integer topK = context.getAttribute("topK", Integer.class);

        @SuppressWarnings("unchecked")
        List<String> domains = context.getAttribute("domains", List.class);

        List<WebSearchService.WebSearchResult> results = webSearchService.search(query, topK, domains);
        String referenceAnswer = webSearchService.synthesizeReferenceAnswer(context.getQuestion(), results);

        List<Map<String, Object>> sources = new ArrayList<>();
        StringBuilder contextText = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            WebSearchService.WebSearchResult r = results.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("title", r.title());
            item.put("url", r.url());
            item.put("snippet", r.snippet());
            sources.add(item);

            contextText.append("[").append(i + 1).append("] ")
                    .append(r.title())
                    .append("\n")
                    .append(r.snippet() == null ? "" : r.snippet())
                    .append("\nURL: ")
                    .append(r.url())
                    .append("\n\n");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("query", query);
        data.put("topK", topK == null ? 5 : topK);
        data.put("sources", sources);
        data.put("context", contextText.toString());
        data.put("referenceAnswer", referenceAnswer);
        return InterviewToolResult.of(name(), data);
    }
}
