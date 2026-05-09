package com.example.interviewcoach.model;

import java.util.ArrayDeque;
import java.util.Deque;

public class InterviewSessionMemory {
    private final InterviewProfile profile = new InterviewProfile();
    private final Deque<ChatTurn> recentTurns = new ArrayDeque<>();
    private int maxTurns = 6;
    private ConversationStage stage = ConversationStage.INIT;

    public InterviewProfile getProfile() {
        return profile;
    }

    public Deque<ChatTurn> getRecentTurns() {
        return recentTurns;
    }

    public ConversationStage getStage() {
        return stage;
    }

    public void setStage(ConversationStage stage) {
        this.stage = stage;
    }

    public void mergeRequest(ChatRequest request) {
        profile.mergeFrom(request);
        // Do not force the session into COLLECTING_PROFILE when profile is incomplete.
        // Keep the current stage unless the profile becomes complete, in which case
        // we mark the session as READY.
        if (profile.isComplete()) {
            this.stage = ConversationStage.READY;
        }
    }

    public void addTurn(String question, String answer) {
        if (question == null || question.isBlank()) {
            return;
        }
        if (profile.isComplete() && stage != ConversationStage.INTERVIEWING) {
            stage = ConversationStage.INTERVIEWING;
        }
        recentTurns.addLast(new ChatTurn(question, answer));
        while (recentTurns.size() > maxTurns) {
            recentTurns.removeFirst();
        }
    }

    public boolean hasEnoughProfile() {
        return profile.isComplete();
    }
}
