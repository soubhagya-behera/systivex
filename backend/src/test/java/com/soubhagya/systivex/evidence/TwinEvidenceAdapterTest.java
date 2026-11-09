package com.soubhagya.systivex.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.twin.api.SystemEntityResponse;
import com.soubhagya.systivex.twin.api.SystemRelationshipResponse;
import com.soubhagya.systivex.twin.model.SystemEntity;
import com.soubhagya.systivex.twin.model.SystemEntityType;
import com.soubhagya.systivex.twin.model.SystemRelationship;
import com.soubhagya.systivex.twin.model.SystemRelationshipType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TwinEvidenceAdapterTest {

    @Test
    void twinEntityAdapterImplementsSourceAndExcludesMetadata() {
        SystemEntity entity = new SystemEntity(
                SystemEntityType.SERVICE,
                "checkout",
                "svc-1",
                "prod",
                Map.of("secret", "value", "managedBy", "repository-connector"));

        List<EvidenceItem<?>> items = new TwinEntityEvidenceAdapter().adapt(entity);

        assertThat(items).hasSize(1);
        EvidenceItem<?> item = items.get(0);
        assertThat(item.category()).isEqualTo(EvidenceCategory.OBSERVED_FACT);
        assertThat(item.source()).isEqualTo("twin.entity");
        assertThat(item.ref().value()).isEqualTo("E1");
        TwinEntityEvidence content = (TwinEntityEvidence) item.content();
        assertThat(content.name()).isEqualTo("checkout");
        assertThat(content.externalRef()).isEqualTo("svc-1");
        assertThat(content.environment()).isEqualTo("prod");
    }

    @Test
    void twinEntityAdapterSortsDeterministicallyAndAssignsRefs() {
        SystemEntity b = entity("z");
        SystemEntity a = entity("a");
        SystemEntity c = entity("m");

        List<EvidenceItem<?>> items = new TwinEntityEvidenceAdapter().adaptAll(List.of(b, c, a));

        assertThat(items).hasSize(3);
        assertThat(items).extracting(item -> item.ref().value())
                .containsExactly("E1", "E2", "E3");
        assertThat(items).extracting(item -> ((TwinEntityEvidence) item.content()).externalRef())
                .containsExactly("a", "m", "z");
    }

    @Test
    void twinEntityAdapterCapsAt100AndFlagsLastItem() {
        List<SystemEntity> entities = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            entities.add(entity(String.format("e%03d", i)));
        }

        List<EvidenceItem<?>> items = new TwinEntityEvidenceAdapter().adaptAll(entities);

        assertThat(items).hasSize(100);
        EvidenceItem<?> last = items.get(99);
        assertThat(last.ref().value()).isEqualTo("E100");
        assertThat(last.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(last.qualification()).isEqualTo(EvidenceQualification.TRUNCATED);
        assertThat(last.includedCount()).isEqualTo(100);
        assertThat(last.omittedCount()).isEqualTo(1);
        assertThat(last.limitations()).anyMatch(value -> value.contains("capped"));
        assertThat(((TwinEntityEvidence) last.content()).externalRef()).isEqualTo("e099");
    }

    @Test
    void twinEntityAdapterSkipsNullRows() {
        List<SystemEntity> entities = new ArrayList<>();
        entities.add(null);
        entities.add(entity("a"));

        assertThat(new TwinEntityEvidenceAdapter().adaptAll(entities)).hasSize(1);
        assertThat(new TwinEntityEvidenceAdapter().adaptAll(null)).isEmpty();
    }

    @Test
    void twinRelationshipAdapterSortsAndCaps() {
        SystemEntity source = entity("source");
        SystemEntity target = entity("target");
        SystemRelationship writes =
                new SystemRelationship(source, target, SystemRelationshipType.WRITES);
        SystemRelationship calls =
                new SystemRelationship(source, target, SystemRelationshipType.CALLS);
        SystemRelationship reads =
                new SystemRelationship(source, target, SystemRelationshipType.READS);

        List<EvidenceItem<?>> sorted =
                new TwinRelationshipEvidenceAdapter().adaptAll(List.of(writes, reads, calls));

        assertThat(sorted).extracting(
                        item -> ((TwinRelationshipEvidence) item.content()).type())
                .containsExactly(
                        SystemRelationshipType.CALLS,
                        SystemRelationshipType.READS,
                        SystemRelationshipType.WRITES);

        List<SystemRelationship> many = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            many.add(calls);
        }
        List<EvidenceItem<?>> capped = new TwinRelationshipEvidenceAdapter().adaptAll(many);
        assertThat(capped).hasSize(100);
        assertThat(capped.get(99).status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(capped.get(99).omittedCount()).isEqualTo(1);
    }

    @Test
    void twinResponseMappingsUseDeterministicRefs() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        SystemEntityResponse response = new SystemEntityResponse(
                UUID.randomUUID(),
                SystemEntityType.API,
                "api",
                "api-1",
                "prod",
                Map.of("secret", "value"),
                created,
                created);
        EvidenceItem<TwinEntityEvidence> entityItem =
                TwinEntityEvidenceAdapter.adaptResponse(response);
        assertThat(entityItem.ref().value()).isEqualTo("E1");
        assertThat(entityItem.timestamps()).hasSize(2);
        assertThat(entityItem.content().externalRef()).isEqualTo("api-1");

        SystemRelationshipResponse relationshipResponse = new SystemRelationshipResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                SystemRelationshipType.DEPENDS_ON,
                created);
        EvidenceItem<TwinRelationshipEvidence> relationshipItem =
                TwinRelationshipEvidenceAdapter.adaptResponse(relationshipResponse);
        assertThat(relationshipItem.ref().value()).isEqualTo("E1");
        assertThat(relationshipItem.content().type())
                .isEqualTo(SystemRelationshipType.DEPENDS_ON);
    }

    private static SystemEntity entity(String externalRef) {
        return new SystemEntity(
                SystemEntityType.SERVICE, externalRef, externalRef, "prod", Map.of());
    }
}
