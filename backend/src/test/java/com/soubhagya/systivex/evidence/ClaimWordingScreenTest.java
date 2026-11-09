package com.soubhagya.systivex.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClaimWordingScreenTest {

    @Test
    void causalityAssertionsAreForbidden() {
        assertThat(ClaimWordingScreen.isForbidden("The removal caused by the change affects svc-a"))
                .isTrue();
        assertThat(ClaimWordingScreen.isForbidden("This change causes the outage in svc-a"))
                .isTrue();
        assertThat(ClaimWordingScreen.isForbidden("The update caused the latency in svc-a"))
                .isTrue();
    }

    @Test
    void breakageAssertionsAreForbidden() {
        assertThat(ClaimWordingScreen.isForbidden("Removing this will break svc-b")).isTrue();
        assertThat(ClaimWordingScreen.isForbidden("Removing this will not break svc-b")).isTrue();
        assertThat(ClaimWordingScreen.isForbidden("This breaks the api contract")).isTrue();
        assertThat(ClaimWordingScreen.isForbidden("Confirmed breakage of the database")).isTrue();
    }

    @Test
    void safetyAssertionsAreForbidden() {
        assertThat(ClaimWordingScreen.isForbidden("The change is safe")).isTrue();
        assertThat(ClaimWordingScreen.isForbidden("It is safe to execute now")).isTrue();
        assertThat(ClaimWordingScreen.isForbidden("There is no risk in this change")).isTrue();
        assertThat(ClaimWordingScreen.isForbidden("It is safe to deploy on Friday")).isTrue();
    }

    @Test
    void completenessAssertionsAreForbidden() {
        assertThat(ClaimWordingScreen.isForbidden("The audit trail was fully verified")).isTrue();
        assertThat(ClaimWordingScreen.isForbidden("This guarantees complete coverage")).isTrue();
        assertThat(ClaimWordingScreen.isForbidden("The evidence proves that svc-a is healthy"))
                .isTrue();
        assertThat(ClaimWordingScreen.isForbidden("This is proof of chain integrity")).isTrue();
        assertThat(ClaimWordingScreen.isForbidden("The chain verified against the events"))
                .isTrue();
    }

    @Test
    void authorizationAssertionsAreForbidden() {
        assertThat(ClaimWordingScreen.isForbidden("The proposal is approved to execute")).isTrue();
        assertThat(ClaimWordingScreen.isForbidden("The team is authorized to proceed")).isTrue();
        assertThat(ClaimWordingScreen.isForbidden("You have permission to run this")).isTrue();
        assertThat(ClaimWordingScreen.isForbidden("You should execute this change")).isTrue();
        assertThat(ClaimWordingScreen.isForbidden("The change must execute at noon")).isTrue();
    }

    @Test
    void executionAssertionsAreForbidden() {
        assertThat(ClaimWordingScreen.isForbidden("The change will execute automatically"))
                .isTrue();
        assertThat(ClaimWordingScreen.isForbidden("The migration was executed last night"))
                .isTrue();
    }

    @Test
    void caseVariationsAreForbidden() {
        assertThat(ClaimWordingScreen.isForbidden("THE CHANGE IS SAFE")).isTrue();
        assertThat(ClaimWordingScreen.isForbidden("It WILL BREAK svc-a")).isTrue();
        assertThat(ClaimWordingScreen.isForbidden("CAUSED BY the update")).isTrue();
    }

    @Test
    void allowedTechnicalStatementsAreNotForbidden() {
        assertThat(ClaimWordingScreen.isForbidden(
                        "svc-a has 2 impacted entities at depth 1 in the recorded analysis"))
                .isFalse();
        assertThat(ClaimWordingScreen.isForbidden(
                        "The simulation would record 14 impacted entities if tbl-1 were removed"))
                .isFalse();
        assertThat(ClaimWordingScreen.isForbidden(
                        "Policy evaluation returned ALLOW with advisory wording"))
                .isFalse();
        assertThat(ClaimWordingScreen.isForbidden(
                        "The audit list contains 100 events in sequence order"))
                .isFalse();
        assertThat(ClaimWordingScreen.isForbidden(
                        "Runtime health was unavailable at the last observation"))
                .isFalse();
        assertThat(ClaimWordingScreen.isForbidden(
                        "The traversal records steps in FORWARD and REVERSE directions"))
                .isFalse();
    }

    @Test
    void boundaryAndEmptyInputsBehaveDeterministically() {
        assertThat(ClaimWordingScreen.isForbidden(null)).isFalse();
        assertThat(ClaimWordingScreen.isForbidden("")).isFalse();
        assertThat(ClaimWordingScreen.isForbidden("   ")).isFalse();
        assertThat(ClaimWordingScreen.isForbidden("safe")).isFalse();
        assertThat(ClaimWordingScreen.isForbidden("safetrades are physical guards")).isFalse();
        assertThat(ClaimWordingScreen.isForbidden("x".repeat(1000))).isFalse();
    }

    @Test
    void screenHasNoSemanticClaim() {
        assertThat(ClaimWordingScreen.isForbidden(
                        "The recorded evidence shows svc-a reached at depth 2"))
                .isFalse();
    }
}
