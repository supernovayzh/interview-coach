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
                            if (content != null) return normalizeSingleQuestion(content.toString());
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
        sb.append("请基于下面的面试问题与回答生成一个简洁的追问，只能输出一个问题句。\n");
        sb.append("要求：只问一个具体问题，不要连问多个问题，不要给出答案，不要加入解释性段落。\n");
        sb.append("如果你想到多个追问，只保留最重要的一个。\n");
        sb.append("问题：\n").append(question).append("\n");
        sb.append("回答：\n").append(answer).append("\n");
        if (ragContext != null && !ragContext.isBlank()) sb.append("参考材料：\n").append(ragContext).append("\n");
        sb.append("只输出追问句子，不要包含额外解释。");
        return sb.toString();
    }

    private String normalizeSingleQuestion(String content) {
        if (content == null) {
            return "";
        }
        String text = content.trim();
        if (text.isEmpty()) {
            return text;
        }

        int firstQuestion = firstQuestionMarkIndex(text);
        if (firstQuestion >= 0) {
            return text.substring(0, firstQuestion + 1).trim();
        }

        int newline = text.indexOf('\n');
        if (newline >= 0) {
            return text.substring(0, newline).trim();
        }

        return text;
    }

    private int firstQuestionMarkIndex(String text) {
        int chinese = text.indexOf('？');
        int english = text.indexOf('?');
        if (chinese < 0) return english;
        if (english < 0) return chinese;
        return Math.min(chinese, english);
    }
}
