/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.elite;

import io.github.ndellagrotte.ordinaryai.ai.TaskUtil;
import io.github.ndellagrotte.ordinaryai.ai.grief.BlockBreaking;
import io.github.ndellagrotte.ordinaryai.config.ConfigSnapshot;
import org.jspecify.annotations.Nullable;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import java.util.List;

/**
 * Hangs back behind an ally that is fighting the same target, and periodically heals and strengthens every such
 * ally it can see.
 */
final class ShamanTask extends EliteTask {

    private static final String KEY = "shaman";
    private static final String EFFECTS_KEY = "shamanFX";
    private static final int PULSE_INTERVAL = 40;
    private static final int SEARCH_RETRY = 20;

    private final float healAmount;
    private final NBTTagList effects;

    private @Nullable EntityLiving followed;
    private long nextPulseAt;
    private long nextSearchAt;

    ShamanTask(EntityLiving mob, NBTTagCompound eliteTag) {
        super(mob, TaskUtil.MUTEX_MOVEMENT | TaskUtil.MUTEX_FACING);
        healAmount = eliteTag.getFloat(KEY);
        effects = eliteTag.getTagList(EFFECTS_KEY, Constants.NBT.TAG_COMPOUND);
    }

    static void writeDefault(String key, NBTTagCompound eliteTag, ConfigSnapshot.Elite config) {
        eliteTag.setFloat(key, config.shamanHealAmount());
        // Saved in full so the list doubles as an example for anyone editing it by hand
        NBTTagList defaults = new NBTTagList();
        defaults.appendTag(new PotionEffect(MobEffects.STRENGTH, 50, 0
        ).writeCustomPotionEffectToNBT(new NBTTagCompound()));
        defaults.appendTag(new PotionEffect(MobEffects.RESISTANCE, 50, 0
        ).writeCustomPotionEffectToNBT(new NBTTagCompound()));
        defaults.appendTag(new PotionEffect(MobEffects.SPEED, 50, 0).writeCustomPotionEffectToNBT(new NBTTagCompound()
        ));
        eliteTag.setTag(EFFECTS_KEY, defaults);
    }

    static void equip(EntityLiving mob, ConfigSnapshot.Elite config) {
        mob.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.BONE));
        mob.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Blocks.LIT_PUMPKIN));
        modify(mob, SharedMonsterAttributes.MAX_HEALTH, "Shaman health boost", config.shamanHealthBoost(), ADD);
    }

    @Override
    public boolean shouldExecute() {
        return findAllyToFollow();
    }

    @Override
    public boolean shouldContinueExecuting() {
        EntityLivingBase target = mob.getAttackTarget();
        EntityLiving ally = followed;
        if (target == null || ally == null || !ally.isEntityAlive()) {
            return false;
        }
        return target == ally.getAttackTarget() || findAllyToFollow();
    }

    @Override
    public void resetTask() {
        followed = null;
    }

    @Override
    public void updateTask() {
        EntityLiving ally = followed;
        if (ally == null) {
            return;
        }
        EntityLivingBase target = mob.getAttackTarget();
        double distanceSq = mob.getDistanceSq(ally);
        if (distanceSq > 36.0) {
            mob.getNavigator().tryMoveToEntityLiving(ally, 1.3);
        } else if (distanceSq < 4.0) {
            mob.getNavigator().clearPath();
        }
        if (!mob.getNavigator().noPath()) {
            mob.getLookHelper().setLookPositionWithEntity(ally, 30.0F, 30.0F);
        } else if (target != null) {
            mob.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
        }

        long now = now();
        if (now >= nextPulseAt && target != null) {
            nextPulseAt = now + PULSE_INTERVAL;
            for (EntityLiving ally2 : alliesAgainst(target)) {
                if (mob.getEntitySenses().canSee(ally2)) {
                    bless(ally2);
                }
            }
        }
    }

    private void bless(EntityLiving ally) {
        ally.heal(healAmount);
        ally.extinguish();
        for (int i = 0; i < effects.tagCount(); i++) {
            PotionEffect effect = PotionEffect.readCustomPotionEffectFromNBT(effects.getCompoundTagAt(i));
            if (effect != null) {
                ally.addPotionEffect(effect);
            }
        }
        mob.world.playEvent(BlockBreaking.EVENT_BONEMEAL_PARTICLES, new BlockPos(ally), 0);
    }

    /** Picks a random nearby ally with the same target. A failed search is not repeated straight away. */
    private boolean findAllyToFollow() {
        EntityLivingBase target = mob.getAttackTarget();
        if (target == null) {
            return false;
        }
        long now = now();
        if (now < nextSearchAt) {
            return false;
        }
        EntityLiving ally = TaskUtil.pickRandom(alliesAgainst(target), mob.getRNG());
        if (ally == null) {
            nextSearchAt = now + SEARCH_RETRY;
            return false;
        }
        followed = ally;
        return true;
    }

    private List<EntityLiving> alliesAgainst(EntityLivingBase target) {
        return mob.world.getEntitiesWithinAABB(EntityLiving.class, mob.getEntityBoundingBox().grow(16.0, 8.0, 16.0),
            candidate -> candidate != mob && candidate.isEntityAlive() && candidate.getAttackTarget() == target
        );
    }

}
