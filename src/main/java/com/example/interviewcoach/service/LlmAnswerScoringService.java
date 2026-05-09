package com.example.interviewcoach.service;

import com.example.interviewcoach.config.LlmOpenAiProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@Primary
public class LlmAnswerScoringService implements AnswerScoringService {

    private final WebClient webClient;
    private final LlmOpenAiProperties properties;

    public LlmAnswerScoringService(LlmOpenAiProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.getEndpoint()).build();
    }

    @Override
    public ScoreResult score(String question, String answer, String ragContext) {
        if (answer == null || answer.isBlank()) return new ScoreResult(0.0, "空答案");
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return new ScoreResult(0.0, "[LLM评分未启用] 未配置 API Key");
        }

        String prompt = buildScoringPrompt(question, answer, ragContext);

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", properties.getSystemPrompt()),
                        Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", Math.min(256, properties.getMaxTokens())
        );

        try {
            Map resp = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null) return new ScoreResult(0.0, "[LLM评分错误] 空响应");
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
                                // 期望 LLM 返回格式: SCORE: <0-100>\nFEEDBACK: <短句>
                                double score = parseScoreFromText(text);
                                String feedback = parseFeedbackFromText(text);
                                return new ScoreResult(score, feedback);
                            }
                        }
                    }
                }
            }
            return new ScoreResult(0.0, "[LLM评分错误] 无候选结果");
        } catch (Exception ex) {
            return new ScoreResult(0.0, "[LLM评分异常] " + ex.getMessage());
        }
    }

    private String buildScoringPrompt(String question, String answer, String ragContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("请对下面的面试回答进行打分（0-100，整数为佳），并给出一句简短的反馈（中文）。\n");
        sb.append("要求：评分考虑回答的正确性、完整性、可落地性以及与问题的匹配度。输出格式严格为：\n");
        sb.append("SCORE: <分数>\nFEEDBACK: <一句话反馈>\n");
        sb.append("问题：\n").append(question).append("\n");
        sb.append("回答：\n").append(answer).append("\n");
        if (ragContext != null && !ragContext.isBlank()) {
            sb.append("参考材料：\n").append(ragContext).append("\n");
        }
        return sb.toString();
    }

    private double parseScoreFromText(String text) {
        // try find 'SCORE:'
        try {
            int idx = text.indexOf("SCORE:");
            if (idx >= 0) {
                String sub = text.substring(idx + 6).trim();
                String[] parts = sub.split("\\s+|\\n");
                if (parts.length > 0) {
                    String num = parts[0].replaceAll("[^0-9.-]", "");
                    if (!num.isBlank()) {
                        return Math.max(0.0, Math.min(100.0, Double.parseDouble(num)));
                    }
                }
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    private String parseFeedbackFromText(String text) {
        int idx = text.indexOf("FEEDBACK:");
        if (idx >= 0) {
            return text.substring(idx + 9).trim().replaceAll("\n", " ");
        }
        // fallback: return first line
        String[] lines = text.split("\\n");
        return lines.length > 0 ? lines[0] : text;
    }
}
