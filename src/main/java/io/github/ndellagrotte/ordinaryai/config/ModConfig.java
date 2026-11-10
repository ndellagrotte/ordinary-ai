/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.config;

import io.github.ndellagrotte.ordinaryai.OrdinaryAI;
import io.github.ndellagrotte.ordinaryai.ai.elite.EliteType;
import io.github.ndellagrotte.ordinaryai.village.Reputation;
import org.jspecify.annotations.Nullable;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Owns the config file and the current {@link ConfigSnapshot}.
 *
 * <p>The file is located during pre-init but only read once loading has completed, because the default block lists
 * are derived from the block registry and must see blocks from every mod.
 */
public final class ModConfig {

    private static final double INF = Double.POSITIVE_INFINITY;
    private static final float INF_F = Float.POSITIVE_INFINITY;
    private static final int MAX = Integer.MAX_VALUE;

    private static volatile @Nullable ConfigSnapshot snapshot;
    private static @Nullable Configuration file;
    private static @Nullable DefaultBlockLists defaults;

    private ModConfig() { }

    /** Returns the current options. Fails if called before loading has completed. */
    public static ConfigSnapshot get() {
        ConfigSnapshot current = snapshot;
        if (current == null) {
            throw new IllegalStateException("Config is read before it has been loaded");
        }
        return current;
    }

    /** Returns true once the first snapshot exists. */
    public static boolean isLoaded() {
        return snapshot != null;
    }

    /** Returns the backing file, for the config GUI. */
    public static synchronized @Nullable
    Configuration file() {
        return file;
    }

    /** Chooses the config file, importing a Special AI config if this mod has none yet. Does not parse anything. */
    public static synchronized void locate(File configDir) {
        File target = new File(configDir, OrdinaryAI.MOD_ID + ".cfg");
        try {
            if (LegacyConfigImporter.importIfAbsent(configDir.toPath(), target.toPath())) {
                OrdinaryAI.LOGGER.info("Imported settings from {} into {}", LegacyConfigImporter.LEGACY_FILE_NAME,
                        target.getName());
            }
        } catch (IOException ex) {
            OrdinaryAI.LOGGER.warn("Could not import {}", LegacyConfigImporter.LEGACY_FILE_NAME, ex);
        }
        file = new Configuration(target);
    }

    /** Reads the file for the first time. Call once every mod has registered its blocks and entities. */
    public static synchronized void load() {
        Configuration config = requireFile();
        long start = System.nanoTime();
        config.load();
        defaults = DefaultBlockLists.scanRegistry();
        rebuild();
        OrdinaryAI.LOGGER.info("Loaded config in {} ms", (System.nanoTime() - start) / 1.0E6);
    }

    /** Re-reads the file from disk and replaces the current options. */
    public static synchronized void reloadFromDisk() {
        requireFile().load();
        rebuild();
    }

    /** Rebuilds the options from the in-memory file, then writes back any defaults or corrections. */
    public static synchronized void rebuild() {
        Configuration config = requireFile();
        DefaultBlockLists blockDefaults = defaults;
        if (blockDefaults == null) {
            throw new IllegalStateException("Config is rebuilt before it has been loaded");
        }
        Consumer<String> warn = OrdinaryAI.LOGGER::warn;
        ConfigReader in = new ConfigReader(config, warn);
        snapshot = new ConfigSnapshot(general(in), doorBreaking(in, blockDefaults), idle(in),
                fiddling(in, blockDefaults), griefing(in, blockDefaults), reactions(in), jockeys(in), elite(in),
                villages(in, blockDefaults));
        if (config.hasChanged()) {
            config.save();
        }
    }

    private static Configuration requireFile() {
        Configuration config = file;
        if (config == null) {
            throw new IllegalStateException("Config file has not been located yet");
        }
        return config;
    }

