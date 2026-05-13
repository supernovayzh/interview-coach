package com.example.interviewcoach.eval;

public interface EvalJudgeService {
    JudgeResult judge(EvalCase evalCase, String answer, RuleEvaluationContext context);

    record RuleEvaluationContext(double ruleScore,
                                 int questionCount,
                                 int matchedFocusPointCount,
                                 String ruleFeedback) {
    }

    record JudgeResult(Double score, String feedback, String rawText, String status) {
    }
}