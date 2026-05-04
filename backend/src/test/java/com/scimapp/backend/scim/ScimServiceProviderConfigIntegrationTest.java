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
class ScimServiceProviderConfigIntegrationTest {

	private static final String SCIM_TOKEN = "Bearer scrambleid-local-bearer-token-change-me";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void withoutBearer_returns401() throws Exception {
		mockMvc.perform(get("/scim/v2/ServiceProviderConfig"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void withBearer_returnsCapabilities() throws Exception {
		mockMvc.perform(get("/scim/v2/ServiceProviderConfig").header("Authorization", SCIM_TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.schemas[0]").value(ScimSchemas.SERVICE_PROVIDER_CONFIG))
			.andExpect(jsonPath("$.patch.supported").value(true))
			.andExpect(jsonPath("$.bulk.supported").value(false))
			.andExpect(jsonPath("$.filter.supported").value(false))
			.andExpect(jsonPath("$.authenticationSchemes[0].type").value("oauthbearertoken"))
			.andExpect(jsonPath("$.authenticationSchemes[0].primary").value(true));
	}
}
