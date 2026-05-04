package com.scimapp.backend.scim;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.scimapp.backend.scim.dto.ScimGroupResource;
import com.scimapp.backend.scim.dto.ScimListResponse;
import com.scimapp.backend.scim.dto.ScimPatchRequest;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(path = "/scim/v2/Groups", produces = { "application/scim+json", MediaType.APPLICATION_JSON_VALUE })
public class ScimGroupController {

	private static final String APPLICATION_SCIM_JSON = "application/scim+json";

	private final ScimGroupService scimGroupService;

	public ScimGroupController(ScimGroupService scimGroupService) {
		this.scimGroupService = scimGroupService;
	}

	@PostMapping(consumes = { MediaType.APPLICATION_JSON_VALUE, APPLICATION_SCIM_JSON })
	public ResponseEntity<ScimGroupResource> create(@RequestBody ScimGroupResource body, HttpServletRequest request) {
		String groupsUrl = groupsCollectionUrl(request);
		String usersUrl = usersCollectionUrl(request);
		ScimGroupResource saved = scimGroupService.create(body, groupsUrl, usersUrl);
		URI location = URI.create(saved.getMeta().location());
		return ResponseEntity.status(HttpStatus.CREATED).location(location).body(saved);
	}

	@GetMapping
	public ScimListResponse<ScimGroupResource> list(
			@RequestParam(name = "startIndex", required = false) Integer startIndex,
			@RequestParam(name = "count", required = false) Integer count,
			HttpServletRequest request) {
		return scimGroupService.list(startIndex, count, groupsCollectionUrl(request), usersCollectionUrl(request));
	}

	@GetMapping("/{id}")
	public ScimGroupResource get(@PathVariable String id, HttpServletRequest request) {
		return scimGroupService.get(id, groupsCollectionUrl(request), usersCollectionUrl(request));
	}

	@PutMapping(path = "/{id}", consumes = { MediaType.APPLICATION_JSON_VALUE, APPLICATION_SCIM_JSON })
	public ScimGroupResource replace(@PathVariable String id, @RequestBody ScimGroupResource body, HttpServletRequest request) {
		return scimGroupService.replace(id, body, groupsCollectionUrl(request), usersCollectionUrl(request));
	}

	@PatchMapping(path = "/{id}", consumes = { MediaType.APPLICATION_JSON_VALUE, APPLICATION_SCIM_JSON })
	public ScimGroupResource patch(@PathVariable String id, @RequestBody ScimPatchRequest body, HttpServletRequest request) {
		return scimGroupService.patch(id, body, groupsCollectionUrl(request), usersCollectionUrl(request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		scimGroupService.delete(id);
		return ResponseEntity.noContent().build();
	}

	private static String groupsCollectionUrl(HttpServletRequest request) {
		return ServletUriComponentsBuilder.fromContextPath(request).path("/scim/v2/Groups").build().toUriString();
	}

	private static String usersCollectionUrl(HttpServletRequest request) {
		return ServletUriComponentsBuilder.fromContextPath(request).path("/scim/v2/Users").build().toUriString();
	}
}
