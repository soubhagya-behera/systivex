package com.soubhagya.systivex.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClaimQualificationTableTest {

    @Test
    void everyPairIsHandledExactlyOnceWithExpectedContribution() {
        Map<EvidenceQualification, Map<EvidenceSourceStatus, EvidenceQualification>> expected =
                new EnumMap<>(EvidenceQualification.class);
        expected.put(EvidenceQualification.CURRENT, nonNullMap(
                EvidenceSourceStatus.AVAILABLE, EvidenceQualification.CURRENT,
                EvidenceSourceStatus.UNAVAILABLE, EvidenceQualification.UNCERTAIN,
                EvidenceSourceStatus.TRUNCATED, EvidenceQualification.TRUNCATED));
        expected.put(EvidenceQualification.STALE, nonNullMap(
                EvidenceSourceStatus.AVAILABLE, EvidenceQualification.STALE,
                EvidenceSourceStatus.UNAVAILABLE, EvidenceQualification.UNCERTAIN,
                EvidenceSourceStatus.TRUNCATED, EvidenceQualification.TRUNCATED));
        expected.put(EvidenceQualification.UNCERTAIN, nonNullMap(
                EvidenceSourceStatus.AVAILABLE, EvidenceQualification.UNCERTAIN,
                EvidenceSourceStatus.UNAVAILABLE, EvidenceQualification.UNCERTAIN,
                EvidenceSourceStatus.TRUNCATED, EvidenceQualification.TRUNCATED));
        expected.put(EvidenceQualification.TRUNCATED, nonNullMap(
                EvidenceSourceStatus.TRUNCATED, EvidenceQualification.TRUNCATED));
        expected.put(EvidenceQualification.MISSING, nonNullMap(
                EvidenceSourceStatus.UNAVAILABLE, EvidenceQualification.MISSING));
        expected.put(EvidenceQualification.HYPOTHETICAL, nonNullMap(
                EvidenceSourceStatus.AVAILABLE, EvidenceQualification.HYPOTHETICAL,
                EvidenceSourceStatus.TRUNCATED, EvidenceQualification.TRUNCATED));
        expected.put(EvidenceQualification.HISTORICAL, nonNullMap(
                EvidenceSourceStatus.AVAILABLE, EvidenceQualification.HISTORICAL,
                EvidenceSourceStatus.UNAVAILABLE, EvidenceQualification.UNCERTAIN,
                EvidenceSourceStatus.TRUNCATED, EvidenceQualification.TRUNCATED));
        expected.put(EvidenceQualification.ADVISORY, nonNullMap(
                EvidenceSourceStatus.AVAILABLE, EvidenceQualification.ADVISORY,
                EvidenceSourceStatus.TRUNCATED, EvidenceQualification.TRUNCATED));

        int valid = 0;
        int invalid = 0;
        for (EvidenceQualification qualification : EvidenceQualification.values()) {
            for (EvidenceSourceStatus status : EvidenceSourceStatus.values()) {
                EvidenceQualification contribution =
                        ClaimValidator.contributionFor(qualification, status);
                EvidenceQualification want = expected
                        .getOrDefault(qualification, Map.of())
                        .get(status);
                assertThat(contribution)
                        .as("%s/%s", qualification, status)
                        .isEqualTo(want);
                if (contribution == null) {
                    invalid++;
                } else {
                    valid++;
                }
            }
        }
        assertThat(valid).isEqualTo(18);
        assertThat(invalid).isEqualTo(14);
    }

    @Test
    void unavailableFloorAppliesToDisputedPairs() {
        assertThat(ClaimValidator.contributionFor(
                        EvidenceQualification.HISTORICAL, EvidenceSourceStatus.UNAVAILABLE))
                .isEqualTo(EvidenceQualification.UNCERTAIN);
        assertThat(ClaimValidator.contributionFor(
                        EvidenceQualification.STALE, EvidenceSourceStatus.UNAVAILABLE))
                .isEqualTo(EvidenceQualification.UNCERTAIN);
        assertThat(ClaimValidator.contributionFor(
                        EvidenceQualification.CURRENT, EvidenceSourceStatus.UNAVAILABLE))
                .isEqualTo(EvidenceQualification.UNCERTAIN);
    }

    @Test
    void missingOutranksTruncatedAndUncertainFloor() {
        assertThat(ClaimValidator.contributionFor(
                        EvidenceQualification.MISSING, EvidenceSourceStatus.UNAVAILABLE))
                .isEqualTo(EvidenceQualification.MISSING);
        assertThat(ClaimValidator.contributionFor(
                        EvidenceQualification.UNCERTAIN, EvidenceSourceStatus.TRUNCATED))
                .isEqualTo(EvidenceQualification.TRUNCATED);
    }

    @Test
    void allOmittedPairsAreInvalid() {
        for (EvidenceQualification qualification : EvidenceQualification.values()) {
            assertThat(ClaimValidator.contributionFor(qualification, EvidenceSourceStatus.OMITTED))
                    .as("%s/OMITTED", qualification)
                    .isNull();
        }
    }

    private static Map<EvidenceSourceStatus, EvidenceQualification> nonNullMap(
            EvidenceSourceStatus firstStatus, EvidenceQualification firstQualification,
            Object... rest) {
        Map<EvidenceSourceStatus, EvidenceQualification> map =
                new EnumMap<>(EvidenceSourceStatus.class);
        map.put(firstStatus, firstQualification);
        for (int i = 0; i < rest.length; i += 2) {
            map.put((EvidenceSourceStatus) rest[i], (EvidenceQualification) rest[i + 1]);
        }
        return map;
    }
}
