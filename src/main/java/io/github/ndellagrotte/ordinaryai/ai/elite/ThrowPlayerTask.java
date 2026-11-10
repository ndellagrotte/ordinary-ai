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
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.List;

/** Grabs its target, carries it off, and throws it into the thickest group of allies it can find. */
final class ThrowPlayerTask extends EliteTask {

    private static final double SPEED_TO_VICTIM = 1.2;
    private static final double SPEED_TO_ALLIES = 1.3;
    private static final int GIVE_UP_AFTER = 400;
    private static final int CARRY_TIME = 20;

    private Phase phase = Phase.IDLE;
    private @Nullable EntityLiving destination;
    private long readyAt;
    private long throwAt;
    private long giveUpAt;
    private int extraGrabAttempts;

    ThrowPlayerTask(EntityLiving mob, NBTTagCompound eliteTag) {
        super(mob, TaskUtil.MUTEX_MOVEMENT | TaskUtil.MUTEX_FACING);
    }

    static void equip(EntityLiving mob, ConfigSnapshot.Elite config) {
        ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
        if (config.throwPlayerHelmetDamageBoost() > 0.0) {
            helmet.setStackDisplayName("Helmet of Strength");
            modifyItem(helmet, SharedMonsterAttributes.ATTACK_DAMAGE, config.throwPlayerHelmetDamageBoost(), ADD);
        }
        Items.LEATHER_HELMET.setColor(helmet, 0xFF0000);
        mob.setItemStackToSlot(EntityEquipmentSlot.HEAD, helmet);
        modify(mob, SharedMonsterAttributes.KNOCKBACK_RESISTANCE, "Playerthrower knockback resistance",
            config.throwPlayerKnockbackResistance(), ADD
        );
        modify(mob, SharedMonsterAttributes.MAX_HEALTH, "Playerthrower health boost", config.throwPlayerHealthBoost(),
            ADD
        );
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase target = mob.getAttackTarget();
        if (now() < readyAt || target == null || !target.onGround || mob.isRiding() || mob.getRNG().nextInt(20) != 0) {
            return false;
        }
        destination = findDestination(target);
        return destination != null;
    }

    @Override
    public void startExecuting() {
        phase = Phase.GRABBING;
        giveUpAt = now() + GIVE_UP_AFTER;
        extraGrabAttempts = mob.getRNG().nextInt(4);
        EntityLivingBase target = mob.getAttackTarget();
        if (target != null) {
            mob.getNavigator().tryMoveToEntityLiving(target, SPEED_TO_VICTIM);
        }
    }

    @Override
    public boolean shouldContinueExecuting() {
        return phase != Phase.IDLE &&
            mob.getAttackTarget() != null &&
            !mob.isRiding() &&
            destination != null &&
            destination.isEntityAlive();
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }

    @Override
    public void updateTask() {
        EntityLiving allies = destination;
        if (allies == null) {
            return;
        }
        long now = now();
        if (phase == Phase.GRABBING) {
            grab(allies, now);
        } else if (phase == Phase.CARRYING && mob.isBeingRidden()) {
            carry(allies, now);
        } else if (extraGrabAttempts > 0) {
            // The victim wriggled free; go after it again
            extraGrabAttempts--;
            phase = Phase.GRABBING;
        } else {
            phase = Phase.IDLE;
        }
        if (now > giveUpAt) {
            phase = Phase.IDLE;
        }
    }

    private void grab(EntityLiving allies, long now) {
        EntityLivingBase target = mob.getAttackTarget();
        if (target == null) {
            return;
        }
        mob.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
        if (TaskUtil.isInContact(mob, target)) {
            target.startRiding(mob, true);
            mob.getNavigator().tryMoveToEntityLiving(allies, SPEED_TO_ALLIES);
            TaskUtil.swingArms(mob);
            throwAt = now + CARRY_TIME;
            phase = Phase.CARRYING;
        } else if (mob.getNavigator().noPath()) {
            mob.getNavigator().tryMoveToEntityLiving(target, SPEED_TO_VICTIM);
        }
    }

    private void carry(EntityLiving allies, long now) {
        mob.getLookHelper().setLookPositionWithEntity(allies, 30.0F, 30.0F);
        if (now >= throwAt && mob.getRNG().nextInt(10) == 0 && mob.getDistanceSq(allies) <= 100.0) {
            double dx = allies.posX - mob.posX;
            double dz = allies.posZ - mob.posZ;
            double distance = Math.max(Math.sqrt(dx * dx + dz * dz), 1.0E-4);
            Entity victim = mob.getPassengers().get(0);
            victim.dismountRidingEntity();
            launch(victim, dx / distance + mob.motionX * 0.2, 0.4, dz / distance + mob.motionZ * 0.2);
            TaskUtil.swingArms(mob);
            phase = Phase.IDLE;
        } else if (mob.getNavigator().noPath()) {
            mob.getNavigator().tryMoveToEntityLiving(allies, SPEED_TO_ALLIES);
        }
    }

    @Override
    public void resetTask() {
        List<Entity> passengers = mob.getPassengers();
        if (!passengers.isEmpty()) {
            passengers.get(0).dismountRidingEntity();
        }
        mob.getNavigator().clearPath();
        phase = Phase.IDLE;
        destination = null;
        readyAt = now() + (80L + mob.getRNG().nextInt(41)) * TaskUtil.EVALUATION_INTERVAL;
    }

    /**
     * Returns the ally fighting the same target that has the most company around it, or null if any such ally is
     * already on top of the target (in which case there is nowhere better to throw it).
     */
    private @Nullable
    EntityLiving findDestination(EntityLivingBase target) {
        List<Entity> nearby = mob.world.getEntitiesWithinAABBExcludingEntity(mob,
            target.getEntityBoundingBox().grow(16.0, 8.0, 16.0)
        );
        EntityLiving best = null;
        int bestCrowd = -1;
        for (Entity entity : nearby) {
            if (!(entity instanceof EntityLiving candidate) || candidate.getAttackTarget() != target) {
                continue;
            }
            if (candidate.getDistanceSq(target) <= 9.0) {
                return null;
            }
            // Count company from the entities already fetched rather than searching the world again per candidate
            AxisAlignedBB surroundings = candidate.getEntityBoundingBox().grow(4.0, 1.0, 4.0);
            int crowd = 0;
            for (Entity other : nearby) {
                if (other != candidate && surroundings.intersects(other.getEntityBoundingBox())) {
                    crowd++;
                }
            }
            if (crowd > bestCrowd) {
                bestCrowd = crowd;
                best = candidate;
            }
        }
        return best;
    }

    private enum Phase {

        IDLE,
        GRABBING,
        CARRYING

    }

}
