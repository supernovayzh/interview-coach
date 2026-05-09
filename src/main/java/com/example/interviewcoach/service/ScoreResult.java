package com.example.interviewcoach.service;

public class ScoreResult {
    private double score;
    private String feedback;

    public ScoreResult() {}

    public ScoreResult(double score, String feedback) {
        this.score = score;
        this.feedback = feedback;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }
}
