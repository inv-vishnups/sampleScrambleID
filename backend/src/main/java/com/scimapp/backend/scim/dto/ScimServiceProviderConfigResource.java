package com.scimapp.backend.scim.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.scimapp.backend.scim.ScimSchemas;

/**
 * RFC 7644 §4 — Service Provider Configuration. Lets SCIM clients discover capabilities
 * (patch, bulk, filter, auth schemes) before provisioning.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScimServiceProviderConfigResource(
		@JsonProperty("schemas") List<String> schemas,
		@JsonProperty("patch") ScimFeatureSupported patch,
		@JsonProperty("bulk") ScimBulkConfig bulk,
		@JsonProperty("filter") ScimFilterConfig filter,
		@JsonProperty("changePassword") ScimFeatureSupported changePassword,
		@JsonProperty("sort") ScimFeatureSupported sort,
		@JsonProperty("etag") ScimFeatureSupported etag,
		@JsonProperty("authenticationSchemes") List<ScimAuthenticationScheme> authenticationSchemes) {

	public static ScimServiceProviderConfigResource forThisDeployment() {
		return new ScimServiceProviderConfigResource(
				List.of(ScimSchemas.SERVICE_PROVIDER_CONFIG),
				new ScimFeatureSupported(true),
				new ScimBulkConfig(false, 0, 0),
				new ScimFilterConfig(false, 100),
				new ScimFeatureSupported(false),
				new ScimFeatureSupported(false),
				new ScimFeatureSupported(false),
				List.of(new ScimAuthenticationScheme(
						"oauthbearertoken",
						"HTTP Bearer",
						"Use Authorization: Bearer with the configured SCIM API token, or an admin access JWT from /auth/login.",
						"https://www.rfc-editor.org/rfc/rfc6750",
						null,
						true)));
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ScimFeatureSupported(@JsonProperty("supported") boolean supported) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ScimBulkConfig(
			@JsonProperty("supported") boolean supported,
			@JsonProperty("maxOperations") int maxOperations,
			@JsonProperty("maxPayloadSize") int maxPayloadSize) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ScimFilterConfig(
			@JsonProperty("supported") boolean supported,
			@JsonProperty("maxResults") int maxResults) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ScimAuthenticationScheme(
			@JsonProperty("type") String type,
			@JsonProperty("name") String name,
			@JsonProperty("description") String description,
			@JsonProperty("specUri") String specUri,
			@JsonProperty("documentationUri") String documentationUri,
			@JsonProperty("primary") Boolean primary) {
	}
}
