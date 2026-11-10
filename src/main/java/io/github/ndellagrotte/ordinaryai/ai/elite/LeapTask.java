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
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;

/** Pounces at a target that is a few blocks away. */
final class LeapTask extends EliteTask {

    LeapTask(EntityLiving mob, NBTTagCompound eliteTag) {
        super(mob, TaskUtil.MUTEX_SWIMMING);
    }

    static void equip(EntityLiving mob, ConfigSnapshot.Elite config) {
        ItemStack held = mob.getHeldItemMainhand();
        if (!held.isEmpty() && held.getItem() instanceof ItemBow) {
            // A leaper needs to fight up close
            mob.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.GOLDEN_SWORD));
        }
        modify(mob, SharedMonsterAttributes.MOVEMENT_SPEED, "Leaper speed boost", config.leapSpeedBoost(),
                MULTIPLY_BASE);
    }

    @Override
    public boolean shouldExecute() {
        if (!mob.onGround || mob.isRiding() || mob.getRNG().nextInt(5) != 0) {
            return false;
        }
        EntityLivingBase target = mob.getAttackTarget();
        if (target == null) {
            return false;
        }
        double distanceSq = mob.getDistanceSq(target);
        return distanceSq <= 36.0 && distanceSq >= 4.0;
    }

    @Override
    public void startExecuting() {
        EntityLivingBase target = mob.getAttackTarget();
        if (target == null) {
            return;
        }
        mob.getLookHelper().setLookPositionWithEntity(target, 180.0F, 0.0F);
        double dx = target.posX - mob.posX;
        double dz = target.posZ - mob.posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0E-4) {
            return;
        }
        mob.motionX = dx / distance * 0.8 + mob.motionX * 0.2;
        mob.motionZ = dz / distance * 0.8 + mob.motionZ * 0.2;
        mob.motionY = 0.4;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return false;
    }

}
