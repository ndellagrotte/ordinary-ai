/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.react;

import io.github.ndellagrotte.ordinaryai.ai.OrdinaryTask;
import io.github.ndellagrotte.ordinaryai.ai.TaskUtil;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityCreeper;

/**
 * Reports a creeper to the {@link ThreatTracker} while its fuse is burning. It never actually runs; being asked
 * whether it should is the cheap, regular check it needs.
 */
public final class FuseBeaconTask extends EntityAIBase implements OrdinaryTask {

    private final EntityCreeper creeper;

    public FuseBeaconTask(EntityCreeper creeper) {
        this.creeper = creeper;
        setMutexBits(TaskUtil.MUTEX_NONE);
    }

    @Override
    public boolean shouldExecute() {
        if (creeper.getCreeperState() > 0) {
            ThreatTracker.of(creeper.world).add(creeper);
        }
        return false;
    }

}
