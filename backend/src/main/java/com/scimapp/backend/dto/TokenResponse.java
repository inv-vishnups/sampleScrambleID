package com.scimapp.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenResponse(
		@JsonProperty("accessToken") String accessToken,
		@JsonProperty("refreshToken") String refreshToken,
		@JsonProperty("expiresIn") long expiresInSeconds,
		@JsonProperty("tokenType") String tokenType) {
}
