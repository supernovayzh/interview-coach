package com.example.interviewcoach.tool;

import com.example.interviewcoach.service.ClarifierService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ClarifyQuestionTool implements InterviewTool {

    private final ClarifierService clarifierService;

    public ClarifyQuestionTool(ClarifierService clarifierService) {
        this.clarifierService = clarifierService;
    }

    @Override
    public String name() {
        return "clarify";
    }

    @Override
    public String description() {
        return "基于回答生成一句追问，用于继续深挖";
    }

    @Override
    public InterviewToolResult execute(InterviewToolContext context) {
        String question = context.getQuestion();
        String answer = context.getAnswer();
        String ragContext = context.getRagContext();
        String followUp = clarifierService.generateClarifyingQuestion(question, answer, ragContext);

        Map<String, Object> data = new HashMap<>();
        data.put("question", question);
        data.put("answer", answer);
        data.put("followUpQuestion", followUp);
        return InterviewToolResult.of(name(), data);
    }
}
