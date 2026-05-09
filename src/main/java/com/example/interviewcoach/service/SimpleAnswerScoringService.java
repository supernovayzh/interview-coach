package com.example.interviewcoach.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;

@Service
public class SimpleAnswerScoringService implements AnswerScoringService {

    @Override
    public ScoreResult score(String question, String answer, String ragContext) {
        if (answer == null || answer.isBlank()) {
            return new ScoreResult(0.0, "空答案");
        }

        String normQ = normalize(question);
        String normA = normalize(answer);

        // basic length-based score (0-40)
        double lengthScore = Math.min(40.0, normA.length() / 5.0);

        // keyword match score (0-40)
        String[] qTokens = normQ.split("\\s+");
        int matches = 0;
        for (String t : qTokens) {
            if (t.length() < 2) continue;
            if (normA.contains(t)) matches++;
        }
        double keywordScore = Math.min(40.0, matches * 8.0);

        // presence of RAG content increases confidence (0-20)
        double ragBoost = 0.0;
        if (ragContext != null && !ragContext.isBlank() && normA.length() > 50) {
            ragBoost = 10.0;
        }

        double total = lengthScore + keywordScore + ragBoost;
        total = Math.max(0.0, Math.min(100.0, total));

        String feedback = String.format(Locale.ROOT,
                "长度分:%.1f, 关键词匹配:%.1f, RAG加成:%.1f。总分:%.1f/100。",
                lengthScore, keywordScore, ragBoost, total);

        return new ScoreResult(total, feedback);
    }

    private String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC);
        n = n.replaceAll("[^\\p{L}\\p{N}\\s]", " ");
        return n.toLowerCase(Locale.ROOT);
    }
}
