package com.scimapp.backend.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserResponse(
		@JsonProperty("id") UUID id,
		@JsonProperty("username") String username,
		@JsonProperty("email") String email,
		@JsonProperty("active") boolean active,
		@JsonProperty("roles") List<String> roles) {
}
