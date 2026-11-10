/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.grief;

import io.github.ndellagrotte.ordinaryai.ai.OrdinaryTask;
import io.github.ndellagrotte.ordinaryai.ai.TaskUtil;
import io.github.ndellagrotte.ordinaryai.config.ModConfig;
import org.jspecify.annotations.Nullable;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.List;

/** Makes an animal walk to and eat its breeding item when one is lying on the ground. */
public final class EatBreedingItemTask extends EntityAIBase implements OrdinaryTask {

    private static final int SEARCH_INTERVAL = 30 * TaskUtil.EVALUATION_INTERVAL;
    private static final int GIVE_UP_AFTER = 400;
    private static final double SPEED = 1.25;

    private final EntityAnimal mob;

    private @Nullable EntityItem food;
    private long nextSearchAt;
    private long giveUpAt;

    public EatBreedingItemTask(EntityAnimal mob) {
        this.mob = mob;
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
        if (!ModConfig.get().general().eatBreedingItems() || !ForgeEventFactory.getMobGriefingEvent(mob.world, mob)) {
            return false;
        }
        List<EntityItem> items = mob.world.getEntitiesWithinAABB(EntityItem.class,
            mob.getEntityBoundingBox().grow(16.0, 8.0, 16.0),
            item -> item.isEntityAlive() && mob.isBreedingItem(item.getItem())
        );
        food = TaskUtil.pickRandom(items, mob.getRNG());
        return food != null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        EntityItem target = food;
        return !mob.isRiding() &&
            target != null &&
            target.isEntityAlive() &&
            TaskUtil.now(mob) <= giveUpAt &&
            mob.isBreedingItem(target.getItem()) &&
            !mob.getNavigator().noPath();
    }

    @Override
    public void startExecuting() {
        giveUpAt = TaskUtil.now(mob) + GIVE_UP_AFTER;
        moveToFood();
    }

    @Override
    public void updateTask() {
        EntityItem target = food;
        if (target == null) {
            return;
        }
        mob.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);

        if (mob.getEntityBoundingBox().grow(0.2, 0.0, 0.2).intersects(target.getEntityBoundingBox())) {
            eat(target);
        } else if (mob.getNavigator().noPath()) {
            moveToFood();
        }
    }

    @Override
    public void resetTask() {
        mob.getNavigator().clearPath();
        food = null;
    }

    private void moveToFood() {
        EntityItem target = food;
        if (target != null) {
            mob.getNavigator().tryMoveToXYZ(target.posX, target.posY, target.posZ, SPEED);
        }
    }

    private void eat(EntityItem target) {
        if (ModConfig.get().general().eatingHeals()) {
            ItemStack stack = target.getItem();
            float healAmount = stack.getCount();
            if (!stack.isEmpty() && stack.getItem() instanceof ItemFood edible) {
                healAmount *= edible.getHealAmount(stack);
            }
            mob.heal(healAmount);
        }
        mob.world.playSound(null, new BlockPos(mob), SoundEvents.ENTITY_PLAYER_BURP, mob.getSoundCategory(), 0.5F,
            mob.getRNG().nextFloat() * 0.1F + 0.9F
        );
        mob.getNavigator().clearPath();
        target.setDead();
        food = null;
    }

}
