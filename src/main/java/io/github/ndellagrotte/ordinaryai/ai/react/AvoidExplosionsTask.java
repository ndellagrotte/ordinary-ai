/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.react;

import io.github.ndellagrotte.ordinaryai.ai.OrdinaryTask;
import io.github.ndellagrotte.ordinaryai.ai.TaskUtil;
import org.jspecify.annotations.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Makes a mob run from primed TNT, fireballs and creepers that are about to explode. */
public final class AvoidExplosionsTask extends EntityAIBase implements OrdinaryTask {

    private static final double SPEED_SLOW = 1.1;
    private static final double SPEED_FAST = 1.3;
    private static final int RETRY_DELAY = 10;

    private final EntityCreature mob;

    private @Nullable Entity threat;
    private @Nullable Path escapePath;
    private long retryAt;

    public AvoidExplosionsTask(EntityCreature mob) {
        this.mob = mob;
        setMutexBits(TaskUtil.MUTEX_MOVEMENT | TaskUtil.MUTEX_FACING);
    }

    @Override
    public boolean shouldExecute() {
        ThreatTracker tracker = ThreatTracker.of(mob.world);
        if (tracker.isEmpty()) {
            return false;
        }
        long now = TaskUtil.now(mob);
        if (now < retryAt) {
            return false;
        }
        Entity found = tracker.findWithin(mob.getEntityBoundingBox().grow(9.0, 3.0, 9.0), mob);
        if (found == null) {
            return false;
        }

        // Finding a way out is the expensive part, so do not repeat a failed attempt straight away
        retryAt = now + RETRY_DELAY;
        Vec3d away = RandomPositionGenerator.findRandomTargetBlockAwayFrom(mob, 16, 7,
                new Vec3d(found.posX, found.posY, found.posZ));
        if (away == null || found.getDistanceSq(away.x, away.y, away.z) < found.getDistanceSq(mob)) {
            return false;
        }
        escapePath = mob.getNavigator().getPathToPos(new BlockPos(away));
        if (escapePath == null) {
            return false;
        }
        threat = found;
        return true;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return threat != null && !threat.isDead && !mob.getNavigator().noPath();
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }

    @Override
    public void startExecuting() {
        mob.getNavigator().setPath(escapePath, SPEED_SLOW);
    }

    @Override
    public void updateTask() {
        if (threat != null) {
            mob.getNavigator().setSpeed(mob.getDistanceSq(threat) < 64.0 ? SPEED_FAST : SPEED_SLOW);
        }
    }

    @Override
    public void resetTask() {
        threat = null;
        escapePath = null;
    }

}
