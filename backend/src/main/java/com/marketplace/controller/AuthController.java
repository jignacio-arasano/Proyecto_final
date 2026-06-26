package com.marketplace.controller;

import com.marketplace.dto.LoginRequest;
import com.marketplace.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JwtUtil jwtUtil;

    @Value("${app.auth.username}")
    private String appUsername;

    @Value("${app.auth.password}")
    private String appPassword;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        if (appUsername.equals(req.getUsername()) && appPassword.equals(req.getPassword())) {
            String token = jwtUtil.generateToken(req.getUsername());
            return ResponseEntity.ok(Map.of("token", token, "username", req.getUsername()));
        }
        return ResponseEntity.status(401).body(Map.of("error", "Credenciales inválidas"));
    }
}
