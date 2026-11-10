/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai;

import io.github.ndellagrotte.ordinaryai.ai.MobData;
import io.github.ndellagrotte.ordinaryai.ai.OrdinaryTask;
import io.github.ndellagrotte.ordinaryai.config.ConfigSnapshot;
import io.github.ndellagrotte.ordinaryai.config.ModConfig;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * A smoke test that runs inside a real server, covering what unit tests cannot reach because it needs game classes.
 * Started with {@code -Dordinaryai.selftest=true}; the server stops when it is done, and the process exits with a
 * failure code if any check failed.
 */
final class SelfTest {

    private final List<String> failures = new ArrayList<>();

    private SelfTest() { }

    static void runAndStop() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        SelfTest test = new SelfTest();
        try {
            test.checkConfig();
            test.checkMobSetUp(server.getWorld(0));
        } catch (RuntimeException ex) {
            OrdinaryAI.LOGGER.error("Self-test stopped by an error", ex);
            test.failures.add("unexpected " + ex);
        }

        if (test.failures.isEmpty()) {
            OrdinaryAI.LOGGER.info("SELF-TEST PASSED");
            server.initiateShutdown();
        } else {
            for (String failure : test.failures) {
                OrdinaryAI.LOGGER.error("SELF-TEST FAILED: {}", failure);
            }
            // The world is a throwaway; halt so the failure is visible in the exit code
            FMLCommonHandler.instance().exitJava(1, true);
        }
    }

    private static int countOurTasks(EntityLiving mob) {
        int count = 0;
        for (EntityAITasks.EntityAITaskEntry entry : mob.tasks.taskEntries) {
            if (entry.action instanceof OrdinaryTask) {
                count++;
            }
        }
        for (EntityAITasks.EntityAITaskEntry entry : mob.targetTasks.taskEntries) {
            if (entry.action instanceof OrdinaryTask) {
                count++;
            }
        }
        return count;
    }

    private void check(boolean condition, String description) {
        if (!condition) {
            failures.add(description);
        }
    }

    private void checkConfig() {
        check(ModConfig.isLoaded(), "config is loaded");
        ConfigSnapshot config = ModConfig.get();
        check(!config.doorBreaking().targetBlocks().isEmpty(), "default door list found blocks");
        check(!config.fiddling().targetBlocks().isEmpty(), "default fiddle list found blocks");
        check(!config.griefing().targetBlocks().isEmpty(), "default grief list found blocks");
        check(!config.griefing().targetLootable().isEmpty(), "default lootable list found blocks");
        check(!config.villages().blockBlacklist().isEmpty(), "default village blacklist found blocks");
        check(!config.villages().blockTreasuredList().isEmpty(), "treasured block list parsed");
        check(!config.jockeys().mountList().isEmpty(), "mount list parsed");
        check(config.elite().weights().length > 0, "elite weights read");
    }

    private void checkMobSetUp(WorldServer world) {
        BlockPos spawn = world.getSpawnPoint();

        // A zombie as Special AI would have saved it, asking for one elite pattern
        EntityZombie zombie = new EntityZombie(world);
        zombie.setPosition(spawn.getX() + 0.5, 200.0, spawn.getZ() + 0.5);
        NBTTagCompound elite = new NBTTagCompound();
        elite.setFloat("charge", 0.5F);
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setTag(MobData.ELITE_AI, elite);
        legacy.setBoolean(MobData.FORCE_INIT, true);
        legacy.setBoolean(MobData.GRIEFING, true);
        zombie.getEntityData().setTag(MobData.LEGACY_ROOT, legacy);
        zombie.forceSpawn = true;
        check(world.spawnEntity(zombie), "test zombie spawned");

        NBTTagCompound data = zombie.getEntityData();
        check(!data.hasKey(MobData.LEGACY_ROOT), "legacy data root was removed");
        check(data.hasKey(MobData.ROOT), "data root was created");
        check(data.getCompoundTag(MobData.ROOT).getBoolean(MobData.GRIEFING), "legacy values carried over");
        check(!data.getCompoundTag(MobData.ROOT).hasKey(MobData.FORCE_INIT), "one-time init flag was consumed");
        check(!zombie.getItemStackFromSlot(EntityEquipmentSlot.HEAD).isEmpty(), "elite gear was handed out");

        int tasks = countOurTasks(zombie);
        check(tasks >= 2, "tasks were added (found " + tasks + ")");
        MinecraftForge.EVENT_BUS.post(new EntityJoinWorldEvent(zombie, world));
        check(countOurTasks(zombie) == tasks, "a repeated join does not add tasks again");
        zombie.setDead();

        // The depacify roll must be remembered whichever way it went
        EntityCow cow = new EntityCow(world);
        cow.setPosition(spawn.getX() + 0.5, 200.0, spawn.getZ() + 0.5);
        cow.forceSpawn = true;
        check(world.spawnEntity(cow), "test cow spawned");
        check(MobData.hasNumber(MobData.of(cow), MobData.DEPACIFY), "depacify roll was saved");
        cow.setDead();
    }

}
