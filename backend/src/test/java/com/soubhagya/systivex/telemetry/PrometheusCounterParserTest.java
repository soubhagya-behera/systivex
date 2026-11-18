package com.soubhagya.systivex.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for the Prometheus exposition parser: allowlisted
 * parsing, unknown-family handling, label/cardinality guards, malformed
 * values, privacy deny-list, bounds, and determinism.
 */
class PrometheusCounterParserTest {

    private static final String VALID_ORDER =
            """
            # HELP checkout_success_total business counter
            # TYPE checkout_success_total counter
            checkout_success_total 3.0
            checkout_failure_total 1
            http_server_requests_seconds_count 42
            jvm_memory_used_bytes 1.0E9
            """;

    @Test
    void validExpositionParsesAllowlistedFamilies() {
        Map<String, Long> parsed = PrometheusCounterParser.parse(VALID_ORDER);

        assertThat(parsed)
                .containsEntry("checkout.success", 3L)
                .containsEntry("checkout.failure", 1L);
        assertThat(parsed).doesNotContainKey("http_server_requests_seconds_count");
    }

    @Test
    void allSixFamiliesParse() {
        String body =
                """
                checkout_success_total 1
                checkout_failure_total 2
                inventory_reservation_success_total 4
                inventory_reservation_rejected_total 0
                payment_authorization_success_total 7
                payment_authorization_declined_total 9
                """;

        Map<String, Long> parsed = PrometheusCounterParser.parse(body);

        assertThat(parsed).hasSize(6);
        assertThat(parsed.get("payment.authorization.declined")).isEqualTo(9L);
    }

    @Test
    void unknownNonBusinessFamiliesAreIgnored() {
        String body =
                """
                http_server_requests_seconds_count 5
                process_cpu_usage 0.1
                checkout_success_total 2
                """;

        Map<String, Long> parsed = PrometheusCounterParser.parse(body);

        assertThat(parsed).containsOnlyKeys("checkout.success");
    }

