package com.example.routerdemo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Access token, sent as Authorization: Bearer <accessToken>")
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
