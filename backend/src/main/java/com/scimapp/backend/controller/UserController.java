package com.scimapp.backend.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scimapp.backend.dto.UserResponse;
import com.scimapp.backend.service.UserProfileService;

@RestController
@RequestMapping("/api")
public class UserController {

	private final UserProfileService userProfileService;

	public UserController(UserProfileService userProfileService) {
		this.userProfileService = userProfileService;
	}

	@GetMapping("/me")
	public UserResponse me(Authentication authentication) {
		UUID id = UUID.fromString(authentication.getName());
		return userProfileService.getById(id);
	}

	@GetMapping("/admin/ping")
	@PreAuthorize("hasRole('ADMIN')")
	public String adminPing() {
		return "ok";
	}
}
