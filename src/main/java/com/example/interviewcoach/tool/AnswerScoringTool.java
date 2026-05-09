package com.example.interviewcoach.tool;

import com.example.interviewcoach.service.AnswerScoringService;
import com.example.interviewcoach.service.ScoreResult;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AnswerScoringTool implements InterviewTool {

    private final AnswerScoringService scoringService;

    public AnswerScoringTool(AnswerScoringService scoringService) {
        this.scoringService = scoringService;
    }

    @Override
    public String name() {
        return "score";
    }

    @Override
    public String description() {
        return "对用户回答进行评分并给出简短反馈";
    }

    @Override
    public InterviewToolResult execute(InterviewToolContext context) {
        String question = context.getQuestion();
        String answer = context.getAnswer();
        String ragContext = context.getRagContext();
        ScoreResult result = scoringService.score(question, answer, ragContext);

        Map<String, Object> data = new HashMap<>();
        data.put("question", question);
        data.put("answer", answer);
        data.put("score", result.getScore());
        data.put("feedback", result.getFeedback());
        return InterviewToolResult.of(name(), data);
    }
}
