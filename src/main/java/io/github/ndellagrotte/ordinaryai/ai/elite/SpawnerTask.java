/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.elite;

import io.github.ndellagrotte.ordinaryai.ai.TaskUtil;
import io.github.ndellagrotte.ordinaryai.config.ConfigSnapshot;

import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.MobSpawnerBaseLogic;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

/**
 * Turns the mob into a walking monster spawner that produces reinforcements while it can see its target. By default
 * it spawns its own kind; a {@code spawnerTags} compound in the elite tag replaces the spawner settings wholesale.
 */
final class SpawnerTask extends EliteTask {

    private static final String SETTINGS_KEY = "spawnerTags";

    private final WalkingSpawner spawner;

    SpawnerTask(EntityLiving mob, NBTTagCompound eliteTag) {
        super(mob, TaskUtil.MUTEX_NONE);
        spawner = new WalkingSpawner(mob);
        if (eliteTag.hasKey(SETTINGS_KEY, Constants.NBT.TAG_COMPOUND)) {
            spawner.readFromNBT(eliteTag.getCompoundTag(SETTINGS_KEY));
        } else {
            spawner.readFromNBT(defaultSettings());
            ResourceLocation ownType = EntityList.getKey(mob);
            if (ownType != null) {
                spawner.setEntityId(ownType);
            }
        }
    }

    static void equip(EntityLiving mob, ConfigSnapshot.Elite config) {
        mob.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Blocks.MOB_SPAWNER));
        mob.setDropChance(EntityEquipmentSlot.HEAD, 0.0F);
        modify(mob, SharedMonsterAttributes.MOVEMENT_SPEED, "Spawner speed boost", config.spawnerSpeedBoost(),
                MULTIPLY_BASE);
        modify(mob, SharedMonsterAttributes.MAX_HEALTH, "Spawner health boost", config.spawnerHealthBoost(), ADD);
    }

    private static NBTTagCompound defaultSettings() {
        NBTTagCompound settings = new NBTTagCompound();
        settings.setShort("Delay", (short) 20);
        settings.setShort("MinSpawnDelay", (short) 150);
        settings.setShort("MaxSpawnDelay", (short) 600);
        settings.setShort("SpawnCount", (short) 4);
        settings.setShort("MaxNearbyEntities", (short) 7);
        settings.setShort("RequiredPlayerRange", (short) 16);
        settings.setShort("SpawnRange", (short) 4);
        return settings;
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase target = mob.getAttackTarget();
        return target != null && mob.getEntitySenses().canSee(target);
    }

    @Override
    public boolean shouldContinueExecuting() {
        return shouldExecute();
    }

    @Override
    public void updateTask() {
        spawner.updateSpawner();
    }

    /** Vanilla spawner logic, positioned at the mob's head. */
    private static final class WalkingSpawner extends MobSpawnerBaseLogic {

        private static final int EVENT_TIMER_RESET = 1;

        private final EntityLiving host;

        private int range;

        WalkingSpawner(EntityLiving host) {
            this.host = host;
        }

        @Override
        public World getSpawnerWorld() {
            return host.world;
        }

        @Override
        public BlockPos getSpawnerPosition() {
            return new BlockPos(host.posX, host.posY + host.getEyeHeight(), host.posZ);
        }

        @Override
        public void broadcastEvent(int id) {
            if (id != EVENT_TIMER_RESET) {
                return;
            }
            // The timer resets right after a spawn, so anything that has not ticked yet is one of ours: send it in
            EntityLivingBase target = host.getAttackTarget();
            for (EntityLiving spawned : host.world.getEntitiesWithinAABB(EntityLiving.class,
                    host.getEntityBoundingBox().grow(range + 4.0, 2.0, range + 4.0),
                    candidate -> candidate != host && candidate.ticksExisted == 0)) {
                spawned.setAttackTarget(target);
                spawned.setRevengeTarget(target);
            }
        }

        @Override
        public void readFromNBT(NBTTagCompound settings) {
            super.readFromNBT(settings);
            range = settings.getShort("SpawnRange");
        }

    }

}
