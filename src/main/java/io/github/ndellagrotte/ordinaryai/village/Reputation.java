/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.village;

import java.util.function.DoubleSupplier;

/** Village reputation rules, kept free of game classes so they can be tested directly. */
public final class Reputation {

    /** Lowest reputation a village tracks. */
    public static final int MIN = -30;
    /** Highest reputation a village tracks. */
    public static final int MAX = 10;
    /** Villages treat players at or below this reputation as enemies. */
    public static final int HATED = -15;

    private Reputation() { }

    /** Classifies a reputation value using the configured limits. */
    public static Standing standing(int reputation, int aggressionLimit, int repLossLimit) {
        if (reputation <= HATED) {
            return Standing.HATED;
        }
        if (reputation <= aggressionLimit) {
            return Standing.DISLIKED;
        }
        if (reputation <= repLossLimit) {
            return Standing.NEUTRAL;
        }
        return Standing.TRUSTED;
    }

    /**
     * Decides the consequences of breaking a block in a village. Both decisions use the reputation from before the
     * block was broken, and a roll is only drawn when its outcome matters.
     *
     * @param reputation       the player's reputation before the break
     * @param repLossLimit     reputation at or below which reputation can be lost
     * @param repLossChance    chance to lose reputation, already chosen for treasured or ordinary blocks
     * @param aggressionLimit  reputation at or below which the player can be marked as an aggressor
     * @param aggressionChance chance to be marked, already chosen for treasured or ordinary blocks
     * @param roll             source of rolls from 0 (inclusive) to 1 (exclusive)
     */
    public static BreakOutcome onBlockBroken(int reputation, int repLossLimit, double repLossChance,
            int aggressionLimit, double aggressionChance, DoubleSupplier roll) {
        boolean loseReputation = reputation <= repLossLimit && roll.getAsDouble() < repLossChance;
        boolean becomeAggressor;
        if (reputation <= HATED) {
            becomeAggressor = true;
        } else {
            becomeAggressor = reputation <= aggressionLimit && roll.getAsDouble() < aggressionChance;
        }
        return new BreakOutcome(loseReputation, becomeAggressor);
    }

    /**
     * Decides whether a defender keeps attacking a player. Hated players are always pursued; others are let go more
     * readily the closer they are to the aggression limit.
     *
     * @param roll a value from 0 (inclusive) to 1 (exclusive)
     */
    public static boolean keepsAttacking(int reputation, int aggressionLimit, float roll) {
        if (reputation > aggressionLimit) {
            return false;
        }
        return reputation <= HATED || roll >= (reputation - aggressionLimit + 10.0F) / 150.0F;
    }

    /** How a village regards a player. */
    public enum Standing {

        HATED,
        DISLIKED,
        NEUTRAL,
        TRUSTED

    }

    /**
     * What happens when a player breaks a block the village cares about.
     *
     * @param loseReputation  the player loses one reputation
     * @param becomeAggressor the village starts treating the player as an attacker
     */
    public record BreakOutcome(boolean loseReputation, boolean becomeAggressor) { }

}