    private static ConfigSnapshot.General general(ConfigReader in) {
        in.category("_general", "General and/or miscellaneous options.");
        return new ConfigSnapshot.General(in.bool("_debug_mode", false,
                        "If true, the mod will start up in debug mode."),
                in.real("depacify_aggressive_chance", 0.0,
                        "Chance for an entity on the depacify list to spawn aggressive, instead of just neutral.", 0.0,
                        1.0),
                in.mobs("depacify_list",
                        new String[] {
                                "minecraft:chicken 1.0", "minecraft:cow 1.0", "minecraft:pig 1.0",
                                "minecraft:sheep 1.0", "minecraft:rabbit 1.0", "minecraft:squid 1.0"
                        },
                        """
                        List of passive mobs (by entity id) that are made "neutral" like wolves.
                        You may put a tilde (~) in front of any entity id to make it specific; specific entity ids
                        will not count any entities extending (i.e., based on) the specified entity.
                        Additional number after the entity id is the chance (0.0 to 1.0) for entities of that type to spawn with the AI.
                        May or may not work on mod mobs."""),
                in.bool("eat_breeding_items", true,
                        "If true, passive mobs will seek out and eat the items used to breed them laying on the floor."),
                in.bool("eating_heals", true,
                        "If true, when mobs eat breeding items off the floor, they will regain health like wolves."));
    }

    private static ConfigSnapshot.DoorBreaking doorBreaking(ConfigReader in, DefaultBlockLists blockDefaults) {
        in.category("door_breaking", "Options to customize monsters' door-breaking behavior.");
        return new ConfigSnapshot.DoorBreaking(in.bool("_enabled", true,
                        "If true, mobs will destroy doors that are blocking their path."),
                in.real("break_speed", 0.33F,
                        "The block breaking speed multiplier for mobs, relative to the player's block breaking speed.",
                        0.0F, INF_F),
                in.bool("leave_drops", true, "If true, broken blocks will leave item drops."),
                in.bool("mad_creepers", true,
                        "If true, creepers will be very mad about not having arms to break things with, and resort to what they know best..."),
                in.mobs("mob_list",
                        new String[] { "minecraft:zombie 1.0", "minecraft:creeper 1.0", "minecraft:zombie_pigman 1.0" },
                        """
                        List of mobs that can gain door-breaking AI (note that the entity must have task-based AI enabled).
                        The number after each mob is the chance for that mob type to get the AI, from 0 to 1."""),
                in.bool("requires_target", true,
                        """
                        If true, mobs will only break doors while they are chasing an attack target.
                        Setting this to false typically leads to mobs smashing into your house to get to blocks they are targeting
                        as part of an idle griefing or fiddling behavior, such as torches or chests."""),
                in.bool("requires_tools", true,
                        """
                        If true, mobs will only target blocks they have the tools to harvest.
                        For example, they will only break iron doors with a pickaxe."""),
                in.blocks("target_blocks", blockDefaults.doors(),
                        "Door blocks that can be broken by mobs chasing you."));
    }

    private static ConfigSnapshot.IdleActivities idle(ConfigReader in) {
        in.category("idle_activities", "Options to customize all idle behaviors for monsters (fiddling/griefing).");
        return new ConfigSnapshot.IdleActivities(in.real("reach", 3.5F,
                        "Mobs' reach (from eye height) when targeting blocks. Player reach is about 4.5.", 0.0F, INF_F),
                in.integer("range", 12,
                        "The range at which mobs will search for blocks to target horizontally (xz-plane).", 1, MAX),
                in.integer("range_vertical", 6,
                        "The range at which mobs will search for blocks to target vertically (y-axis).", 1, MAX),
                in.integer("scan_count", 32,
                        "The number of blocks each mob randomly searches to grief/fiddle with every \"scan_delay\" ticks.",
                        1, MAX),
                in.integer("scan_count_global", 0,
                        "The maximum number of blocks that can be searched in any given tick by all mobs. 0 is no limit.",
                        0, MAX),
                in.integer("scan_delay", 2, "The number of ticks between each block scan.", 1, MAX));
    }

    private static ConfigSnapshot.Fiddling fiddling(ConfigReader in, DefaultBlockLists blockDefaults) {
        in.category("idle_fiddling", "Options to customize monsters' idle fiddling behavior.");
        return new ConfigSnapshot.Fiddling(in.bool("_enabled", true,
                        "If true, mobs will flip switches, press buttons, etc. while not doing anything else."),
                in.mobs("mob_list", new String[] { "minecraft:skeleton 1.0", "minecraft:zombie_pigman 1.0" },
                        """
                        List of mobs that can gain idle fiddling AI (note that the entity must have task-based AI enabled).
                        The number after each mob is the chance for that mob type to get the AI, from 0 to 1."""),
                in.blocks("target_blacklist", new String[0],
                        """
                        Specific blocks that will NOT be fiddled with by mobs.
                        Only really useful if you whitelist an entire namespace (*) to prevent mobs from fiddling with a few blocks from that namespace."""),
                in.blocks("target_blocks", blockDefaults.fiddleTargets(),
                        "Specific blocks that will be fiddled with by mobs."));
    }

