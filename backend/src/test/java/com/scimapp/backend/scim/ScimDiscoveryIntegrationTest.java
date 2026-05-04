package com.scimapp.backend.scim;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScimDiscoveryIntegrationTest {

	private static final String SCIM_TOKEN = "Bearer scrambleid-local-bearer-token-change-me";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void resourceTypes_listAndGet() throws Exception {
		mockMvc.perform(get("/scim/v2/ResourceTypes").header("Authorization", SCIM_TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalResults").value(2))
			.andExpect(jsonPath("$.Resources[0].id").exists())
			.andExpect(jsonPath("$.Resources[1].id").exists());

		mockMvc.perform(get("/scim/v2/ResourceTypes/User").header("Authorization", SCIM_TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("User"))
			.andExpect(jsonPath("$.endpoint").value("/Users"))
			.andExpect(jsonPath("$.schema").value(ScimSchemas.USER));

		mockMvc.perform(get("/scim/v2/ResourceTypes/Unknown").header("Authorization", SCIM_TOKEN))
			.andExpect(status().isNotFound());
	}

	@Test
	void schemas_listAndGetByUrn() throws Exception {
		mockMvc.perform(get("/scim/v2/Schemas").header("Authorization", SCIM_TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalResults").value(2))
			.andExpect(jsonPath("$.Resources[0].id").value(ScimSchemas.USER));

		mockMvc.perform(get("/scim/v2/Schemas/" + ScimSchemas.USER).header("Authorization", SCIM_TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("User"))
			.andExpect(jsonPath("$.attributes[0].name").value("userName"));

		mockMvc.perform(get("/scim/v2/Schemas/urn:ietf:params:scim:schemas:core:2.0:Group")
				.header("Authorization", SCIM_TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attributes[0].name").value("displayName"));

		mockMvc.perform(get("/scim/v2/Schemas/urn:ietf:params:scim:schemas:extension:enterprise:2.0:User")
				.header("Authorization", SCIM_TOKEN))
			.andExpect(status().isNotFound());
	}
}
