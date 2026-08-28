package com.carya.energynews.watchlistdiscovery;

import java.util.Objects;

public final class FixedDelayDiscoveryRequestPacer implements DiscoveryRequestPacer {

    private final long delayMillis;
    private final Sleeper sleeper;
    private boolean firstRequest = true;

    public FixedDelayDiscoveryRequestPacer(long delayMillis) {
        this(delayMillis, Thread::sleep);
    }

    FixedDelayDiscoveryRequestPacer(long delayMillis, Sleeper sleeper) {
        if (delayMillis < 0) {
            throw new IllegalArgumentException("Discovery request delay must not be negative");
        }
        this.delayMillis = delayMillis;
        this.sleeper = Objects.requireNonNull(sleeper, "Discovery request sleeper is required");
    }

    @Override
    public synchronized void awaitNextRequest() {
        if (firstRequest) {
            firstRequest = false;
            return;
        }
        if (delayMillis == 0) {
            return;
        }

        try {
            sleeper.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Discovery request pacing was interrupted", exception);
        }
    }

    @FunctionalInterface
    interface Sleeper {

        void sleep(long millis) throws InterruptedException;
    }
}
