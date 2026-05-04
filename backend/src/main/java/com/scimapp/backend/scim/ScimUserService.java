package com.scimapp.backend.scim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scimapp.backend.entity.Role;
import com.scimapp.backend.entity.User;
import com.scimapp.backend.repository.RoleRepository;
import com.scimapp.backend.repository.UserRepository;
import com.scimapp.backend.scim.dto.PatchOperation;
import com.scimapp.backend.scim.dto.ScimEmail;
import com.scimapp.backend.scim.dto.ScimListResponse;
import com.scimapp.backend.scim.dto.ScimMeta;
import com.scimapp.backend.scim.dto.ScimName;
import com.scimapp.backend.scim.dto.ScimPatchRequest;
import com.scimapp.backend.scim.dto.ScimUserResource;

@Service
public class ScimUserService {

	private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final ObjectMapper objectMapper;

	public ScimUserService(
			UserRepository userRepository,
			RoleRepository roleRepository,
			PasswordEncoder passwordEncoder,
			ObjectMapper objectMapper) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public ScimUserResource create(ScimUserResource in, String usersCollectionUrl) {
		if (in.getUserName() == null || in.getUserName().isBlank()) {
			throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "userName is required");
		}
		if (userRepository.existsByUsername(in.getUserName())) {
			throw new ScimException(HttpStatus.CONFLICT, "uniqueness", "userName must be unique");
		}
		String email = primaryEmail(in);
		if (email == null || email.isBlank()) {
			email = in.getUserName() + "@scim.local";
		}
		email = email.trim();
		if (!SIMPLE_EMAIL.matcher(email).matches()) {
			throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "emails[0].value must be a valid email address");
		}
		if (userRepository.existsByEmail(email)) {
			throw new ScimException(HttpStatus.CONFLICT, "uniqueness", "email must be unique");
		}
		if (in.getExternalId() != null && !in.getExternalId().isBlank()
				&& userRepository.findByExternalId(in.getExternalId()).isPresent()) {
			throw new ScimException(HttpStatus.CONFLICT, "uniqueness", "externalId must be unique");
		}

		User user = new User(
				in.getUserName().trim(),
				email,
				passwordEncoder.encode(UUID.randomUUID().toString()));
		applyIncoming(user, in, true);
		Set<Role> roles = new HashSet<>();
		roles.add(defaultUserRole());
		user.setRoles(roles);
		userRepository.save(user);
		return toResource(user, usersCollectionUrl);
	}

	@Transactional(readOnly = true)
	public ScimListResponse<ScimUserResource> list(Integer startIndex, Integer count, String usersCollectionUrl) {
		int start = startIndex == null || startIndex < 1 ? 1 : startIndex;
		int pageSize = count == null || count < 1 ? 20 : Math.min(count, 100);
		long total = userRepository.count();
		int pageIndex = (start - 1) / pageSize;
		Page<User> page = userRepository.findAll(
				PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.ASC, "createdAt")));
		List<ScimUserResource> resources = page.getContent().stream()
				.map(u -> toResource(u, usersCollectionUrl))
				.toList();
		return ScimListResponse.of(total, start, resources.size(), resources);
	}

	@Transactional(readOnly = true)
	public ScimUserResource get(String id, String usersCollectionUrl) {
		User user = findUserByScimId(id);
		return toResource(user, usersCollectionUrl);
	}

	@Transactional
	public ScimUserResource replace(String id, ScimUserResource body, String usersCollectionUrl) {
		User user = findUserByScimId(id);
		if (body.getUserName() == null || body.getUserName().isBlank()) {
			throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "userName is required for PUT");
		}
		String newUsername = body.getUserName().trim();
		if (!newUsername.equals(user.getUsername()) && userRepository.existsByUsername(newUsername)) {
			throw new ScimException(HttpStatus.CONFLICT, "uniqueness", "userName must be unique");
		}
		String email = primaryEmail(body);
		if (email == null || email.isBlank()) {
			email = newUsername + "@scim.local";
		}
		if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(email)) {
			throw new ScimException(HttpStatus.CONFLICT, "uniqueness", "email must be unique");
		}
		if (body.getExternalId() != null && !body.getExternalId().isBlank()) {
			userRepository.findByExternalId(body.getExternalId()).ifPresent(other -> {
				if (!other.getId().equals(user.getId())) {
					throw new ScimException(HttpStatus.CONFLICT, "uniqueness", "externalId must be unique");
				}
			});
		}

		user.setUsername(newUsername);
		user.setEmail(email.trim());
		applyIncoming(user, body, false);
		userRepository.save(user);
		return toResource(user, usersCollectionUrl);
	}

	@Transactional
	public ScimUserResource patch(String id, ScimPatchRequest request, String usersCollectionUrl) {
		User user = findUserByScimId(id);
		if (request.operations() == null || request.operations().isEmpty()) {
			throw new ScimException(HttpStatus.BAD_REQUEST, "invalidSyntax", "Operations must not be empty");
		}
		for (PatchOperation op : request.operations()) {
			if (op.op() == null) {
				throw new ScimException(HttpStatus.BAD_REQUEST, "invalidSyntax", "op is required");
			}
			switch (op.op().toLowerCase()) {
				case "replace" -> applyReplace(user, op.path(), op.value());
				case "remove" -> applyRemove(user, op.path());
				case "add" -> applyAdd(user, op.path(), op.value());
				default -> throw new ScimException(
						HttpStatus.BAD_REQUEST,
						"invalidSyntax",
						"Unsupported patch op: " + op.op());
			}
		}
		validateUniquenessAfterPatch(user);
		userRepository.save(user);
		return toResource(user, usersCollectionUrl);
	}

	@Transactional
	public void deactivate(String id) {
		User user = findUserByScimId(id);
		user.setActive(false);
		userRepository.save(user);
	}

	private void validateUniquenessAfterPatch(User user) {
		userRepository.findByUsername(user.getUsername()).ifPresent(other -> {
			if (!other.getId().equals(user.getId())) {
				throw new ScimException(HttpStatus.CONFLICT, "uniqueness", "userName must be unique");
			}
		});
		userRepository.findByEmail(user.getEmail()).ifPresent(other -> {
			if (!other.getId().equals(user.getId())) {
				throw new ScimException(HttpStatus.CONFLICT, "uniqueness", "email must be unique");
			}
		});
		if (user.getExternalId() != null) {
			userRepository.findByExternalId(user.getExternalId()).ifPresent(other -> {
				if (!other.getId().equals(user.getId())) {
					throw new ScimException(HttpStatus.CONFLICT, "uniqueness", "externalId must be unique");
				}
			});
		}
	}

	private User findUserByScimId(String id) {
		try {
			UUID uuid = UUID.fromString(id);
			return userRepository.findById(uuid)
				.orElseThrow(() -> new ScimException(HttpStatus.NOT_FOUND, null, "User not found"));
		}
		catch (IllegalArgumentException ex) {
			throw new ScimException(HttpStatus.NOT_FOUND, null, "User not found");
		}
	}

	private Role defaultUserRole() {
		return roleRepository.findByName("USER")
			.orElseThrow(() -> new IllegalStateException("ROLE USER not seeded"));
	}

	private void applyIncoming(User user, ScimUserResource in, boolean isCreate) {
		if (in.getExternalId() != null) {
			user.setExternalId(in.getExternalId().isBlank() ? null : in.getExternalId().trim());
		}
		else if (isCreate) {
			user.setExternalId(null);
		}
		if (in.getActive() != null) {
			user.setActive(in.getActive());
		}
		else if (isCreate) {
			user.setActive(true);
		}
		if (in.getName() != null) {
			if (in.getName().givenName() != null) {
				user.setGivenName(in.getName().givenName().isBlank() ? null : in.getName().givenName().trim());
			}
			if (in.getName().familyName() != null) {
				user.setFamilyName(in.getName().familyName().isBlank() ? null : in.getName().familyName().trim());
			}
		}
	}

	private String primaryEmail(ScimUserResource in) {
		if (in.getEmails() == null) {
			return null;
		}
		for (ScimEmail e : in.getEmails()) {
			if (e == null || e.value() == null) {
				continue;
			}
			if (Boolean.TRUE.equals(e.primary())) {
				return e.value();
			}
		}
		return in.getEmails().stream().filter(e -> e != null && e.value() != null).findFirst().map(ScimEmail::value).orElse(null);
	}

	private ScimUserResource toResource(User user, String usersCollectionUrl) {
		String base = usersCollectionUrl.endsWith("/") ? usersCollectionUrl.substring(0, usersCollectionUrl.length() - 1) : usersCollectionUrl;
		String location = base + "/" + user.getId();

		List<ScimEmail> emails = new ArrayList<>();
		if (user.getEmail() != null) {
			emails.add(new ScimEmail(user.getEmail(), true, "work"));
		}

		ScimName name = null;
		if (user.getGivenName() != null || user.getFamilyName() != null) {
			name = new ScimName(user.getGivenName(), user.getFamilyName());
		}

		ScimMeta meta = new ScimMeta(
				"User",
				user.getCreatedAt() != null ? user.getCreatedAt().toString() : null,
				user.getUpdatedAt() != null ? user.getUpdatedAt().toString() : null,
				location);

		ScimUserResource r = new ScimUserResource();
		r.setSchemas(List.of(ScimSchemas.USER));
		r.setId(user.getId().toString());
		r.setExternalId(user.getExternalId());
		r.setUserName(user.getUsername());
		r.setActive(user.isActive());
		r.setName(name);
		r.setEmails(emails.isEmpty() ? null : emails);
		r.setMeta(meta);
		return r;
	}

	private void applyReplace(User user, String path, JsonNode value) {
		if (path == null || path.isBlank()) {
			if (value == null || value.isNull()) {
				throw new ScimException(HttpStatus.BAD_REQUEST, "invalidSyntax", "replace without path requires value");
			}
			ScimUserResource partial = objectMapper.convertValue(value, ScimUserResource.class);
			if (partial.getUserName() != null) {
				user.setUsername(partial.getUserName().trim());
			}
			if (partial.getExternalId() != null) {
				user.setExternalId(partial.getExternalId().isBlank() ? null : partial.getExternalId().trim());
			}
			if (partial.getActive() != null) {
				user.setActive(partial.getActive());
			}
			if (partial.getName() != null) {
				if (partial.getName().givenName() != null) {
					user.setGivenName(partial.getName().givenName().isBlank() ? null : partial.getName().givenName().trim());
				}
				if (partial.getName().familyName() != null) {
					user.setFamilyName(partial.getName().familyName().isBlank() ? null : partial.getName().familyName().trim());
				}
			}
			String email = primaryEmail(partial);
			if (email != null && !email.isBlank()) {
				user.setEmail(email.trim());
			}
			return;
		}
		String p = path.trim();
		if ("active".equalsIgnoreCase(p)) {
			if (value == null || !value.isBoolean()) {
				throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "active must be a boolean");
			}
			user.setActive(value.booleanValue());
			return;
		}
		if ("userName".equalsIgnoreCase(p)) {
			if (value == null || value.isNull() || !value.isTextual()) {
				throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "userName must be a string");
			}
			user.setUsername(value.asText().trim());
			return;
		}
		if ("externalId".equalsIgnoreCase(p)) {
			if (value == null || value.isNull() || (value.isTextual() && value.asText().isBlank())) {
				user.setExternalId(null);
			}
			else if (value.isTextual()) {
				user.setExternalId(value.asText().trim());
			}
			else {
				throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "externalId must be a string");
			}
			return;
		}
		if ("name.givenName".equalsIgnoreCase(p)) {
			if (value == null || value.isNull()) {
				user.setGivenName(null);
			}
			else if (value.isTextual()) {
				user.setGivenName(value.asText().trim());
			}
			else {
				throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "name.givenName must be a string");
			}
			return;
		}
		if ("name.familyName".equalsIgnoreCase(p)) {
			if (value == null || value.isNull()) {
				user.setFamilyName(null);
			}
			else if (value.isTextual()) {
				user.setFamilyName(value.asText().trim());
			}
			else {
				throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "name.familyName must be a string");
			}
			return;
		}
		if ("emails".equalsIgnoreCase(p) || p.toLowerCase().startsWith("emails[")) {
			if (value == null || !value.isArray() || value.isEmpty()) {
				throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "emails value must be a non-empty array");
			}
			JsonNode first = value.get(0);
			if (first == null || !first.has("value") || !first.get("value").isTextual()) {
				throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "emails[0].value is required");
			}
			user.setEmail(first.get("value").asText().trim());
			return;
		}
		throw new ScimException(HttpStatus.BAD_REQUEST, "noTarget", "Unsupported path: " + path);
	}

	private void applyRemove(User user, String path) {
		if (path == null || path.isBlank()) {
			throw new ScimException(HttpStatus.BAD_REQUEST, "invalidSyntax", "remove requires path");
		}
		String p = path.trim();
		if ("externalId".equalsIgnoreCase(p)) {
			user.setExternalId(null);
			return;
		}
		if ("name.givenName".equalsIgnoreCase(p)) {
			user.setGivenName(null);
			return;
		}
		if ("name.familyName".equalsIgnoreCase(p)) {
			user.setFamilyName(null);
			return;
		}
		throw new ScimException(HttpStatus.BAD_REQUEST, "noTarget", "Unsupported path for remove: " + path);
	}

	private void applyAdd(User user, String path, JsonNode value) {
		if (path != null && !path.isBlank()) {
			applyReplace(user, path, value);
			return;
		}
		if (value == null || value.isNull()) {
			throw new ScimException(HttpStatus.BAD_REQUEST, "invalidSyntax", "add requires value when path is omitted");
		}
		applyReplace(user, null, value);
	}
}
