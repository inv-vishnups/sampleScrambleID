package com.scimapp.backend.scim.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScimName(
		@JsonProperty("givenName") String givenName,
		@JsonProperty("familyName") String familyName) {
}
