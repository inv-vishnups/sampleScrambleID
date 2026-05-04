package com.scimapp.backend.scim.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PatchOperation(
		@JsonProperty("op") String op,
		@JsonProperty("path") String path,
		@JsonProperty("value") JsonNode value) {
}
