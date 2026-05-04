package com.scimapp.backend.scim.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.scimapp.backend.scim.ScimSchemas;

public record ScimListResponse<T>(
		@JsonProperty("schemas") List<String> schemas,
		@JsonProperty("totalResults") long totalResults,
		@JsonProperty("startIndex") int startIndex,
		@JsonProperty("itemsPerPage") int itemsPerPage,
		@JsonProperty("Resources") List<T> resources) {

	public static <T> ScimListResponse<T> of(long total, int startIndex, int itemsPerPage, List<T> resources) {
		return new ScimListResponse<>(
				List.of(ScimSchemas.LIST_RESPONSE),
				total,
				startIndex,
				itemsPerPage,
				resources);
	}
}
