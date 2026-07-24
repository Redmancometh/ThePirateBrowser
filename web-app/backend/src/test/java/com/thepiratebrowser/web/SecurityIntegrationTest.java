package com.thepiratebrowser.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;

    @Test
    void registrationLoginAndProtectedAccountRoundTrip() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "test.pirate",
                                  "password": "a-long-test-password",
                                  "inviteCode": "test-invite"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("test.pirate"))
                .andExpect(jsonPath("$.canary").value("TEST-CANARY"))
                .andExpect(jsonPath("$.putIoConfigured").value(false));

        MockHttpSession session = (MockHttpSession) mvc.perform(
                        formLogin("/api/auth/login")
                                .user("test.pirate")
                                .password("a-long-test-password"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void protectedApiRejectsAnonymousRequestsAndMissingCsrf() throws Exception {
        mvc.perform(get("/api/saved-searches"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicMetaNeverExposesThePutIoCredential() throws Exception {
        mvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("putio"))))
                .andExpect(jsonPath("$.canary").value("test-canary"));
    }

    @Test
    void savedSearchesAreIsolatedBetweenAccounts() throws Exception {
        register("alice.pirate");
        register("bob.pirate");
        MockHttpSession alice = loginSession("alice.pirate");
        MockHttpSession bob = loginSession("bob.pirate");

        mvc.perform(post("/api/saved-searches")
                        .session(alice)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alice only",
                                  "query": "private query",
                                  "minimumSeeders": 2,
                                  "enabled": true,
                                  "knownMagnets": []
                                }
                                """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/saved-searches").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alice only"));
        mvc.perform(get("/api/saved-searches").session(bob))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void authenticatedUserCanCreateAReceiverReadableCastGrant() throws Exception {
        register("cast.pirate");
        MockHttpSession session = loginSession("cast.pirate");

        String response = mvc.perform(post("/api/putio/files/42/cast")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(
                        org.hamcrest.Matchers.startsWith("/api/cast/")))
                .andReturn().getResponse().getContentAsString();
        String url = mapper.readTree(response).path("url").asText();

        mvc.perform(get(url))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value(
                        "The shared put.io account is not configured on this server."));
    }

    private void register(String username) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "a-long-test-password",
                                  "inviteCode": "test-invite"
                                }
                                """.formatted(username)))
                .andExpect(status().isCreated());
    }

    private MockHttpSession loginSession(String username) throws Exception {
        return (MockHttpSession) mvc.perform(
                        formLogin("/api/auth/login")
                                .user(username)
                                .password("a-long-test-password"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }
}
