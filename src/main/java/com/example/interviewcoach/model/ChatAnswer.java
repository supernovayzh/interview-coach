package com.example.interviewcoach.model;

public class ChatAnswer {
    private String answer;
    private double score;
    private String scoreFeedback;

    public ChatAnswer() {}

    public ChatAnswer(String answer, double score, String scoreFeedback) {
        this.answer = answer;
        this.score = score;
        this.scoreFeedback = scoreFeedback;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getScoreFeedback() {
        return scoreFeedback;
    }

    public void setScoreFeedback(String scoreFeedback) {
        this.scoreFeedback = scoreFeedback;
    }
}
