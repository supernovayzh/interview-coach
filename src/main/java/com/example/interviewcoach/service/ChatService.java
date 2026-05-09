package com.example.interviewcoach.service;

import com.example.interviewcoach.model.ChatAnswer;
import com.example.interviewcoach.model.ChatRequest;

public interface ChatService {
    ChatAnswer ask(ChatRequest request);
}
