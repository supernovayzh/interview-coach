package com.example.interviewcoach.tool;

import com.example.interviewcoach.model.InterviewSessionMemory;
import com.example.interviewcoach.service.SummarizerService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class SessionSummaryTool implements InterviewTool {

    private final SummarizerService summarizerService;

    public SessionSummaryTool(SummarizerService summarizerService) {
        this.summarizerService = summarizerService;
    }

    @Override
    public String name() {
        return "summarize";
    }

    @Override
    public String description() {
        return "基于当前会话记忆生成复盘总结与建议";
    }

    @Override
    public InterviewToolResult execute(InterviewToolContext context) {
        InterviewSessionMemory memory = context.getMemory();
        String summary = summarizerService.summarize(memory);

        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", context.getSessionId());
        data.put("summary", summary);
        return InterviewToolResult.of(name(), data);
    }
}
