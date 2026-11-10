/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.village;

import io.github.ndellagrotte.ordinaryai.ai.grief.BlockBreaking;
import io.github.ndellagrotte.ordinaryai.config.ConfigSnapshot;
import io.github.ndellagrotte.ordinaryai.config.ModConfig;
import org.jspecify.annotations.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.Village;
import net.minecraft.village.VillageCollection;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Ties player actions in and around villages to their reputation there. */
public final class ReputationEvents {

    private static final int BLOCK_BREAK_RADIUS = 8;
    private static final int MONSTER_KILL_RADIUS = 32;

    private static void onBlockBroken(World world, BlockPos pos, IBlockState state, @Nullable EntityPlayer player) {
        if (world.isRemote || player == null || !ModConfig.isLoaded()) {
            return;
        }
        Village village = nearestVillage(world, pos, BLOCK_BREAK_RADIUS);
        if (village == null) {
            return;
        }
        ConfigSnapshot.Villages config = ModConfig.get().villages();
        boolean treasured = config.blockTreasuredList().matches(state);
        if (!treasured) {
            boolean offWhitelist = !config.blockWhitelist().isEmpty() && !config.blockWhitelist().matches(state);
            if (offWhitelist || config.blockBlacklist().matches(state)) {
                return;
            }
        }

        Reputation.BreakOutcome outcome = Reputation.onBlockBroken(village.getPlayerReputation(player.getUniqueID()),
            config.blockRepLossLimit(), treasured ? config.blockTreasuredRepLossChance() : config.blockRepLossChance(),
            config.blockAggressionLimit(),
            treasured ? config.blockTreasuredAggressionChance() : config.blockAggressionChance(), world.rand::nextDouble
        );
        if (outcome.loseReputation()) {
            changeReputation(player, pos, village, -1);
        }
        if (outcome.becomeAggressor()) {
            village.addOrRenewAgressor(player);
            if (config.reputationParticles()) {
                world.playEvent(BlockBreaking.EVENT_SPAWNER_PARTICLES, pos, 0);
            }
            playSound(world, pos, SoundEvents.ENTITY_VILLAGER_HURT, config);
        }
    }

    private static @Nullable
    Village nearestVillage(World world, BlockPos pos, int radius) {
        // Not every world keeps villages
        VillageCollection villages = world.getVillageCollection();
        return villages == null ? null : villages.getNearestVillage(pos, radius);
    }

    private static void changeReputation(EntityPlayer player, BlockPos pos, Village village, int change) {
        village.modifyPlayerReputation(player.getUniqueID(), change);
        ConfigSnapshot.Villages config = ModConfig.get().villages();
        boolean gained = change > 0;
        if (config.reputationParticles() && player.world instanceof WorldServer world) {
            // Sent as a vanilla particle packet, so clients do not need this mod to see it
            world.spawnParticle(gained ? EnumParticleTypes.VILLAGER_HAPPY : EnumParticleTypes.VILLAGER_ANGRY,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, gained ? 10 : 5, 0.45, 0.45, 0.45, 0.02
            );
        }
        playSound(player.world, pos, gained ? SoundEvents.ENTITY_VILLAGER_YES : SoundEvents.ENTITY_VILLAGER_NO, config);
    }

    private static void playSound(World world, BlockPos pos, SoundEvent sound, ConfigSnapshot.Villages config) {
        if (config.reputationSounds()) {
            float pitch = (world.rand.nextFloat() - world.rand.nextFloat()) * 0.2F + 1.0F;
            world.playSound(null, pos, sound, SoundCategory.PLAYERS, 1.0F, pitch);
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote ||
            !ModConfig.isLoaded() ||
            !(event.getEntity() instanceof EntityVillager villager)) {
            return;
        }
        ConfigSnapshot.Villages config = ModConfig.get().villages();
        if (config.nameVillagers() && !villager.hasCustomName()) {
            VillagerNames.assign(villager, ModConfig.get().general().debugMode());
        }
        if (config.nameVillagersAlwaysShow() && villager.hasCustomName()) {
            villager.setAlwaysRenderNameTag(true);
        }
    }

    /** Rewards players for killing monsters near a village. */
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim.world.isRemote ||
            !ModConfig.isLoaded() ||
            !(event.getSource().getTrueSource() instanceof EntityPlayer player)) {
            return;
        }
        double chance = ModConfig.get().villages().helpRepChance();
        if (chance <= 0.0 ||
            !victim.isCreatureType(EnumCreatureType.MONSTER, false) ||
            victim.getRNG().nextDouble() >= chance) {
            return;
        }
        BlockPos pos = new BlockPos(victim);
        Village village = nearestVillage(victim.world, pos, MONSTER_KILL_RADIUS);
        if (village != null) {
            changeReputation(player, pos, village, 1);
        }
    }

    // Lowest priority so that a trample another mod prevented does not count
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof EntityPlayer player) {
            onBlockBroken(event.getWorld(), event.getPos(), event.getState(), player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        onBlockBroken(event.getWorld(), event.getPos(), event.getState(), event.getPlayer());
    }

}
