package com.scimapp.backend.scim;

import java.util.ArrayList;
import java.util.List;

import com.scimapp.backend.scim.dto.ScimResourceTypeResource;
import com.scimapp.backend.scim.dto.ScimSchemaAttribute;
import com.scimapp.backend.scim.dto.ScimSchemaResource;

/**
 * Static SCIM discovery payloads for {@code ResourceTypes} and {@code Schemas} (RFC 7643/7644).
 * Describes the User and Group resources this server actually implements.
 */
public final class ScimDiscoveryCatalog {

	private static final List<ScimResourceTypeResource> RESOURCE_TYPES = List.of(buildUserResourceType(), buildGroupResourceType());

	private static final List<ScimSchemaResource> SCHEMA_RESOURCES = List.of(buildUserSchema(), buildGroupSchema());

	private ScimDiscoveryCatalog() {
	}

	public static List<ScimResourceTypeResource> resourceTypes() {
		return RESOURCE_TYPES;
	}

	public static ScimResourceTypeResource resourceTypeById(String id) {
		if (id == null) {
			return null;
		}
		return RESOURCE_TYPES.stream()
				.filter(r -> r.getId().equals(id))
				.findFirst()
				.orElse(null);
	}

	public static List<ScimSchemaResource> schemas() {
		return SCHEMA_RESOURCES;
	}

	public static ScimSchemaResource schemaById(String schemaId) {
		if (schemaId == null) {
			return null;
		}
		return SCHEMA_RESOURCES.stream()
				.filter(s -> s.getId().equals(schemaId))
				.findFirst()
				.orElse(null);
	}

	private static ScimResourceTypeResource buildUserResourceType() {
		ScimResourceTypeResource r = new ScimResourceTypeResource();
		r.setId("User");
		r.setName("User");
		r.setDescription("User accounts");
		r.setEndpoint("/Users");
		r.setSchema(ScimSchemas.USER);
		return r;
	}

	private static ScimResourceTypeResource buildGroupResourceType() {
		ScimResourceTypeResource r = new ScimResourceTypeResource();
		r.setId("Group");
		r.setName("Group");
		r.setDescription("Groups of users");
		r.setEndpoint("/Groups");
		r.setSchema(ScimSchemas.GROUP);
		return r;
	}

	private static ScimSchemaResource buildUserSchema() {
		ScimSchemaResource s = new ScimSchemaResource();
		s.setId(ScimSchemas.USER);
		s.setName("User");
		s.setDescription("User Account");

		List<ScimSchemaAttribute> attrs = new ArrayList<>();

		attrs.add(strAttr("userName", true, true, "server", "Unique identifier for the User"));

		ScimSchemaAttribute name = complexAttr("name", false, false, "The components of the user's name");
		name.setSubAttributes(List.of(
				strSub("givenName", false, "Given name"),
				strSub("familyName", false, "Family name")));
		attrs.add(name);

		ScimSchemaAttribute emails = complexAttr("emails", true, false, "Email addresses");
		emails.setSubAttributes(List.of(
				strSub("value", true, "Email address value"),
				boolSub("primary", false, "Primary email flag"),
				strSub("type", false, "Label (e.g. work)")));
		attrs.add(emails);

		attrs.add(boolAttr("active", false, false, "Whether the user is active"));

		ScimSchemaAttribute ext = strAttr("externalId", false, false, "none", "IdP identifier");
		ext.setMutability("readWrite");
		attrs.add(ext);

		s.setAttributes(attrs);
		return s;
	}

	private static ScimSchemaResource buildGroupSchema() {
		ScimSchemaResource s = new ScimSchemaResource();
		s.setId(ScimSchemas.GROUP);
		s.setName("Group");
		s.setDescription("Group");

		List<ScimSchemaAttribute> attrs = new ArrayList<>();

		attrs.add(strAttr("displayName", true, false, "none", "Human-readable group name"));

		ScimSchemaAttribute ext = strAttr("externalId", false, false, "none", "IdP identifier for the group");
		ext.setMutability("readWrite");
		attrs.add(ext);

		ScimSchemaAttribute members = complexAttr("members", true, false, "Members of the group");
		ScimSchemaAttribute ref = new ScimSchemaAttribute();
		ref.setName("$ref");
		ref.setType("reference");
		ref.setMultiValued(false);
		ref.setRequired(false);
		ref.setMutability("readWrite");
		ref.setReturned("default");
		ref.setUniqueness("none");
		ref.setReferenceTypes("User");
		ref.setDescription("URI of the corresponding User resource");

		members.setSubAttributes(List.of(
				strSub("value", true, "Identifier of the member (user id)"),
				ref,
				strSub("display", false, "Display name")));
		attrs.add(members);

		s.setAttributes(attrs);
		return s;
	}

	private static ScimSchemaAttribute strAttr(
			String name,
			boolean required,
			boolean multiValued,
			String uniqueness,
			String description) {
		ScimSchemaAttribute a = new ScimSchemaAttribute();
		a.setName(name);
		a.setType("string");
		a.setMultiValued(multiValued);
		a.setRequired(required);
		a.setMutability("readWrite");
		a.setReturned("default");
		a.setUniqueness(uniqueness);
		a.setDescription(description);
		return a;
	}

	private static ScimSchemaAttribute boolAttr(String name, boolean required, boolean multiValued, String description) {
		ScimSchemaAttribute a = new ScimSchemaAttribute();
		a.setName(name);
		a.setType("boolean");
		a.setMultiValued(multiValued);
		a.setRequired(required);
		a.setMutability("readWrite");
		a.setReturned("default");
		a.setUniqueness("none");
		a.setDescription(description);
		return a;
	}

	private static ScimSchemaAttribute complexAttr(String name, boolean multiValued, boolean required, String description) {
		ScimSchemaAttribute a = new ScimSchemaAttribute();
		a.setName(name);
		a.setType("complex");
		a.setMultiValued(multiValued);
		a.setRequired(required);
		a.setMutability("readWrite");
		a.setReturned("default");
		a.setUniqueness("none");
		a.setDescription(description);
		return a;
	}

	private static ScimSchemaAttribute strSub(String name, boolean required, String description) {
		ScimSchemaAttribute a = new ScimSchemaAttribute();
		a.setName(name);
		a.setType("string");
		a.setMultiValued(false);
		a.setRequired(required);
		a.setMutability("readWrite");
		a.setReturned("default");
		a.setUniqueness("none");
		a.setDescription(description);
		return a;
	}

	private static ScimSchemaAttribute boolSub(String name, boolean required, String description) {
		ScimSchemaAttribute a = new ScimSchemaAttribute();
		a.setName(name);
		a.setType("boolean");
		a.setMultiValued(false);
		a.setRequired(required);
		a.setMutability("readWrite");
		a.setReturned("default");
		a.setUniqueness("none");
		a.setDescription(description);
		return a;
	}
}
