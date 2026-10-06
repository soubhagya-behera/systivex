package com.soubhagya.systivex.inventory;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/**
 * Keeps response bodies and business payloads out of exported telemetry.
 *
 * <p>Why this exists: the OTel bridge records {@code observation.error()} on
 * the span immediately ({@code Span.recordException}), copying the throwable
 * message into the {@code exception.message} span attribute. For downstream
 * HTTP failures that message is the raw response body, and for rejected
 * business operations it is the service-layer message — both can carry
 * customer, product, or amount data. A stop-time filter would run too late,
 * and {@code @Order} alone is not enough either: Boot registers its tracing
 * handlers through a tracing handler group ahead of ungrouped handler beans,
 * so this handler must belong to {@link SanitizerObservationHandlerGroup}
 * (ordered before the tracing group) for its {@code onError} to replace the
 * recorded error with a payload-free equivalent before the tracing handler
 * sees it.
 *
 * <p>What survives: HTTP status codes, the original exception type name, the
 * observation name/route, and trace correlation. The thrown exceptions
 * themselves — and therefore API responses and logs — are untouched.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ObservationErrorSanitizer implements ObservationHandler<Observation.Context> {

    @Override
    public void onError(Observation.Context context) {
        Throwable error = context.getError();
        if (error == null || error instanceof SanitizedObservationError) {
            return;
        }
        context.setError(new SanitizedObservationError(error));
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }

    /**
     * Payload-free replacement for a recorded observation error. Carries only
     * the original exception type name and, for downstream HTTP failures, the
     * status code. No original message, no response body, and no cause chain
     * (a cause would reintroduce the payload through the stacktrace
     * attribute).
     */
    static final class SanitizedObservationError extends RuntimeException {

        SanitizedObservationError(Throwable original) {
            super(describe(original));
        }

        private static String describe(Throwable original) {
            String type = original.getClass().getSimpleName();
            if (original instanceof RestClientResponseException http) {
                return type
                        + " [HTTP "
                        + http.getStatusCode().value()
                        + "] (response body withheld from telemetry)";
            }
            return type + " (detail withheld from telemetry)";
        }
    }
}
