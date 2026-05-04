package com.scimapp.backend.scim.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScimEmail(
		@JsonProperty("value") String value,
		@JsonProperty("primary") Boolean primary,
		@JsonProperty("type") String type) {
}
