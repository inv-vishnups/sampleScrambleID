package com.scimapp.backend.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
		String path = request.getRequestURI();
		return path.startsWith("/auth")
				|| path.startsWith("/h2-console")
				|| path.startsWith("/scim/v2");
	}

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}
		String token = header.substring(7).trim();
		if (token.isEmpty()) {
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
			SecurityContextHolder.clearContext();
		}
		filterChain.doFilter(request, response);
	}
}
