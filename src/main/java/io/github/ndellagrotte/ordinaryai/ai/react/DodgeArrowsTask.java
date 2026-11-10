/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.react;

import io.github.ndellagrotte.ordinaryai.ai.OrdinaryTask;
import io.github.ndellagrotte.ordinaryai.ai.TaskUtil;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.List;

/** Lets a mob sidestep an arrow that was just fired towards it. */
public final class DodgeArrowsTask extends EntityAIBase implements OrdinaryTask {

    private static final double HORIZONTAL_RANGE = 24.0;
    private static final int VERTICAL_RANGE = 16;
    private static final int REACTION_WINDOW = 10 * TaskUtil.EVALUATION_INTERVAL;
    private static final int COOLDOWN = 40 * TaskUtil.EVALUATION_INTERVAL;

    private final EntityCreature mob;
    private final float chance;

    private boolean pending;
    private double arrowDirX;
    private double arrowDirZ;
    private long pendingUntil;
    private long readyAt;

    public DodgeArrowsTask(EntityCreature mob, float chance) {
        this.mob = mob;
        this.chance = chance;
        setMutexBits(TaskUtil.MUTEX_SWIMMING);
    }

    /** Offers a newly fired arrow to every mob standing in its path. */
    public static void onArrowFired(Entity arrow) {
        double speed = DodgeMath.horizontalSpeed(arrow.motionX, arrow.motionZ);
        if (speed <= 0.0 || Double.isNaN(speed)) {
            return;
        }
        double ux = arrow.motionX / speed;
        double uz = arrow.motionZ / speed;
        double halfWidth = arrow.width + 0.3F;

        AxisAlignedBB reach = new AxisAlignedBB(arrow.posX - HORIZONTAL_RANGE, arrow.posY - VERTICAL_RANGE - 1.0,
            arrow.posZ - HORIZONTAL_RANGE, arrow.posX + HORIZONTAL_RANGE, arrow.posY + VERTICAL_RANGE + 1.0,
            arrow.posZ + HORIZONTAL_RANGE
        );
        List<EntityCreature> nearby = arrow.world.getEntitiesWithinAABB(EntityCreature.class, reach);
        for (EntityCreature creature : nearby) {
            if (Math.abs((int) creature.posY - (int) arrow.posY) > VERTICAL_RANGE) {
                continue;
            }
            double dx = creature.posX - arrow.posX;
            double dz = creature.posZ - arrow.posZ;
            if (DodgeMath.isInPath(dx, dz, ux, uz, halfWidth, HORIZONTAL_RANGE)) {
                DodgeArrowsTask task = TaskUtil.findTask(creature, DodgeArrowsTask.class);
                if (task != null) {
                    task.offer(ux, uz);
                }
            }
        }
    }

    private void offer(double ux, double uz) {
        long now = TaskUtil.now(mob);
        if (now >= readyAt && mob.getRNG().nextFloat() < chance) {
            pending = true;
            arrowDirX = ux;
            arrowDirZ = uz;
            pendingUntil = now + REACTION_WINDOW;
        }
    }

    @Override
    public boolean shouldExecute() {
        if (!pending) {
            return false;
        }
        long now = TaskUtil.now(mob);
        if (now > pendingUntil) {
            pending = false;
            return false;
        }
        return now >= readyAt && mob.onGround && !mob.isRiding();
    }

    @Override
    public void startExecuting() {
        double scale = mob.getRNG().nextBoolean() ? 0.8 : -0.8;
        // Sideways is the arrow's direction turned a quarter circle
        mob.motionX = arrowDirZ * scale;
        mob.motionZ = -arrowDirX * scale;
        mob.motionY = 0.4;
        pending = false;
        readyAt = TaskUtil.now(mob) + COOLDOWN;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return false;
    }

}
