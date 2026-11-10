/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.grief;

/**
 * The server-wide allowance of block lookups that idle mobs may spend each tick, shared across all worlds. Only used
 * from the server thread.
 */
public final class ScanBudget {

    private static int remaining;

    private ScanBudget() { }

    /** Refills the allowance. Called once per server tick. */
    public static void refill(int limit) {
        remaining = limit;
    }

    /**
     * Takes one lookup from the allowance.
     *
     * @param limit the configured limit, where zero or less means unlimited
     * @return true if the lookup may go ahead
     */
    public static boolean tryTake(int limit) {
        return limit <= 0 || remaining-- > 0;
    }

}
