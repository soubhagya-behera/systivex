package com.soubhagya.systivex.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EvidenceSourceContractTest {

    @Test
    void everySourceAdapterImplementsTheEvidenceSourceContract() {
        Object[] adapters = {
                new TwinEntityEvidenceAdapter(),
                new TwinRelationshipEvidenceAdapter(),
                new RuntimeObservationEvidenceAdapter(),
                new ImpactEvidenceAdapter(),
                new RiskEvidenceAdapter(),
                new SimulationEvidenceAdapter(),
                new PolicyEvidenceAdapter(),
                new ProposalEvidenceAdapter(),
                new AuditEvidenceAdapter()
        };

        for (Object adapter : adapters) {
            assertThat(adapter).isInstanceOf(EvidenceSource.class);
        }
        assertThat(adapters).hasSize(9);
    }
}
