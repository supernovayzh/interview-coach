package com.example.interviewcoach.service;

import com.example.interviewcoach.config.LlmOpenAiProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class LlmClarifierService implements ClarifierService {

    private final WebClient webClient;
    private final LlmOpenAiProperties properties;

    public LlmClarifierService(LlmOpenAiProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.getEndpoint()).build();
    }

    @Override
    public String generateClarifyingQuestion(String question, String answer, String ragContext) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return "(追问未启用，未配置 API Key)";
        }
        String prompt = buildPrompt(question, answer, ragContext);
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", properties.getSystemPrompt()),
                        Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 60
        );

        try {
            Map resp = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (resp == null) return "";
            Object choicesObj = resp.get("choices");
            if (choicesObj instanceof List) {
                List choices = (List) choicesObj;
                if (!choices.isEmpty()) {
                    Object c0 = choices.get(0);
                    if (c0 instanceof Map) {
                        Object message = ((Map) c0).get("message");
                        if (message instanceof Map) {
                            Object content = ((Map) message).get("content");
                            if (content != null) return content.toString().trim();
                        }
                    }
                }
            }
            return "";
        } catch (Exception ex) {
            return "(生成追问失败: " + ex.getMessage() + ")";
        }
    }

    private String buildPrompt(String question, String answer, String ragContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("请基于下面的面试问题与回答生成一个简洁的追问（只需一句话），用于澄清或引导考察深度。例如：要求候选人补充具体实现细节、场景或边界条件。\n");
        sb.append("问题：\n").append(question).append("\n");
        sb.append("回答：\n").append(answer).append("\n");
        if (ragContext != null && !ragContext.isBlank()) sb.append("参考材料：\n").append(ragContext).append("\n");
        sb.append("只输出追问句子，不要包含额外解释。");
        return sb.toString();
    }
}
