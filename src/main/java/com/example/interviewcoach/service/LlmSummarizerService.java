package com.example.interviewcoach.service;

import com.example.interviewcoach.config.LlmOpenAiProperties;
import com.example.interviewcoach.model.ChatTurn;
import com.example.interviewcoach.model.InterviewSessionMemory;
import com.example.interviewcoach.model.ConversationMessage;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public String generateSessionTitle(List<ConversationMessage> messages) {
        String fallback = fallbackTitle(messages);
        String compactFallback = compactTitle(fallback);
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return compactFallback;
        }
        String prompt = buildTitlePrompt(messages);
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", "你是会话标题生成器，只负责生成短标题。"),
                        Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 18,
                "temperature", 0.2
        );
        try {
            Map resp = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (resp == null) {
                return fallback;
            }
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
                                String title = sanitizeTitle(content.toString());
                                title = compactTitle(title);
                                if (title.isBlank() || looksLikeSentence(title)) {
                                    return compactFallback;
                                }
                                return title;
                            }
                        }
                    }
                }
            }
            return compactFallback;
        } catch (Exception ex) {
            return compactFallback;
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

    private String buildTitlePrompt(List<ConversationMessage> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("根据下面的用户输入，生成一个中文会话标题。要求：\n");
        sb.append("1. 只输出标题本身，不要加前缀，不要编号，不要解释。\n");
        sb.append("2. 标题控制在 4 到 12 个汉字或等价短语。\n");
        sb.append("3. 优先输出主题词，不要输出完整句子。\n");
        sb.append("4. 允许使用少量短语连接，例如 Java后端 / MySQL索引 / Redis追问。\n");
        sb.append("5. 不要出现标点结尾，不要像对话内容。\n");
        sb.append("6. 如果信息不足，就给出一个通用但明确的主题标题。\n");
        sb.append("用户输入：\n");

        List<ConversationMessage> safeMessages = messages == null ? List.of() : messages;
        List<String> userInputs = new ArrayList<>();
        for (ConversationMessage message : safeMessages) {
            if (message == null || message.getContent() == null) {
                continue;
            }
            if (!"user".equalsIgnoreCase(message.getRole())) {
                continue;
            }
            String content = normalizeTitleSource(message.getContent());
            if (content.isBlank()) {
                continue;
            }
            userInputs.add(content);
        }

        if (userInputs.isEmpty()) {
            sb.append("（无有效用户输入）\n");
        } else {
            int start = Math.max(0, userInputs.size() - 4);
            for (int i = start; i < userInputs.size(); i++) {
                sb.append("- ").append(userInputs.get(i)).append('\n');
            }
        }
        sb.append("只输出标题。");
        return sb.toString();
    }

    private String normalizeTitleSource(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return text;
        }
        text = text.replaceAll("[\r\n]+", " ");
        text = text.replaceAll("[\"'“”`]+", "");
        text = text.replaceAll("\s+", " ").trim();
        if (text.length() > 120) {
            text = text.substring(0, 120).trim();
        }
        return text;
    }

    private String sanitizeTitle(String raw) {
        if (raw == null) {
            return "";
        }
        String title = raw.trim();
        if (title.isEmpty()) {
            return title;
        }
        int newline = title.indexOf('\n');
        if (newline >= 0) {
            title = title.substring(0, newline).trim();
        }
        title = title.replaceAll("(?is)^#+\\s*", "");
        title = title.replaceAll("(?i)^标题\\s*[:：]\\s*", "");
        title = title.replaceAll("^[\"'“”`]+|[\"'“”`]+$", "");
        title = title.replaceAll("[。！？!?；;：:，,\\s]+$", "");
        title = title.replaceAll("\\s+", " ").trim();
        if (title.length() > 20) {
            title = title.substring(0, 20).trim();
        }
        return title;
    }

    private String compactTitle(String raw) {
        if (raw == null) {
            return "";
        }
        String title = raw.trim();
        if (title.isBlank()) {
            return "";
        }
        title = title.replaceAll("[\"'“”`、，,。！？!?；;:：\\[\\]（）()]+", " ");
        title = title.replaceAll("\s+", " ").trim();
        String[] parts = title.split(" ");
        if (parts.length > 1 && title.length() > 12) {
            String candidate = parts[0];
            for (int i = 1; i < parts.length && candidate.length() < 12; i++) {
                String next = parts[i];
                if (next.isBlank()) continue;
                if (candidate.length() + next.length() > 12) break;
                candidate += next;
            }
            title = candidate;
        }
        if (title.length() > 12) {
            title = title.substring(0, 12).trim();
        }
        return title;
    }

    private boolean looksLikeSentence(String title) {
        if (title == null || title.isBlank()) {
            return true;
        }
        return title.contains("我") || title.contains("你") || title.contains("可以") || title.contains("会") || title.contains("如果") || title.contains("因为") || title.contains("然后") || title.contains("面试官") || title.contains("回答");
    }

    private String fallbackTitle(List<ConversationMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "面试会话";
        }
        List<String> userInputs = new ArrayList<>();
        for (ConversationMessage message : messages) {
            if (message == null || message.getContent() == null) {
                continue;
            }
            if (!"user".equalsIgnoreCase(message.getRole())) {
                continue;
            }
            String candidate = normalizeTitleSource(message.getContent());
            if (candidate.isEmpty()) {
                continue;
            }
            userInputs.add(candidate);
        }
        if (userInputs.isEmpty()) {
            return "面试会话";
        }

        String keywordTitle = buildKeywordTitle(userInputs);
        if (!keywordTitle.isBlank()) {
            return keywordTitle;
        }
        String genericTitle = buildGenericTitle(userInputs);
        if (!genericTitle.isBlank()) {
            return genericTitle;
        }

        return "面试会话";
    }

    private String buildGenericTitle(List<String> userInputs) {
        if (userInputs == null || userInputs.isEmpty()) {
            return "";
        }
        String joined = String.join(" ", userInputs);
        if (joined.contains("面试")) {
            if (joined.contains("后端")) {
                return "后端面试";
            }
            if (joined.contains("Java")) {
                return "Java面试";
            }
            if (joined.contains("实习")) {
                return "面试实习";
            }
            return "面试会话";
        }
        if (joined.contains("实习")) {
            return "实习面试";
        }
        if (joined.contains("项目")) {
            return "项目面试";
        }
        if (joined.contains("技术栈")) {
            return "技术栈面试";
        }
        return "";
    }

    private String buildKeywordTitle(List<String> userInputs) {
        Set<String> topics = new LinkedHashSet<>();
        for (String text : userInputs) {
            addKeywordIfPresent(topics, text, "Java", "Java");
            addKeywordIfPresent(topics, text, "Spring Boot", "SpringBoot");
            addKeywordIfPresent(topics, text, "MySQL", "MySQL");
            addKeywordIfPresent(topics, text, "Redis", "Redis");
            addKeywordIfPresent(topics, text, "RabbitMQ", "RabbitMQ");
            addKeywordIfPresent(topics, text, "Kafka", "Kafka");
            addKeywordIfPresent(topics, text, "JVM", "JVM");
            addKeywordIfPresent(topics, text, "MyBatis", "MyBatis");
            addKeywordIfPresent(topics, text, "微服务", "微服务");
            addKeywordIfPresent(topics, text, "网络", "网络");
            addKeywordIfPresent(topics, text, "并发", "并发");
            addKeywordIfPresent(topics, text, "索引", "索引");
            addKeywordIfPresent(topics, text, "事务", "事务");
            addKeywordIfPresent(topics, text, "缓存", "缓存");
            addKeywordIfPresent(topics, text, "实习", "实习");
            addKeywordIfPresent(topics, text, "校招", "校招");
            addKeywordIfPresent(topics, text, "后端", "后端");
        }

        if (topics.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (String topic : topics) {
            if (sb.length() + topic.length() > 12) {
                break;
            }
            sb.append(topic);
        }
        return sb.length() == 0 ? "" : sb.toString();
    }

    private void addKeywordIfPresent(Set<String> topics, String text, String keyword, String label) {
        if (text == null || text.isBlank() || topics.contains(label)) {
            return;
        }
        if (text.contains(keyword)) {
            topics.add(label);
        }
    }
}
