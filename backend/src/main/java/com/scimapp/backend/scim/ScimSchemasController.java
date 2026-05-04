package com.scimapp.backend.scim;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scimapp.backend.scim.dto.ScimListResponse;
import com.scimapp.backend.scim.dto.ScimSchemaResource;

@RestController
@RequestMapping(path = "/scim/v2/Schemas", produces = { "application/scim+json", MediaType.APPLICATION_JSON_VALUE })
public class ScimSchemasController {

	@GetMapping
	public ScimListResponse<ScimSchemaResource> list() {
		List<ScimSchemaResource> all = ScimDiscoveryCatalog.schemas();
		return ScimListResponse.of(all.size(), 1, all.size(), all);
	}

	/**
	 * Schema id is the full URN, e.g. {@code urn:ietf:params:scim:schemas:core:2.0:User}.
	 */
	@GetMapping("/{schemaId:.+}")
	public ScimSchemaResource get(@PathVariable String schemaId) {
		ScimSchemaResource s = ScimDiscoveryCatalog.schemaById(schemaId);
		if (s == null) {
			throw new ScimException(HttpStatus.NOT_FOUND, null, "Schema not found");
		}
		return s;
	}
}
