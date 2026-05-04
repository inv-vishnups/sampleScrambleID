package com.scimapp.backend.scim.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.scimapp.backend.scim.ScimSchemas;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimUserResource {

	@JsonProperty("schemas")
	private List<String> schemas = List.of(ScimSchemas.USER);

	@JsonProperty("id")
	private String id;

	@JsonProperty("externalId")
	private String externalId;

	@JsonProperty("userName")
	private String userName;

	@JsonProperty("active")
	private Boolean active;

	@JsonProperty("name")
	private ScimName name;

	@JsonProperty("emails")
	private List<ScimEmail> emails;

	@JsonProperty("meta")
	private ScimMeta meta;

	public List<String> getSchemas() {
		return schemas;
	}

	public void setSchemas(List<String> schemas) {
		this.schemas = schemas != null ? schemas : List.of(ScimSchemas.USER);
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

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public ScimName getName() {
		return name;
	}

	public void setName(ScimName name) {
		this.name = name;
	}

	public List<ScimEmail> getEmails() {
		return emails;
	}

	public void setEmails(List<ScimEmail> emails) {
		this.emails = emails;
	}

	public ScimMeta getMeta() {
		return meta;
	}

	public void setMeta(ScimMeta meta) {
		this.meta = meta;
	}
}
