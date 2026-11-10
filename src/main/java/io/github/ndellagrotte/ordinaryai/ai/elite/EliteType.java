/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.elite;

import io.github.ndellagrotte.ordinaryai.config.ConfigSnapshot;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.nbt.NBTTagCompound;

/**
 * The elite AI patterns. The order is part of the saved format: it decides config key order and how a weighted roll
 * maps onto a type.
 */
public enum EliteType {

    CHARGE("charge", ChargeTask::writeDefault, ChargeTask::new, ChargeTask::equip),
    SPRINT("sprint", SprintTask::writeDefault, SprintTask::new, SprintTask::equip),
    JUMP("jump", EliteType::writeFlag, JumpTask::new, JumpTask::equip),
    LEAP("leap", EliteType::writeFlag, LeapTask::new, LeapTask::equip),
    BARRAGE("barrage", BarrageTask::writeDefault, BarrageTask::new, BarrageTask::equip),
    SPAWNER("spawner", EliteType::writeFlag, SpawnerTask::new, SpawnerTask::equip),
    SHAMAN("shaman", ShamanTask::writeDefault, ShamanTask::new, ShamanTask::equip),
    THROW("throw", EliteType::writeFlag, ThrowAllyTask::new, ThrowAllyTask::equip),
    THROW_PLAYER("throw_player", EliteType::writeFlag, ThrowPlayerTask::new, ThrowPlayerTask::equip),
    THIEF("thief", ThiefTask::writeDefault, ThiefTask::new, ThiefTask::equip);

    private final String key;
    private final DefaultWriter defaultWriter;
    private final TaskFactory factory;
    private final Equipper equipper;

    EliteType(String key, DefaultWriter defaultWriter, TaskFactory factory, Equipper equipper) {
        this.key = key;
        this.defaultWriter = defaultWriter;
        this.factory = factory;
        this.equipper = equipper;
    }

    private static void writeFlag(String key, NBTTagCompound eliteTag, ConfigSnapshot.Elite config) {
        eliteTag.setByte(key, (byte) 1);
    }

    /** The name used for the NBT key and, prefixed with {@code ai_}, the config weight key. */
    public String key() {
        return key;
    }

    /** Returns true if this type is saved in the mob's elite tag. Works for both flag and value tags. */
    public boolean isPresent(NBTTagCompound eliteTag) {
        return eliteTag.getFloat(key) > 0.0F;
    }

    /** Saves this type to the mob's elite tag with its default settings. */
    public void writeDefault(NBTTagCompound eliteTag, ConfigSnapshot.Elite config) {
        defaultWriter.write(key, eliteTag, config);
    }

    /** Creates the task for a mob from its elite tag. */
    public EntityAIBase createTask(EntityLiving mob, NBTTagCompound eliteTag) {
        return factory.create(mob, eliteTag);
    }

    /** Applies the one-time gear and attribute changes that come with this type. */
    public void equip(EntityLiving mob, ConfigSnapshot.Elite config) {
        equipper.equip(mob, config);
    }

    @FunctionalInterface
    interface DefaultWriter {

        void write(String key, NBTTagCompound eliteTag, ConfigSnapshot.Elite config);

    }

    @FunctionalInterface
    interface TaskFactory {

        EntityAIBase create(EntityLiving mob, NBTTagCompound eliteTag);

    }

    @FunctionalInterface
    interface Equipper {

        void equip(EntityLiving mob, ConfigSnapshot.Elite config);

    }

}
