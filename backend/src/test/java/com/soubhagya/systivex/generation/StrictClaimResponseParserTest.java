package com.soubhagya.systivex.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soubhagya.systivex.evidence.EvidenceCategory;
import com.soubhagya.systivex.evidence.EvidenceQualification;
import com.soubhagya.systivex.localmodel.Deadline;
import com.soubhagya.systivex.localmodel.MonotonicClock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrictClaimResponseParserTest {

    private static StrictClaimResponseParser parser() {
        return new StrictClaimResponseParser(new tools.jackson.databind.ObjectMapper());
    }

    private static String claimJson(int count) {
        StringBuilder builder = new StringBuilder("{\"claims\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append("{\"statement\":\"statement-").append(i)
                    .append("\",\"category\":\"OBSERVED_FACT\",")
                    .append("\"evidenceReferences\":[\"E1\"],")
                    .append("\"qualification\":\"CURRENT\",")
                    .append("\"limitations\":[]}");
        }
        builder.append("]}");
        return builder.toString();
    }

    @Test
    void parsesValidClaim() {
        List<StrictClaimResponseParser.ParsedClaim> claims =
                parser().parse(claimJson(1), Deadline.after(Duration.ofSeconds(5), MonotonicClock.system()));

        assertThat(claims).hasSize(1);
        assertThat(claims.get(0).statement()).isEqualTo("statement-0");
    }

    @Test
    void emptyClaimsArrayIsValid() {
        assertThat(parser().parse("{\"claims\":[]}",
                Deadline.after(Duration.ofSeconds(5), MonotonicClock.system()))).isEmpty();
    }

    @Test
    void blankResponseIsMalformed() {
        assertThatThrownBy(() -> parser().parse("   ",
                Deadline.after(Duration.ofSeconds(5), MonotonicClock.system())))
                .isInstanceOf(StrictClaimResponseParser.MalformedResponseException.class);
    }

    @Test
    void malformedJsonIsRejected() {
        assertThatThrownBy(() -> parser().parse("{\"claims\":[",
                Deadline.after(Duration.ofSeconds(5), MonotonicClock.system())))
                .isInstanceOf(StrictClaimResponseParser.MalformedResponseException.class);
    }

    @Test
    void duplicatePropertyIsRejected() {
        assertThatThrownBy(() -> parser().parse(
                "{\"claims\":[],\"claims\":[]}",
                Deadline.after(Duration.ofSeconds(5), MonotonicClock.system())))
                .isInstanceOf(StrictClaimResponseParser.MalformedResponseException.class);
    }

    @Test
    void unknownPropertyIsRejected() {
        assertThatThrownBy(() -> parser().parse(
                "{\"claims\":[],\"unexpected\":true}",
                Deadline.after(Duration.ofSeconds(5), MonotonicClock.system())))
                .isInstanceOf(StrictClaimResponseParser.MalformedResponseException.class);
    }

    @Test
    void trailingTokenIsRejected() {
        assertThatThrownBy(() -> parser().parse(
                "{\"claims\":[]} extra",
                Deadline.after(Duration.ofSeconds(5), MonotonicClock.system())))
                .isInstanceOf(StrictClaimResponseParser.MalformedResponseException.class);
    }

    @Test
    void documentLengthBoundaryIsEnforced() {
        StringBuilder limitation = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            limitation.append('a');
        }
        StringBuilder response = new StringBuilder(
                "{\"claims\":[{\"statement\":\"x\",\"category\":\"OBSERVED_FACT\","
                        + "\"evidenceReferences\":[\"E1\"],\"qualification\":\"CURRENT\",\"limitations\":[");
        for (int i = 0; i < 70; i++) {
            if (i > 0) {
                response.append(",");
            }
            response.append("\"").append(limitation).append("\"");
        }
        response.append("]}]}");
        assertThatThrownBy(() -> parser().parse(response.toString(),
                Deadline.after(Duration.ofSeconds(5), MonotonicClock.system())))
                .isInstanceOf(StrictClaimResponseParser.MalformedResponseException.class);
    }

    @Test
    void nullArrayElementIsRejected() {
        assertThatThrownBy(() -> parser().parse(
                "{\"claims\":[null]}",
                Deadline.after(Duration.ofSeconds(5), MonotonicClock.system())))
                .isInstanceOf(StrictClaimResponseParser.MalformedResponseException.class);
    }

    @Test
    void twentyFiveClaimsAreAccepted() {
        assertThat(parser().parse(claimJson(25),
                Deadline.after(Duration.ofSeconds(5), MonotonicClock.system()))).hasSize(25);
    }

    @Test
    void twentySixClaimsRejectWholeResponse() {
        assertThatThrownBy(() -> parser().parse(claimJson(26),
                Deadline.after(Duration.ofSeconds(5), MonotonicClock.system())))
                .isInstanceOf(StrictClaimResponseParser.GenerationClaimCapExceededException.class);
    }

    @Test
    void oneHundredOneClaimsRejectsStructuralLimit() {
        assertThatThrownBy(() -> parser().parse(claimJson(101),
                Deadline.after(Duration.ofSeconds(5), MonotonicClock.system())))
                .isInstanceOf(StrictClaimResponseParser.StructuralLimitExceededException.class);
    }

    @Test
    void expiredDeadlineRejectsParsing() {
        Deadline deadline = Deadline.after(Duration.ZERO, MonotonicClock.system());

        assertThatThrownBy(() -> parser().parse(claimJson(1), deadline))
                .isInstanceOf(com.soubhagya.systivex.localmodel.OperationDeadlineExceededException.class);
    }
}