    @Test
    void unknownBusinessFamilyFailsClosed() {
        String body = "checkout_custom_total 5\ncheckout_success_total 1\n";

        assertThatThrownBy(() -> PrometheusCounterParser.parse(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported metric family");
    }

    @Test
    void labelsOnAllowlistedCounterFailClosed() {
        String body = "checkout_success_total{outcome=\"ok\"} 5\n";

        assertThatThrownBy(() -> PrometheusCounterParser.parse(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unexpected labels");
    }

    @Test
    void emptyBodyFails() {
        assertThatThrownBy(() -> PrometheusCounterParser.parse("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeValueFails() {
        assertThatThrownBy(
                        () -> PrometheusCounterParser.parse("checkout_success_total -1\n"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonIntegralValueFails() {
        assertThatThrownBy(
                        () -> PrometheusCounterParser.parse("checkout_success_total 1.5\n"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nanValueFails() {
        assertThatThrownBy(() -> PrometheusCounterParser.parse("checkout_success_total NaN\n"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conflictingDuplicatesFail() {
        String body = "checkout_success_total 1\ncheckout_success_total 2\n";

        assertThatThrownBy(() -> PrometheusCounterParser.parse(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conflicting duplicate");
    }

    @Test
    void identicalDuplicatesAreAccepted() {
        String body = "checkout_success_total 1\ncheckout_success_total 1\n";

        assertThat(PrometheusCounterParser.parse(body))
                .containsEntry("checkout.success", 1L);
    }

    @Test
    void privacySensitiveContentFailsClosed() {
        String body = "checkout_success_total 1\n# customer CUST-1001 leaked\n";

        assertThatThrownBy(() -> PrometheusCounterParser.parse(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("privacy");
    }

    @Test
    void passwordInPayloadFailsClosed() {
        String body = "checkout_success_total 1\n# password=secret\n";

        assertThatThrownBy(() -> PrometheusCounterParser.parse(body))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oversizedPayloadFails() {
        String body = "checkout_success_total 1\n" + "x".repeat(PrometheusCounterParser.MAX_CHARS);

        assertThatThrownBy(() -> PrometheusCounterParser.parse(body))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsingIsDeterministic() {
        assertThat(PrometheusCounterParser.parse(VALID_ORDER))
                .isEqualTo(PrometheusCounterParser.parse(VALID_ORDER));
    }

    @Test
    void payloadOfExactlyMaxCharsIsAccepted() {
        String body = expositionOfSize(PrometheusCounterParser.MAX_CHARS);

        assertThat(body).hasSize(PrometheusCounterParser.MAX_CHARS);
        assertThat(PrometheusCounterParser.parse(body)).containsEntry("checkout.success", 1L);
    }

    @Test
    void payloadOfMaxCharsPlusOneIsRejected() {
        String body = expositionOfSize(PrometheusCounterParser.MAX_CHARS + 1);

        assertThat(body).hasSize(PrometheusCounterParser.MAX_CHARS + 1);
        assertThatThrownBy(() -> PrometheusCounterParser.parse(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "exceeds " + PrometheusCounterParser.MAX_CHARS + " characters");
    }

    @Test
    void sampleCountOfExactlyMaxLinesIsAccepted() {
        String body = expositionWithSamples(PrometheusCounterParser.MAX_LINES);

        assertThat(body.length()).isLessThan(PrometheusCounterParser.MAX_CHARS);
        assertThat(PrometheusCounterParser.parse(body)).containsEntry("checkout.success", 1L);
    }

    @Test
    void sampleCountOfMaxLinesPlusOneIsRejected() {
        String body = expositionWithSamples(PrometheusCounterParser.MAX_LINES + 1);

        assertThat(body.length()).isLessThan(PrometheusCounterParser.MAX_CHARS);
        assertThatThrownBy(() -> PrometheusCounterParser.parse(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "exceeds " + PrometheusCounterParser.MAX_LINES + " samples");
    }

    @Test
    void blankAndCommentLinesDoNotCountTowardsTheSampleLimit() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < PrometheusCounterParser.MAX_LINES; i++) {
            body.append("checkout_success_total 1\n");
        }
        body.append("#\n\n");

        assertThat(body.length()).isLessThan(PrometheusCounterParser.MAX_CHARS);
        assertThat(PrometheusCounterParser.parse(body.toString()))
                .containsEntry("checkout.success", 1L);
    }

    @Test
    void timestampedSampleIsParsed() {
        Map<String, Long> parsed =
                PrometheusCounterParser.parse("checkout_success_total 7 1700000000000\n");

        assertThat(parsed).containsEntry("checkout.success", 7L);
    }

    @Test
    void timestampedSampleWithEpochZeroIsParsed() {
        Map<String, Long> parsed = PrometheusCounterParser.parse("checkout_failure_total 2 0\n");

        assertThat(parsed).containsEntry("checkout.failure", 2L);
    }

    @Test
    void blankAndCommentLinesAreIgnored() {
        String body =
                "# HELP checkout_success_total business counter\n"
                        + "# TYPE checkout_success_total counter\n"
                        + "\n"
                        + "   \n"
                        + "\t\n"
                        + "checkout_success_total 3\n"
                        + "# trailing comment\n";

        Map<String, Long> parsed = PrometheusCounterParser.parse(body);

        assertThat(parsed).containsOnlyKeys("checkout.success");
        assertThat(parsed.get("checkout.success")).isEqualTo(3L);
    }

    @Test
    void unrelatedMetricFamiliesAreIgnored() {
        String body =
                """
                jvm_memory_used_bytes 1.0E9
                node_cpu_seconds_total 0.5
                tomcat_sessions_active_current_sessions 4
                hikaricp_connections_pending 2
                system_cpu_usage 0.2
                checkout_success_total 8
                """;

        Map<String, Long> parsed = PrometheusCounterParser.parse(body);

        assertThat(parsed).containsOnlyKeys("checkout.success");
        assertThat(parsed.get("checkout.success")).isEqualTo(8L);
    }

    @Test
    void infinityValueIsRejected() {
        String body = "checkout_success_total Infinity\n";

        assertThatThrownBy(() -> PrometheusCounterParser.parse(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid counter value");
    }

    @Test
    void negativeInfinityValueIsRejected() {
        String body = "checkout_success_total -Infinity\n";

        assertThatThrownBy(() -> PrometheusCounterParser.parse(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid counter value");
    }

    @Test
    void malformedNumericValueIsRejected() {
        assertThatThrownBy(() -> PrometheusCounterParser.parse("checkout_success_total abc\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed counter value");
    }

    @Test
    void malformedDecimalValueIsRejected() {
        assertThatThrownBy(() -> PrometheusCounterParser.parse("checkout_success_total 1.2.3\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed counter value");
    }

    @Test
    void counterValueAboveSupportedRangeIsRejected() {
        String body = "checkout_success_total 9000000000000001\n";
        String scientific = "checkout_success_total 1e16\n";

        assertThatThrownBy(() -> PrometheusCounterParser.parse(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checkout_success_total");

        assertThatThrownBy(() -> PrometheusCounterParser.parse(scientific))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checkout_success_total");
    }

    @Test
    void counterValueAtSupportedRangeUpperBoundIsAccepted() {
        Map<String, Long> parsed =
                PrometheusCounterParser.parse("checkout_success_total 9000000000000000\n");

        assertThat(parsed).containsEntry("checkout.success", 9000000000000000L);
    }

    /** Builds an exposition of exactly {@code size} characters ending in a comment. */
    private static String expositionOfSize(int size) {
        StringBuilder body = new StringBuilder("checkout_success_total 1\n# ");
        while (body.length() < size) {
            body.append('x');
        }
        return body.toString();
    }

    /** Builds an exposition carrying exactly {@code sampleCount} sample lines. */
    private static String expositionWithSamples(int sampleCount) {
        StringBuilder body = new StringBuilder("checkout_success_total 1\n");
        for (int i = 1; i < sampleCount; i++) {
            body.append("http_requests 1\n");
        }
        body.setLength(body.length() - 1);
        return body.toString();
    }
}
