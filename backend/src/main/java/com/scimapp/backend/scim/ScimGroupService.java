package com.scimapp.backend.scim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scimapp.backend.entity.ScimGroup;
import com.scimapp.backend.entity.User;
import com.scimapp.backend.repository.ScimGroupRepository;
import com.scimapp.backend.repository.UserRepository;
import com.scimapp.backend.scim.dto.PatchOperation;
import com.scimapp.backend.scim.dto.ScimGroupResource;
import com.scimapp.backend.scim.dto.ScimListResponse;
import com.scimapp.backend.scim.dto.ScimMember;
import com.scimapp.backend.scim.dto.ScimMeta;
import com.scimapp.backend.scim.dto.ScimPatchRequest;

@Service
public class ScimGroupService {

	private static final Pattern MEMBER_REMOVE_PATH = Pattern.compile(
			"members\\[value\\s+eq\\s+\"([0-9a-fA-F\\-]{36})\"\\]",
			Pattern.CASE_INSENSITIVE);

	private final ScimGroupRepository scimGroupRepository;
	private final UserRepository userRepository;
	private final ObjectMapper objectMapper;

	public ScimGroupService(
			ScimGroupRepository scimGroupRepository,
			UserRepository userRepository,
			ObjectMapper objectMapper) {
		this.scimGroupRepository = scimGroupRepository;
		this.userRepository = userRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public ScimGroupResource create(ScimGroupResource in, String groupsCollectionUrl, String usersCollectionUrl) {
		if (in.getDisplayName() == null || in.getDisplayName().isBlank()) {
			throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "displayName is required");
		}
		String displayName = in.getDisplayName().trim();
		if (scimGroupRepository.existsByDisplayName(displayName)) {
			throw new ScimException(HttpStatus.CONFLICT, "uniqueness", "displayName must be unique");
		}
		if (in.getExternalId() != null && !in.getExternalId().isBlank()
				&& scimGroupRepository.findByExternalId(in.getExternalId().trim()).isPresent()) {
			throw new ScimException(HttpStatus.CONFLICT, "uniqueness", "externalId must be unique");
		}

		ScimGroup group = new ScimGroup(displayName);
		applyIncoming(group, in, true);
		if (in.getMembers() != null && !in.getMembers().isEmpty()) {
			Set<User> members = new HashSet<>();
			for (ScimMember m : in.getMembers()) {
				if (m == null || m.getValue() == null || m.getValue().isBlank()) {
					continue;
				}
				members.add(findUserById(m.getValue().trim()));
			}
			group.setMembers(members);
		}
		scimGroupRepository.save(group);
		return toResource(group, groupsCollectionUrl, usersCollectionUrl);
	}

