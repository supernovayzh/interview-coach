package com.example.interviewcoach.tool;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class InterviewToolResult {
    private final String toolName;
    private final Map<String, Object> data;

    public InterviewToolResult(String toolName, Map<String, Object> data) {
        this.toolName = toolName;
        this.data = new HashMap<>(data == null ? Collections.emptyMap() : data);
    }

    public static InterviewToolResult of(String toolName, Map<String, Object> data) {
        return new InterviewToolResult(toolName, data);
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getData() {
        return Collections.unmodifiableMap(data);
    }

    public Object get(String key) {
        return data.get(key);
    }

    public String getString(String key) {
        Object value = data.get(key);
        return value == null ? null : value.toString();
    }

    public double getDouble(String key, double defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
