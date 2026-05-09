package com.example.interviewcoach.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "llm.openai")
public class LlmOpenAiProperties {

    private String apiKey;
    private String model;
    private String endpoint;
    private Integer maxTokens = 512;
    private String systemPrompt = "你是一个专业的Java后端面试陪练。你要先根据用户画像判断面试场景，再给出符合公司层级和考察重点的回答。回答风格要简洁、专业、结构化，优先给出可落地的工程实践建议。";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
}
