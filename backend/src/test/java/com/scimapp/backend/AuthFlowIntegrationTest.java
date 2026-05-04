package com.scimapp.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class AuthFlowIntegrationTest {

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
	void seedUser() {
		User u = new User("tester", "tester@example.com", passwordEncoder.encode("SecretPass1"));
		Set<Role> roles = new HashSet<>();
		roles.add(roleRepository.findByName("USER").orElseThrow());
		u.setRoles(roles);
		userRepository.save(u);
	}

	@Test
	void loginThenMe() throws Exception {
		String body = mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"tester\",\"password\":\"SecretPass1\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").exists())
			.andExpect(jsonPath("$.refreshToken").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();

		JsonNode node = objectMapper.readTree(body);
		String access = node.get("accessToken").asText();

		mockMvc.perform(get("/api/me")
				.header("Authorization", "Bearer " + access))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("tester"))
			.andExpect(jsonPath("$.roles").isArray());
	}

	@Test
	void loginWithBadPassword_returns401() throws Exception {
		mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"tester\",\"password\":\"wrong\"}"))
			.andExpect(status().isUnauthorized());
	}
}
