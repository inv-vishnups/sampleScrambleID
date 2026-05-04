package com.scimapp.backend.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.scimapp.backend.dto.UserResponse;
import com.scimapp.backend.entity.Role;
import com.scimapp.backend.entity.User;
import com.scimapp.backend.repository.UserRepository;

@Service
public class UserProfileService {

	private final UserRepository userRepository;

	public UserProfileService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public UserResponse getById(UUID id) {
		User user = userRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
		return toResponse(user);
	}

	private static UserResponse toResponse(User user) {
		return new UserResponse(
				user.getId(),
				user.getUsername(),
				user.getEmail(),
				user.isActive(),
				user.getRoles().stream().map(Role::getName).sorted().toList());
	}
}
