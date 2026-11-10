package com.soubhagya.systivex.localmodel;

import java.time.Duration;
import java.util.Objects;

public final class Deadline {

    private final MonotonicClock clock;
    private final long deadlineNanos;

    private Deadline(MonotonicClock clock, long deadlineNanos) {
        this.clock = clock;
        this.deadlineNanos = deadlineNanos;
    }

    public static Deadline after(Duration duration, MonotonicClock clock) {
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(clock, "clock");
        return new Deadline(clock, clock.nanoTime() + duration.toNanos());
    }

    public boolean isExpired() {
        return clock.nanoTime() >= deadlineNanos;
    }

    public Duration remaining() {
        long remainingNanos = deadlineNanos - clock.nanoTime();
        return remainingNanos <= 0 ? Duration.ZERO : Duration.ofNanos(remainingNanos);
    }

    public void throwIfExpired() {
        if (isExpired()) {
            throw new OperationDeadlineExceededException(
                    "Local model operation exceeded the overall deadline");
        }
    }
}
