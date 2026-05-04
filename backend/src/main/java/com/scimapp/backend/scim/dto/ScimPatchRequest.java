package com.scimapp.backend.scim.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.scimapp.backend.scim.ScimSchemas;

public record ScimPatchRequest(
		@JsonProperty("schemas") List<String> schemas,
		@JsonProperty("Operations") List<PatchOperation> operations) {

	public static ScimPatchRequest of(List<PatchOperation> operations) {
		return new ScimPatchRequest(List.of(ScimSchemas.PATCH_OP), operations);
	}
}
