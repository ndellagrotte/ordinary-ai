/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.config;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBrewingStand;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockEnchantmentTable;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockHugeMushroom;
import net.minecraft.block.BlockIce;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockMelon;
import net.minecraft.block.BlockMycelium;
import net.minecraft.block.BlockPackedIce;
import net.minecraft.block.BlockPumpkin;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.BlockRedstoneDiode;
import net.minecraft.block.BlockSand;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.BlockStone;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockWorkbench;
import net.minecraft.block.IGrowable;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.IShearable;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Default block lists derived from the block registry, so that blocks added by other mods are picked up by type.
 * Must be built after all blocks are registered.
 */
record DefaultBlockLists(String[] doors,
    String[] fiddleTargets,
    String[] griefTargets,
    String[] griefLootable,
    String[] villageBlacklist
) {

    static DefaultBlockLists scanRegistry() {
        return new DefaultBlockLists(collect(DefaultBlockLists::isDoor), collect(DefaultBlockLists::isFiddleTarget),
            collect(DefaultBlockLists::isGriefTarget), collect(block -> block instanceof BlockChest),
            collect(DefaultBlockLists::isIgnoredByVillagers)
        );
    }

    private static String[] collect(Predicate<Block> filter) {
        List<String> ids = new ArrayList<>();
        for (Block block : ForgeRegistries.BLOCKS) {
            ResourceLocation id = block.getRegistryName();
            if (id != null && block != Blocks.AIR && filter.test(block)) {
                ids.add(id.toString());
            }
        }
        return ids.toArray(new String[0]);
    }

    private static boolean isDoor(Block block) {
        return block instanceof BlockDoor || block instanceof BlockFenceGate || block instanceof BlockTrapDoor;
    }

    private static boolean isFiddleTarget(Block block) {
        if (block instanceof BlockLever || block instanceof BlockButton || block instanceof BlockRedstoneDiode) {
            return true;
        }
        if (block instanceof BlockDoor || block instanceof BlockFenceGate) {
            return block.getDefaultState().getMaterial() != Material.IRON;
        }
        return block == Blocks.TNT || block == Blocks.CAKE;
    }

    private static boolean isGriefTarget(Block block) {
        return block instanceof BlockBed ||
            block instanceof BlockWorkbench ||
            block instanceof BlockFurnace ||
            block instanceof BlockEnchantmentTable ||
            block instanceof BlockBrewingStand ||
            block instanceof BlockLadder ||
            block == Blocks.FARMLAND ||
            block == Blocks.GOLDEN_RAIL;
    }

    private static boolean isIgnoredByVillagers(Block block) {
        // Natural blocks
        if (block instanceof BlockGrass ||
            block instanceof BlockMycelium ||
            block instanceof BlockStone ||
            block instanceof BlockDirt ||
            block instanceof BlockSand ||
            block instanceof BlockPumpkin ||
            block instanceof BlockMelon ||
            block instanceof BlockHugeMushroom ||
            block instanceof BlockSnow ||
            block instanceof BlockIce ||
            block instanceof BlockPackedIce) {
            return true;
        }
        // Plants
        if (block instanceof IGrowable || block instanceof IPlantable || block instanceof IShearable) {
            return true;
        }
        // Utility blocks, and doors (reputation for houses is handled separately)
        if (block instanceof BlockRailBase ||
            block instanceof BlockShulkerBox ||
            block instanceof BlockDoor ||
            block instanceof BlockFenceGate) {
            return true;
        }
        return isTrivial(block);
    }

    /** Instant-break blocks that are not meaningful light sources. */
    @SuppressWarnings("deprecation")
    private static boolean isTrivial(Block block) {
        try {
            return block.getDefaultState().getBlockHardness(null, BlockPos.ORIGIN) == 0.0F &&
                block.getDefaultState().getLightValue() < 4;
        } catch (RuntimeException _) {
            // Some blocks need a real world to answer; leave those off the list
            return false;
        }
    }

}
