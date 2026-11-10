/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai;

import io.github.ndellagrotte.ordinaryai.config.ConfigSnapshot;
import io.github.ndellagrotte.ordinaryai.config.ModConfig;
import org.jspecify.annotations.Nullable;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntitySlime;

import java.util.List;

/** Makes a mob look for a suitable mount and ride it, forming a jockey. */
public final class MountSeekTask extends EntityAIBase implements OrdinaryTask {

    private static final int SEARCH_INTERVAL = 50 * TaskUtil.EVALUATION_INTERVAL;
    private static final int GIVE_UP_AFTER = 400;
    private static final double SPEED = 1.3;

    private final EntityLiving mob;
    private final boolean small;

    private @Nullable EntityLiving mount;
    private long nextSearchAt;
    private long giveUpAt;

    public MountSeekTask(EntityLiving mob, boolean small) {
        this.mob = mob;
        this.small = small;
        // Spread the first search out so a freshly loaded chunk does not search all at once
        nextSearchAt = TaskUtil.now(mob) + mob.getRNG().nextInt(SEARCH_INTERVAL);
        setMutexBits(TaskUtil.MUTEX_MOVEMENT | TaskUtil.MUTEX_FACING);
    }

    @Override
    public boolean shouldExecute() {
        if (mob.isRiding()) {
            return false;
        }
        long now = TaskUtil.now(mob);
        if (now < nextSearchAt) {
            return false;
        }
        nextSearchAt = now + SEARCH_INTERVAL;
        mount = findMount();
        return mount != null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        EntityLiving target = mount;
        return !mob.isRiding() &&
            target != null &&
            !target.isBeingRidden() &&
            target.isEntityAlive() &&
            !mob.getNavigator().noPath();
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }

    @Override
    public void startExecuting() {
        giveUpAt = TaskUtil.now(mob) + GIVE_UP_AFTER;
        if (mount != null) {
            mob.getNavigator().tryMoveToEntityLiving(mount, SPEED);
        }
    }

    @Override
    public void updateTask() {
        EntityLiving target = mount;
        if (target == null) {
            return;
        }
        mob.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);

        if (!target.isBeingRidden() && TaskUtil.isInContact(mob, target)) {
            mob.startRiding(target, true);
            mount = null;
        } else if (TaskUtil.now(mob) > giveUpAt) {
            mob.getNavigator().clearPath();
        } else if (mob.getNavigator().noPath()) {
            mob.getNavigator().tryMoveToEntityLiving(target, SPEED);
        }
    }

    @Override
    public void resetTask() {
        mob.getNavigator().clearPath();
        mount = null;
    }

    private @Nullable
    EntityLiving findMount() {
        ConfigSnapshot.Jockeys config = ModConfig.get().jockeys();
        List<EntityLiving> candidates = mob.world.getEntitiesWithinAABB(EntityLiving.class,
            mob.getEntityBoundingBox().grow(16.0, 8.0, 16.0),
            candidate -> candidate != mob && isValidMount(candidate, config)
        );
        return TaskUtil.pickRandom(candidates, mob.getRNG());
    }

    private boolean isValidMount(EntityLiving candidate, ConfigSnapshot.Jockeys config) {
        if (!candidate.isEntityAlive() || candidate.isBeingRidden()) {
            return false;
        }
        // Never climb onto something that is fighting the rider, or that the rider is fighting
        if (candidate.getAttackTarget() == mob || mob.getAttackTarget() == candidate) {
            return false;
        }
        boolean ridesSmall = small || mob.isChild() || mob instanceof EntitySlime slime && slime.isSmallSlime();
        if (ridesSmall) {
            return config.mountListSmall().contains(candidate.getClass()) ||
                candidate.isChild() && config.mountList().contains(candidate.getClass());
        }
        return !candidate.isChild() && config.mountList().contains(candidate.getClass());
    }

}
