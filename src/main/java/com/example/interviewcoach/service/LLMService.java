package com.example.interviewcoach.service;

import com.example.interviewcoach.config.LlmOpenAiProperties;
import com.example.interviewcoach.model.ChatRequest;
import com.example.interviewcoach.model.ConversationStage;
import com.example.interviewcoach.model.RagChunk;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class LLMService implements ChatService {

    private final WebClient webClient;
    private final LlmOpenAiProperties properties;
    private final InterviewMemoryService memoryService;
    private final RagKnowledgeService ragKnowledgeService;

    public LLMService(LlmOpenAiProperties properties,
                      WebClient.Builder webClientBuilder,
                      InterviewMemoryService memoryService,
                      RagKnowledgeService ragKnowledgeService) {
        this.properties = properties;
        this.memoryService = memoryService;
        this.ragKnowledgeService = ragKnowledgeService;
        this.webClient = webClientBuilder.baseUrl(properties.getEndpoint()).build();
    }

    @SuppressWarnings("unchecked")
    public String ask(ChatRequest request) {
        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            return "请先提供一个明确的问题。";
        }

        String sessionId = request.getEffectiveSessionId();
        ConversationStage stage = memoryService.getStage(sessionId);

        if (request.hasEnoughIntake()) {
            memoryService.upsertProfile(sessionId, request);
            stage = memoryService.getStage(sessionId);
        }

        if (!memoryService.getOrCreate(sessionId).hasEnoughProfile()) {
            return buildIntakePrompt(
                    memoryService.buildMissingFields(sessionId),
                    memoryService.buildContext(sessionId),
                    stage
            );
        }

        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return "[LLM disabled] API key not configured. Question: " + request.getQuestion();
        }

        String ragContext = buildRagContext(request, sessionId);

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                    Map.of("role", "system", "content", buildSystemPrompt(stage)),
                        Map.of("role", "user", "content", buildUserPrompt(request, memoryService.buildContext(sessionId), ragContext))
                ),
                "max_tokens", properties.getMaxTokens()
        );

        try {
            Map<String, Object> resp = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null) return "[LLM error] empty response";

            Object choicesObj = resp.get("choices");
            if (choicesObj instanceof List) {
                List choices = (List) choicesObj;
                if (!choices.isEmpty()) {
                    Object c0 = choices.get(0);
                    if (c0 instanceof Map) {
                        Map m = (Map) c0;
                        Object message = m.get("message");
                        if (message instanceof Map) {
                            Object content = ((Map) message).get("content");
                            if (content != null) {
                                String answer = content.toString();
                                memoryService.addTurn(sessionId, request.getQuestion(), answer);
                                return answer;
                            }
                        }
                    }
                }
            }
            return "[LLM error] no choice found";
        } catch (Exception e) {
            return "[LLM error] " + e.getMessage();
        }
    }

    private String buildUserPrompt(ChatRequest request, String memoryContext, String ragContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请扮演Java后端面试陪练，根据以下画像进行回答和追问。\n");
        if (memoryContext != null && !memoryContext.isBlank()) {
            prompt.append(memoryContext).append('\n');
        }
        if (ragContext != null && !ragContext.isBlank()) {
            prompt.append("本地八股/面经检索资料：\n").append(ragContext).append('\n');
        }
        appendIfNotBlank(prompt, "本轮补充目标公司", request.getTargetCompany());
        appendIfNotBlank(prompt, "本轮补充公司类型/规模", request.getCompanyTier());
        appendIfNotBlank(prompt, "本轮补充目标岗位", request.getTargetRole());
        appendIfNotBlank(prompt, "本轮补充重点考察方向", request.getFocusAreas());
        appendIfNotBlank(prompt, "本轮补充简历摘要", request.getResumeSummary());
        appendIfNotBlank(prompt, "本轮补充当前目标", request.getInterviewGoal());
        prompt.append("用户当前问题：").append(request.getQuestion());
        return prompt.toString();
    }

    private String buildRagContext(ChatRequest request, String memoryContext) {
        if (ragKnowledgeService == null) {
            return "";
        }
        StringBuilder query = new StringBuilder();
        query.append(request.getQuestion()).append(' ');
        appendQueryIfNotBlank(query, request.getTargetCompany());
        appendQueryIfNotBlank(query, request.getCompanyTier());
        appendQueryIfNotBlank(query, request.getTargetRole());
        appendQueryIfNotBlank(query, request.getFocusAreas());
        appendQueryIfNotBlank(query, request.getInterviewGoal());
        if (memoryContext != null && !memoryContext.isBlank()) {
            query.append(memoryContext);
        }
        List<RagChunk> results = ragKnowledgeService.search(query.toString(), 3);
        if (results.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            RagChunk chunk = results.get(i);
            builder.append("【").append(i + 1).append("】");
            builder.append(chunk.getTitle() == null ? "未命名" : chunk.getTitle());
            builder.append(" (来源: ").append(chunk.getSourceFile()).append(", 分数: ")
                    .append(String.format(java.util.Locale.ROOT, "%.2f", chunk.getScore())).append(")\n");
            builder.append(chunk.preview(650)).append("\n");
        }
        return builder.toString();
    }

    private void appendQueryIfNotBlank(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(value).append(' ');
        }
    }

    private String buildSystemPrompt(ConversationStage stage) {
        String base = properties.getSystemPrompt();
        if (stage == ConversationStage.INIT || stage == ConversationStage.COLLECTING_PROFILE) {
            return base + " 你当前处于信息收集阶段，必须优先确认用户的面试画像，不要直接进入正式问答。";
        }
        if (stage == ConversationStage.READY || stage == ConversationStage.INTERVIEWING) {
            return base + " 你当前处于正式陪练阶段，需要结合用户画像连续追问，并对回答进行工程化点评。";
        }
        return base;
    }

    private String buildIntakePrompt(String missingFields, String memoryContext, ConversationStage stage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("这是一个Java后端面试陪练场景。当前会话阶段：").append(stage).append("。\n");
        prompt.append("先不要进入正式面试，请先收集用户画像中的缺失信息。\n");
        if (missingFields != null && !missingFields.isBlank()) {
            prompt.append("当前缺失项：").append(missingFields).append("。\n");
        }
        prompt.append("需要收集的信息包括：\n")
                .append("1. 目标公司（例如：美团、阿里、字节、腾讯、京东、蚂蚁、百度）\n")
                .append("2. 公司类型/规模（大厂、中厂、小厂、创业公司）\n")
                .append("3. 目标岗位（Java后端实习/校招/社招、后端开发、平台开发等）\n")
                .append("4. 重点考察方向（例如：Redis、MySQL、计算机网络、JVM、Spring、并发、MQ）\n")
                .append("5. 简历摘要（把与面试相关的项目和经历简单说明）\n")
                .append("6. 本次目标（模拟面试、查漏补缺、专项训练、复盘简历）\n");
        if (memoryContext != null && !memoryContext.isBlank()) {
            prompt.append("当前已知信息：\n").append(memoryContext).append('\n');
        }
        prompt.append("请用简洁、专业、一次只问少量关键问题的方式，先把缺失画像补齐，再继续后续陪练。");
        return prompt.toString();
    }

    private void appendIfNotBlank(StringBuilder builder, String label, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(label).append("：").append(value).append('\n');
        }
    }
}
