package com.scimapp.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.scim")
public record ScimAuthProperties(String apiToken) {

	public ScimAuthProperties {
		if (apiToken == null || apiToken.isBlank()) {
			throw new IllegalArgumentException("app.scim.api-token is required for SCIM (set SCIM_API_TOKEN in production)");
		}
	}
}
