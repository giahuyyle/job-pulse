package com.huy.jobpulse.security;

import com.huy.jobpulse.admin.infrastructure.AdminAuditRepository;
import com.huy.jobpulse.alerts.api.CreateSavedSearchRequest;
import com.huy.jobpulse.alerts.application.SavedSearchService;
import com.huy.jobpulse.alerts.domain.JobAlert;
import com.huy.jobpulse.alerts.infrastructure.JobAlertRepository;
import com.huy.jobpulse.alerts.infrastructure.SavedSearchRepository;
import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import com.huy.jobpulse.jobs.infrastructure.JobPostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jobpulse.admin.enabled=true",
        "jobpulse.ingestion.initial-delay-ms=3600000",
        "jobpulse.events.publish-initial-delay-ms=3600000"
})
@AutoConfigureMockMvc
@Testcontainers
class SecurityIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired SavedSearchService searches;
    @Autowired SavedSearchRepository searchRepository;
    @Autowired JobAlertRepository alertRepository;
    @Autowired JobPostingRepository jobs;
    @Autowired AdminAuditRepository audits;

    @BeforeEach
    void clean() {
        audits.deleteAll();
        alertRepository.deleteAll();
        searchRepository.deleteAll();
        jobs.deleteAll();
    }

    @Test
    void sessionIncludesGoogleProfileForAccountDropdown() throws Exception {
        mvc.perform(get("/api/v1/auth/session").with(oidcLogin().idToken(token -> token
                        .claim("sub", "alice-subject").claim("email", "alice@example.com")
                        .claim("name", "Alice Example").claim("picture", "https://example.com/avatar.jpg"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Example"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.picture").value("https://example.com/avatar.jpg"));
        mvc.perform(get("/api/v1/auth/session"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.name").isEmpty()).andExpect(jsonPath("$.picture").isEmpty());
    }

    @Test
    void publicReadsRemainAvailableButWritesRequireLoginAndCsrf() throws Exception {
        mvc.perform(get("/api/v1/jobs")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/saved-searches")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/jobs").with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/saved-searches").with(alice())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Backend\"}"))
                .andExpect(status().isForbidden());
        var csrfResponse = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(
                csrfResponse.getResponse().getContentAsString(), "$.token");
        String header = com.jayway.jsonpath.JsonPath.read(
                csrfResponse.getResponse().getContentAsString(), "$.headerName");
        var session = (MockHttpSession) csrfResponse.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(post("/api/v1/saved-searches").with(alice())
                        .session(session).header(header, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Backend\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void savedSearchesAndAlertsAreScopedToGoogleSubject() throws Exception {
        var aliceSearch = searches.create("alice-subject", request("Alice"));
        var bobSearch = searches.create("bob-subject", request("Bob"));
        var job = jobs.save(JobPosting.create(JobSource.GREENHOUSE, "board", "security-job",
                "Example", "Engineer", "Remote", "Build systems", "Full-time",
                RemotePolicy.REMOTE, "https://example.com/jobs/security-job",
                Instant.now(), Instant.now()));
        var bobAlert = alertRepository.save(JobAlert.create(bobSearch.getId(), job.getId(),
                Instant.now()));
        alertRepository.save(JobAlert.create(aliceSearch.getId(), job.getId(), Instant.now()));

        mvc.perform(get("/api/v1/saved-searches").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Alice"));
        mvc.perform(patch("/api/v1/saved-searches/{id}", bobSearch.getId())
                        .with(alice()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Taken\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/alerts").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(patch("/api/v1/alerts/{id}/read", bobAlert.getId())
                        .with(alice()).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(alertRepository.findById(bobAlert.getId()).orElseThrow().getReadAt()).isNull();
    }

    @Test
    void adminRequiresRoleAndAuditIgnoresCallerActorHeader() throws Exception {
        mvc.perform(get("/api/v1/admin/overview").with(alice()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/ingestion-targets").with(alice()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/boards").with(admin()).with(csrf())
                        .header("X-Admin-Actor", "forged-actor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"GREENHOUSE\",\"sourceAccount\":\"security-board\",\"company\":\"Example\",\"intervalMinutes\":60}"))
                .andExpect(status().isOk());
        assertThat(audits.findAll()).anySatisfy(entry ->
                assertThat(entry.getActor()).isEqualTo("admin-subject"));
    }

    private static CreateSavedSearchRequest request(String name) {
        return new CreateSavedSearchRequest(name, "engineer", null, null, null, null);
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor alice() {
        return oidcLogin().idToken(token -> token.claim("sub", "alice-subject")
                .claim("email", "alice@example.com")
                .claim("email_verified", true));
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor admin() {
        return oidcLogin().idToken(token -> token.claim("sub", "admin-subject")
                .claim("email", "admin@example.com")
                .claim("email_verified", true))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