    private static ConfigSnapshot.Griefing griefing(ConfigReader in, DefaultBlockLists blockDefaults) {
        in.category("idle_griefing", "Options to customize monsters' idle block breaking.");
        return new ConfigSnapshot.Griefing(in.bool("_enabled", true,
                        "If true, mobs will destroy blocks while not doing anything else."),
                in.bool("break_lights", true,
                        "If true, block breaking AI will automatically target all light sources (light value > 1)."),
                in.bool("break_sound", false,
                        """
                        If true, a loud snapping sound (the vanilla door break sound) will be played when a block
                        is broken, which is audible regardless of distance."""),
                in.real("break_speed", 0.5F,
                        "The block breaking speed multiplier for mobs, relative to the player's block breaking speed.",
                        0.0F, INF_F),
                in.bool("leave_drops", true, "If true, griefed blocks will leave item drops."),
                in.bool("mad_creepers", true,
                        "If true, creepers will be very mad about not having arms to break things with, and resort to what they know best..."),
                in.mobs("mob_list",
                        new String[] { "minecraft:zombie 1.0", "minecraft:creeper 1.0", "minecraft:zombie_pigman 1.0" },
                        """
                        List of mobs that can gain passive griefing AI (note that the entity must have task-based AI enabled).
                        The number after each mob is the chance for that mob type to get the AI, from 0 to 1."""),
                in.bool("requires_tools", false,
                        """
                        If true, mobs will only target blocks they have the tools to harvest.
                        For example, they will only break stone with a pickaxe."""),
                in.blocks("target_blacklist", new String[0],
                        """
                        Specific blocks that will NOT be griefed by mobs.
                        Only really useful if "break_lights" is enabled or when you whitelist an entire namespace (*) to create safe
                        light sources, prevent mobs from breaking normal world gen that produces light, or for removing a few blocks
                        from a namespace that you don't want mobs to break."""),
                in.blocks("target_blocks", blockDefaults.griefTargets(),
                        "Specific blocks that will be griefed by mobs."),
                in.blocks("target_lootable", blockDefaults.griefLootable(),
                        """
                        Specific lootable blocks that will be griefed by mobs.
                        Unlike the normal "target_blocks", these blocks will not be targeted if they still have a loot table tag
                        (e.g., unopened dungeon chests)."""));
    }

    private static ConfigSnapshot.Reactions reactions(ConfigReader in) {
        in.category("reaction_ai", "Options to customize reactive behaviors.");
        return new ConfigSnapshot.Reactions(in.bool("avoid_explosions", true,
                        "If true, all mobs will try to avoid TNT and creepers that are about to explode."),
                in.bool("call_for_help", true,
                        """
                        If true, all mobs will call for help from nearby mobs of the same type when struck.
                        Note that this does not trigger from killing blows."""),
                in.real("call_for_help_on_death", 0.2, "Chance for mobs to call for help when dealt a killing blow.",
                        0.0, 1.0),
                in.real("dodge_arrows", 0.4,
                        "The chance any mob will try to sidestep an arrow fired in their direction.", 0.0, 1.0));
    }

    private static ConfigSnapshot.Jockeys jockeys(ConfigReader in) {
        in.category("jockeys", "Options relating to which mobs should act as riders or mounts.");
        return new ConfigSnapshot.Jockeys(in.mobs("mount_list",
                        new String[] {
                                "minecraft:spider", "minecraft:slime", "minecraft:pig", "minecraft:sheep",
                                "minecraft:cow", "minecraft:polar_bear"
                        },
                        "List of mobs that can be ridden on by normal-sized riders (not all entities can be controlled by their rider)."),
                in.mobs("mount_list_small", new String[] { "minecraft:chicken", "minecraft:rabbit" },
                        "List of mobs that can be ridden on by small riders or normal-sized riders that are babies"
                                + " (not all entities can be controlled by their rider)."),
                in.mobs("rider_list",
                        new String[] {
                                "minecraft:zombie 0.05", "minecraft:skeleton 0.1", "minecraft:creeper 0.05",
                                "minecraft:witch 0.05", "minecraft:zombie_pigman 0.1"
                        },
                        """
                        List of mobs that can ride normal-sized mounts and the chance for them to gain the rider AI.
                        Note that the entity must have task-based AI enabled."""),
                in.mobs("rider_list_small", new String[0],
                        """
                        List of mobs that can only ride small mounts or normal-sized mounts that are babies and the chance for them to gain the rider AI.
                        Note that the entity must have task-based AI enabled."""));
    }

