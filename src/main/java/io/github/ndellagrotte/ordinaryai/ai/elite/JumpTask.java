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
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Makes a huge jump towards a target at medium range, landing without fall damage. */
final class JumpTask extends EliteTask {

    JumpTask(EntityLiving mob, NBTTagCompound eliteTag) {
        super(mob, TaskUtil.MUTEX_MOVEMENT | TaskUtil.MUTEX_SWIMMING);
    }

    static void equip(EntityLiving mob, ConfigSnapshot.Elite config) {
        ItemStack boots = dyedLeather(Items.LEATHER_BOOTS, "Feather Boots", 0x9664B4);
        boots.addEnchantment(Enchantments.FEATHER_FALLING, Enchantments.FEATHER_FALLING.getMaxLevel());
        mob.setItemStackToSlot(EntityEquipmentSlot.FEET, boots);
    }

    @Override
    public boolean shouldExecute() {
        if (!mob.onGround || mob.isRiding() || mob.getRNG().nextInt(10) != 0) {
            return false;
        }
        EntityLivingBase target = mob.getAttackTarget();
        if (target == null) {
            return false;
        }
        double distanceSq = mob.getDistanceSq(target);
        return distanceSq <= 144.0 && distanceSq >= 36.0;
    }

    @Override
    public void startExecuting() {
        EntityLivingBase target = mob.getAttackTarget();
        if (target == null) {
            return;
        }
        double dx = target.posX - mob.posX;
        double dz = target.posZ - mob.posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0E-4) {
            return;
        }
        mob.motionX = dx / distance * 1.4 + mob.motionX * 0.2;
        mob.motionZ = dz / distance * 1.4 + mob.motionZ * 0.2;
        mob.motionY = 1.0;
    }

    @Override
    public void updateTask() {
        mob.fallDistance = 0.0F;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return !mob.onGround && !mob.isInWater() && !mob.isInLava();
    }

}
