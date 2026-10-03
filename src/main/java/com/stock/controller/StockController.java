package com.stock.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StockController {

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of("message", "Spring Boot 정상 실행");
    }

    @GetMapping("/api/hello")
    public Map<String, String> hello() {
        return Map.of("message", "hello");
    }
}