package com.example.interviewcoach.service;

public interface AnswerScoringService {
    ScoreResult score(String question, String answer, String ragContext);
}
