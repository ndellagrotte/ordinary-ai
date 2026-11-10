/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

/**
 * Per-mob settings stored in the entity's Forge data. Every key can be set by hand (for example with
 * {@code /summon}) to override what the config would roll.
 */
public final class MobData {

    /** Root compound for this mod's data. */
    public static final String ROOT = "ordinaryai";
    /** Root compound used by Special AI, read once and moved to {@link #ROOT}. */
    public static final String LEGACY_ROOT = "sAI";

    public static final String AVOID_EXPLOSIONS = "AvoidExplosions";
    public static final String DODGE_ARROWS = "DodgeArrows";
    public static final String DEFEND_VILLAGE = "DefendVillage";
    public static final String DEPACIFY = "Depacify";
    public static final String RIDER = "Rider";

    public static final String DOOR_BREAKING = "DoorBreaking";
    public static final String DOOR_BREAK_SPEED = "DoorBreakSpeed";
    public static final String DOOR_NEEDS_TARGET = "DoorNeedsTarget";
    public static final String DOOR_NEEDS_TOOL = "DoorNeedsTool";
    public static final String DOOR_BLOCKS = "DoorBlocks";

    public static final String IDLE_RANGE_XZ = "IdleScanRangeXZ";
    public static final String IDLE_RANGE_Y = "IdleScanRangeY";

    public static final String GRIEFING = "Griefing";
    public static final String GRIEF_BREAK_SPEED = "GriefBreakSpeed";
    public static final String GRIEF_NEEDS_TOOL = "GriefNeedsTool";
    public static final String GRIEF_LIGHTS = "GriefLights";
    public static final String GRIEF_BLOCKS = "GriefBlocks";
    public static final String GRIEF_LOOTABLE = "GriefLootable";
    public static final String GRIEF_BLACKLIST = "GriefBlacklist";

    public static final String FIDDLING = "Fiddling";
    public static final String FIDDLE_BLOCKS = "FiddleBlocks";
    public static final String FIDDLE_BLACKLIST = "FiddleBlacklist";

    public static final String ELITE_AI = "EliteAI";
    public static final String FORCE_INIT = "ForceInit";

    /** Flag on arrows that have already been offered to nearby mobs to dodge. */
    public static final String ARROW_CHECKED = "OrdinaryAIDodgeCheck";
    /** Special AI's name for {@link #ARROW_CHECKED}. */
    public static final String LEGACY_ARROW_CHECKED = "SAIArrowDodgeCheck";

    private MobData() { }

    /** Returns the mob's data compound, creating it and moving any Special AI data across if needed. */
    public static NBTTagCompound of(Entity entity) {
        NBTTagCompound forgeData = entity.getEntityData();
        if (!forgeData.hasKey(ROOT, Constants.NBT.TAG_COMPOUND)) {
            if (forgeData.hasKey(LEGACY_ROOT, Constants.NBT.TAG_COMPOUND)) {
                forgeData.setTag(ROOT, forgeData.getCompoundTag(LEGACY_ROOT));
                forgeData.removeTag(LEGACY_ROOT);
            } else {
                forgeData.setTag(ROOT, new NBTTagCompound());
            }
        }
        return forgeData.getCompoundTag(ROOT);
    }

    /** Returns true if the key holds any numeric value, which is how flags and chances are stored. */
    public static boolean hasNumber(NBTTagCompound tag, String key) {
        return tag.hasKey(key, Constants.NBT.TAG_ANY_NUMERIC);
    }

}
