package com.example.interviewcoach.service;

import com.example.interviewcoach.tool.InterviewToolContext;
import com.example.interviewcoach.tool.InterviewToolResult;

public interface PlannerService {
    /**
     * 使用 Planner 生成并执行计划，返回聚合结果
     */
    InterviewToolResult planAndExecute(InterviewToolContext context);
}
