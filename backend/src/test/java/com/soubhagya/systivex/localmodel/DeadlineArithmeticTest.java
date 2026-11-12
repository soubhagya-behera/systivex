package com.soubhagya.systivex.localmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for overflow-safe monotonic deadline arithmetic.
 *
 * <p>A monotonic clock has an arbitrary origin and its absolute reading may sit
 * close to {@code Long.MAX_VALUE}. Only differences between readings are
 * meaningful, so a deadline has to be measured as an elapsed difference rather
 * than as an absolute instant computed by addition. Computing
 * {@code start + duration} overflows for such an origin, which turns a fresh
 * deadline into an already-expired one.
 */
class DeadlineArithmeticTest {

    private static final Duration BUDGET = Duration.ofSeconds(35);

    /** Origins that are legal readings for a monotonic clock. */
    private static final long[] LEGAL_ORIGINS = {
        0L,
        -1L,
        Long.MIN_VALUE / 2,
        Long.MAX_VALUE / 2,
        Long.MAX_VALUE - 1_000L
    };

    private static final class OriginClock implements MonotonicClock {

        private final long origin;
        private long nanos;

        OriginClock(long origin) {
            this.origin = origin;
        }

        @Override
        public long nanoTime() {
            // A real monotonic clock also wraps; only differences are meaningful.
            return origin + nanos;
        }

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }
    }

    @Test
    void deadlineIsNotExpiredAtCreationForAnyLegalClockOrigin() {
        for (long origin : LEGAL_ORIGINS) {
            OriginClock clock = new OriginClock(origin);
            Deadline deadline = Deadline.after(BUDGET, clock);

            assertFalse(deadline.isExpired(), "clock origin " + origin);
            assertEquals(BUDGET, deadline.remaining(), "clock origin " + origin);
        }
    }

    @Test
    void deadlineExpiresExactlyWhenTheBudgetIsConsumedForAnyLegalClockOrigin() {
        for (long origin : LEGAL_ORIGINS) {
            OriginClock clock = new OriginClock(origin);
            Deadline deadline = Deadline.after(BUDGET, clock);

            clock.advance(BUDGET.minusMillis(1));
            assertFalse(deadline.isExpired(), "clock origin " + origin);
            assertEquals(Duration.ofMillis(1), deadline.remaining(), "clock origin " + origin);

            clock.advance(Duration.ofMillis(1));
            assertTrue(deadline.isExpired(), "clock origin " + origin);
            assertEquals(Duration.ZERO, deadline.remaining(), "clock origin " + origin);
            assertThrows(OperationDeadlineExceededException.class, deadline::throwIfExpired);
        }
    }

    @Test
    void deadlineInputStreamStillReadsWhenTheClockOriginIsNearLongMaxValue() throws IOException {
        OriginClock clock = new OriginClock(Long.MAX_VALUE - 1_000L);
        Deadline deadline = Deadline.after(BUDGET, clock);
        InputStream stream = new LocalModelTransport.DeadlineInputStream(
                new ByteArrayInputStream(new byte[10]), deadline);

        assertEquals(10, stream.readAllBytes().length);
    }

    @Test
    void deadlineInputStreamStillFailsAfterTheBudgetWhenTheClockOriginIsNearLongMaxValue()
            throws IOException {
        OriginClock clock = new OriginClock(Long.MAX_VALUE - 1_000L);
        Deadline deadline = Deadline.after(BUDGET, clock);
        InputStream stream = new LocalModelTransport.DeadlineInputStream(
                new ByteArrayInputStream(new byte[10]), deadline);

        clock.advance(BUDGET);

        assertThrows(OperationDeadlineExceededException.class, stream::read);
    }
}
