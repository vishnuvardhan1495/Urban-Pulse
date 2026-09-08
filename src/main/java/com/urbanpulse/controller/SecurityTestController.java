package com.urbanpulse.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/security-test")
public class SecurityTestController {

    @GetMapping("/public")
    public ResponseEntity<Map<String, String>> publicEndpoint() {
        return ResponseEntity.ok(Map.of("message", "Public endpoint accessible without authentication"));
    }

    @GetMapping("/citizen")
    public ResponseEntity<Map<String, String>> citizenEndpoint() {
        return ResponseEntity.ok(Map.of("message", "Citizen endpoint accessed successfully"));
    }

    @GetMapping("/worker")
    public ResponseEntity<Map<String, String>> workerEndpoint() {
        return ResponseEntity.ok(Map.of("message", "Worker endpoint accessed successfully"));
    }

    @GetMapping("/supervisor")
    public ResponseEntity<Map<String, String>> supervisorEndpoint() {
        return ResponseEntity.ok(Map.of("message", "Supervisor endpoint accessed successfully"));
    }

    @GetMapping("/admin")
    public ResponseEntity<Map<String, String>> adminEndpoint() {
        return ResponseEntity.ok(Map.of("message", "Admin endpoint accessed successfully"));
    }
}
