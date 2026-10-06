package com.soubhagya.systivex.payment;

import io.micrometer.tracing.handler.TracingObservationHandler;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationHandlerGroup;
import org.springframework.stereotype.Component;

/**
 * Places {@link ObservationErrorSanitizer} ahead of Boot's tracing handlers.
 *
 * <p>Why a group is needed: Boot registers tracing/meter handlers through
 * its tracing handler group ahead of ungrouped observation-handler beans,
 * so a plain early-ordered handler still runs AFTER the tracing handler has
 * already recorded the raw exception event on the span. Belonging to a group
 * ordered before the tracing group makes the sanitizer's {@code onError}
 * replace the observation error first; the tracing handler then records only
 * the sanitized error.
 *
 * <p>Why not a tracing handler: group members dispatch first-match, so joining
 * the tracing group itself would suppress the real tracing handler and stop
 * tracing. This group holds only the sanitizer, so tracing proceeds unchanged.
 */
@Component
public class SanitizerObservationHandlerGroup implements ObservationHandlerGroup {

    @Override
    public Class<?> handlerType() {
        return ObservationErrorSanitizer.class;
    }

    @Override
    public int compareTo(ObservationHandlerGroup other) {
        if (other == this) {
            return 0;
        }
        if (TracingObservationHandler.class.isAssignableFrom(other.handlerType())) {
            return -1;
        }
        return 0;
    }
}
