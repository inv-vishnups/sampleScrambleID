package com.scimapp.backend.scim.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.scimapp.backend.scim.ScimSchemas;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimSchemaResource {

	@JsonProperty("schemas")
	private List<String> schemas = List.of(ScimSchemas.SCHEMA);

	@JsonProperty("id")
	private String id;

	@JsonProperty("name")
	private String name;

	@JsonProperty("description")
	private String description;

	@JsonProperty("attributes")
	private List<ScimSchemaAttribute> attributes;

	public List<String> getSchemas() {
		return schemas;
	}

	public void setSchemas(List<String> schemas) {
		this.schemas = schemas != null ? schemas : List.of(ScimSchemas.SCHEMA);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public List<ScimSchemaAttribute> getAttributes() {
		return attributes;
	}

	public void setAttributes(List<ScimSchemaAttribute> attributes) {
		this.attributes = attributes;
	}
}