	@Transactional(readOnly = true)
	public ScimListResponse<ScimGroupResource> list(Integer startIndex, Integer count, String groupsUrl, String usersUrl) {
		int start = startIndex == null || startIndex < 1 ? 1 : startIndex;
		int pageSize = count == null || count < 1 ? 20 : Math.min(count, 100);
		long total = scimGroupRepository.count();
		int pageIndex = (start - 1) / pageSize;
		Page<ScimGroup> page = scimGroupRepository.findAllPaged(
				PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.ASC, "createdAt")));
		List<ScimGroupResource> resources = page.getContent().stream()
				.map(g -> toResource(g, groupsUrl, usersUrl))
				.toList();
		return ScimListResponse.of(total, start, resources.size(), resources);
	}

	@Transactional(readOnly = true)
	public ScimGroupResource get(String id, String groupsUrl, String usersUrl) {
		ScimGroup group = findGroupByScimId(id);
		return toResource(group, groupsUrl, usersUrl);
	}

	@Transactional
	public ScimGroupResource replace(String id, ScimGroupResource body, String groupsUrl, String usersUrl) {
		ScimGroup group = findGroupByScimId(id);
		if (body.getDisplayName() == null || body.getDisplayName().isBlank()) {
			throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "displayName is required for PUT");
		}
		String newName = body.getDisplayName().trim();
		if (!newName.equals(group.getDisplayName()) && scimGroupRepository.existsByDisplayName(newName)) {
			throw new ScimException(HttpStatus.CONFLICT, "uniqueness", "displayName must be unique");
		}
		if (body.getExternalId() != null && !body.getExternalId().isBlank()) {
			String ext = body.getExternalId().trim();
			scimGroupRepository.findByExternalId(ext).ifPresent(other -> {
				if (!other.getId().equals(group.getId())) {
					throw new ScimException(HttpStatus.CONFLICT, "uniqueness", "externalId must be unique");
				}
			});
		}
		group.setDisplayName(newName);
		applyIncoming(group, body, false);
		Set<User> members = new HashSet<>();
		if (body.getMembers() != null) {
			for (ScimMember m : body.getMembers()) {
				if (m == null || m.getValue() == null || m.getValue().isBlank()) {
					continue;
				}
				members.add(findUserById(m.getValue().trim()));
			}
		}
		group.setMembers(members);
		scimGroupRepository.save(group);
		return toResource(group, groupsUrl, usersUrl);
	}

	@Transactional
	public ScimGroupResource patch(String id, ScimPatchRequest request, String groupsUrl, String usersUrl) {
		ScimGroup group = findGroupByScimId(id);
		if (request.operations() == null || request.operations().isEmpty()) {
			throw new ScimException(HttpStatus.BAD_REQUEST, "invalidSyntax", "Operations must not be empty");
		}
		for (PatchOperation op : request.operations()) {
			if (op.op() == null) {
				throw new ScimException(HttpStatus.BAD_REQUEST, "invalidSyntax", "op is required");
			}
			switch (op.op().toLowerCase()) {
				case "replace" -> applyReplace(group, op.path(), op.value());
				case "remove" -> applyRemove(group, op.path());
				case "add" -> applyAdd(group, op.path(), op.value());
				default -> throw new ScimException(
						HttpStatus.BAD_REQUEST,
						"invalidSyntax",
						"Unsupported patch op: " + op.op());
			}
		}
		validateUniquenessAfterPatch(group);
		scimGroupRepository.save(group);
		return toResource(group, groupsUrl, usersUrl);
	}

	@Transactional
	public void delete(String id) {
		ScimGroup group = findGroupByScimId(id);
		scimGroupRepository.delete(group);
	}

	private void validateUniquenessAfterPatch(ScimGroup group) {
		scimGroupRepository.findByDisplayName(group.getDisplayName()).ifPresent(other -> {
			if (!other.getId().equals(group.getId())) {
				throw new ScimException(HttpStatus.CONFLICT, "uniqueness", "displayName must be unique");
			}
		});
		if (group.getExternalId() != null) {
			scimGroupRepository.findByExternalId(group.getExternalId()).ifPresent(other -> {
				if (!other.getId().equals(group.getId())) {
					throw new ScimException(HttpStatus.CONFLICT, "uniqueness", "externalId must be unique");
				}
			});
		}
	}

	private ScimGroup findGroupByScimId(String id) {
		try {
			UUID uuid = UUID.fromString(id);
			return scimGroupRepository.findByIdWithMembers(uuid)
					.orElseThrow(() -> new ScimException(HttpStatus.NOT_FOUND, null, "Group not found"));
		}
		catch (IllegalArgumentException ex) {
			throw new ScimException(HttpStatus.NOT_FOUND, null, "Group not found");
		}
	}

	private User findUserById(String id) {
		try {
			UUID uuid = UUID.fromString(id);
			return userRepository.findById(uuid)
					.orElseThrow(() -> new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "Unknown member user id: " + id));
		}
		catch (IllegalArgumentException ex) {
			throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "Invalid member user id: " + id);
		}
	}

	private void applyIncoming(ScimGroup group, ScimGroupResource in, boolean isCreate) {
		if (in.getExternalId() != null) {
			group.setExternalId(in.getExternalId().isBlank() ? null : in.getExternalId().trim());
		}
		else if (isCreate) {
			group.setExternalId(null);
		}
	}

	private ScimGroupResource toResource(ScimGroup group, String groupsCollectionUrl, String usersCollectionUrl) {
		String base = groupsCollectionUrl.endsWith("/")
				? groupsCollectionUrl.substring(0, groupsCollectionUrl.length() - 1)
				: groupsCollectionUrl;
		String location = base + "/" + group.getId();

		List<ScimMember> members = new ArrayList<>();
		var userBase = usersCollectionUrl.endsWith("/")
				? usersCollectionUrl.substring(0, usersCollectionUrl.length() - 1)
				: usersCollectionUrl;
		for (User u : group.getMembers()) {
			ScimMember m = new ScimMember();
			m.setValue(u.getId().toString());
			m.setRef(userBase + "/" + u.getId());
			m.setDisplay(buildDisplay(u));
			members.add(m);
		}

		ScimMeta meta = new ScimMeta(
				"Group",
				group.getCreatedAt() != null ? group.getCreatedAt().toString() : null,
				group.getUpdatedAt() != null ? group.getUpdatedAt().toString() : null,
				location);

		ScimGroupResource r = new ScimGroupResource();
		r.setSchemas(List.of(ScimSchemas.GROUP));
		r.setId(group.getId().toString());
		r.setExternalId(group.getExternalId());
		r.setDisplayName(group.getDisplayName());
		r.setMembers(members.isEmpty() ? null : members);
		r.setMeta(meta);
		return r;
	}

	private static String buildDisplay(User u) {
		String gn = u.getGivenName();
		String fn = u.getFamilyName();
		if (gn != null && fn != null) {
			return gn + " " + fn;
		}
		if (gn != null) {
			return gn;
		}
		if (fn != null) {
			return fn;
		}
		return u.getUsername();
	}

	private void applyReplace(ScimGroup group, String path, JsonNode value) {
		if (path == null || path.isBlank()) {
			if (value == null || value.isNull()) {
				throw new ScimException(HttpStatus.BAD_REQUEST, "invalidSyntax", "replace without path requires value");
			}
			ScimGroupResource partial = objectMapper.convertValue(value, ScimGroupResource.class);
			if (partial.getDisplayName() != null) {
				group.setDisplayName(partial.getDisplayName().trim());
			}
			if (partial.getExternalId() != null) {
				group.setExternalId(partial.getExternalId().isBlank() ? null : partial.getExternalId().trim());
			}
			if (partial.getMembers() != null) {
				Set<User> members = new HashSet<>();
				for (ScimMember m : partial.getMembers()) {
					if (m == null || m.getValue() == null || m.getValue().isBlank()) {
						continue;
					}
					members.add(findUserById(m.getValue().trim()));
				}
				group.setMembers(members);
			}
			return;
		}
		String p = path.trim();
		if ("displayName".equalsIgnoreCase(p)) {
			if (value == null || !value.isTextual()) {
				throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "displayName must be a string");
			}
			group.setDisplayName(value.asText().trim());
			return;
		}
		if ("externalId".equalsIgnoreCase(p)) {
			if (value == null || value.isNull() || (value.isTextual() && value.asText().isBlank())) {
				group.setExternalId(null);
			}
			else if (value.isTextual()) {
				group.setExternalId(value.asText().trim());
			}
			else {
				throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "externalId must be a string");
			}
			return;
		}
		if ("members".equalsIgnoreCase(p)) {
			if (value == null || !value.isArray()) {
				throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "members must be an array");
			}
			Set<User> members = new HashSet<>();
			for (JsonNode m : value) {
				if (m != null && m.has("value") && m.get("value").isTextual()) {
					members.add(findUserById(m.get("value").asText().trim()));
				}
			}
			group.setMembers(members);
			return;
		}
		throw new ScimException(HttpStatus.BAD_REQUEST, "noTarget", "Unsupported path: " + path);
	}

	private void applyRemove(ScimGroup group, String path) {
		if (path == null || path.isBlank()) {
			throw new ScimException(HttpStatus.BAD_REQUEST, "invalidSyntax", "remove requires path");
		}
		String p = path.trim();
		if ("externalId".equalsIgnoreCase(p)) {
			group.setExternalId(null);
			return;
		}
		Matcher m = MEMBER_REMOVE_PATH.matcher(p);
		if (m.matches()) {
			String userId = m.group(1);
			try {
				UUID uuid = UUID.fromString(userId);
				removeMemberByUserId(group, uuid);
			}
			catch (IllegalArgumentException ex) {
				throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "Invalid path: " + path);
			}
			return;
		}
		throw new ScimException(HttpStatus.BAD_REQUEST, "noTarget", "Unsupported path for remove: " + path);
	}

	private static void removeMemberByUserId(ScimGroup group, UUID userId) {
		for (Iterator<User> it = group.getMembers().iterator(); it.hasNext();) {
			if (it.next().getId().equals(userId)) {
				it.remove();
				return;
			}
		}
	}

	private void applyAdd(ScimGroup group, String path, JsonNode value) {
		if (path != null && path.trim().equalsIgnoreCase("members")) {
			if (value == null || !value.isArray()) {
				throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "members add value must be an array");
			}
			for (JsonNode m : value) {
				if (m != null && m.has("value") && m.get("value").isTextual()) {
					group.getMembers().add(findUserById(m.get("value").asText().trim()));
				}
			}
			return;
		}
		if (path == null || path.isBlank()) {
			if (value == null || value.isNull()) {
				throw new ScimException(HttpStatus.BAD_REQUEST, "invalidSyntax", "add requires value when path is omitted");
			}
			applyReplace(group, null, value);
			return;
		}
		applyReplace(group, path, value);
	}
}
