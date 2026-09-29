package com.soubhagya.systivex.twin;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.twin.api.CreateSystemEntityRequest;
import com.soubhagya.systivex.twin.api.CreateSystemRelationshipRequest;
import com.soubhagya.systivex.twin.api.SystemEntityResponse;
import com.soubhagya.systivex.twin.api.SystemRelationshipResponse;
import com.soubhagya.systivex.twin.model.SystemEntityType;
import com.soubhagya.systivex.twin.model.SystemRelationshipType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class TwinApiTest extends AbstractPostgresIntegrationTest {

    @Autowired private TestRestTemplate rest;

    private SystemEntityResponse createEntity(String name, String externalRef) {
        ResponseEntity<SystemEntityResponse> response =
                rest.postForEntity(
                        "/api/v1/twin/entities",
                        new CreateSystemEntityRequest(
                                SystemEntityType.SERVICE,
                                name,
                                externalRef,
                                "dev",
                                Map.of("owner", "twin-test")),
                        SystemEntityResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    @Test
    void createAndRetrieveEntity() {
        SystemEntityResponse created = createEntity("orders-api", "test:orders-api");

        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("orders-api");
        assertThat(created.metadata()).containsEntry("owner", "twin-test");
        assertThat(created.createdAt()).isNotNull();

        ResponseEntity<SystemEntityResponse> fetched =
                rest.getForEntity(
                        "/api/v1/twin/entities/" + created.id(), SystemEntityResponse.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().name()).isEqualTo("orders-api");
    }

    @Test
    void listEntitiesContainsCreated() {
        SystemEntityResponse created = createEntity("inventory", "test:inventory");

        ResponseEntity<SystemEntityResponse[]> response =
                rest.getForEntity("/api/v1/twin/entities", SystemEntityResponse[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .anyMatch(entity -> entity.id().equals(created.id()));
    }

    @Test
    void getMissingEntityReturns404() {
        ResponseEntity<String> response =
                rest.getForEntity(
                        "/api/v1/twin/entities/" + UUID.randomUUID(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createEntityWithBlankNameReturns400() {
        ResponseEntity<String> response =
                rest.postForEntity(
                        "/api/v1/twin/entities",
                        new CreateSystemEntityRequest(
                                SystemEntityType.API, "  ", null, null, null),
                        String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void duplicateExternalRefReturns409() {
        createEntity("ledger", "test:ledger");

        ResponseEntity<String> response =
                rest.postForEntity(
                        "/api/v1/twin/entities",
                        new CreateSystemEntityRequest(
                                SystemEntityType.DATABASE, "ledger-copy", "test:ledger", null, null),
                        String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createAndListRelationship() {
        SystemEntityResponse source = createEntity("web-rel", "test:web-rel");
        SystemEntityResponse target = createEntity("db-rel", "test:db-rel");

        ResponseEntity<SystemRelationshipResponse> created =
                rest.postForEntity(
                        "/api/v1/twin/relationships",
                        new CreateSystemRelationshipRequest(
                                source.id(), target.id(), SystemRelationshipType.READS),
                        SystemRelationshipResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().sourceId()).isEqualTo(source.id());
        assertThat(created.getBody().targetId()).isEqualTo(target.id());

        ResponseEntity<SystemRelationshipResponse> fetched =
                rest.getForEntity(
                        "/api/v1/twin/relationships/" + created.getBody().id(),
                        SystemRelationshipResponse.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<SystemRelationshipResponse[]> listed =
                rest.getForEntity(
                        "/api/v1/twin/relationships", SystemRelationshipResponse[].class);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBody())
                .anyMatch(rel -> rel.id().equals(created.getBody().id()));
    }

    @Test
    void createRelationshipWithUnknownEndpointReturns404() {
        SystemEntityResponse source = createEntity("lonely", "test:lonely");

        ResponseEntity<String> response =
                rest.postForEntity(
                        "/api/v1/twin/relationships",
                        new CreateSystemRelationshipRequest(
                                source.id(), UUID.randomUUID(), SystemRelationshipType.CALLS),
                        String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createSelfRelationshipReturns400() {
        SystemEntityResponse source = createEntity("selfish", "test:selfish");

        ResponseEntity<String> response =
                rest.postForEntity(
                        "/api/v1/twin/relationships",
                        new CreateSystemRelationshipRequest(
                                source.id(), source.id(), SystemRelationshipType.CALLS),
                        String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createEntityWithUnknownTypeReturns400() {
        ResponseEntity<String> response =
                rest.postForEntity(
                        "/api/v1/twin/entities",
                        Map.of("type", "SPACESHIP", "name", "nope"),
                        String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getEntityWithMalformedIdReturns400() {
        ResponseEntity<String> response =
                rest.getForEntity("/api/v1/twin/entities/not-a-uuid", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void actuatorHealthStillUp() {
        ResponseEntity<String> response =
                rest.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
