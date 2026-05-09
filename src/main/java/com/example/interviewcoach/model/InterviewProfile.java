package com.example.interviewcoach.model;

public class InterviewProfile {
    private String targetCompany;
    private String companyTier;
    private String targetRole;
    private String focusAreas;
    private String resumeSummary;
    private String interviewGoal;

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

    public void mergeFrom(ChatRequest request) {
        if (request == null) {
            return;
        }
        if (isNotBlank(request.getTargetCompany())) {
            this.targetCompany = request.getTargetCompany();
        }
        if (isNotBlank(request.getCompanyTier())) {
            this.companyTier = request.getCompanyTier();
        }
        if (isNotBlank(request.getTargetRole())) {
            this.targetRole = request.getTargetRole();
        }
        if (isNotBlank(request.getFocusAreas())) {
            this.focusAreas = request.getFocusAreas();
        }
        if (isNotBlank(request.getResumeSummary())) {
            this.resumeSummary = request.getResumeSummary();
        }
        if (isNotBlank(request.getInterviewGoal())) {
            this.interviewGoal = request.getInterviewGoal();
        }
    }

    public boolean isComplete() {
        return isNotBlank(targetCompany)
                && isNotBlank(companyTier)
                && isNotBlank(targetRole)
                && isNotBlank(focusAreas)
                && isNotBlank(resumeSummary)
                && isNotBlank(interviewGoal);
    }

    public String missingFields() {
        StringBuilder sb = new StringBuilder();
        appendMissing(sb, "目标公司", targetCompany);
        appendMissing(sb, "公司类型/规模", companyTier);
        appendMissing(sb, "目标岗位", targetRole);
        appendMissing(sb, "重点考察方向", focusAreas);
        appendMissing(sb, "简历摘要", resumeSummary);
        appendMissing(sb, "本次目标", interviewGoal);
        return sb.toString();
    }

    private void appendMissing(StringBuilder sb, String label, String value) {
        if (!isNotBlank(value)) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(label);
        }
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
