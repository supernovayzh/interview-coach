package com.example.interviewcoach.model;

import java.time.Instant;

public class ChatTurn {
    private String question;
    private String answer;
    private Instant timestamp;

    public ChatTurn() {}

    public ChatTurn(String question, String answer) {
        this.question = question;
        this.answer = answer;
        this.timestamp = Instant.now();
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
