/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.village;

import io.github.ndellagrotte.ordinaryai.ai.OrdinaryTask;
import io.github.ndellagrotte.ordinaryai.ai.TaskUtil;
import io.github.ndellagrotte.ordinaryai.config.ModConfig;
import org.jspecify.annotations.Nullable;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.Village;
import net.minecraft.village.VillageCollection;

import java.util.UUID;

/** Makes a villager go after its village's aggressors and hated players, as iron golems do. */
public final class DefendVillageTask extends EntityAITarget implements OrdinaryTask {

    private @Nullable Village village;
    private long nextVillageLookupAt;
    private @Nullable EntityLivingBase aggressor;
    private @Nullable UUID aggressorPlayerId;

    public DefendVillageTask(EntityCreature mob) {
        super(mob, false, true);
        setMutexBits(TaskUtil.MUTEX_TARGET);
    }

    @Override
    public boolean shouldExecute() {
        if (!findAggressor()) {
            return false;
        }
        if (taskOwner instanceof EntityVillager) {
            taskOwner.world.playSound(null, new BlockPos(taskOwner), SoundEvents.ENTITY_VILLAGER_NO,
                    taskOwner.getSoundCategory(), 1.0F, 1.0F / (taskOwner.getRNG().nextFloat() * 0.4F + 0.8F));
        }
        aggressorPlayerId = aggressor instanceof EntityPlayer ? aggressor.getUniqueID() : null;
        return true;
    }

    @Override
    public void startExecuting() {
        taskOwner.setAttackTarget(aggressor);
        super.startExecuting();
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (!super.shouldContinueExecuting()) {
            return false;
        }
        Village home = village;
        UUID playerId = aggressorPlayerId;
        if (home == null || playerId == null) {
            return true;
        }
        // Players can be forgiven mid-fight once their standing recovers
        return Reputation.keepsAttacking(home.getPlayerReputation(playerId),
                ModConfig.get().villages().blockAggressionLimit(), taskOwner.getRNG().nextFloat());
    }

    private boolean findAggressor() {
        long now = TaskUtil.now(taskOwner);
        if (now >= nextVillageLookupAt) {
            nextVillageLookupAt = now + (70L + taskOwner.getRNG().nextInt(50)) * TaskUtil.EVALUATION_INTERVAL;
            VillageCollection villages = taskOwner.world.getVillageCollection();
            village = villages == null ? null : villages.getNearestVillage(new BlockPos(taskOwner), 32);
        }
        Village home = village;
        if (home == null) {
            return false;
        }
        aggressor = home.findNearestVillageAggressor(taskOwner);
        if (isSuitableTarget(aggressor, false)) {
            return true;
        }
        if (taskOwner.getRNG().nextInt(20) != 0) {
            return false;
        }
        aggressor = home.getNearestTargetPlayer(taskOwner);
        return isSuitableTarget(aggressor, false);
    }

}
