package com.example.interviewcoach.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "llm.openai")
public class LlmOpenAiProperties {

    private String apiKey;
    private String model;
    private String endpoint;
    private Integer maxTokens = 1024;
    private String systemPrompt = "你要扮演Java后端面试官，而不是替用户回答的陪练。默认目标是按面试官视角与用户进行一问一答的互动：每次只问一个具体问题，等待用户回答后再决定是追问、给建议，还是进入下一个问题。不要一次提出多个问题，不要自问自答，不要先替用户总结答案。";

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