    private static ConfigSnapshot.Elite elite(ConfigReader in) {
        in.category("elite_ai", "Options to control the types of elite AI and their weighted chances of occurring.");
        MobList mobList = in.mobs("_mob_list",
                new String[] {
                        "minecraft:zombie 0.05 0.05", "minecraft:skeleton 0.2 0.05",
                        "minecraft:zombie_pigman 0.1 0.05 0.02"
                },
                """
                List of mobs that can gain special AI patterns and their chances to gain those patterns.
                You can specify multiple chances for each entity - each chance will be rolled and multiple AIs can stack.
                Note that the entity must have task-based AI enabled.""");
        EliteType[] types = EliteType.values();
        int[] weights = new int[types.length];
        for (EliteType type : types) {
            weights[type.ordinal()] = in.integer("ai_" + type.key(), 1,
                    "Weight for the " + type.key() + " ai pattern to be chosen.", 0, MAX);
        }
        return new ConfigSnapshot.Elite(mobList, weights,
                in.real("barrage_health_boost", 20.0, "Flat health increase added to mobs with barrage ai.", -INF, INF),
                in.real("charge_health_boost", 20.0, "Flat health increase added to mobs with charge ai.", -INF, INF),
                in.real("charge_knockback_resistance", 0.5, "Knockback resistance added to mobs with charge ai.", 0.0,
                        1.0),
                in.real("leap_speed_boost", 0.1,
                        "Speed increase multiplier to mobs with leap ai. Recommended to keep this well below 0.5.",
                        -INF, INF),
                in.real("shaman_heal_amount", 1.0F,
                        "Amount (in half-hearts) healed by mobs with shaman ai every 2 seconds. Can be overridden by the mob's nbt data.",
                        -INF_F, INF_F),
                in.real("shaman_health_boost", 20.0, "Flat health increase added to mobs with shaman ai.", -INF, INF),
                in.real("spawner_health_boost", 40.0, "Flat health increase added to mobs with spawner ai.", -INF, INF),
                in.real("spawner_speed_boost", -0.2,
                        "Speed increase multiplier to mobs with spawner ai. Recommended to keep this well below 0.5.",
                        -INF, INF),
                in.real("sprint_boots_speed_boost", 0.1,
                        """
                        Speed increase multiplier to the boots worn by mobs with sprint ai (these can drop as loot!).
                        Recommended to keep this well below 0.5.""",
                        0.0, INF),
                in.real("sprint_speed_boost", 0.7F,
                        """
                        Speed increase multiplier to mobs with sprint ai while they are sprinting. Can be overridden by the mob's nbt data.
                        Setting this to 0 breaks the sprint ai, so don't do that.""",
                        0.0F, INF_F),
                in.real("thief_avoid_range", 16.0F,
                        """
                        The minimum distance that mobs with thief ai will try to keep from players once they have stolen an item.
                        Can be overridden by the mob's nbt data.""",
                        1.0F, INF_F),
                in.real("thief_helmet_speed_boost", 0.1,
                        """
                        Speed increase multiplier to the helmet worn by mobs with thief ai (these can drop as loot!).
                        Recommended to keep this well below 0.5.""",
                        0.0, INF),
                in.real("throw_speed_boost", 0.1,
                        "Speed increase multiplier to mobs with throw ai. Recommended to keep this well below 0.5.",
                        -INF, INF),
                in.real("throw_player_health_boost", 20.0, "Flat health increase added to mobs with throw-player ai.",
                        -INF, INF),
                in.real("throw_player_helmet_damage_boost", 1.0,
                        "Flat damage increase added to the helmet worn by mobs with throw-player ai (these can drop as loot!).",
                        0.0, INF),
                in.real("throw_player_knockback_resistance", 0.5,
                        "Knockback resistance added to mobs with throw-player ai.", 0.0, 1.0));
    }

