package com.example.interviewcoach.eval;

import java.util.ArrayList;
import java.util.List;

public class EvalRunRequest {
    private String suiteName = "mini-interview-agent";
    private List<String> caseIds = new ArrayList<>();
    private double passThreshold = 70.0;

    public String getSuiteName() {
        return suiteName;
    }

    public void setSuiteName(String suiteName) {
        this.suiteName = suiteName;
    }

    public List<String> getCaseIds() {
        return caseIds;
    }

    public void setCaseIds(List<String> caseIds) {
        this.caseIds = caseIds == null ? new ArrayList<>() : new ArrayList<>(caseIds);
    }

    public double getPassThreshold() {
        return passThreshold;
    }

    public void setPassThreshold(double passThreshold) {
        this.passThreshold = passThreshold;
    }
}