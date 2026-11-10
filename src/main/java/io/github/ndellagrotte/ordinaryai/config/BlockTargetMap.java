/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.config;

import com.google.common.base.Optional;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** An immutable set of block states, looked up by block and then by state conditions. */
public final class BlockTargetMap {

    private static final StateMatcher[] ANY_STATE = new StateMatcher[0];

    /** A map that matches nothing. */
    public static final BlockTargetMap EMPTY = new BlockTargetMap(new Reference2ObjectOpenHashMap<>());

    private final Reference2ObjectOpenHashMap<Block, StateMatcher[]> targets;

    private BlockTargetMap(Reference2ObjectOpenHashMap<Block, StateMatcher[]> targets) {
        this.targets = targets;
    }

    /** Parses a single comma-separated line, as used by per-mob NBT overrides. */
    public static BlockTargetMap parseLine(String line, Consumer<String> warn) {
        return parse(BlockTargetSyntax.splitLine(line, warn), warn);
    }

    /** Parses a list of targets. Must not be called before blocks are registered. */
    public static BlockTargetMap parse(Iterable<String> lines, Consumer<String> warn) {
        Reference2ObjectOpenHashMap<Block, List<StateMatcher>> building = new Reference2ObjectOpenHashMap<>();
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            BlockTargetSyntax.Target target = BlockTargetSyntax.parse(line, warn);
            if (target.wholeNamespace()) {
                addNamespace(building, target.id());
                continue;
            }
            Block block = findBlock(target.id(), warn);
            if (block == null || block == Blocks.AIR) {
                warn.accept("Missing or invalid block! (" + target.id() + ")");
                continue;
            }
            merge(building, block, StateMatcher.of(block, target.conditions(), warn), line, warn);
        }

        Reference2ObjectOpenHashMap<Block, StateMatcher[]> built = new Reference2ObjectOpenHashMap<>(building.size());
        for (Map.Entry<Block, List<StateMatcher>> entry : building.entrySet()) {
            List<StateMatcher> matchers = entry.getValue();
            built.put(entry.getKey(), matchers.isEmpty() ? ANY_STATE : matchers.toArray(new StateMatcher[0]));
        }
        return built.isEmpty() ? EMPTY : new BlockTargetMap(built);
    }

    private static @Nullable
    Block findBlock(String id, Consumer<String> warn) {
        ResourceLocation name = new ResourceLocation(id);
        if (ForgeRegistries.BLOCKS.containsKey(name)) {
            return ForgeRegistries.BLOCKS.getValue(name);
        }
        // Very old configs named blocks by number
        try {
            Block block = Block.getBlockById(Integer.parseInt(id));
            if (block != Blocks.AIR) {
                warn.accept("Usage of numerical block id! (" + id + ")");
            }
            return block;
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static void addNamespace(Reference2ObjectOpenHashMap<Block, List<StateMatcher>> building, String prefix) {
        for (Block block : ForgeRegistries.BLOCKS) {
            ResourceLocation id = block.getRegistryName();
            if (block != Blocks.AIR && id != null && id.toString().startsWith(prefix)) {
                // A namespace always means every state
                building.put(block, new ArrayList<>(0));
            }
        }
    }

    private static void merge(Reference2ObjectOpenHashMap<Block, List<StateMatcher>> building, Block block,
            StateMatcher matcher, String line, Consumer<String> warn) {
        List<StateMatcher> current = building.get(block);
        if (current == null) {
            current = new ArrayList<>(1);
            if (!matcher.isEmpty()) {
                current.add(matcher);
            }
            building.put(block, current);
        } else if (current.isEmpty()) {
            if (!matcher.isEmpty()) {
                warn.accept("Ignoring redundant block state '" + line.trim() + "'; the whole block is already listed");
            }
        } else if (matcher.isEmpty()) {
            current.clear();
        } else {
            current.add(matcher);
        }
    }

    /** Returns true if the map matches nothing. */
    public boolean isEmpty() {
        return targets.isEmpty();
    }

    /** Returns true if the state is one of the targets. */
    public boolean matches(IBlockState state) {
        StateMatcher[] matchers = targets.get(state.getBlock());
        if (matchers == null) {
            return false;
        }
        if (matchers.length == 0) {
            return true;
        }
        for (StateMatcher matcher : matchers) {
            if (matcher.matches(state)) {
                return true;
            }
        }
        return false;
    }

    private record StateMatcher(IProperty<?>[] properties, Comparable<?>[] values) {

        static StateMatcher of(Block block, List<BlockTargetSyntax.Condition> conditions, Consumer<String> warn) {
            List<IProperty<?>> properties = new ArrayList<>(conditions.size());
            List<Comparable<?>> values = new ArrayList<>(conditions.size());
            for (BlockTargetSyntax.Condition condition : conditions) {
                IProperty<?> property = block.getBlockState().getProperty(condition.property());
                if (property == null) {
                    warn.accept("Invalid block property key '" + condition.property() + "' for block '"
                                    + block.getRegistryName() + "'");
                    continue;
                }
                Optional<? extends Comparable<?>> value = property.parseValue(condition.value());
                if (value == null || !value.isPresent()) {
                    warn.accept("Invalid block property value '" + condition.value() + "' for property key '"
                                    + condition.property() + "' and block '" + block.getRegistryName() + "'");
                    continue;
                }
                properties.add(property);
                values.add(value.get());
            }
            return new StateMatcher(properties.toArray(new IProperty<?>[0]), values.toArray(new Comparable<?>[0]));
        }

        boolean isEmpty() {
            return properties.length == 0;
        }

        boolean matches(IBlockState state) {
            for (int i = 0; i < properties.length; i++) {
                if (!state.getValue(properties[i]).equals(values[i])) {
                    return false;
                }
            }
            return true;
        }

    }

}
