package com.example.interviewcoach.eval;

import java.util.ArrayList;
import java.util.List;

public class EvalRunResponse {
    private String runId;
    private String suiteName;
    private String description;
    private String startedAt;
    private long durationMs;
    private int totalCases;
    private int passedCases;
    private int failedCases;
    private double averageScore;
    private List<EvalCaseResult> cases = new ArrayList<>();

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getSuiteName() {
        return suiteName;
    }

    public void setSuiteName(String suiteName) {
        this.suiteName = suiteName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public int getTotalCases() {
        return totalCases;
    }

    public void setTotalCases(int totalCases) {
        this.totalCases = totalCases;
    }

    public int getPassedCases() {
        return passedCases;
    }

    public void setPassedCases(int passedCases) {
        this.passedCases = passedCases;
    }

    public int getFailedCases() {
        return failedCases;
    }

    public void setFailedCases(int failedCases) {
        this.failedCases = failedCases;
    }

    public double getAverageScore() {
        return averageScore;
    }

    public void setAverageScore(double averageScore) {
        this.averageScore = averageScore;
    }

    public List<EvalCaseResult> getCases() {
        return cases;
    }

    public void setCases(List<EvalCaseResult> cases) {
        this.cases = cases == null ? new ArrayList<>() : new ArrayList<>(cases);
    }
}