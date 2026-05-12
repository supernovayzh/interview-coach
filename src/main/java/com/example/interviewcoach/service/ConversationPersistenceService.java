package com.example.interviewcoach.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.interviewcoach.model.ConversationMessage;

import java.util.Collections;
import java.util.List;

@Service
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class ConversationPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationPersistenceService.class);
    private final JdbcTemplate jdbcTemplate;

    public ConversationPersistenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureSchema();
    }

    public void saveConversationTurn(String sessionId, String question, String answer, String traceId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "default";
        }
        if (question != null && !question.isBlank()) {
            jdbcTemplate.update(
                    "INSERT INTO conversation_messages(session_id, trace_id, role, content) VALUES (?, ?, ?, ?)",
                    sessionId, traceId, "user", question
            );
        }
        if (answer != null && !answer.isBlank()) {
            jdbcTemplate.update(
                    "INSERT INTO conversation_messages(session_id, trace_id, role, content) VALUES (?, ?, ?, ?)",
                    sessionId, traceId, "assistant", answer
            );
        }
    }

    public void saveEvaluation(String sessionId, String question, Double score, String feedback, String traceId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "default";
        }
        jdbcTemplate.update(
                "INSERT INTO conversation_evaluations(session_id, trace_id, question, score, feedback) VALUES (?, ?, ?, ?, ?)",
                sessionId, traceId, question, score, feedback
        );
    }

    public List<ConversationMessage> listMessages(String sessionId, int limit) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "default";
        }
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, 200);
        List<ConversationMessage> messages = jdbcTemplate.query(
                "SELECT session_id, trace_id, role, content, created_at FROM conversation_messages WHERE session_id = ? ORDER BY id DESC LIMIT ?",
                (rs, rowNum) -> new ConversationMessage(
                        rs.getString("session_id"),
                        rs.getString("trace_id"),
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getString("created_at")
                ),
                sessionId, safeLimit
        );
        Collections.reverse(messages);
        return messages;
    }

    public static class EvaluationRecord {
        public final String sessionId;
        public final String traceId;
        public final String question;
        public final Double score;
        public final String feedback;
        public final String createdAt;

        public EvaluationRecord(String sessionId, String traceId, String question, Double score, String feedback, String createdAt) {
            this.sessionId = sessionId;
            this.traceId = traceId;
            this.question = question;
            this.score = score;
            this.feedback = feedback;
            this.createdAt = createdAt;
        }
    }

    public List<EvaluationRecord> listEvaluations(String sessionId, int limit) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "default";
        }
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, 200);
        List<EvaluationRecord> evals = jdbcTemplate.query(
                "SELECT session_id, trace_id, question, score, feedback, created_at FROM conversation_evaluations WHERE session_id = ? ORDER BY id DESC LIMIT ?",
                (rs, rowNum) -> new EvaluationRecord(
                        rs.getString("session_id"),
                        rs.getString("trace_id"),
                        rs.getString("question"),
                        rs.getDouble("score"),
                        rs.getString("feedback"),
                        rs.getString("created_at")
                ),
                sessionId, safeLimit
        );
        java.util.Collections.reverse(evals);
        return evals;
    }

    public void deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "default";
        }
        jdbcTemplate.update("DELETE FROM conversation_messages WHERE session_id = ?", sessionId);
        jdbcTemplate.update("DELETE FROM conversation_evaluations WHERE session_id = ?", sessionId);
    }

    private void ensureSchema() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS conversation_messages ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "session_id VARCHAR(128) NOT NULL,"
                            + "trace_id VARCHAR(64),"
                            + "role VARCHAR(32) NOT NULL,"
                            + "content TEXT NOT NULL,"
                            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                            + ")"
            );
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_conv_session ON conversation_messages(session_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_conv_trace ON conversation_messages(trace_id)");
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS conversation_evaluations ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "session_id VARCHAR(128) NOT NULL,"
                            + "trace_id VARCHAR(64),"
                            + "question TEXT,"
                            + "score REAL,"
                            + "feedback TEXT,"
                            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                            + ")"
            );
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_eval_session ON conversation_evaluations(session_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_eval_trace ON conversation_evaluations(trace_id)");
        } catch (Exception e) {
            logger.error("failed to initialize conversation_messages schema: {}", e.getMessage(), e);
            throw e;
        }
    }
}
