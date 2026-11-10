/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai;

import org.jspecify.annotations.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.util.EnumHand;

import java.util.List;
import java.util.Random;

/** Small helpers shared by this mod's AI tasks. */
public final class TaskUtil {

    /** Tasks only run together if they share no mutex bits. */
    public static final int MUTEX_NONE = 0b0000;
    public static final int MUTEX_MOVEMENT = 0b0001;
    public static final int MUTEX_FACING = 0b0010;
    public static final int MUTEX_SWIMMING = 0b0100;
    /** The bit shared by all targeting tasks so that only one chooses a target at a time. */
    public static final int MUTEX_TARGET = 0b0001;

    /**
     * Idle tasks are evaluated on every third tick. Delays that Special AI counted in evaluations are multiplied by
     * this to give the same delay measured against the world clock, which stays correct if another mod skips AI
     * ticks.
     */
    public static final int EVALUATION_INTERVAL = 3;

    /** Priority used for idle tasks on a mob that has no tasks of its own to rank against. */
    private static final int FALLBACK_IDLE_PRIORITY = 6;

    private TaskUtil() { }

    /** Returns the current world time for the mob, used for cooldowns. */
    public static long now(Entity entity) {
        return entity.world.getTotalWorldTime();
    }

    /** Returns true if either task list already holds a task added by this mod. */
    public static boolean isSetUp(EntityLiving mob) {
        return hasOrdinaryTask(mob.tasks) || hasOrdinaryTask(mob.targetTasks);
    }

    private static boolean hasOrdinaryTask(EntityAITasks tasks) {
        for (EntityAITasks.EntityAITaskEntry entry : tasks.taskEntries) {
            if (entry.action instanceof OrdinaryTask) {
                return true;
            }
        }
        return false;
    }

    /** Returns the mob's task of the given type, or null if it has none. */
    public static <T> @Nullable T findTask(EntityLiving mob, Class<T> type) {
        for (EntityAITasks.EntityAITaskEntry entry : mob.tasks.taskEntries) {
            if (type.isInstance(entry.action)) {
                return type.cast(entry.action);
            }
        }
        return null;
    }

    /**
     * Returns the priority to give an idle task: the same as the mob's own wandering or looking around, or just
     * below everything it has when it does none of those.
     */
    public static int idlePriority(EntityLiving mob) {
        boolean any = false;
        int lowest = Integer.MIN_VALUE;
        for (EntityAITasks.EntityAITaskEntry entry : mob.tasks.taskEntries) {
            EntityAIBase action = entry.action;
            if (action instanceof EntityAIWander ||
                action instanceof EntityAIWatchClosest ||
                action instanceof EntityAILookIdle) {
                return entry.priority;
            }
            any = true;
            lowest = Math.max(lowest, entry.priority);
        }
        return any && lowest < Integer.MAX_VALUE ? lowest + 1 : FALLBACK_IDLE_PRIORITY;
    }

    /** Squared distance at which a mob counts as touching something it walked up to. */
    public static double contactRangeSq(Entity mob, Entity target) {
        return mob.width * 2.0F * mob.width * 2.0F + target.width;
    }

    /** Returns true if the mob is close enough to the target's feet to grab or strike it. */
    public static boolean isInContact(Entity mob, Entity target) {
        return mob.getDistanceSq(target.posX, target.getEntityBoundingBox().minY, target.posZ) <=
            contactRangeSq(mob, target);
    }

    /** Swings both arms. */
    public static void swingArms(EntityLiving mob) {
        mob.swingArm(EnumHand.MAIN_HAND);
        mob.swingArm(EnumHand.OFF_HAND);
    }

    /** Returns a random element, or null if the list is empty. */
    public static <T> @Nullable T pickRandom(List<T> list, Random random) {
        return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
    }

}
