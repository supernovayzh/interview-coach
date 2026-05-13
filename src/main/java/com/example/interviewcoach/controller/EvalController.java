package com.example.interviewcoach.controller;

import com.example.interviewcoach.eval.AgentEvalService;
import com.example.interviewcoach.eval.EvalCase;
import com.example.interviewcoach.eval.EvalRunRequest;
import com.example.interviewcoach.eval.EvalRunResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/eval")
public class EvalController {

    private final AgentEvalService agentEvalService;

    public EvalController(AgentEvalService agentEvalService) {
        this.agentEvalService = agentEvalService;
    }

    @GetMapping("/cases")
    public ResponseEntity<Map<String, Object>> cases(@RequestParam(defaultValue = "mini-interview-agent") String suiteName) {
        List<EvalCase> cases = agentEvalService.listCases(suiteName);
        return ResponseEntity.ok(Map.of(
                "suiteName", suiteName,
                "totalCases", cases.size(),
                "cases", cases
        ));
    }

    @PostMapping("/run")
    public ResponseEntity<EvalRunResponse> run(@RequestBody(required = false) EvalRunRequest request) {
        EvalRunResponse response = agentEvalService.run(request == null ? new EvalRunRequest() : request);
        return ResponseEntity.ok(response);
    }
}