/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.elite;

import io.github.ndellagrotte.ordinaryai.ai.EndOfTickQueue;
import io.github.ndellagrotte.ordinaryai.ai.TaskUtil;
import io.github.ndellagrotte.ordinaryai.config.ConfigSnapshot;
import org.jspecify.annotations.Nullable;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

/**
 * Runs up to a player empty-handed, snatches a random stack from their inventory, then keeps its distance. The loot
 * is carried in the mob's main hand and always drops when it is killed.
 */
final class ThiefTask extends EliteTask {

    private static final String KEY = "thief";
    private static final double SPEED = 1.2;

    private final @Nullable EntityAIAvoidEntity<EntityPlayer> avoidPlayers;

    private boolean fleeing;

    ThiefTask(EntityLiving mob, NBTTagCompound eliteTag) {
        super(mob, TaskUtil.MUTEX_MOVEMENT | TaskUtil.MUTEX_FACING);
        avoidPlayers = mob instanceof EntityCreature creature
                ? new EntityAIAvoidEntity<>(creature, EntityPlayer.class, eliteTag.getFloat(KEY), 1.0, SPEED)
                : null;
    }

    static void writeDefault(String key, NBTTagCompound eliteTag, ConfigSnapshot.Elite config) {
        eliteTag.setFloat(key, config.thiefAvoidRange());
    }

    static void equip(EntityLiving mob, ConfigSnapshot.Elite config) {
        mob.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, ItemStack.EMPTY);
        ItemStack helmet = dyedLeather(Items.LEATHER_HELMET, "Thief's Cap", 0x102024);
        modifyItem(helmet, SharedMonsterAttributes.MOVEMENT_SPEED, config.thiefHelmetSpeedBoost(), MULTIPLY_BASE);
        mob.setItemStackToSlot(EntityEquipmentSlot.HEAD, helmet);
        modify(mob, SharedMonsterAttributes.MOVEMENT_SPEED, "Thief speed boost", 0.2, MULTIPLY_BASE);
        mob.setCanPickUpLoot(false);
    }

    private static boolean hasItems(EntityPlayer player) {
        InventoryPlayer inventory = player.inventory;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase target = mob.getAttackTarget();
        if (target instanceof EntityPlayer player && isEmptyHanded()) {
            fleeing = false;
            return hasItems(player);
        }
        // Having got what it came for, it only turns to fight a target that is already badly hurt
        boolean playerNearby = avoidPlayers != null
                && (target == null || target.getHealth() > target.getMaxHealth() / 3.0F)
                && avoidPlayers.shouldExecute();
        // With nothing in hand and something else to attack, it goes for that instead of running
        fleeing = playerNearby && (target == null || !isEmptyHanded());
        return playerNearby;
    }

    @Override
    public void startExecuting() {
        if (fleeing && avoidPlayers != null) {
            avoidPlayers.startExecuting();
            return;
        }
        EntityLivingBase target = mob.getAttackTarget();
        if (target != null) {
            mob.getNavigator().tryMoveToEntityLiving(target, SPEED);
        }
    }

    @Override
    public boolean shouldContinueExecuting() {
        return !mob.getNavigator().noPath();
    }

    @Override
    public void updateTask() {
        if (fleeing) {
            if (avoidPlayers != null) {
                avoidPlayers.updateTask();
            }
            return;
        }
        EntityLivingBase target = mob.getAttackTarget();
        if (target == null || !isEmptyHanded()) {
            mob.getNavigator().clearPath();
            return;
        }
        mob.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
        if (TaskUtil.isInContact(mob, target)) {
            target.attackEntityFrom(DamageSource.causeMobDamage(mob), 1.0F);
            if (target instanceof EntityPlayer player) {
                steal(player);
            }
            mob.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 60, 0));
            mob.getNavigator().clearPath();
        } else if (mob.getNavigator().noPath()) {
            mob.getNavigator().tryMoveToEntityLiving(target, SPEED);
        }
    }

    @Override
    public void resetTask() {
        if (fleeing && avoidPlayers != null) {
            avoidPlayers.resetTask();
        }
        fleeing = false;
    }

    private boolean isEmptyHanded() {
        return mob.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND).isEmpty();
    }

    private void pocket(ItemStack loot) {
        if (!mob.isEntityAlive() || !isEmptyHanded()) {
            mob.entityDropItem(loot, 0.5F);
            return;
        }
        mob.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, loot);
        // A drop chance above 1 guarantees the drop and leaves the item undamaged
        mob.setDropChance(EntityEquipmentSlot.MAINHAND, 2.0F);
        mob.enablePersistence();
    }

    /** Takes a random occupied inventory slot from the player, to be carried in the mob's main hand. */
    private void steal(EntityPlayer player) {
        InventoryPlayer inventory = player.inventory;
        int occupied = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                occupied++;
            }
        }
        if (occupied == 0) {
            return;
        }
        int choice = mob.getRNG().nextInt(occupied);
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && --choice < 0) {
                inventory.setInventorySlotContents(slot, ItemStack.EMPTY);
                // Equipping can make a mob rebuild its tasks, which must not happen while they are being run
                EndOfTickQueue.defer(() -> pocket(stack));
                return;
            }
        }
    }

}
