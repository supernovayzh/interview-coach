package com.example.interviewcoach.service;

import com.example.interviewcoach.config.RagProperties;
import com.example.interviewcoach.model.RagChunk;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class LocalMarkdownRagService implements RagKnowledgeService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[A-Za-z0-9_\\-]{2,}");
    private static final Set<String> STOP_WORDS = Set.of(
            "请问", "帮我", "一下", "什么", "为什么", "如何", "怎么", "可以", "一个", "这个", "那些",
            "面试", "知识", "问题", "讲一下", "介绍", "说明", "模拟", "面经"
    );

    private final RagProperties properties;
    private final AtomicReference<List<RagChunk>> index = new AtomicReference<>(List.of());

    public LocalMarkdownRagService(RagProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    @Override
    public void reload() {
        if (!properties.isEnabled()) {
            index.set(List.of());
            return;
        }

        String knowledgePath = properties.getKnowledgePath();
        if (knowledgePath == null || knowledgePath.isBlank()) {
            index.set(List.of());
            return;
        }

        Path root = Paths.get(knowledgePath);
        if (!Files.exists(root)) {
            index.set(List.of());
            return;
        }

        List<RagChunk> all = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .forEach(path -> all.addAll(indexMarkdown(path)));
        } catch (IOException e) {
            index.set(List.of());
            return;
        }

        index.set(List.copyOf(all));
    }

    @Override
    public List<RagChunk> search(String query) {
        return search(query, properties.getTopK());
    }

    @Override
    public List<RagChunk> search(String query, int topK) {
        if (!properties.isEnabled() || query == null || query.isBlank()) {
            return List.of();
        }

        List<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            keywords = List.of(query.trim());
        }

        List<RagChunk> candidates = new ArrayList<>();
        for (RagChunk chunk : index.get()) {
            double score = scoreChunk(chunk, keywords);
            if (score >= properties.getMinScore()) {
                RagChunk copy = new RagChunk(chunk.getId(), chunk.getSourceFile(), chunk.getTitle(), chunk.getContent());
                copy.setScore(score);
                candidates.add(copy);
            }
        }

        candidates.sort(Comparator.comparingDouble(RagChunk::getScore).reversed()
                .thenComparing(chunk -> chunk.getTitle() == null ? "" : chunk.getTitle()));

        int limit = Math.max(1, topK);
        if (candidates.size() > limit) {
            return candidates.subList(0, limit);
        }
        return candidates;
    }

    @Override
    public String buildContext(String query) {
        List<RagChunk> results = search(query);
        if (results.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("本地八股/面经检索结果：\n");
        for (int i = 0; i < results.size(); i++) {
            RagChunk chunk = results.get(i);
            builder.append("【").append(i + 1).append("】");
            if (chunk.getTitle() != null && !chunk.getTitle().isBlank()) {
                builder.append(chunk.getTitle());
            } else {
                builder.append("未命名片段");
            }
            builder.append(" (来源: ").append(chunk.getSourceFile()).append(", 分数: ").append(String.format(Locale.ROOT, "%.2f", chunk.getScore())).append(")\n");
            builder.append(chunk.preview(700)).append("\n");
        }
        return builder.toString();
    }

    private List<RagChunk> indexMarkdown(Path file) {
        List<RagChunk> chunks = new ArrayList<>();
        String fileName = file.getFileName().toString();
        String fallbackTitle = fileName.replaceFirst("\\.md$", "");
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            String[] lines = text.split("\\R");
            String currentTitle = fallbackTitle;
            StringBuilder buffer = new StringBuilder();
            boolean inCodeBlock = false;

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("```")) {
                    inCodeBlock = !inCodeBlock;
                    buffer.append(line).append('\n');
                    continue;
                }

                if (!inCodeBlock && isHeading(trimmed)) {
                    flushChunk(chunks, file, currentTitle, buffer);
                    currentTitle = trimmed.replaceFirst("^#{1,6}\\s+", "").trim();
                    continue;
                }

                if (!inCodeBlock && trimmed.isEmpty()) {
                    flushChunk(chunks, file, currentTitle, buffer);
                    continue;
                }

                buffer.append(line).append('\n');
                if (!inCodeBlock && buffer.length() >= properties.getChunkSize()) {
                    flushChunk(chunks, file, currentTitle, buffer);
                }
            }

            flushChunk(chunks, file, currentTitle, buffer);
        } catch (IOException ignored) {
            // keep going with other files
        }
        return chunks;
    }

    private void flushChunk(List<RagChunk> chunks, Path file, String title, StringBuilder buffer) {
        String content = buffer.toString().trim();
        buffer.setLength(0);
        if (content.isEmpty()) {
            return;
        }
        chunks.add(new RagChunk(UUID.randomUUID().toString(), file.toString(), title, content));
    }

    private boolean isHeading(String line) {
        return line.startsWith("#");
    }

    private List<String> extractKeywords(String query) {
        Set<String> keywords = new LinkedHashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(query);
        while (matcher.find()) {
            String token = matcher.group().trim();
            if (token.length() < 2) {
                continue;
            }
            if (!STOP_WORDS.contains(token)) {
                keywords.add(token);
            }
        }
        return new ArrayList<>(keywords);
    }

    private double scoreChunk(RagChunk chunk, List<String> keywords) {
        String title = chunk.getTitle() == null ? "" : chunk.getTitle();
        String content = chunk.getContent() == null ? "" : chunk.getContent();
        String titleLower = title.toLowerCase(Locale.ROOT);
        String contentLower = content.toLowerCase(Locale.ROOT);

        double score = 0.0;
        for (String keyword : keywords) {
            String normalized = keyword.toLowerCase(Locale.ROOT);
            if (titleLower.contains(normalized)) {
                score += 4.0;
            }
            score += countOccurrences(contentLower, normalized);
        }

        if (content.length() < 150) {
            score += 0.2;
        }
        return score;
    }

    private int countOccurrences(String text, String keyword) {
        if (text.isEmpty() || keyword.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(keyword, index)) >= 0) {
            count++;
            index += keyword.length();
        }
        return count;
    }
}
