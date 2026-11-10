/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.elite;

import io.github.ndellagrotte.ordinaryai.ai.TaskUtil;
import io.github.ndellagrotte.ordinaryai.config.ConfigSnapshot;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Sprints after a distant target to close the gap. */
final class SprintTask extends EliteTask {

    private static final String KEY = "sprint";
    private static final int GIVE_UP_AFTER = 400;

    private final double speed;

    private long giveUpAt;

    SprintTask(EntityLiving mob, NBTTagCompound eliteTag) {
        super(mob, TaskUtil.MUTEX_MOVEMENT | TaskUtil.MUTEX_FACING);
        speed = eliteTag.getFloat(KEY) + 1.0F;
    }

    static void writeDefault(String key, NBTTagCompound eliteTag, ConfigSnapshot.Elite config) {
        eliteTag.setFloat(key, config.sprintSpeedBoost());
    }

    static void equip(EntityLiving mob, ConfigSnapshot.Elite config) {
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        if (config.sprintBootsSpeedBoost() > 0.0) {
            boots.setStackDisplayName("Running Boots");
            modifyItem(boots, SharedMonsterAttributes.MOVEMENT_SPEED, config.sprintBootsSpeedBoost(), MULTIPLY_BASE);
        }
        Items.LEATHER_BOOTS.setColor(boots, 0xFF0000);
        mob.setItemStackToSlot(EntityEquipmentSlot.FEET, boots);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase target = mob.getAttackTarget();
        if (target == null || mob.isRiding() || !target.isSprinting() && mob.getRNG().nextInt(20) != 0) {
            return false;
        }
        return mob.getDistanceSq(target) > 144.0;
    }

    @Override
    public boolean shouldContinueExecuting() {
        EntityLivingBase target = mob.getAttackTarget();
        if (mob.getNavigator().noPath() || target == null || mob.isRiding()) {
            return false;
        }
        double distanceSq = mob.getDistanceSq(target);
        return distanceSq > 36.0 || distanceSq > 9.0 && mob.getRNG().nextInt(10) != 0;
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }

    @Override
    public void startExecuting() {
        mob.setSprinting(true);
        giveUpAt = now() + GIVE_UP_AFTER;
        EntityLivingBase target = mob.getAttackTarget();
        if (target != null && mob.getNavigator().noPath()) {
            mob.getNavigator().tryMoveToEntityLiving(target, speed);
        }
    }

    @Override
    public void updateTask() {
        EntityLivingBase target = mob.getAttackTarget();
        if (target == null) {
            return;
        }
        mob.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
        if (now() > giveUpAt) {
            mob.getNavigator().clearPath();
        } else if (mob.getNavigator().noPath()) {
            mob.getNavigator().tryMoveToEntityLiving(target, speed);
        }
    }

    @Override
    public void resetTask() {
        mob.setSprinting(false);
    }

}
