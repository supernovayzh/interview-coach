package com.example.interviewcoach.service;

import com.example.interviewcoach.config.LlmOpenAiProperties;
import com.example.interviewcoach.tool.InterviewToolContext;
import com.example.interviewcoach.tool.InterviewToolResult;
import com.example.interviewcoach.tool.ToolRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmPlannerService implements PlannerService {

    private final WebClient webClient;
    private final LlmOpenAiProperties properties;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper mapper = new ObjectMapper();

    public LlmPlannerService(LlmOpenAiProperties properties, WebClient.Builder webClientBuilder, ToolRegistry toolRegistry) {
        this.properties = properties;
        this.toolRegistry = toolRegistry;
        this.webClient = webClientBuilder.baseUrl(properties.getEndpoint()).build();
    }

    @Override
    public InterviewToolResult planAndExecute(InterviewToolContext context) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            // fallback: return empty result
            return InterviewToolResult.of("planner", Map.of("error", "apiKey missing"));
        }

        String prompt = buildPlannerPrompt(context);

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", properties.getSystemPrompt()),
                        Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", Math.min(512, properties.getMaxTokens())
        );

        try {
            Map resp = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));
            if (resp == null) return InterviewToolResult.of("planner", Map.of("error", "empty response"));
            Object choicesObj = resp.get("choices");
            if (choicesObj instanceof List) {
                List choices = (List) choicesObj;
                if (!choices.isEmpty()) {
                    Object c0 = choices.get(0);
                    if (c0 instanceof Map) {
                        Object message = ((Map) c0).get("message");
                        if (message instanceof Map) {
                            Object content = ((Map) message).get("content");
                            if (content != null) {
                                String text = content.toString().trim();
                                // try parse JSON
                                Map<String, Object> planMap = parsePlan(text);
                                if (planMap == null) {
                                    return InterviewToolResult.of("planner", Map.of("error", "invalid plan format", "raw", text));
                                }
                                // execute steps
                                List<Map<String, Object>> steps = (List<Map<String, Object>>) planMap.getOrDefault("steps", List.of());
                                List<Map<String, Object>> stepResults = new ArrayList<>();
                                String finalAction = (String) planMap.getOrDefault("finalAction", "");
                                // limit
                                int max = Math.min(steps.size(), 6);
                                for (int i = 0; i < max; i++) {
                                    Map<String, Object> step = steps.get(i);
                                    String tool = (String) step.get("tool");
                                    Map<String, Object> args = (Map<String, Object>) step.getOrDefault("args", Map.of());
                                    // validate tool
                                    try {
                                        InterviewToolContext stepContext = context;
                                        for (Map.Entry<String, Object> entry : args.entrySet()) {
                                            stepContext = stepContext.withAttribute(entry.getKey(), entry.getValue());
                                        }
                                        InterviewToolResult r = toolRegistry.invoke(tool, stepContext);
                                        Map<String, Object> m = new HashMap<>();
                                        m.put("tool", tool);
                                        m.put("result", r.getData());
                                        stepResults.add(m);
                                    } catch (Exception ex) {
                                        Map<String, Object> m = new HashMap<>();
                                        m.put("tool", tool);
                                        m.put("error", ex.getMessage());
                                        stepResults.add(m);
                                    }
                                }

                                // aggregate common outputs
                                Map<String, Object> aggregated = new HashMap<>();
                                aggregated.put("plan", planMap);
                                aggregated.put("steps", stepResults);
                                aggregated.put("finalAction", finalAction);

                                // extract score/feedback/followUp/summary if present in steps
                                for (Map<String, Object> sr : stepResults) {
                                    Object toolName = sr.get("tool");
                                    Object res = sr.get("result");
                                    if (toolName == null || res == null) continue;
                                    String tn = toolName.toString();
                                    if ("score".equals(tn) && res instanceof Map) {
                                        Object sc = ((Map) res).get("score");
                                        Object fb = ((Map) res).get("feedback");
                                        aggregated.put("score", sc);
                                        aggregated.put("feedback", fb == null ? "" : fb.toString());
                                    }
                                    if ("clarify".equals(tn) && res instanceof Map) {
                                        Object fup = ((Map) res).get("followUpQuestion");
                                        aggregated.put("followUpQuestion", fup == null ? "" : fup.toString());
                                    }
                                    if ("summarize".equals(tn) && res instanceof Map) {
                                        Object sum = ((Map) res).get("summary");
                                        aggregated.put("summary", sum == null ? "" : sum.toString());
                                    }
                                    if ("web_search".equals(tn) && res instanceof Map) {
                                        Object ref = ((Map) res).get("referenceAnswer");
                                        Object webContext = ((Map) res).get("context");
                                        if (ref != null) {
                                            aggregated.put("referenceAnswer", ref.toString());
                                        }
                                        if (webContext != null) {
                                            aggregated.put("webContext", webContext.toString());
                                        }
                                    }
                                }

                                return InterviewToolResult.of("planner", aggregated);
                            }
                        }
                    }
                }
            }
            return InterviewToolResult.of("planner", Map.of("error", "no choice"));
        } catch (Exception ex) {
            return InterviewToolResult.of("planner", Map.of("error", ex.getMessage()));
        }
    }

    private Map<String, Object> parsePlan(String text) {
        try {
            // try parse as JSON directly
            return mapper.readValue(text, new TypeReference<Map<String, Object>>(){});
        } catch (Exception ex) {
            // try to find JSON substring
            int s = text.indexOf('{');
            int e = text.lastIndexOf('}');
            if (s >= 0 && e > s) {
                String sub = text.substring(s, e + 1);
                try {
                    return mapper.readValue(sub, new TypeReference<Map<String, Object>>(){});
                } catch (Exception ex2) {
                    return null;
                }
            }
            return null;
        }
    }

    private String buildPlannerPrompt(InterviewToolContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个面试流程规划器。系统提供以下工具：\n");
        for (var t : toolRegistry.listTools()) {
            sb.append("- ").append(t.name()).append(": ").append(t.description()).append("\n");
        }
        sb.append("\n基于下面信息，输出一个 JSON 计划，字段说明：\n");
        sb.append("{\n  \"steps\": [ { \"tool\": \"search|web_search|score|clarify|summarize\", \"args\": {...} } , ... ],\n  \"finalAction\": \"ASK_FOLLOWUP|NEXT_QUESTION|SUGGEST_IMPROVEMENT|END\"\n}\n");
        sb.append("不要包含解释，严格返回 JSON（如果含多余文本，后端会尝试抽取）。\n\n");
        sb.append("上下文信息：\n问题：\n").append(context.getQuestion()).append("\n回答：\n").append(context.getAnswer()).append("\nRAG：\n").append(context.getRagContext()).append("\n");
        sb.append("会话摘要（最近若干轮）：\n");
        var turns = context.getMemory().getRecentTurns();
        for (var t : turns) {
            sb.append("Q: ").append(t.getQuestion()).append("\nA: ").append(t.getAnswer()).append("\n");
        }
        sb.append("\n请生成不超过 6 步的计划，可按需先用 web_search 获取外部事实依据；优先输出 score，然后根据 score 决定是否调用 clarify；仅在会话末尾调用 summarize。\n");
        return sb.toString();
    }
}
