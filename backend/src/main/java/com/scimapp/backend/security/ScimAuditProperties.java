package com.scimapp.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.scim.audit")
public class ScimAuditProperties {

	/**
	 * Emit structured SCIM access lines (no request/response bodies).
	 */
	private boolean enabled = true;

	/**
	 * Include 401/403 responses (still no body logged).
	 */
	private boolean logDeniedRequests = true;

	/**
	 * Use DEBUG for GET/HEAD; use INFO for mutations and denials (when {@link #logDeniedRequests}).
	 */
	private boolean verboseReadsAtDebug = true;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isLogDeniedRequests() {
		return logDeniedRequests;
	}

	public void setLogDeniedRequests(boolean logDeniedRequests) {
		this.logDeniedRequests = logDeniedRequests;
	}

	public boolean isVerboseReadsAtDebug() {
		return verboseReadsAtDebug;
	}

	public void setVerboseReadsAtDebug(boolean verboseReadsAtDebug) {
		this.verboseReadsAtDebug = verboseReadsAtDebug;
	}
}
