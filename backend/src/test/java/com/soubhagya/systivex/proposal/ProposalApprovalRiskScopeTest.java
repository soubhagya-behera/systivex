package com.soubhagya.systivex.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.security.TestJwt;
import com.soubhagya.systivex.twin.AbstractPostgresIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Approval freshness compares equivalent risk scopes (D29).
 *
 * <p>The assessment stores the <em>after</em>-simulation risk: the risk of
 * the proposal's own hypothetical graph. The approval recheck must
 * therefore recompute the risk of that same hypothetical state over the
 * current verified rows — baseline risk against hypothetical risk compares
 * two different scopes and rejects valid proposals whose change reshapes
 * the impact set.
 *
 * <p>The fixture makes the two scopes differ on purpose: removing an
 * entity drops its exclusive dependents out of the hypothetical scope while
 * the baseline scope still contains them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProposalApprovalRiskScopeTest extends AbstractPostgresIntegrationTest {

    private static final tools.jackson.databind.ObjectMapper JSON =
            JsonMapper.builder().build();

    @LocalServerPort private int port;

    @Autowired private JdbcTemplate jdbc;

    private final RestTemplate http = new RestTemplate();

    private String prefix;
    private String refA;
    private String refB;
    private String refC;
    private String refD;
    private String refE;

    @BeforeEach
    void setup() {
        http.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response)
                    throws java.io.IOException {
                return false;
            }
        });
        prefix = "sec-scope-" + UUID.randomUUID().toString().substring(0, 8);
        refA = prefix + ":svc-a";
        refB = prefix + ":svc-b";
        refC = prefix + ":svc-c";
        refD = prefix + ":svc-d";
        refE = prefix + ":svc-e";
        insertEntity("Alpha", refA);
        insertEntity("Beta", refB);
        insertEntity("Gamma", refC);
        insertEntity("Delta", refD);
        insertEntity("Epsilon", refE);
        // Alpha -> Gamma, Beta -> Gamma, Beta -> Epsilon.
        insertEdge(refA, refC, "CALLS");
        insertEdge(refB, refC, "CALLS");
        insertEdge(refB, refE, "CALLS");
        // Gamma is the only entity the proposal still impacts, and it starts
        // with fresh, healthy runtime evidence (MEDIUM, not UNKNOWN).
        setRuntime(refC, "UP", Instant.now());
        resetGovernance();
    }

    @AfterEach
    void clean() {
        resetGovernance();
        jdbc.update("DELETE FROM system_entity WHERE external_ref LIKE '" + prefix + ":%'");
    }

    private void resetGovernance() {
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM proposal_revision");
        jdbc.update("UPDATE audit_chain_head SET last_seq = 0, last_hash = 'GENESIS'");
    }

    private void insertEntity(String name, String ref) {
        jdbc.update(
                "INSERT INTO system_entity (id, type, name, external_ref, environment, metadata)"
                        + " VALUES (?, 'SERVICE', ?, ?, 'test', '{}')",
                UUID.randomUUID(), name, ref);
    }

    private void insertEdge(String source, String target, String type) {
        jdbc.update(
                "INSERT INTO system_relationship (id, source_entity_id, target_entity_id,"
                        + " relationship_type) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), UUID.fromString(idOf(source)), UUID.fromString(idOf(target)),
                type);
    }

    private String idOf(String ref) {
        return jdbc.queryForObject(
                "SELECT id FROM system_entity WHERE external_ref = ?", String.class, ref);
    }

    private void setRuntime(String ref, String status, Instant observedAt) {
        String runtime = "{\"runtime\": {\"observedAt\": \"" + observedAt
                + "\", \"health\": {\"status\": \"" + status + "\", \"available\": true}}}";
        jdbc.update("UPDATE system_entity SET metadata = ?::jsonb WHERE external_ref = ?",
                runtime, ref);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return http.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
    }

    private static String token(String... roles) {
        return TestJwt.token(List.of(roles));
    }

    private static JsonNode body(ResponseEntity<String> response) {
        try {
            return JSON.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Removes Beta and links Alpha to Delta.
     *
     * <p>Roots are the touched entities (Alpha, Beta, Delta). The baseline
     * impact set is {Gamma, Epsilon}: Gamma is reachable from Alpha and
     * Beta, Epsilon only from Beta. The hypothetical graph drops Beta and
     * its incident edges, so its impact set is {Gamma} alone.
     */
    private List<Map<String, Object>> removeBetaAndLinkAlpha() {
        return List.of(
                Map.of("type", "REMOVE_ENTITY", "entity", refB),
                Map.of("type", "ADD_RELATIONSHIP", "source", refA, "target", refD,
                        "relationshipType", "DEPENDS_ON"));
    }

    private String submittedKey(String analyst, List<Map<String, Object>> ops) {
        ResponseEntity<String> drafted = call(HttpMethod.POST, "/api/v1/twin/proposals", analyst,
                Map.of("title", "Change", "operations", ops));
        assertThat(drafted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String key = body(drafted).get("proposalKey").asText();
        JsonNode assessed = body(call(HttpMethod.POST,
                "/api/v1/twin/proposals/" + key + "/assess", analyst, null));
        ResponseEntity<String> submitted = call(HttpMethod.POST,
                "/api/v1/twin/proposals/" + key + "/submit", analyst,
                Map.of("revisionNumber", assessed.get("revisionNumber").asInt(),
                        "operationsHash", assessed.get("operationsHash").asText(),
                        "assessmentHash", assessed.get("assessmentHash").asText()));
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);
        return key;
    }

    private String statusOf(String key) {
        return body(call(HttpMethod.GET, "/api/v1/twin/proposals/" + key, token("TWIN_READ"),
                null)).get("status").asText();
    }

    @Test
    void removalProposalIsApprovedWhenTheAssessedScopeIsTheHypotheticalScope() {
        String analyst = token("TWIN_ANALYZE");
        String key = submittedKey(analyst, removeBetaAndLinkAlpha());

        ResponseEntity<String> approved = call(HttpMethod.POST,
                "/api/v1/twin/proposals/" + key + "/approve", token("TWIN_APPROVER"),
                Map.of("reason", "Reviewed against the twin"));

        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(approved).get("status").asText()).isEqualTo("APPROVED");
        assertThat(statusOf(key)).isEqualTo("APPROVED");
    }

    @Test
    void escalationInsideTheAssessedScopeBlocksApproval() {
        String analyst = token("TWIN_ANALYZE");
        String key = submittedKey(analyst, removeBetaAndLinkAlpha());
        setRuntime(refC, "DOWN", Instant.now());

        ResponseEntity<String> approved = call(HttpMethod.POST,
                "/api/v1/twin/proposals/" + key + "/approve", token("TWIN_APPROVER"),
                Map.of("reason", "escalated"));

        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body(approved).get("message").asText())
                .contains("Risk level increased since assessment");
        assertThat(statusOf(key)).isEqualTo("AWAITING_APPROVAL");
    }

    @Test
    void runtimeEvidenceOutsideTheAssessedScopeDoesNotBlockApproval() {
        String analyst = token("TWIN_ANALYZE");
        String key = submittedKey(analyst, removeBetaAndLinkAlpha());
        // Epsilon is not in the proposal's impact set any more: removing
        // Beta disconnects it, so its runtime evidence is not this
        // proposal's risk and must not decide the approval.
        setRuntime(refE, "DOWN", Instant.now());

        ResponseEntity<String> approved = call(HttpMethod.POST,
                "/api/v1/twin/proposals/" + key + "/approve", token("TWIN_APPROVER"),
                Map.of("reason", "Reviewed against the twin"));

        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(approved).get("status").asText()).isEqualTo("APPROVED");
    }
}
