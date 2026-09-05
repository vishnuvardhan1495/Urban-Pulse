package com.urbanpulse.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestController {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/test")
    public String test() {
        return "UrbanPulse Backend is running";
    }

    @GetMapping("/test/db")
    public String testDb() {
        if (jdbcTemplate != null) {
            String dbVersion = jdbcTemplate.queryForObject("SELECT version()", String.class);
            return "Database connection successful: " + dbVersion;
        }
        return "JdbcTemplate not configured";
    }
}
