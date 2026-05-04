package com.scimapp.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scimapp.backend.dto.LoginRequest;
import com.scimapp.backend.dto.LogoutRequest;
import com.scimapp.backend.dto.RefreshRequest;
import com.scimapp.backend.dto.TokenResponse;
import com.scimapp.backend.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(authService.login(request.username(), request.password()));
	}

	@PostMapping("/refresh")
	public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		return ResponseEntity.ok(authService.refresh(request.refreshToken()));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request) {
		if (request != null) {
			authService.logout(request.refreshToken());
		}
		return ResponseEntity.noContent().build();
	}
}
