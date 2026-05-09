package com.example.interviewcoach.service;

import com.example.interviewcoach.config.LlmOpenAiProperties;
import com.example.interviewcoach.config.WebSearchProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class WebSearchService {

    public record WebSearchResult(String title, String url, String snippet) {}

    private final WebSearchProperties webSearchProperties;
    private final LlmOpenAiProperties llmOpenAiProperties;
    private final WebClient webSearchClient;
    private final WebClient llmClient;

    public WebSearchService(WebSearchProperties webSearchProperties,
                            LlmOpenAiProperties llmOpenAiProperties,
                            WebClient.Builder webClientBuilder) {
        this.webSearchProperties = webSearchProperties;
        this.llmOpenAiProperties = llmOpenAiProperties;
        this.webSearchClient = webClientBuilder.baseUrl(webSearchProperties.getEndpoint()).build();
        this.llmClient = webClientBuilder.baseUrl(llmOpenAiProperties.getEndpoint()).build();
    }

    public List<WebSearchResult> search(String query, Integer topK, List<String> domains) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (!webSearchProperties.isEnabled()) {
            return List.of();
        }
        if (webSearchProperties.getApiKey() == null || webSearchProperties.getApiKey().isBlank()) {
            return List.of();
        }

        int limit = topK == null || topK <= 0 ? webSearchProperties.getDefaultTopK() : topK;

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("q", query);
        params.add("api_key", webSearchProperties.getApiKey());
        params.add("engine", webSearchProperties.getEngine());

        URI uri = UriComponentsBuilder.fromUriString(webSearchProperties.getEndpoint())
                .queryParams(params)
                .build(true)
                .toUri();

        try {
            Map<String, Object> resp = webSearchClient.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(webSearchProperties.getTimeoutSeconds()));

            if (resp == null) {
                return List.of();
            }
            return parseResults(resp, limit, domains);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public String synthesizeReferenceAnswer(String question, List<WebSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "未检索到可用网络资料。";
        }

        if (llmOpenAiProperties.getApiKey() == null || llmOpenAiProperties.getApiKey().isBlank()) {
            return fallbackReferenceAnswer(results);
        }

        StringBuilder sourceText = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            WebSearchResult r = results.get(i);
            sourceText.append("[").append(i + 1).append("] ")
                    .append(r.title()).append("\n")
                    .append(r.snippet()).append("\n")
                    .append("URL: ").append(r.url()).append("\n\n");
        }

        String prompt = "你是Java面试官助手。基于检索资料给出高质量参考答案。"
                + "要求：1) 先给结论，再分点说明；2) 每个关键点附带来源编号如[1][2]；3) 若资料不足要明确说明。\n"
                + "问题：" + (question == null ? "" : question) + "\n\n"
                + "检索资料：\n" + sourceText;

        Map<String, Object> body = Map.of(
                "model", llmOpenAiProperties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", "你是严谨的技术事实整理助手。"),
                        Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", Math.min(700, llmOpenAiProperties.getMaxTokens())
        );

        try {
            Map<String, Object> resp = llmClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + llmOpenAiProperties.getApiKey())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(20));

            if (resp == null) {
                return fallbackReferenceAnswer(results);
            }
            Object choicesObj = resp.get("choices");
            if (choicesObj instanceof List<?> choices && !choices.isEmpty()) {
                Object c0 = choices.get(0);
                if (c0 instanceof Map<?, ?> map) {
                    Object messageObj = map.get("message");
                    if (messageObj instanceof Map<?, ?> msg) {
                        Object content = msg.get("content");
                        if (content != null && !content.toString().isBlank()) {
                            return content.toString();
                        }
                    }
                }
            }
            return fallbackReferenceAnswer(results);
        } catch (Exception ex) {
            return fallbackReferenceAnswer(results);
        }
    }

    private List<WebSearchResult> parseResults(Map<String, Object> resp, int topK, List<String> domains) {
        List<WebSearchResult> parsed = new ArrayList<>();

        Object organicObj = resp.get("organic_results");
        if (organicObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    String title = toText(m.get("title"));
                    String link = toText(m.get("link"));
                    String snippet = toText(m.get("snippet"));
                    if (!title.isBlank() && !link.isBlank()) {
                        parsed.add(new WebSearchResult(title, link, snippet));
                    }
                }
            }
        }

        if (parsed.isEmpty()) {
            Object webPagesObj = resp.get("webPages");
            if (webPagesObj instanceof Map<?, ?> webPages) {
                Object valueObj = webPages.get("value");
                if (valueObj instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            String title = toText(m.get("name"));
                            String link = toText(m.get("url"));
                            String snippet = toText(m.get("snippet"));
                            if (!title.isBlank() && !link.isBlank()) {
                                parsed.add(new WebSearchResult(title, link, snippet));
                            }
                        }
                    }
                }
            }
        }

        Set<String> domainFilters = normalizeDomains(domains);
        Set<String> seenUrl = new HashSet<>();
        List<WebSearchResult> filtered = new ArrayList<>();
        for (WebSearchResult r : parsed) {
            if (!seenUrl.add(r.url())) {
                continue;
            }
            if (!domainFilters.isEmpty() && !matchDomain(r.url(), domainFilters)) {
                continue;
            }
            filtered.add(r);
            if (filtered.size() >= topK) {
                break;
            }
        }
        return filtered;
    }

    private String fallbackReferenceAnswer(List<WebSearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("参考资料摘要（未使用LLM合成）：\n");
        int limit = Math.min(results.size(), 5);
        for (int i = 0; i < limit; i++) {
            WebSearchResult r = results.get(i);
            sb.append(i + 1)
                    .append(". ")
                    .append(r.title())
                    .append(" - ")
                    .append(r.snippet() == null ? "" : r.snippet())
                    .append(" (")
                    .append(r.url())
                    .append(")\n");
        }
        return sb.toString();
    }

    private Set<String> normalizeDomains(List<String> domains) {
        if (domains == null || domains.isEmpty()) {
            return Set.of();
        }
        Set<String> set = new HashSet<>();
        for (String d : domains) {
            if (d != null && !d.isBlank()) {
                set.add(d.trim().toLowerCase(Locale.ROOT));
            }
        }
        return set;
    }

    private boolean matchDomain(String url, Set<String> domains) {
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        for (String d : domains) {
            if (u.contains(d)) {
                return true;
            }
        }
        return false;
    }

    private String toText(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
