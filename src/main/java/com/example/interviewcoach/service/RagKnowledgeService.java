package com.example.interviewcoach.service;

import com.example.interviewcoach.model.RagChunk;

import java.util.List;

public interface RagKnowledgeService {
    List<RagChunk> search(String query, int topK);

    List<RagChunk> search(String query);

    String buildContext(String query);

    void reload();
}
