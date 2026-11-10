/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.elite;

import io.github.ndellagrotte.ordinaryai.ai.TaskUtil;
import io.github.ndellagrotte.ordinaryai.config.ConfigSnapshot;
import org.jspecify.annotations.Nullable;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Locks on to the target, then sprays a volley of arrows in that direction. */
final class BarrageTask extends EliteTask {

    private static final String KEY = "barrage";
    private static final int COOLDOWN = 60 * TaskUtil.EVALUATION_INTERVAL;

    private final float arrowDamage;

    private Phase phase = Phase.IDLE;
    private int timer;
    private long readyAt;
    private @Nullable Vec3d aim;

    BarrageTask(EntityLiving mob, NBTTagCompound eliteTag) {
        super(mob, TaskUtil.MUTEX_MOVEMENT | TaskUtil.MUTEX_FACING | TaskUtil.MUTEX_SWIMMING);
        arrowDamage = eliteTag.getFloat(KEY);
    }

    static void writeDefault(String key, NBTTagCompound eliteTag, ConfigSnapshot.Elite config) {
        eliteTag.setFloat(key, 3.0F);
    }

    static void equip(EntityLiving mob, ConfigSnapshot.Elite config) {
        mob.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Blocks.DISPENSER));
        modify(mob, SharedMonsterAttributes.MAX_HEALTH, "Barrager health boost", config.barrageHealthBoost(), ADD);
    }

    @Override
    public boolean shouldExecute() {
        if (!mob.onGround || mob.isRiding() || now() < readyAt || mob.getRNG().nextInt(10) != 0) {
            return false;
        }
        EntityLivingBase target = mob.getAttackTarget();
        if (target == null) {
            return false;
        }
        double distanceSq = mob.getDistanceSq(target);
        return distanceSq <= 256.0 && distanceSq >= 25.0 && mob.getEntitySenses().canSee(target);
    }

    @Override
    public boolean shouldContinueExecuting() {
        return phase != Phase.IDLE;
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }

    @Override
    public void startExecuting() {
        mob.getNavigator().clearPath();
        timer = 30;
        phase = Phase.AIMING;
        TaskUtil.swingArms(mob);
        mob.world.playSound(null, new BlockPos(mob), SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON, mob.getSoundCategory(),
            1.0F, 1.0F / (mob.getRNG().nextFloat() * 0.4F + 0.8F)
        );
    }

    @Override
    public void updateTask() {
        timer--;
        if (phase == Phase.AIMING) {
            takeAim();
        } else if (phase == Phase.SHOOTING) {
            shoot();
        }
    }

    private void takeAim() {
        EntityLivingBase target = mob.getAttackTarget();
        if (target == null || !target.isEntityAlive()) {
            phase = Phase.IDLE;
            return;
        }
        mob.getLookHelper().setLookPositionWithEntity(target, 100.0F, 100.0F);
        if (timer > 0) {
            return;
        }
        double dx = target.posX - mob.posX;
        double dy = target.getEntityBoundingBox().minY + target.height / 3.0F - mob.posY - mob.getEyeHeight();
        double dz = target.posZ - mob.posZ;
        // Aim a little high to allow for the arrows dropping
        aim = new Vec3d(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz);
        timer = 60;
        phase = Phase.SHOOTING;
    }

    private void shoot() {
        Vec3d direction = aim;
        if (direction == null) {
            phase = Phase.IDLE;
            return;
        }
        mob.getLookHelper()
            .setLookPosition(mob.posX + direction.x, mob.posY + mob.getEyeHeight(), mob.posZ + direction.z, 100.0F,
                100.0F
            );
        if (timer < 0) {
            phase = Phase.IDLE;
        } else if (timer % 5 == 0) {
            EntityArrow arrow = new EntityTippedArrow(mob.world, mob.posX, mob.posY + mob.getEyeHeight(), mob.posZ);
            arrow.shootingEntity = mob;
            arrow.setDamage(arrowDamage + mob.getRNG().nextGaussian() * 0.5 + mob.world.getDifficulty().getId() * 0.22);
            arrow.shoot(direction.x, direction.y, direction.z, 1.8F, 20.0F);
            if (mob.isBurning()) {
                arrow.setFire(100);
            }
            mob.world.playSound(null, new BlockPos(mob), SoundEvents.ENTITY_ARROW_SHOOT, mob.getSoundCategory(), 1.0F,
                1.0F / (mob.getRNG().nextFloat() * 0.4F + 0.8F)
            );
            mob.world.spawnEntity(arrow);
        }
    }

    @Override
    public void resetTask() {
        phase = Phase.IDLE;
        aim = null;
        readyAt = now() + COOLDOWN;
    }

    private enum Phase {

        IDLE,
        AIMING,
        SHOOTING

    }

}
