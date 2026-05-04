package com.scimapp.backend.scim;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scimapp.backend.scim.dto.ScimServiceProviderConfigResource;

/**
 * RFC 7644 discovery — many IdPs call this to learn supported features and authentication.
 */
@RestController
@RequestMapping(path = "/scim/v2/ServiceProviderConfig", produces = { "application/scim+json", MediaType.APPLICATION_JSON_VALUE })
public class ScimServiceProviderConfigController {

	@GetMapping
	public ScimServiceProviderConfigResource get() {
		return ScimServiceProviderConfigResource.forThisDeployment();
	}
}
