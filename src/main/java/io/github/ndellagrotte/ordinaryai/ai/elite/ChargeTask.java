/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.elite;

import io.github.ndellagrotte.ordinaryai.ai.TaskUtil;
import io.github.ndellagrotte.ordinaryai.config.ConfigSnapshot;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;

/** Winds up, then dashes in a straight line at the target, bowling it over on contact or stunning itself on a wall. */
final class ChargeTask extends EliteTask {

    private static final String KEY = "charge";

    private final float knockback;
    private final float normalStepHeight;

    private Phase phase = Phase.IDLE;
    private int timer;
    private long readyAt;
    private double dirX;
    private double dirZ;

    ChargeTask(EntityLiving mob, NBTTagCompound eliteTag) {
        super(mob, TaskUtil.MUTEX_MOVEMENT | TaskUtil.MUTEX_FACING | TaskUtil.MUTEX_SWIMMING);
        knockback = eliteTag.getFloat(KEY);
        normalStepHeight = mob.stepHeight;
    }

    static void writeDefault(String key, NBTTagCompound eliteTag, ConfigSnapshot.Elite config) {
        eliteTag.setFloat(key, 0.5F);
    }

    static void equip(EntityLiving mob, ConfigSnapshot.Elite config) {
        ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
        EnchantmentHelper.addRandomEnchantment(mob.getRNG(), helmet, 30, true);
        helmet.setStackDisplayName("Charger's Helmet");
        Items.LEATHER_HELMET.setColor(helmet, 0xFFFF00);
        mob.setItemStackToSlot(EntityEquipmentSlot.HEAD, helmet);
        modify(mob, SharedMonsterAttributes.KNOCKBACK_RESISTANCE, "Charger knockback resistance",
            config.chargeKnockbackResistance(), ADD
        );
        modify(mob, SharedMonsterAttributes.MAX_HEALTH, "Charger health boost", config.chargeHealthBoost(), ADD);
    }

    @Override
    public boolean shouldExecute() {
        if (!mob.onGround || mob.isRiding() || now() < readyAt || mob.getRNG().nextInt(10) != 0) {
            return false;
        }
        EntityLivingBase target = mob.getAttackTarget();
        if (target == null) {
            return false;
        }
        double distanceSq = mob.getDistanceSq(target);
        return distanceSq <= 256.0 && distanceSq >= 25.0 && mob.getEntitySenses().canSee(target);
    }

    @Override
    public boolean shouldContinueExecuting() {
        return phase != Phase.IDLE;
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }

    @Override
    public void startExecuting() {
        mob.getNavigator().clearPath();
        timer = 30;
        phase = Phase.WIND_UP;
        mob.motionY = 0.3;
        TaskUtil.swingArms(mob);
    }

    @Override
    public void updateTask() {
        timer--;
        switch (phase) {
            case WIND_UP -> windUp();
            case CHARGING -> charge();
            case STUNNED -> {
                mob.swingArm(timer % 2 == 0 ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND);
                if (timer <= 0) {
                    end(0);
                }
            }
            default -> {
                // Not running
            }
        }
    }

    private void windUp() {
        EntityLivingBase target = mob.getAttackTarget();
        if (target == null || !target.isEntityAlive()) {
            end(Math.max(0, timer));
            return;
        }
        mob.getLookHelper().setLookPositionWithEntity(target, 100.0F, 100.0F);
        if (timer > 0) {
            return;
        }
        double dx = target.posX - mob.posX;
        double dz = target.posZ - mob.posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0E-4) {
            end(0);
            return;
        }
        dirX = dx / distance + mob.motionX * 0.2;
        dirZ = dz / distance + mob.motionZ * 0.2;
        if (mob.stepHeight < 1.0F) {
            mob.stepHeight = 1.0F;
        }
        mob.setSprinting(true);
        timer = 20;
        phase = Phase.CHARGING;
    }

    private void charge() {
        mob.getLookHelper()
            .setLookPosition(mob.posX + dirX, mob.posY + mob.getEyeHeight(), mob.posZ + dirZ, 100.0F, 100.0F);
        mob.motionX = dirX;
        mob.motionZ = dirZ;

        EntityLivingBase target = mob.getAttackTarget();
        if (target != null && mob.getEntityBoundingBox().grow(0.2).intersects(target.getEntityBoundingBox())) {
            mob.attackEntityAsMob(target);
            TaskUtil.swingArms(mob);
            mob.motionX *= -0.8;
            mob.motionY = 0.4;
            mob.motionZ *= -0.8;
            target.motionX += dirX * knockback;
            target.motionY = 0.4;
            target.motionZ += dirZ * knockback;
            target.velocityChanged = true;
            end(Math.max(0, timer));
        } else if (mob.collidedHorizontally) {
            mob.attackEntityFrom(DamageSource.FALLING_BLOCK, 5.0F);
            mob.motionX *= -0.5;
            mob.motionY = 0.4;
            mob.motionZ *= -0.5;
            mob.stepHeight = normalStepHeight;
            mob.setSprinting(false);
            timer = 80;
            phase = Phase.STUNNED;
        } else if (timer <= 0 || mob.isInWater() || mob.isInLava()) {
            end(40);
        }
    }

    /** Stops the task after this update and sets how long before the next charge may start. */
    private void end(int cooldownEvaluations) {
        phase = Phase.IDLE;
        readyAt = now() + (long) cooldownEvaluations * TaskUtil.EVALUATION_INTERVAL;
    }

    @Override
    public void resetTask() {
        mob.stepHeight = normalStepHeight;
        mob.setSprinting(false);
        phase = Phase.IDLE;
    }

    private enum Phase {

        IDLE,
        WIND_UP,
        CHARGING,
        STUNNED

    }

}
