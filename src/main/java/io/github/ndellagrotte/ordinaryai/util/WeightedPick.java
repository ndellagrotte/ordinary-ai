/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.util;

/** Weighted random selection over a small array of integer weights. */
public final class WeightedPick {

    private WeightedPick() { }

    /** Returns the sum of all weights, treating negative weights as zero. */
    public static int total(int[] weights) {
        int total = 0;
        for (int weight : weights) {
            total += Math.max(0, weight);
        }
        return total;
    }

    /**
     * Picks an index for a roll.
     *
     * @param weights the weights; negative values count as zero
     * @param roll    a value from 0 (inclusive) to {@link #total(int[])} (exclusive)
     * @return the chosen index, or -1 if the roll is out of range
     */
    public static int pick(int[] weights, int roll) {
        if (roll < 0) {
            return -1;
        }
        int remaining = roll;
        for (int index = weights.length; index-- > 0;) {
            remaining -= Math.max(0, weights[index]);
            if (remaining < 0) {
                return index;
            }
        }
        return -1;
    }

}
