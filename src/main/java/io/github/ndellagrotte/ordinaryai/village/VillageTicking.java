/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.village;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.Village;
import net.minecraft.village.VillageDoorInfo;
import net.minecraft.world.World;

/**
 * Support for the village tick changes: villages are frozen while their area is not loaded, houses can be kept for
 * good, and gaining or losing a house moves everyone's reputation.
 */
public final class VillageTicking {

    /** How often, in village ticks, each periodic job runs. */
    public static final int LOAD_CHECK_INTERVAL = 40;
    public static final int HOUSE_CHECK_INTERVAL = 50;
    public static final int DOOR_REFRESH_INTERVAL = 810;

    /** Players this far beyond the village edge are introduced to the village before house reputation is applied. */
    private static final double HOUSE_REPUTATION_MARGIN = 32.0;

    /** Chunks checked around the centre; 2 covers the minimum village radius of 32 blocks. */
    private static final int LOAD_CHECK_CHUNKS = 2;

    private VillageTicking() { }

    /** Returns true if the chunks around the village centre are loaded. */
    public static boolean isLoaded(World world, BlockPos center) {
        for (int x = -LOAD_CHECK_CHUNKS; x <= LOAD_CHECK_CHUNKS; x++) {
            for (int z = -LOAD_CHECK_CHUNKS; z <= LOAD_CHECK_CHUNKS; z++) {
                if (Math.abs(x) + Math.abs(z) <= LOAD_CHECK_CHUNKS
                        && !world.isBlockLoaded(center.add(x << 4, 0, z << 4))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Marks every door as recently used, so the village does not drop houses nobody has walked through lately. */
    public static void refreshDoors(Village village, int tickCounter) {
        for (VillageDoorInfo door : village.getVillageDoorInfoList()) {
            door.setLastActivityTimestamp(tickCounter);
        }
    }

    /** Changes the reputation of every player the village knows, first making sure nearby players are known. */
    public static void changeEveryonesReputation(World world, Village village, int change) {
        double maxDistance = village.getVillageRadius() + HOUSE_REPUTATION_MARGIN;
        BlockPos center = village.getCenter();
        for (EntityPlayer player : world.playerEntities) {
            if (player.getDistanceSq(center) <= maxDistance * maxDistance) {
                village.modifyPlayerReputation(player.getUniqueID(), 0);
            }
        }
        village.setDefaultPlayerReputation(change);
    }

}
