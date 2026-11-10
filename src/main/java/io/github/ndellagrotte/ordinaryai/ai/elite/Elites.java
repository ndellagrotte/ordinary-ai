/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.elite;

import io.github.ndellagrotte.ordinaryai.config.ConfigSnapshot;
import io.github.ndellagrotte.ordinaryai.util.WeightedPick;

import net.minecraft.entity.EntityLiving;
import net.minecraft.nbt.NBTTagCompound;

/** Decides which elite AI patterns a mob gets and attaches them. */
public final class Elites {

    private static final EliteType[] TYPES = EliteType.values();

    private Elites() { }

    /**
     * Rolls the mob's elite chances, saving a randomly chosen pattern to its elite tag for each success. Patterns
     * can stack, and rolling the same one twice has no further effect.
     */
    public static void roll(EntityLiving mob, NBTTagCompound eliteTag, ConfigSnapshot.Elite config) {
        float[] chances = config.mobList().chancesFor(mob.getClass());
        int totalWeight = WeightedPick.total(config.weights());
        if (chances == null || totalWeight <= 0) {
            return;
        }
        for (float chance : chances) {
            if (chance > 0.0F && mob.getRNG().nextFloat() < chance) {
                int index = WeightedPick.pick(config.weights(), mob.getRNG().nextInt(totalWeight));
                if (index >= 0) {
                    TYPES[index].writeDefault(eliteTag, config);
                }
            }
        }
    }

    /**
     * Adds a task for every pattern saved in the mob's elite tag.
     *
     * @param equip true the first time, to also hand out the gear and attribute changes that go with each pattern
     */
    public static void attach(EntityLiving mob, NBTTagCompound eliteTag, boolean equip, ConfigSnapshot.Elite config) {
        // Health boosts should not leave the mob looking wounded, nor heal one that was
        float missingHealth = mob.getMaxHealth() - mob.getHealth();
        boolean equipped = false;
        for (EliteType type : TYPES) {
            if (type.isPresent(eliteTag)) {
                mob.tasks.addTask(0, type.createTask(mob, eliteTag));
                if (equip) {
                    type.equip(mob, config);
                    equipped = true;
                }
            }
        }
        if (equipped) {
            mob.setHealth(mob.getMaxHealth() - missingHealth);
        }
    }

}
