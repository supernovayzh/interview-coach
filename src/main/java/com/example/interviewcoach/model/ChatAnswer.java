package com.example.interviewcoach.model;

public class ChatAnswer {
    private String answer;
    private double score;
    private String scoreFeedback;
    private String nextAction;
    private String followUpQuestion;

    public ChatAnswer() {}

    public ChatAnswer(String answer, double score, String scoreFeedback) {
        this.answer = answer;
        this.score = score;
        this.scoreFeedback = scoreFeedback;
    }

    public String getNextAction() {
        return nextAction;
    }

    public void setNextAction(String nextAction) {
        this.nextAction = nextAction;
    }

    public String getFollowUpQuestion() {
        return followUpQuestion;
    }

    public void setFollowUpQuestion(String followUpQuestion) {
        this.followUpQuestion = followUpQuestion;
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
