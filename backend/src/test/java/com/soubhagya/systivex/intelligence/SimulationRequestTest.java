package com.soubhagya.systivex.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The D39 operation-count boundary lives on the request itself, so an
 * oversized request fails at construction/deserialization — before any
 * controller, entity resolution, or simulation work can start.
 */
class SimulationRequestTest {

    private static final List<String> FROM = List.of("svc:a");

    @Test
    void nullOperationsAreAcceptedAsTheNullProposal() {
        SimulationRequest request = request(null);

        assertThat(request.operations()).isNull();
    }

    @Test
    void emptyOperationsAreAcceptedAsTheNullProposal() {
        SimulationRequest request = request(List.of());

        assertThat(request.operations()).isEmpty();
    }

    @Test
    void exactlyHundredOperationsAreAccepted() {
        List<SimulationOperation> operations = operations(SimulationLimits.MAX_OPERATIONS);

        SimulationRequest request = request(operations);

        assertThat(request.operations()).hasSize(100).containsExactlyElementsOf(operations);
    }

    @Test
    void hundredAndOneOperationsAreRejected() {
        List<SimulationOperation> operations = operations(SimulationLimits.MAX_OPERATIONS + 1);

        assertThatThrownBy(() -> request(operations))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At most 100 simulation operations are supported");
    }

    @Test
    void rejectionMessageIsFixedAndDoesNotEchoOperations() {
        List<SimulationOperation> operations = new ArrayList<>();
        for (int i = 0; i < SimulationLimits.MAX_OPERATIONS + 1; i++) {
            operations.add(
                    new SimulationOperation(
                            SimulationOperationType.REMOVE_ENTITY,
                            "secret-marker-" + i,
                            null,
                            null,
                            null));
        }

        assertThatThrownBy(() -> request(operations))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("secret-marker");
    }

    @Test
    void oversizedListIsNeverSilentlyTruncatedIntoAPartialRequest() {
        List<SimulationOperation> operations = operations(SimulationLimits.MAX_OPERATIONS + 1);
        int originalSize = operations.size();

        assertThatThrownBy(() -> request(operations))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(operations).hasSize(originalSize);
        assertThat(SimulationLimits.MAX_OPERATIONS).isEqualTo(100);
    }

    @Test
    void acceptedRequestKeepsEveryOperationInOrder() {
        List<SimulationOperation> operations = operations(SimulationLimits.MAX_OPERATIONS);

        assertThat(request(operations).operations())
                .containsExactlyElementsOf(operations)
                .doesNotContainNull();
    }

    private static SimulationRequest request(List<SimulationOperation> operations) {
        return new SimulationRequest(FROM, null, null, operations);
    }

    private static List<SimulationOperation> operations(int count) {
        List<SimulationOperation> operations = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            operations.add(
                    new SimulationOperation(
                            SimulationOperationType.REMOVE_ENTITY,
                            "svc:" + i,
                            null,
                            null,
                            null));
        }
        return operations;
    }
}
