/*
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeightedPickTest {

    @Test
    void totalIgnoresNegativeWeights() {
        assertThat(WeightedPick.total(new int[] { 1, 2, 3 })).isEqualTo(6);
        assertThat(WeightedPick.total(new int[] { 1, -5, 3 })).isEqualTo(4);
        assertThat(WeightedPick.total(new int[0])).isZero();
    }

    @Test
    void everyRollMapsToAnIndexInProportionToItsWeight() {
        int[] weights = { 2, 0, 3, 1 };
        int[] hits = new int[weights.length];
        for (int roll = 0; roll < WeightedPick.total(weights); roll++) {
            hits[WeightedPick.pick(weights, roll)]++;
        }
        assertThat(hits).containsExactly(2, 0, 3, 1);
    }

    @Test
    void lowRollsGoToTheLastEntries() {
        // Special AI walked its list backwards; keeping that means a given roll picks the same pattern as before
        int[] weights = { 1, 1, 1 };
        assertThat(WeightedPick.pick(weights, 0)).isEqualTo(2);
        assertThat(WeightedPick.pick(weights, 1)).isEqualTo(1);
        assertThat(WeightedPick.pick(weights, 2)).isZero();
    }

    @Test
    void outOfRangeRollsPickNothing() {
        int[] weights = { 1, 1 };
        assertThat(WeightedPick.pick(weights, 2)).isEqualTo(-1);
        assertThat(WeightedPick.pick(weights, -1)).isEqualTo(-1);
        assertThat(WeightedPick.pick(new int[0], 0)).isEqualTo(-1);
    }

    @Test
    void zeroWeightEntriesAreNeverPicked() {
        int[] weights = { 0, 4, 0 };
        for (int roll = 0; roll < 4; roll++) {
            assertThat(WeightedPick.pick(weights, roll)).isEqualTo(1);
        }
    }

}
