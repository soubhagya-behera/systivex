package com.soubhagya.systivex.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.security.TestJwt;
import com.soubhagya.systivex.twin.AbstractPostgresIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Edge-conflict validation for governed proposals over real HTTP.
 *
 * <p>The conflict checks answer "does this stored edge exist?" for the edges
 * a proposal proposes. These tests pin the observable contract of that
 * check: existing-edge conflicts, duplicate proposed edges, non-conflicting
 * operations, and empty operations — for both draft creation and amendment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ProposalEdgeConflictApiTest extends AbstractPostgresIntegrationTest {

    private static final tools.jackson.databind.ObjectMapper JSON =
            JsonMapper.builder().build();

    @Autowired private MockMvc mockMvc;

    @Autowired private JdbcTemplate jdbc;

    private String prefix;
    private String refA;
    private String refB;
    private String refC;
    private String idA;
    private String idB;
    private String idC;
    // One token per test: the subject is random per token, and an amendment
    // is only allowed to the proposal's own proposer (issuer and subject pair).
    private String analyst;

    @BeforeEach
    void setup() {
        analyst = TestJwt.token(List.of("TWIN_ANALYZE"));
        prefix = "edge-conflict-" + UUID.randomUUID().toString().substring(0, 8);
        refA = prefix + ":svc-a";
        refB = prefix + ":svc-b";
        refC = prefix + ":svc-c";
        idA = insertEntity("Alpha", refA);
        idB = insertEntity("Beta", refB);
        idC = insertEntity("Gamma", refC);
        // The one stored edge every conflict case is measured against.
        jdbc.update(
                "INSERT INTO system_relationship (id, source_entity_id, target_entity_id,"
                        + " relationship_type) VALUES (?, ?, ?, 'CALLS')",
                UUID.randomUUID(), UUID.fromString(idA), UUID.fromString(idB));
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

    private String insertEntity(String name, String ref) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO system_entity (id, type, name, external_ref, environment, metadata)"
                        + " VALUES (?, 'SERVICE', ?, ?, 'test', '{}')",
                id, name, ref);
        return id.toString();
    }

    private MockHttpServletResponse post(String path, Object body) throws Exception {
        var request = MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body));
        request.header("Authorization", "Bearer " + analyst);
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private MockHttpServletResponse patch(String path, Object body) throws Exception {
        var request = MockMvcRequestBuilders.patch(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body));
        request.header("Authorization", "Bearer " + analyst);
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private static JsonNode body(MockHttpServletResponse response) {
        try {
            return JSON.readTree(response.getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> draftBody(Object operations) {
        return Map.of("title", "Change", "operations", operations);
    }

    private Map<String, Object> addEdge(String source, String target, String type) {
        return Map.of(
                "type", "ADD_RELATIONSHIP", "source", source, "target", target,
                "relationshipType", type);
    }

    private Map<String, Object> dropEdge(String source, String target, String type) {
        return Map.of(
                "type", "REMOVE_RELATIONSHIP", "source", source, "target", target,
                "relationshipType", type);
    }

    @Test
    void addingAnExistingEdgeIsRejectedWithConflict() throws Exception {
        MockHttpServletResponse response =
                post("/api/v1/twin/proposals",
                        draftBody(List.of(addEdge(idA, idB, "CALLS"))));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(body(response).get("message").asText()).contains("Edge already exists");
    }

    @Test
    void addingTheSameEndpointsWithAnotherTypeIsAccepted() throws Exception {
        // CALLS a->b already exists; READS a->b is a different edge.
        MockHttpServletResponse response =
                post("/api/v1/twin/proposals",
                        draftBody(List.of(addEdge(idA, idB, "READS"))));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED.value());
    }

    @Test
    void removingAMissingEdgeIsRejectedWithConflict() throws Exception {
        MockHttpServletResponse response =
                post("/api/v1/twin/proposals",
                        draftBody(List.of(dropEdge(idA, idC, "CALLS"))));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(body(response).get("message").asText())
                .contains("Relationship does not exist");
    }

    @Test
    void removingAnExistingEdgeIsAccepted() throws Exception {
        MockHttpServletResponse response =
                post("/api/v1/twin/proposals",
                        draftBody(List.of(dropEdge(idA, idB, "CALLS"))));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED.value());
    }

    @Test
    void duplicateProposedEdgesAreRejectedWithConflict() throws Exception {
        MockHttpServletResponse response =
                post("/api/v1/twin/proposals",
                        draftBody(
                                List.of(
                                        addEdge(idA, idC, "READS"),
                                        addEdge(idA, idC, "READS"))));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(body(response).get("message").asText()).contains("Duplicate operation");
    }

    @Test
    void emptyOperationsAreRejectedWithBadRequest() throws Exception {
        MockHttpServletResponse response =
                post("/api/v1/twin/proposals", draftBody(List.of()));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void amendmentRejectsAnExistingEdgeWithConflict() throws Exception {
        MockHttpServletResponse created =
                post("/api/v1/twin/proposals",
                        draftBody(List.of(Map.of("type", "REMOVE_ENTITY", "entity", refC))));
        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED.value());
        String key = body(created).get("proposalKey").asText();

        MockHttpServletResponse response =
                patch("/api/v1/twin/proposals/" + key,
                        draftBody(List.of(addEdge(idA, idB, "CALLS"))));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(body(response).get("message").asText()).contains("Edge already exists");
    }

    @Test
    void amendmentAcceptsNonConflictingOperations() throws Exception {
        MockHttpServletResponse created =
                post("/api/v1/twin/proposals",
                        draftBody(List.of(Map.of("type", "REMOVE_ENTITY", "entity", refC))));
        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED.value());
        String key = body(created).get("proposalKey").asText();

        MockHttpServletResponse response =
                patch("/api/v1/twin/proposals/" + key,
                        draftBody(List.of(addEdge(idA, idC, "READS"))));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(body(response).get("revisionNumber").asInt()).isEqualTo(2);
    }
}
