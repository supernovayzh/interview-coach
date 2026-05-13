package com.example.interviewcoach.eval;

import java.util.ArrayList;
import java.util.List;

public class EvalCase {
    private String id;
    private String title;
    private String sessionId;
    private String userInput;
    private String targetCompany;
    private String companyTier;
    private String targetRole;
    private String focusAreas;
    private String resumeSummary;
    private String interviewGoal;
    private String expectedBehavior;
    private List<String> focusPoints = new ArrayList<>();
    private List<String> requiredPhrases = new ArrayList<>();
    private List<String> forbiddenPhrases = new ArrayList<>();
    private boolean mustAskOneQuestion = true;
    private int maxQuestionMarks = 1;
    private String difficulty = "medium";

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserInput() {
        return userInput;
    }

    public void setUserInput(String userInput) {
        this.userInput = userInput;
    }

    public String getTargetCompany() {
        return targetCompany;
    }

    public void setTargetCompany(String targetCompany) {
        this.targetCompany = targetCompany;
    }

    public String getCompanyTier() {
        return companyTier;
    }

    public void setCompanyTier(String companyTier) {
        this.companyTier = companyTier;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(String targetRole) {
        this.targetRole = targetRole;
    }

    public String getFocusAreas() {
        return focusAreas;
    }

    public void setFocusAreas(String focusAreas) {
        this.focusAreas = focusAreas;
    }

    public String getResumeSummary() {
        return resumeSummary;
    }

    public void setResumeSummary(String resumeSummary) {
        this.resumeSummary = resumeSummary;
    }

    public String getInterviewGoal() {
        return interviewGoal;
    }

    public void setInterviewGoal(String interviewGoal) {
        this.interviewGoal = interviewGoal;
    }

    public String getExpectedBehavior() {
        return expectedBehavior;
    }

    public void setExpectedBehavior(String expectedBehavior) {
        this.expectedBehavior = expectedBehavior;
    }

    public List<String> getFocusPoints() {
        return focusPoints;
    }

    public void setFocusPoints(List<String> focusPoints) {
        this.focusPoints = focusPoints == null ? new ArrayList<>() : new ArrayList<>(focusPoints);
    }

    public List<String> getRequiredPhrases() {
        return requiredPhrases;
    }

    public void setRequiredPhrases(List<String> requiredPhrases) {
        this.requiredPhrases = requiredPhrases == null ? new ArrayList<>() : new ArrayList<>(requiredPhrases);
    }

    public List<String> getForbiddenPhrases() {
        return forbiddenPhrases;
    }

    public void setForbiddenPhrases(List<String> forbiddenPhrases) {
        this.forbiddenPhrases = forbiddenPhrases == null ? new ArrayList<>() : new ArrayList<>(forbiddenPhrases);
    }

    public boolean isMustAskOneQuestion() {
        return mustAskOneQuestion;
    }

    public void setMustAskOneQuestion(boolean mustAskOneQuestion) {
        this.mustAskOneQuestion = mustAskOneQuestion;
    }

    public int getMaxQuestionMarks() {
        return maxQuestionMarks;
    }

    public void setMaxQuestionMarks(int maxQuestionMarks) {
        this.maxQuestionMarks = maxQuestionMarks;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
}