package com.scimapp.backend.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates {@code /scim/v2/**} with either:
 * <ul>
 * <li>Static SCIM API Bearer token (external provisioners such as ScrambleID) → {@code ROLE_SCIM_INTEGRATION}</li>
 * <li>Application JWT (admin UI) → roles from the token; must include {@code ADMIN} for authorization</li>
 * </ul>
 * Keeps SCIM separate from the main app JWT filter so one chain can apply SCIM-specific rules.
 */
public class ScimAuthenticationFilter extends OncePerRequestFilter implements Ordered {

	private final ScimAuthProperties properties;
	private final ObjectMapper objectMapper;
	private final JwtService jwtService;

	public ScimAuthenticationFilter(
			ScimAuthProperties properties,
			ObjectMapper objectMapper,
			JwtService jwtService) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.jwtService = jwtService;
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 100;
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
		if (token.isEmpty()) {
			writeUnauthorized(response);
			return;
		}
		if (constantTimeEquals(token, properties.apiToken())) {
			var authentication = new UsernamePasswordAuthenticationToken(
					"scim-provisioner",
					null,
					List.of(new SimpleGrantedAuthority("ROLE_SCIM_INTEGRATION")));
			SecurityContextHolder.getContext().setAuthentication(authentication);
			filterChain.doFilter(request, response);
			return;
		}
		try {
			Claims claims = jwtService.parseAndValidate(token);
			String subject = claims.getSubject();
			@SuppressWarnings("unchecked")
			List<String> roles = claims.get("roles", List.class);
			if (roles == null) {
				roles = List.of();
			}
			var authorities = roles.stream()
					.map(r -> new SimpleGrantedAuthority("ROLE_" + r))
					.toList();
			var authentication = new UsernamePasswordAuthenticationToken(subject, null, authorities);
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}
		catch (JwtException | IllegalArgumentException ex) {
			writeUnauthorized(response);
			return;
		}
		filterChain.doFilter(request, response);
	}

	private void writeUnauthorized(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(
				response.getOutputStream(),
				Map.of(
						"error", "Unauthorized",
						"detail", "Invalid or missing SCIM or admin JWT Bearer token"));
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
