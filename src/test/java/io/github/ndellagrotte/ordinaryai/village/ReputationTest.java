/*
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.village;

import io.github.ndellagrotte.ordinaryai.village.Reputation.BreakOutcome;
import io.github.ndellagrotte.ordinaryai.village.Reputation.Standing;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.DoubleSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class ReputationTest {

    private static final int REP_LOSS_LIMIT = 8;
    private static final int AGGRESSION_LIMIT = -5;

    private static BreakOutcome breakBlock(int reputation, double repChance, double aggroChance, Rolls rolls) {
        return Reputation.onBlockBroken(reputation, REP_LOSS_LIMIT, repChance, AGGRESSION_LIMIT, aggroChance, rolls);
    }

    @Test
    void standingFollowsTheLimits() {
        assertThat(Reputation.standing(10, AGGRESSION_LIMIT, REP_LOSS_LIMIT)).isEqualTo(Standing.TRUSTED);
        assertThat(Reputation.standing(9, AGGRESSION_LIMIT, REP_LOSS_LIMIT)).isEqualTo(Standing.TRUSTED);
        assertThat(Reputation.standing(8, AGGRESSION_LIMIT, REP_LOSS_LIMIT)).isEqualTo(Standing.NEUTRAL);
        assertThat(Reputation.standing(0, AGGRESSION_LIMIT, REP_LOSS_LIMIT)).isEqualTo(Standing.NEUTRAL);
        assertThat(Reputation.standing(-5, AGGRESSION_LIMIT, REP_LOSS_LIMIT)).isEqualTo(Standing.DISLIKED);
        assertThat(Reputation.standing(-14, AGGRESSION_LIMIT, REP_LOSS_LIMIT)).isEqualTo(Standing.DISLIKED);
        assertThat(Reputation.standing(-15, AGGRESSION_LIMIT, REP_LOSS_LIMIT)).isEqualTo(Standing.HATED);
        assertThat(Reputation.standing(-30, AGGRESSION_LIMIT, REP_LOSS_LIMIT)).isEqualTo(Standing.HATED);
    }

    @Test
    void trustedPlayersBreakBlocksFreelyAndUseNoRolls() {
        Rolls rolls = new Rolls();
        assertThat(breakBlock(9, 1.0, 1.0, rolls)).isEqualTo(new BreakOutcome(false, false));
    }

    @Test
    void neutralPlayersCanOnlyLoseReputation() {
        Rolls passing = new Rolls(0.1);
        assertThat(breakBlock(0, 0.15, 1.0, passing)).isEqualTo(new BreakOutcome(true, false));
        assertThat(passing.allUsed()).isTrue();

        Rolls failing = new Rolls(0.15);
        assertThat(breakBlock(0, 0.15, 1.0, failing)).isEqualTo(new BreakOutcome(false, false));
    }

    @Test
    void dislikedPlayersRollForBothConsequencesIndependently() {
        assertThat(breakBlock(-5, 0.15, 0.1, new Rolls(0.5, 0.05))).isEqualTo(new BreakOutcome(false, true));
        assertThat(breakBlock(-5, 0.15, 0.1, new Rolls(0.05, 0.5))).isEqualTo(new BreakOutcome(true, false));
        assertThat(breakBlock(-14, 0.15, 0.1, new Rolls(0.0, 0.0))).isEqualTo(new BreakOutcome(true, true));
    }

    @Test
    void hatedPlayersAlwaysBecomeAggressorsWithoutASecondRoll() {
        Rolls rolls = new Rolls(0.99);
        assertThat(breakBlock(-15, 0.15, 0.0, rolls)).isEqualTo(new BreakOutcome(false, true));
        assertThat(rolls.allUsed()).isTrue();
    }

    @Test
    void zeroChancesNeverTrigger() {
        assertThat(breakBlock(-10, 0.0, 0.0, new Rolls(0.0, 0.0))).isEqualTo(new BreakOutcome(false, false));
    }

    @Test
    void defendersNeverAttackPlayersAboveTheLimit() {
        assertThat(Reputation.keepsAttacking(-4, AGGRESSION_LIMIT, 0.99F)).isFalse();
        assertThat(Reputation.keepsAttacking(10, AGGRESSION_LIMIT, 0.99F)).isFalse();
    }

    @Test
    void defendersAlwaysPursueHatedPlayers() {
        assertThat(Reputation.keepsAttacking(-15, AGGRESSION_LIMIT, 0.0F)).isTrue();
        assertThat(Reputation.keepsAttacking(-30, AGGRESSION_LIMIT, 0.0F)).isTrue();
    }

    @Test
    void defendersLetDislikedPlayersGoMoreReadilyNearTheLimit() {
        // At the limit the chance to give up each check is 10/150; nine below it, it is 1/150
        assertThat(Reputation.keepsAttacking(-5, AGGRESSION_LIMIT, 0.06F)).isFalse();
        assertThat(Reputation.keepsAttacking(-5, AGGRESSION_LIMIT, 0.07F)).isTrue();
        assertThat(Reputation.keepsAttacking(-14, AGGRESSION_LIMIT, 0.006F)).isFalse();
        assertThat(Reputation.keepsAttacking(-14, AGGRESSION_LIMIT, 0.007F)).isTrue();
    }

    /** Hands out fixed rolls and fails the test if more are asked for than expected. */
    private static final class Rolls implements DoubleSupplier {

        private final Deque<Double> remaining = new ArrayDeque<>();

        Rolls(Double... rolls) {
            remaining.addAll(List.of(rolls));
        }

        @Override
        public double getAsDouble() {
            assertThat(remaining).as("unused rolls").isNotEmpty();
            return remaining.removeFirst();
        }

        boolean allUsed() {
            return remaining.isEmpty();
        }

    }

}
