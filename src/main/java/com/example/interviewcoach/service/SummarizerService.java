package com.example.interviewcoach.service;

import com.example.interviewcoach.model.InterviewSessionMemory;

public interface SummarizerService {
    /**
     * 对一次面试会话做总结，返回简洁的复盘要点（要点、总分、两条改进建议）
     */
    String summarize(InterviewSessionMemory memory);
}
