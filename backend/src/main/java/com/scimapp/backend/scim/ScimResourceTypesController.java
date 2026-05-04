package com.scimapp.backend.scim;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scimapp.backend.scim.dto.ScimListResponse;
import com.scimapp.backend.scim.dto.ScimResourceTypeResource;

@RestController
@RequestMapping(path = "/scim/v2/ResourceTypes", produces = { "application/scim+json", MediaType.APPLICATION_JSON_VALUE })
public class ScimResourceTypesController {

	@GetMapping
	public ScimListResponse<ScimResourceTypeResource> list() {
		List<ScimResourceTypeResource> all = ScimDiscoveryCatalog.resourceTypes();
		return ScimListResponse.of(all.size(), 1, all.size(), all);
	}

	@GetMapping("/{id}")
	public ScimResourceTypeResource get(@PathVariable String id) {
		ScimResourceTypeResource r = ScimDiscoveryCatalog.resourceTypeById(id);
		if (r == null) {
			throw new ScimException(HttpStatus.NOT_FOUND, null, "Resource type not found");
		}
		return r;
	}
}
