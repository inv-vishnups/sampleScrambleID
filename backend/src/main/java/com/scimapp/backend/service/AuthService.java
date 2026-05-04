package com.scimapp.backend.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scimapp.backend.dto.TokenResponse;
import com.scimapp.backend.entity.RefreshToken;
import com.scimapp.backend.entity.User;
import com.scimapp.backend.repository.RefreshTokenRepository;
import com.scimapp.backend.repository.UserRepository;
import com.scimapp.backend.security.JwtProperties;
import com.scimapp.backend.security.JwtService;
import com.scimapp.backend.security.TokenHasher;

@Service
public class AuthService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final JwtProperties jwtProperties;

	public AuthService(
			UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			JwtProperties jwtProperties) {
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.jwtProperties = jwtProperties;
	}

	@Transactional
	public TokenResponse login(String username, String password) {
		User user = userRepository.findByUsername(username)
			.orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
		if (!user.isActive()) {
			throw new BadCredentialsException("User is disabled");
		}
		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new BadCredentialsException("Invalid credentials");
		}
		return issueTokens(user);
	}

	@Transactional
	public TokenResponse refresh(String rawRefreshToken) {
		String hash = TokenHasher.sha256Hex(rawRefreshToken);
		RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
			.orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

		if (existing.isRevoked()) {
			revokeAllRefreshTokensForUser(existing.getUser().getId());
			throw new BadCredentialsException("Refresh token reuse detected");
		}
		if (existing.getExpiresAt().isBefore(Instant.now())) {
			throw new BadCredentialsException("Refresh token expired");
		}

		User user = userRepository.findById(existing.getUser().getId())
			.orElseThrow(() -> new BadCredentialsException("User not found"));
		if (!user.isActive()) {
			throw new BadCredentialsException("User is disabled");
		}

		CreatedRefresh rotated = persistNewRefreshToken(user);
		existing.setRevoked(true);
		existing.setReplacedBy(rotated.entity().getId());
		refreshTokenRepository.save(existing);

		return buildResponse(user, rotated.raw());
	}

	@Transactional
	public void logout(String rawRefreshToken) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			return;
		}
		String hash = TokenHasher.sha256Hex(rawRefreshToken);
		refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
			rt.setRevoked(true);
			refreshTokenRepository.save(rt);
		});
	}

	private TokenResponse issueTokens(User user) {
		CreatedRefresh created = persistNewRefreshToken(user);
		return buildResponse(user, created.raw());
	}

	private CreatedRefresh persistNewRefreshToken(User user) {
		String raw = newRawRefreshToken();
		String hash = TokenHasher.sha256Hex(raw);
		Instant expires = Instant.now().plus(jwtProperties.refreshTokenDays(), ChronoUnit.DAYS);
		RefreshToken entity = new RefreshToken(user, hash, expires);
		refreshTokenRepository.save(entity);
		return new CreatedRefresh(entity, raw);
	}

	private TokenResponse buildResponse(User user, String rawRefresh) {
		String access = jwtService.createAccessToken(user);
		return new TokenResponse(
				access,
				rawRefresh,
				jwtService.accessTokenTtlSeconds(),
				"Bearer");
	}

	private record CreatedRefresh(RefreshToken entity, String raw) {
	}

	private void revokeAllRefreshTokensForUser(UUID userId) {
		User user = userRepository.findById(userId).orElse(null);
		if (user != null) {
			refreshTokenRepository.deleteByUser(user);
		}
	}

	private static String newRawRefreshToken() {
		byte[] buf = new byte[32];
		RANDOM.nextBytes(buf);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
	}
}
