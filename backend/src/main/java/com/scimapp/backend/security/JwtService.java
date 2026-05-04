package com.scimapp.backend.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.scimapp.backend.entity.Role;
import com.scimapp.backend.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	private final JwtProperties properties;
	private final SecretKey signingKey;

	public JwtService(JwtProperties properties) {
		this.properties = properties;
		this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
	}

	public String createAccessToken(User user) {
		Instant now = Instant.now();
		Instant exp = now.plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES);
		List<String> roleNames = user.getRoles().stream().map(Role::getName).toList();
		return Jwts.builder()
			.issuer(properties.issuer())
			.subject(user.getId().toString())
			.claim("roles", roleNames)
			.issuedAt(Date.from(now))
			.expiration(Date.from(exp))
			.signWith(signingKey)
			.compact();
	}

	public Claims parseAndValidate(String token) {
		return Jwts.parser()
			.verifyWith(signingKey)
			.requireIssuer(properties.issuer())
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}

	public long accessTokenTtlSeconds() {
		return properties.accessTokenMinutes() * 60;
	}
}
