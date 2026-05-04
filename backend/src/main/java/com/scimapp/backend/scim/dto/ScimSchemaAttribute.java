package com.scimapp.backend.scim.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Attribute definition inside a SCIM {@link ScimSchemaResource} (RFC 7643 §7).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimSchemaAttribute {

	private String name;

	private String type;

	@JsonProperty("multiValued")
	private boolean multiValued;

	private boolean required;

	private String mutability;

	private String returned;

	private String uniqueness;

	private String description;

	private String referenceTypes;

	private List<ScimSchemaAttribute> subAttributes;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public boolean isMultiValued() {
		return multiValued;
	}

	public void setMultiValued(boolean multiValued) {
		this.multiValued = multiValued;
	}

	public boolean isRequired() {
		return required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}

	public String getMutability() {
		return mutability;
	}

	public void setMutability(String mutability) {
		this.mutability = mutability;
	}

	public String getReturned() {
		return returned;
	}

	public void setReturned(String returned) {
		this.returned = returned;
	}

	public String getUniqueness() {
		return uniqueness;
	}

	public void setUniqueness(String uniqueness) {
		this.uniqueness = uniqueness;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getReferenceTypes() {
		return referenceTypes;
	}

	public void setReferenceTypes(String referenceTypes) {
		this.referenceTypes = referenceTypes;
	}

	public List<ScimSchemaAttribute> getSubAttributes() {
		return subAttributes;
	}

	public void setSubAttributes(List<ScimSchemaAttribute> subAttributes) {
		this.subAttributes = subAttributes;
	}
}
