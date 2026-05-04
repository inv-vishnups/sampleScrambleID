package com.scimapp.backend.scim.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.scimapp.backend.scim.ScimSchemas;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimGroupResource {

	@JsonProperty("schemas")
	private List<String> schemas = List.of(ScimSchemas.GROUP);

	@JsonProperty("id")
	private String id;

	@JsonProperty("externalId")
	private String externalId;

	@JsonProperty("displayName")
	private String displayName;

	@JsonProperty("members")
	private List<ScimMember> members;

	@JsonProperty("meta")
	private ScimMeta meta;

	public List<String> getSchemas() {
		return schemas;
	}

	public void setSchemas(List<String> schemas) {
		this.schemas = schemas != null ? schemas : List.of(ScimSchemas.GROUP);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getExternalId() {
		return externalId;
	}

	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public List<ScimMember> getMembers() {
		return members;
	}

	public void setMembers(List<ScimMember> members) {
		this.members = members;
	}

	public ScimMeta getMeta() {
		return meta;
	}

	public void setMeta(ScimMeta meta) {
		this.meta = meta;
	}
}
