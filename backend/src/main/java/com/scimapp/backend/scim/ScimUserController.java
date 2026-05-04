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

import com.scimapp.backend.scim.dto.ScimListResponse;
import com.scimapp.backend.scim.dto.ScimPatchRequest;
import com.scimapp.backend.scim.dto.ScimUserResource;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(path = "/scim/v2/Users", produces = { "application/scim+json", MediaType.APPLICATION_JSON_VALUE })
public class ScimUserController {

	private final ScimUserService scimUserService;

	public ScimUserController(ScimUserService scimUserService) {
		this.scimUserService = scimUserService;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ScimUserResource> create(@RequestBody ScimUserResource body, HttpServletRequest request) {
		String collectionUrl = usersCollectionUrl(request);
		ScimUserResource saved = scimUserService.create(body, collectionUrl);
		URI location = URI.create(saved.getMeta().location());
		return ResponseEntity.status(HttpStatus.CREATED).location(location).body(saved);
	}

	@GetMapping
	public ScimListResponse list(
			@RequestParam(name = "startIndex", required = false) Integer startIndex,
			@RequestParam(name = "count", required = false) Integer count,
			HttpServletRequest request) {
		return scimUserService.list(startIndex, count, usersCollectionUrl(request));
	}

	@GetMapping("/{id}")
	public ScimUserResource get(@PathVariable String id, HttpServletRequest request) {
		return scimUserService.get(id, usersCollectionUrl(request));
	}

	@PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ScimUserResource replace(@PathVariable String id, @RequestBody ScimUserResource body, HttpServletRequest request) {
		return scimUserService.replace(id, body, usersCollectionUrl(request));
	}

	@PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ScimUserResource patch(@PathVariable String id, @RequestBody ScimPatchRequest body, HttpServletRequest request) {
		return scimUserService.patch(id, body, usersCollectionUrl(request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		scimUserService.deactivate(id);
		return ResponseEntity.noContent().build();
	}

	private static String usersCollectionUrl(HttpServletRequest request) {
		return ServletUriComponentsBuilder.fromContextPath(request).path("/scim/v2/Users").build().toUriString();
	}
}
