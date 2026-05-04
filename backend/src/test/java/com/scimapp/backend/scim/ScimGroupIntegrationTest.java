package com.scimapp.backend.scim;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScimGroupIntegrationTest {

	private static final String SCIM_TOKEN = "Bearer scrambleid-local-bearer-token-change-me";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void createUserThenGroupWithMember_listGetPatchDelete() throws Exception {
		String userJson = """
				{
				  "userName": "group.member.one",
				  "emails": [ { "value": "gm1@example.com", "primary": true } ],
				  "name": { "givenName": "GM", "familyName": "One" }
				}
				""";
		String userResp = mockMvc.perform(post("/scim/v2/Users")
				.header("Authorization", SCIM_TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(userJson))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String userId = com.jayway.jsonpath.JsonPath.read(userResp, "$.id");

		String secondUser = """
				{
				  "userName": "group.member.two",
				  "emails": [ { "value": "gm2@example.com", "primary": true } ]
				}
				""";
		String user2Resp = mockMvc.perform(post("/scim/v2/Users")
				.header("Authorization", SCIM_TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(secondUser))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String user2Id = com.jayway.jsonpath.JsonPath.read(user2Resp, "$.id");

		String groupCreate = """
				{
				  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:Group"],
				  "displayName": "Engineering",
				  "externalId": "grp-eng",
				  "members": [ { "value": "%s" } ]
				}
				""".formatted(userId);

		String groupResp = mockMvc.perform(post("/scim/v2/Groups")
				.header("Authorization", SCIM_TOKEN)
				.contentType("application/scim+json")
				.content(groupCreate))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.displayName").value("Engineering"))
			.andExpect(jsonPath("$.members[0].value").value(userId))
			.andExpect(header().exists("Location"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		String groupId = com.jayway.jsonpath.JsonPath.read(groupResp, "$.id");

		mockMvc.perform(get("/scim/v2/Groups").header("Authorization", SCIM_TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalResults").value(1))
			.andExpect(jsonPath("$.Resources[0].id").value(groupId));

		mockMvc.perform(get("/scim/v2/Groups/" + groupId).header("Authorization", SCIM_TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.externalId").value("grp-eng"));

		String addMemberPatch = """
				{
				  "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
				  "Operations": [
				    { "op": "add", "path": "members", "value": [ { "value": "%s" } ] }
				  ]
				}
				""".formatted(user2Id);

		mockMvc.perform(patch("/scim/v2/Groups/" + groupId)
				.header("Authorization", SCIM_TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(addMemberPatch))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.members[0].value").exists())
			.andExpect(jsonPath("$.members[1].value").exists());

		String removePatch = """
				{
				  "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
				  "Operations": [
				    { "op": "remove", "path": "members[value eq \\"%s\\"]" }
				  ]
				}
				""".formatted(userId);

		mockMvc.perform(patch("/scim/v2/Groups/" + groupId)
				.header("Authorization", SCIM_TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(removePatch))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.members[0].value").value(user2Id));

		String putJson = """
				{
				  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:Group"],
				  "displayName": "Engineering",
				  "members": [ { "value": "%s" } ],
				  "externalId": "grp-eng-2"
				}
				""".formatted(userId);

		mockMvc.perform(put("/scim/v2/Groups/" + groupId)
				.header("Authorization", SCIM_TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(putJson))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.externalId").value("grp-eng-2"))
			.andExpect(jsonPath("$.members[0].value").value(userId));

		mockMvc.perform(delete("/scim/v2/Groups/" + groupId).header("Authorization", SCIM_TOKEN))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/scim/v2/Groups/" + groupId).header("Authorization", SCIM_TOKEN))
			.andExpect(status().isNotFound());
	}

	@Test
	void duplicateDisplayName_returns409() throws Exception {
		String body = """
				{ "displayName": "UniqueTeam", "members": [] }
				""";
		mockMvc.perform(post("/scim/v2/Groups")
				.header("Authorization", SCIM_TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/scim/v2/Groups")
				.header("Authorization", SCIM_TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isConflict());
	}
}
