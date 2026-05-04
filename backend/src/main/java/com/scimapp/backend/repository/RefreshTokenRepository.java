package com.scimapp.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.scimapp.backend.entity.RefreshToken;
import com.scimapp.backend.entity.User;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	void deleteByUser(User user);
}
