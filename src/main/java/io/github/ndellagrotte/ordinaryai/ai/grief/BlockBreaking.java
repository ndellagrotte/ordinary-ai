/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.grief;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLiving;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

/** Rules for mobs damaging blocks, modelled on how players mine. */
public final class BlockBreaking {

    /** World event ids, played with {@code World.playEvent}. */
    public static final int EVENT_ATTACK_DOOR_WOOD = 1019;
    public static final int EVENT_ATTACK_DOOR_IRON = 1020;
    public static final int EVENT_BREAK_DOOR_WOOD = 1021;
    public static final int EVENT_SPAWNER_PARTICLES = 2004;
    public static final int EVENT_BONEMEAL_PARTICLES = 2005;

    /** Break progress value that removes the crack overlay. */
    public static final int NO_PROGRESS = -1;

    private BlockBreaking() { }

    /** Returns true if the mob is able and allowed to destroy the block. Other mods can veto through Forge events. */
    public static boolean mayDestroy(IBlockState state, EntityLiving mob, boolean needsTool, World world, BlockPos pos
    ) {
        return state.getBlockHardness(world, pos) >= 0.0F &&
            !state.getMaterial().isLiquid() &&
            (!needsTool || canHarvest(mob.getHeldItemMainhand(), state)) &&
            state.getBlock().canEntityDestroy(state, world, pos, mob) &&
            ForgeEventFactory.onEntityDestroyBlock(mob, pos, state);
    }

    /** Returns the fraction of the block the mob breaks in one tick, before any speed multiplier. */
    public static float damagePerTick(IBlockState state, EntityLiving mob, World world, BlockPos pos) {
        float hardness = state.getBlockHardness(world, pos);
        if (hardness < 0.0F) {
            return 0.0F;
        }
        if (!canHarvest(mob.getHeldItemMainhand(), state)) {
            return 1.0F / (hardness * 100.0F);
        }
        return strengthAgainst(mob, state) / (hardness * 30.0F);
    }

    /** Converts accumulated damage (0 to 1) to the crack stage shown to players. */
    public static int progressStage(float damage) {
        return (int) Math.ceil(damage * 10.0F) - 1;
    }

    private static boolean canHarvest(ItemStack tool, IBlockState state) {
        Material material = state.getMaterial();
        return !material.isLiquid() && (material.isToolNotRequired() || !tool.isEmpty() && tool.canHarvestBlock(state));
    }

    private static float strengthAgainst(EntityLiving mob, IBlockState state) {
        ItemStack held = mob.getHeldItemMainhand();
        float strength = held.isEmpty() ? 1.0F : held.getDestroySpeed(state);

        if (strength > 1.0F) {
            int efficiency = EnchantmentHelper.getEfficiencyModifier(mob);
            if (efficiency > 0) {
                strength += efficiency * efficiency + 1;
            }
        }

        PotionEffect haste = mob.getActivePotionEffect(MobEffects.HASTE);
        if (haste != null) {
            strength *= 1.0F + (haste.getAmplifier() + 1) * 0.2F;
        }
        PotionEffect fatigue = mob.getActivePotionEffect(MobEffects.MINING_FATIGUE);
        if (fatigue != null) {
            strength *= 1.0F - (fatigue.getAmplifier() + 1) * 0.2F;
        }

        if (mob.isInsideOfMaterial(Material.WATER) && !EnchantmentHelper.getAquaAffinityModifier(mob)) {
            strength /= 5.0F;
        }
        if (!mob.onGround) {
            strength /= 5.0F;
        }
        return Math.max(strength, 0.0F);
    }

}
