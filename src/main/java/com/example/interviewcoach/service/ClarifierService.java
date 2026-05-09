package com.example.interviewcoach.service;

public interface ClarifierService {
    /**
     * 生成一个用于追问用户的简短澄清问题（一句话）
     */
    String generateClarifyingQuestion(String question, String answer, String ragContext);
}
