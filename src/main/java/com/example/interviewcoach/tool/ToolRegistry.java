package com.example.interviewcoach.tool;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ToolRegistry {

    private final Map<String, InterviewTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<InterviewTool> interviewTools) {
        if (interviewTools != null) {
            for (InterviewTool tool : interviewTools) {
                register(tool);
            }
        }
    }

    public void register(InterviewTool tool) {
        if (tool == null) {
            return;
        }
        tools.put(tool.name(), tool);
    }

    public InterviewTool getTool(String name) {
        InterviewTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("Tool not found: " + name);
        }
        return tool;
    }

    public InterviewToolResult invoke(String name, InterviewToolContext context) {
        return getTool(name).execute(context);
    }

    public Collection<InterviewTool> listTools() {
        return tools.values();
    }

    public List<String> listToolNames() {
        return tools.keySet().stream().toList();
    }
}
