package com.scimapp.backend.scim.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.scimapp.backend.scim.ScimSchemas;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScimErrorResponse(
		@JsonProperty("schemas") List<String> schemas,
		@JsonProperty("status") String status,
		@JsonProperty("scimType") String scimType,
		@JsonProperty("detail") String detail) {

	public static ScimErrorResponse of(int httpStatus, String scimType, String detail) {
		return new ScimErrorResponse(
				List.of(ScimSchemas.ERROR),
				String.valueOf(httpStatus),
				scimType,
				detail);
	}
}
