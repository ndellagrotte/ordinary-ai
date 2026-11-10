/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.elite;

import io.github.ndellagrotte.ordinaryai.ai.TaskUtil;
import io.github.ndellagrotte.ordinaryai.config.ConfigSnapshot;
import org.jspecify.annotations.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;

/** Picks up an ally that is lagging behind, carries it towards the target and throws it the rest of the way. */
final class ThrowAllyTask extends EliteTask {

    private static final double SPEED_TO_ALLY = 1.3;
    private static final double SPEED_TO_TARGET = 1.1;
    private static final int GIVE_UP_AFTER = 400;
    private static final int CARRY_TIME = 20;

    private @Nullable EntityLiving ally;
    private long readyAt;
    private long throwAt;
    private long giveUpAt;

    ThrowAllyTask(EntityLiving mob, NBTTagCompound eliteTag) {
        super(mob, TaskUtil.MUTEX_MOVEMENT | TaskUtil.MUTEX_FACING);
    }

    static void equip(EntityLiving mob, ConfigSnapshot.Elite config) {
        modify(mob, SharedMonsterAttributes.MOVEMENT_SPEED, "Thrower speed boost", config.throwSpeedBoost(),
            MULTIPLY_BASE
        );
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase target = mob.getAttackTarget();
        if (now() < readyAt || target == null || mob.isRiding()) {
            return false;
        }
        if (mob.isBeingRidden()) {
            return true;
        }
        if (mob.getRNG().nextInt(20) != 0) {
            return false;
        }
        ally = findAllyToThrow(target);
        return ally != null;
    }

    @Override
    public void startExecuting() {
        giveUpAt = now() + GIVE_UP_AFTER;
        if (ally != null) {
            mob.getNavigator().tryMoveToEntityLiving(ally, SPEED_TO_ALLY);
        } else {
            EntityLivingBase target = mob.getAttackTarget();
            if (target != null) {
                mob.getNavigator().tryMoveToEntityLiving(target, SPEED_TO_TARGET);
            }
        }
    }

    @Override
    public boolean shouldContinueExecuting() {
        return mob.getAttackTarget() != null &&
            !mob.isRiding() &&
            (ally != null && ally.isEntityAlive() || mob.isBeingRidden());
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }

    @Override
    public void updateTask() {
        EntityLivingBase target = mob.getAttackTarget();
        if (target == null) {
            return;
        }
        long now = now();

        EntityLiving wanted = ally;
        if (wanted != null) {
            mob.getLookHelper().setLookPositionWithEntity(wanted, 30.0F, 30.0F);
            if (TaskUtil.isInContact(mob, wanted)) {
                wanted.startRiding(mob, true);
                ally = null;
                mob.getNavigator().tryMoveToEntityLiving(target, SPEED_TO_TARGET);
                TaskUtil.swingArms(mob);
                throwAt = now + CARRY_TIME;
            } else if (mob.getNavigator().noPath()) {
                mob.getNavigator().tryMoveToEntityLiving(wanted, SPEED_TO_ALLY);
            }
        }

        if (mob.isBeingRidden()) {
            mob.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
            if (now >= throwAt && mob.getRNG().nextInt(10) == 0 && mob.getDistanceSq(target) <= 100.0) {
                throwPassengerAt(target);
                readyAt = now + (40L + mob.getRNG().nextInt(41)) * TaskUtil.EVALUATION_INTERVAL;
            } else if (mob.getNavigator().noPath()) {
                mob.getNavigator().tryMoveToEntityLiving(target, SPEED_TO_TARGET);
            }
        }

        if (now > giveUpAt) {
            mob.getNavigator().clearPath();
            ally = null;
        }
    }

    private void throwPassengerAt(EntityLivingBase target) {
        List<Entity> passengers = mob.getPassengers();
        if (passengers.isEmpty()) {
            return;
        }
        double dx = target.posX - mob.posX;
        double dz = target.posZ - mob.posZ;
        double distance = Math.max(Math.sqrt(dx * dx + dz * dz), 1.0E-4);
        Entity passenger = passengers.get(0);
        passenger.dismountRidingEntity();
        launch(passenger, dx / distance + mob.motionX * 0.2, 0.4, dz / distance + mob.motionZ * 0.2);
        mob.getNavigator().clearPath();
        TaskUtil.swingArms(mob);
    }

    @Override
    public void resetTask() {
        List<Entity> passengers = mob.getPassengers();
        if (!passengers.isEmpty()) {
            passengers.get(0).dismountRidingEntity();
        }
        mob.getNavigator().clearPath();
        ally = null;
    }

    /**
     * Returns the nearest grounded ally that shares the target, is still some way from it, and is closer to this mob
     * than the target is.
     */
    private @Nullable
    EntityLiving findAllyToThrow(EntityLivingBase target) {
        double best = mob.getDistanceSq(target);
        if (best < 9.0) {
            return null;
        }
        List<EntityLiving> candidates = mob.world.getEntitiesWithinAABB(EntityLiving.class,
            mob.getEntityBoundingBox().grow(16.0, 8.0, 16.0),
            candidate -> candidate != mob &&
                candidate.isEntityAlive() &&
                candidate.onGround &&
                !candidate.isRiding() &&
                candidate.getAttackTarget() == target &&
                candidate.getDistanceSq(target) > 36.0
        );
        EntityLiving nearest = null;
        for (EntityLiving candidate : candidates) {
            double distanceSq = mob.getDistanceSq(candidate);
            if (distanceSq < best) {
                best = distanceSq;
                nearest = candidate;
            }
        }
        return nearest;
    }

}