    private static ConfigSnapshot.Villages villages(ConfigReader in, DefaultBlockLists blockDefaults) {
        int hated = Reputation.HATED;
        in.category("villages",
                "Options to control village aggression and reputation.\n"
                        + "For reference, starting reputation is 0, minimum is -30 and maximum is 10.\n"
                        + "You are considered an enemy to a village if your reputation is " + hated + " or lower.");
        return new ConfigSnapshot.Villages(in.real("block_aggression_chance", 0.1,
                        """
                        Chance for you to be marked as an aggressor (to be attacked) when you break any block in a village
                        that is not on the "block_blacklist" while your reputation is low enough.""",
                        0.0, 1.0),
                in.integer("block_aggression_limit", -5,
                        """
                        The "block_aggression_chance" and "block_treasured_aggression_chance" only trigger if your reputation
                        in the village is less than or equal to this limit.""",
                        Reputation.MIN, Reputation.MAX),
                in.blocks("block_blacklist", blockDefaults.villageBlacklist(),
                        "Specific blocks that will NOT anger villagers when broken."),
                in.real("block_rep_loss_chance", 0.15,
                        """
                        Chance for you to lose 1 reputation when you break any block in a village that is not on the
                        "block_blacklist" while your reputation is low enough.""",
                        0.0, 1.0),
                in.integer("block_rep_loss_limit", 8,
                        """
                        The "block_rep_loss_chance" and "block_treasured_rep_loss_chance" only trigger if your reputation
                        in the village is equal to or less than this limit.""",
                        Reputation.MIN, Reputation.MAX),
                in.real("block_treasured_aggression_chance", 1.0,
                        """
                        Chance for you to be marked as an aggressor (to be attacked) when you break a block in a village that
                        is on the "block_treasured_list" while your reputation is low enough.""",
                        0.0, 1.0),
                in.blocks("block_treasured_list",
                        new String[] {
                                "minecraft:emerald_block", "minecraft:bookshelf", "minecraft:farmland", "minecraft:cake"
                        },
                        "Specific blocks that use separate chances for aggression and rep loss from other blocks."),
                in.real("block_treasured_rep_loss_chance", 1.0,
                        """
                        Chance for you to lose 1 reputation when you break a block in a village that is on the "block_treasured_list"
                        while your reputation is low enough.""",
                        0.0, 1.0),
                in.blocks("block_whitelist", new String[0],
                        """
                        Specific blocks that WILL aggro villagers when broken. If any blocks are specified here, they will
                        then be the only blocks that anger villagers (i.e., trigger reputation loss and aggression)."""),
                in.bool("command_include_center", true,
                        """
                        If true, the "/villageinfo" command will state the village center position.
                        As the command has unlimited search radius, disable this to prevent players from using the command
                        to easily find the nearest village."""),
                in.real("help_rep_chance", 0.2,
                        "Chance for you to earn 1 reputation for each monster killed near a village. The only reasonable way\n"
                                + "to restore rep from " + hated + " or lower with \"villagers_defend\" enabled.",
                        0.0, 1.0),
                in.bool("house_rep", true,
                        """
                        If true, all players known to a village will gain 1 rep when a new house is added to a village and
                        lose 1 rep when a house is lost. Highly recommended to keep "refresh_houses" enabled when this is."""),
                in.bool("name_villagers", true,
                        "If true, all villagers will spawn with randomized names based on their profession and career."),
                in.bool("name_villagers_always_show", true,
                        """
                        If true, villager names will be marked as 'always shown' so that you do not need to mouse over them
                        to see their names. Also makes them a little easier to keep track of."""),
                in.bool("refresh_houses", true,
                        """
                        If true, houses will stay a part of a village permanently once added (until their doors are destroyed
                        or all villagers in the village are killed), instead of being constantly added/removed as villagers move around."""),
                in.bool("reputation_particles", true,
                        "If true, particle effects will play when players lose or gain village reputation."),
                in.bool("reputation_sounds", true,
                        "If true, villager sound effects will play when players lose or gain village reputation."),
                in.bool("villagers_defend", true,
                        "If true, villagers will defend their village by attacking its aggressors and players with\n"
                                + "'hated' standing (reputation <= " + hated
                                + "), just like their iron golems do in vanilla."));
    }

    /** Applies edits made through the in-game config screen. */
    public static final class ChangeListener {

        @SubscribeEvent
        public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (OrdinaryAI.MOD_ID.equals(event.getModID()) && isLoaded()) {
                rebuild();
                OrdinaryAI.LOGGER.info("Config changes applied");
            }
        }

    }

}
