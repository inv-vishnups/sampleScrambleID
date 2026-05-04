package com.scimapp.backend.security;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Access audit for {@code /scim/v2/**}: method, path, status, principal, duration, optional correlation id.
 * Does not log Authorization headers or bodies (PII / secrets).
 */
public class ScimAuditLoggingFilter extends OncePerRequestFilter implements Ordered {

	private static final Logger log = LoggerFactory.getLogger(ScimAuditLoggingFilter.class);

	private static final String MDC_CORRELATION = "scimCorrelationId";

	private final ScimAuditProperties auditProperties;

	public ScimAuditLoggingFilter(ScimAuditProperties auditProperties) {
		this.auditProperties = auditProperties;
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		if (!auditProperties.isEnabled()) {
			filterChain.doFilter(request, response);
			return;
		}
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			filterChain.doFilter(request, response);
			return;
		}

		String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-ID"))
				.or(() -> Optional.ofNullable(request.getHeader("X-Request-Id")))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.orElseGet(() -> UUID.randomUUID().toString());

		MDC.put(MDC_CORRELATION, correlationId);
		long start = System.nanoTime();
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			try {
				int status = response.getStatus();
				boolean skipDenied = !auditProperties.isLogDeniedRequests() && (status == 401 || status == 403);
				if (!skipDenied) {
					Authentication auth = SecurityContextHolder.getContext().getAuthentication();
					String actor = auth != null && auth.isAuthenticated() ? auth.getName() : "unauthenticated";
					String roles = auth != null && auth.getAuthorities() != null
							? auth.getAuthorities().stream().map(Object::toString).collect(Collectors.joining(","))
							: "";
					long durationMs = (System.nanoTime() - start) / 1_000_000L;
					String line = "scim_audit method={} path={} status={} actor={} roles={} durationMs={} correlationId={}";
					if (auditProperties.isVerboseReadsAtDebug()
							&& ("GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod()))) {
						log.debug(line,
								request.getMethod(),
								request.getRequestURI(),
								status,
								actor,
								roles,
								durationMs,
								correlationId);
					}
					else {
						log.info(line,
								request.getMethod(),
								request.getRequestURI(),
								status,
								actor,
								roles,
								durationMs,
								correlationId);
					}
				}
			}
			finally {
				MDC.remove(MDC_CORRELATION);
			}
		}
	}
}
