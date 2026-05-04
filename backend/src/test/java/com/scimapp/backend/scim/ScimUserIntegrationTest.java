package com.scimapp.backend.scim;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scimapp.backend.entity.Role;
import com.scimapp.backend.entity.User;
import com.scimapp.backend.repository.RoleRepository;
import com.scimapp.backend.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScimUserIntegrationTest {

	private static final String SCIM_TOKEN = "Bearer scrambleid-local-bearer-token-change-me";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void seedJwtTestUsers() {
		if (!userRepository.existsByUsername("scim_admin")) {
			User admin = new User("scim_admin", "scim_admin@example.com", passwordEncoder.encode("AdminPass1"));
			Set<Role> roles = new HashSet<>();
			roles.add(roleRepository.findByName("ADMIN").orElseThrow());
			admin.setRoles(roles);
			userRepository.save(admin);
		}
		if (!userRepository.existsByUsername("scim_plain_user")) {
			User plain = new User("scim_plain_user", "scim_plain@example.com", passwordEncoder.encode("UserPass1"));
			Set<Role> roles = new HashSet<>();
			roles.add(roleRepository.findByName("USER").orElseThrow());
			plain.setRoles(roles);
			userRepository.save(plain);
		}
	}

	private String accessTokenForUser(String username, String password) throws Exception {
		String body = mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode node = objectMapper.readTree(body);
		return node.get("accessToken").asText();
	}

	@Test
	void withoutBearer_returns401() throws Exception {
		mockMvc.perform(get("/scim/v2/Users"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void createGetPatchDelete_flow() throws Exception {
		String createJson = """
				{
				  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
				  "userName": "scim.user",
				  "active": true,
				  "externalId": "ext-1001",
				  "name": { "givenName": "SCIM", "familyName": "User" },
				  "emails": [ { "value": "scim.user@example.com", "primary": true, "type": "work" } ]
				}
				""";

		String created = mockMvc.perform(post("/scim/v2/Users")
				.header("Authorization", SCIM_TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createJson))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").exists())
			.andExpect(jsonPath("$.userName").value("scim.user"))
			.andExpect(header().exists("Location"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

		mockMvc.perform(get("/scim/v2/Users/" + id).header("Authorization", SCIM_TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.externalId").value("ext-1001"));

		String patchJson = """
				{
				  "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
				  "Operations": [
				    { "op": "replace", "path": "active", "value": false }
				  ]
				}
				""";

		mockMvc.perform(patch("/scim/v2/Users/" + id)
				.header("Authorization", SCIM_TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(patchJson))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));

		mockMvc.perform(delete("/scim/v2/Users/" + id).header("Authorization", SCIM_TOKEN))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/scim/v2/Users/" + id).header("Authorization", SCIM_TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void duplicateUserName_returns409() throws Exception {
		String body = """
				{
				  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
				  "userName": "dup.user",
				  "emails": [ { "value": "dup1@example.com", "primary": true } ]
				}
				""";
		mockMvc.perform(post("/scim/v2/Users")
				.header("Authorization", SCIM_TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/scim/v2/Users")
				.header("Authorization", SCIM_TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body.replace("dup1@example.com", "dup2@example.com")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.status").value("409"));
	}

	@Test
	void putReplacesUser() throws Exception {
		String createJson = """
				{
				  "userName": "put.me",
				  "emails": [ { "value": "put.me@example.com", "primary": true } ],
				  "name": { "givenName": "A", "familyName": "B" }
				}
				""";
		String created = mockMvc.perform(post("/scim/v2/Users")
				.header("Authorization", SCIM_TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createJson))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

		String putJson = """
				{
				  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
				  "userName": "put.me",
				  "active": true,
				  "emails": [ { "value": "new.mail@example.com", "primary": true } ],
				  "name": { "givenName": "X", "familyName": "Y" }
				}
				""";

		mockMvc.perform(put("/scim/v2/Users/" + id)
				.header("Authorization", SCIM_TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(putJson))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.emails[0].value").value("new.mail@example.com"))
			.andExpect(jsonPath("$.name.givenName").value("X"));
	}

	@Test
	void createWithAdminJwt_succeeds() throws Exception {
		String jwt = accessTokenForUser("scim_admin", "AdminPass1");
		String createJson = """
				{
				  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
				  "userName": "jwt.created.user",
				  "active": true,
				  "name": { "givenName": "JWT", "familyName": "Created" },
				  "emails": [ { "value": "jwt.created.user@example.com", "primary": true } ]
				}
				""";
		mockMvc.perform(post("/scim/v2/Users")
				.header("Authorization", "Bearer " + jwt)
				.contentType("application/scim+json")
				.content(createJson))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.userName").value("jwt.created.user"))
			.andExpect(jsonPath("$.meta.location").exists());
	}

	@Test
	void createWithUserJwt_returns403() throws Exception {
		String jwt = accessTokenForUser("scim_plain_user", "UserPass1");
		String createJson = """
				{
				  "userName": "forbidden.user",
				  "emails": [ { "value": "forbidden.user@example.com", "primary": true } ]
				}
				""";
		mockMvc.perform(post("/scim/v2/Users")
				.header("Authorization", "Bearer " + jwt)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createJson))
			.andExpect(status().isForbidden());
	}
}
