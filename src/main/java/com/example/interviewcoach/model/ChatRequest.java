package com.example.interviewcoach.model;

public class ChatRequest {
    private String sessionId;
    private String question;
    private String targetCompany;
    private String companyTier;
    private String targetRole;
    private String focusAreas;
    private String resumeSummary;
    private String interviewGoal;

    public ChatRequest() {}

    public ChatRequest(String question) {
        this.question = question;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
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

    public String getEffectiveSessionId() {
        return isNotBlank(sessionId) ? sessionId : "default";
    }

    public boolean hasEnoughIntake() {
        return isNotBlank(targetCompany)
                || isNotBlank(companyTier)
                || isNotBlank(targetRole)
                || isNotBlank(focusAreas)
                || isNotBlank(resumeSummary)
                || isNotBlank(interviewGoal);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
