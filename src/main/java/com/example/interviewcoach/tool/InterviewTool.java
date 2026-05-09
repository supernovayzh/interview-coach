package com.example.interviewcoach.tool;

public interface InterviewTool {
    String name();

    String description();

    InterviewToolResult execute(InterviewToolContext context);
}
