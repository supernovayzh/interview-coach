package com.example.interviewcoach.service;

import com.example.interviewcoach.config.LlmOpenAiProperties;
import com.example.interviewcoach.model.ChatTurn;
import com.example.interviewcoach.model.InterviewSessionMemory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

@Service
public class LlmSummarizerService implements SummarizerService {

    private final WebClient webClient;
    private final LlmOpenAiProperties properties;

    public LlmSummarizerService(LlmOpenAiProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.getEndpoint()).build();
    }

    @Override
    public String summarize(InterviewSessionMemory memory) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return "(会话总结未启用，未配置 API Key)";
        }
        String prompt = buildPrompt(memory);
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
            return "(会话总结失败: " + ex.getMessage() + ")";
        }
    }

    private String buildPrompt(InterviewSessionMemory memory) {
        StringBuilder sb = new StringBuilder();
        sb.append("请对下面的面试会话做压缩复盘：给出\n1) 三行内的要点摘要；\n2) 总体评分（0-100）；\n3) 两条可执行的改进建议。\n输出要简洁条理清晰。\n");

        Deque<ChatTurn> turns = memory.getRecentTurns();
        List<String> lines = new ArrayList<>();
        for (ChatTurn t : turns) {
            lines.add("Q: " + t.getQuestion() + "\nA: " + (t.getAnswer() == null ? "" : t.getAnswer()));
        }
        if (!lines.isEmpty()) {
            sb.append("会话摘要：\n");
            for (String l : lines) {
                sb.append(l).append("\n");
            }
        }
        sb.append("请按要求输出，不要包含其他多余说明。");
        return sb.toString();
    }
}
