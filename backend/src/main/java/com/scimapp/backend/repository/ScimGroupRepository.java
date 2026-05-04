package com.scimapp.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.scimapp.backend.entity.ScimGroup;

public interface ScimGroupRepository extends JpaRepository<ScimGroup, UUID> {

	boolean existsByDisplayName(String displayName);

	Optional<ScimGroup> findByDisplayName(String displayName);

	Optional<ScimGroup> findByExternalId(String externalId);

	@Query("SELECT g FROM ScimGroup g LEFT JOIN FETCH g.members WHERE g.id = :id")
	Optional<ScimGroup> findByIdWithMembers(@Param("id") UUID id);

	@EntityGraph(attributePaths = "members")
	@Query("SELECT g FROM ScimGroup g")
	Page<ScimGroup> findAllPaged(Pageable pageable);
}
