package com.scimapp.backend.scim.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.scimapp.backend.scim.ScimSchemas;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimResourceTypeResource {

	@JsonProperty("schemas")
	private List<String> schemas = List.of(ScimSchemas.RESOURCE_TYPE);

	@JsonProperty("id")
	private String id;

	@JsonProperty("name")
	private String name;

	@JsonProperty("description")
	private String description;

	@JsonProperty("endpoint")
	private String endpoint;

	@JsonProperty("schema")
	private String schema;

	@JsonProperty("schemaExtensions")
	private List<Object> schemaExtensions;

	public List<String> getSchemas() {
		return schemas;
	}

	public void setSchemas(List<String> schemas) {
		this.schemas = schemas != null ? schemas : List.of(ScimSchemas.RESOURCE_TYPE);
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

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getSchema() {
		return schema;
	}

	public void setSchema(String schema) {
		this.schema = schema;
	}

	public List<Object> getSchemaExtensions() {
		return schemaExtensions;
	}

	public void setSchemaExtensions(List<Object> schemaExtensions) {
		this.schemaExtensions = schemaExtensions;
	}
}
