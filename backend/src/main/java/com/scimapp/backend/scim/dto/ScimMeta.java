package com.scimapp.backend.scim.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScimMeta(
		@JsonProperty("resourceType") String resourceType,
		@JsonProperty("created") String created,
		@JsonProperty("lastModified") String lastModified,
		@JsonProperty("location") String location) {
}
