package com.soubhagya.systivex.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure unit tests for the Actuator health parser. */
class HealthParserTest {

    @ParameterizedTest
    @ValueSource(strings = {"UP", "DOWN", "OUT_OF_SERVICE", "UNKNOWN"})
    void knownStatusesParse(String status) {
        assertThat(HealthParser.parse("{\"status\":\"" + status + "\"}")).isEqualTo(status);
    }

    @Test
    void fullActuatorBodyParses() {
        String body = "{\"status\":\"UP\",\"components\":{\"db\":{\"status\":\"UP\"}}}";

        assertThat(HealthParser.parse(body)).isEqualTo("UP");
    }

    @Test
    void missingStatusFails() {
        assertThatThrownBy(() -> HealthParser.parse("{\"components\":{}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unexpectedStatusFails() {
        assertThatThrownBy(() -> HealthParser.parse("{\"status\":\"COMPROMISED\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedJsonFails() {
        assertThatThrownBy(() -> HealthParser.parse("not json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyBodyFails() {
        assertThatThrownBy(() -> HealthParser.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullBodyFails() {
        assertThatThrownBy(() -> HealthParser.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void whitespaceOnlyBodyFails() {
        assertThatThrownBy(() -> HealthParser.parse("   \n\t  \r\n "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void maximumSupportedBodySizeIsAccepted() {
        String body = bodyOfSize(HealthParser.MAX_CHARS);

        assertThat(body).hasSize(HealthParser.MAX_CHARS);
        assertThat(HealthParser.parse(body)).isEqualTo("UP");
    }

    @Test
    void bodyExceedingMaxCharsIsRejected() {
        String body = bodyOfSize(HealthParser.MAX_CHARS + 1);

        assertThat(body).hasSize(HealthParser.MAX_CHARS + 1);
        assertThatThrownBy(() -> HealthParser.parse(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds size bound");
    }

    @Test
    void whitespaceAroundHealthStatusIsAccepted() {
        assertThat(HealthParser.parse("  \n {\"status\" : \"UP\"}  \t ")).isEqualTo("UP");
    }

    @Test
    void whitespaceInsideStatusValueIsAccepted() {
        assertThat(HealthParser.parse("{\"status\":\"  UP  \"}")).isEqualTo("UP");
    }

    /** Builds a syntactically valid body of exactly {@code size} characters. */
    private static String bodyOfSize(int size) {
        String prefix = "{\"status\":\"UP\",\"padding\":\"";
        String suffix = "\"}";
        return prefix + "a".repeat(size - prefix.length() - suffix.length()) + suffix;
    }
}
