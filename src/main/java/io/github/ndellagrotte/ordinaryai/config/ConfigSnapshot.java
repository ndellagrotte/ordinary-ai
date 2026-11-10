/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.config;

/**
 * An immutable view of every config option. A new snapshot replaces the old one whenever the config is reloaded, so
 * code should read {@link ModConfig#get()} at the point of use rather than holding on to a snapshot.
 */
public record ConfigSnapshot(General general,
    DoorBreaking doorBreaking,
    IdleActivities idle,
    Fiddling fiddling,
    Griefing griefing,
    Reactions reactions,
    Jockeys jockeys,
    Elite elite,
    Villages villages
) {

    /** Category {@code _general}. */
    public record General(boolean debugMode,
        double depacifyAggressiveChance,
        MobList depacifyList,
        boolean eatBreedingItems,
        boolean eatingHeals
    ) { }

    /** Category {@code door_breaking}. */
    public record DoorBreaking(boolean enabled,
        float breakSpeed,
        boolean leaveDrops,
        boolean madCreepers,
        MobList mobList,
        boolean requiresTarget,
        boolean requiresTools,
        BlockTargetMap targetBlocks
    ) { }

    /** Category {@code idle_activities}. */
    public record IdleActivities(float reach,
        int range,
        int rangeVertical,
        int scanCount,
        int scanCountGlobal,
        int scanDelay
    ) { }

    /** Category {@code idle_fiddling}. */
    public record Fiddling(boolean enabled,
        MobList mobList,
        BlockTargetMap targetBlacklist,
        BlockTargetMap targetBlocks
    ) { }

    /** Category {@code idle_griefing}. */
    public record Griefing(boolean enabled,
        boolean breakLights,
        boolean breakSound,
        float breakSpeed,
        boolean leaveDrops,
        boolean madCreepers,
        MobList mobList,
        boolean requiresTools,
        BlockTargetMap targetBlacklist,
        BlockTargetMap targetBlocks,
        BlockTargetMap targetLootable
    ) { }

    /** Category {@code reaction_ai}. */
    public record Reactions(boolean avoidExplosions,
        boolean callForHelp,
        double callForHelpOnDeath,
        double dodgeArrows
    ) { }

    /** Category {@code jockeys}. */
    public record Jockeys(MobList mountList, MobList mountListSmall, MobList riderList, MobList riderListSmall) { }

    /**
     * Category {@code elite_ai}.
     *
     * @param weights selection weight per elite type, indexed by ordinal
     */
    public record Elite(MobList mobList,
        int[] weights,
        double barrageHealthBoost,
        double chargeHealthBoost,
        double chargeKnockbackResistance,
        double leapSpeedBoost,
        float shamanHealAmount,
        double shamanHealthBoost,
        double spawnerHealthBoost,
        double spawnerSpeedBoost,
        double sprintBootsSpeedBoost,
        float sprintSpeedBoost,
        float thiefAvoidRange,
        double thiefHelmetSpeedBoost,
        double throwSpeedBoost,
        double throwPlayerHealthBoost,
        double throwPlayerHelmetDamageBoost,
        double throwPlayerKnockbackResistance
    ) { }

    /** Category {@code villages}. */
    public record Villages(double blockAggressionChance,
        int blockAggressionLimit,
        BlockTargetMap blockBlacklist,
        double blockRepLossChance,
        int blockRepLossLimit,
        double blockTreasuredAggressionChance,
        BlockTargetMap blockTreasuredList,
        double blockTreasuredRepLossChance,
        BlockTargetMap blockWhitelist,
        boolean commandIncludeCenter,
        double helpRepChance,
        boolean houseRep,
        boolean nameVillagers,
        boolean nameVillagersAlwaysShow,
        boolean refreshHouses,
        boolean reputationParticles,
        boolean reputationSounds,
        boolean villagersDefend
    ) { }

}
