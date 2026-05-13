package com.example.interviewcoach.eval;

import java.util.ArrayList;
import java.util.List;

public class EvalSuiteDefinition {
    private String name;
    private String description;
    private List<EvalCase> cases = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<EvalCase> getCases() {
        return cases;
    }

    public void setCases(List<EvalCase> cases) {
        this.cases = cases == null ? new ArrayList<>() : new ArrayList<>(cases);
    }
}