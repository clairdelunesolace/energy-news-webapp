package com.carya.energynews.watchlistdiscovery;

final class DiscoveryRequestBudget {

    private int remaining;

    DiscoveryRequestBudget(int maximumRequests) {
        if (maximumRequests < 0) {
            throw new IllegalArgumentException("Discovery request budget must not be negative");
        }
        remaining = maximumRequests;
    }

    boolean tryAcquire() {
        if (remaining == 0) {
            return false;
        }
        remaining--;
        return true;
    }

    int remaining() {
        return remaining;
    }
}
