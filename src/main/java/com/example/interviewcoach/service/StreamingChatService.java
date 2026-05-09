package com.example.interviewcoach.service;

import com.example.interviewcoach.model.ChatRequest;
import reactor.core.publisher.Flux;

public interface StreamingChatService {
    Flux<String> stream(ChatRequest request);
}
