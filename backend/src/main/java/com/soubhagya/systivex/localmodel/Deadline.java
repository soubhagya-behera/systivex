package com.soubhagya.systivex.localmodel;

import java.time.Duration;
import java.util.Objects;

/**
 * A monotonic operation deadline.
 *
 * <p>The budget is measured as the difference between the clock reading taken
 * when the deadline was created and the current reading. A monotonic clock has
 * an arbitrary origin and its absolute reading may sit close to
 * {@code Long.MAX_VALUE}, so an absolute instant computed as
 * {@code start + duration} can overflow and turn a fresh deadline into an
 * already-expired one. Differences stay valid across such an origin and across
 * a wrap of the underlying counter.
 */
public final class Deadline {

    private final MonotonicClock clock;
    private final long startNanos;
    private final long durationNanos;

    private Deadline(MonotonicClock clock, long startNanos, long durationNanos) {
        this.clock = clock;
        this.startNanos = startNanos;
        this.durationNanos = durationNanos;
    }

    public static Deadline after(Duration duration, MonotonicClock clock) {
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(clock, "clock");
        return new Deadline(clock, clock.nanoTime(), duration.toNanos());
    }

    public boolean isExpired() {
        return elapsedNanos() >= durationNanos;
    }

    public Duration remaining() {
        long remainingNanos = durationNanos - elapsedNanos();
        return remainingNanos <= 0 ? Duration.ZERO : Duration.ofNanos(remainingNanos);
    }

    public void throwIfExpired() {
        if (isExpired()) {
            throw new OperationDeadlineExceededException(
                    "Local model operation exceeded the overall deadline");
        }
    }

    private long elapsedNanos() {
        return clock.nanoTime() - startNanos;
    }
}
