package com.scimapp.backend.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Validates a static Bearer token for {@code /scim/v2/**} (e.g. shared secret from ScrambleID).
 * Separate from end-user JWT — never reuse the same secret as {@code app.jwt.secret}.
 */
public class ScimBearerAuthenticationFilter extends OncePerRequestFilter {

	private final ScimAuthProperties properties;
	private final ObjectMapper objectMapper;

	public ScimBearerAuthenticationFilter(ScimAuthProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith("Bearer ")) {
			writeUnauthorized(response);
			return;
		}
		String token = header.substring(7).trim();
		if (!constantTimeEquals(token, properties.apiToken())) {
			writeUnauthorized(response);
			return;
		}
		var authentication = new UsernamePasswordAuthenticationToken(
				"scim-provisioner",
				null,
				List.of(new SimpleGrantedAuthority("ROLE_SCIM_INTEGRATION")));
		SecurityContextHolder.getContext().setAuthentication(authentication);
		filterChain.doFilter(request, response);
	}

	private void writeUnauthorized(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(
				response.getOutputStream(),
				Map.of(
						"error", "Unauthorized",
						"detail", "Invalid or missing SCIM Bearer token"));
	}

	private static boolean constantTimeEquals(String a, String b) {
		if (a == null || b == null) {
			return false;
		}
		byte[] x = a.getBytes(StandardCharsets.UTF_8);
		byte[] y = b.getBytes(StandardCharsets.UTF_8);
		if (x.length != y.length) {
			return false;
		}
		return MessageDigest.isEqual(x, y);
	}
}
