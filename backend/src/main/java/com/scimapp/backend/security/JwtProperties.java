package com.scimapp.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
		String secret,
		String issuer,
		long accessTokenMinutes,
		long refreshTokenDays) {

	public JwtProperties {
		if (secret == null || secret.length() < 32) {
			throw new IllegalArgumentException("app.jwt.secret must be at least 32 characters for HS256");
		}
		if (issuer == null || issuer.isBlank()) {
			throw new IllegalArgumentException("app.jwt.issuer is required");
		}
		if (accessTokenMinutes <= 0) {
			throw new IllegalArgumentException("app.jwt.access-token-minutes must be positive");
		}
		if (refreshTokenDays <= 0) {
			throw new IllegalArgumentException("app.jwt.refresh-token-days must be positive");
		}
	}
}
