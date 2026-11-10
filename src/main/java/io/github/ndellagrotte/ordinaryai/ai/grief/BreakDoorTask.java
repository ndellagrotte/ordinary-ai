/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.grief;

import io.github.ndellagrotte.ordinaryai.OrdinaryAI;
import io.github.ndellagrotte.ordinaryai.ai.MobData;
import io.github.ndellagrotte.ordinaryai.ai.OrdinaryTask;
import io.github.ndellagrotte.ordinaryai.ai.TaskUtil;
import io.github.ndellagrotte.ordinaryai.config.BlockTargetMap;
import io.github.ndellagrotte.ordinaryai.config.ConfigSnapshot;
import io.github.ndellagrotte.ordinaryai.config.ModConfig;
import org.jspecify.annotations.Nullable;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIBreakDoor;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * Replaces the vanilla door-breaking task with one that works on any configured door-like block, respects tools, and
 * breaks at a configurable speed. It extends the vanilla task so other mods still recognise it as door breaking.
 */
public final class BreakDoorTask extends EntityAIBreakDoor implements OrdinaryTask {

    private final @Nullable Float breakSpeedOverride;
    private final @Nullable Boolean needsTargetOverride;
    private final @Nullable Boolean needsToolOverride;
    private final @Nullable BlockTargetMap targetsOverride;

    private @Nullable IBlockState targetState;
    private boolean completed;
    // Direction to the door when the mob started on it, used to notice when the mob has passed through
    private float initialDoorVecX;
    private float initialDoorVecZ;

    private int hitCounter;
    private float blockDamage;
    private int lastProgress = BlockBreaking.NO_PROGRESS;

    /**
     * Creates the task.
     *
     * @throws IllegalArgumentException if the mob does not walk on the ground
     */
    public BreakDoorTask(EntityLiving mob, NBTTagCompound tag) {
        super(mob);
        setMutexBits(TaskUtil.MUTEX_NONE);
        breakSpeedOverride = tag.hasKey(MobData.DOOR_BREAK_SPEED) ? tag.getFloat(MobData.DOOR_BREAK_SPEED) : null;
        needsTargetOverride = tag.hasKey(MobData.DOOR_NEEDS_TARGET) ? tag.getBoolean(MobData.DOOR_NEEDS_TARGET) : null;
        needsToolOverride = tag.hasKey(MobData.DOOR_NEEDS_TOOL) ? tag.getBoolean(MobData.DOOR_NEEDS_TOOL) : null;
        targetsOverride = tag.hasKey(MobData.DOOR_BLOCKS)
                ? BlockTargetMap.parseLine(tag.getString(MobData.DOOR_BLOCKS), OrdinaryAI.LOGGER::warn)
                : null;
    }

    @Override
    public boolean shouldExecute() {
        if (!entity.collidedHorizontally) {
            return false;
        }
        ConfigSnapshot.DoorBreaking config = ModConfig.get().doorBreaking();
        if (!config.enabled()) {
            return false;
        }
        boolean needsTarget = needsTargetOverride != null ? needsTargetOverride : config.requiresTarget();
        if (needsTarget && entity.getAttackTarget() == null && entity.getRevengeTarget() == null) {
            return false;
        }
        return ForgeEventFactory.getMobGriefingEvent(entity.world, entity) && findObstructingDoor(config);
    }

    @Override
    public void startExecuting() {
        if (isMadCreeper(ModConfig.get().doorBreaking())) {
            ((EntityCreeper) entity).ignite();
            completed = true;
            return;
        }
        completed = false;
        initialDoorVecX = (float) (doorPosition.getX() + 0.5 - entity.posX);
        initialDoorVecZ = (float) (doorPosition.getZ() + 0.5 - entity.posZ);
        hitCounter = 0;
        blockDamage = 0.0F;
        lastProgress = BlockBreaking.NO_PROGRESS;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return !completed && targetState != null && entity.getDistanceSq(doorPosition) < 4.0;
    }

    @Override
    public void resetTask() {
        blockDamage = 0.0F;
        targetState = null;
        clearBreakProgress();
    }

    @Override
    public void onOwnerDeath() {
        clearBreakProgress();
    }

    @Override
    public void updateTask() {
        IBlockState state = targetState;
        if (state == null) {
            return;
        }
        float doorVecX = (float) (doorPosition.getX() + 0.5 - entity.posX);
        float doorVecZ = (float) (doorPosition.getZ() + 0.5 - entity.posZ);
        // The projection turns negative roughly when the mob has walked past the door
        if (initialDoorVecX * doorVecX + initialDoorVecZ * doorVecZ < 0.0F) {
            completed = true;
        }

        if (hitCounter == 0) {
            entity.swingArm(EnumHand.MAIN_HAND);
            boolean metal = state.getMaterial() == Material.IRON || state.getMaterial() == Material.ANVIL;
            entity.world.playEvent(metal ? BlockBreaking.EVENT_ATTACK_DOOR_IRON : BlockBreaking.EVENT_ATTACK_DOOR_WOOD,
                    doorPosition, 0);
        }
        if (++hitCounter >= 16) {
            hitCounter = 0;
        }

        ConfigSnapshot.DoorBreaking config = ModConfig.get().doorBreaking();
        float speed = breakSpeedOverride != null ? breakSpeedOverride : config.breakSpeed();
        blockDamage += BlockBreaking.damagePerTick(state, entity, entity.world, doorPosition) * speed;
        if (blockDamage >= 1.0F) {
            entity.world.destroyBlock(doorPosition, config.leaveDrops());
            entity.world.playEvent(BlockBreaking.EVENT_BREAK_DOOR_WOOD, doorPosition, 0);
            entity.swingArm(EnumHand.MAIN_HAND);
            blockDamage = 0.0F;
            completed = true;
        }

        int progress = BlockBreaking.progressStage(blockDamage);
        if (progress != lastProgress) {
            entity.world.sendBlockBreakProgress(entity.getEntityId(), doorPosition, progress);
            lastProgress = progress;
        }
    }

    private boolean findObstructingDoor(ConfigSnapshot.DoorBreaking config) {
        // Another mod may have swapped the navigator since this task was added
        if (!(entity.getNavigator() instanceof PathNavigateGround navigator)) {
            return false;
        }
        Path path = navigator.getPath();
        if (path == null || path.isFinished() || !navigator.getEnterDoors()) {
            return false;
        }
        int lastPoint = Math.min(path.getCurrentPathIndex() + 2, path.getCurrentPathLength());
        for (int i = 0; i < lastPoint; i++) {
            PathPoint point = path.getPathPointFromIndex(i);
            double dx = point.x - entity.posX;
            double dz = point.z - entity.posZ;
            if (dx * dx + dz * dz <= 2.25 && tryTargetDoor(config, new BlockPos(point.x, point.y, point.z))) {
                return true;
            }
        }
        return tryTargetDoor(config, new BlockPos(entity));
    }

    /** Targets the highest matching block in the column the mob's body would occupy at the given footing. */
    private boolean tryTargetDoor(ConfigSnapshot.DoorBreaking config, BlockPos footPos) {
        BlockTargetMap targets = targetsOverride != null ? targetsOverride : config.targetBlocks();
        boolean needsTool = (needsToolOverride != null ? needsToolOverride : config.requiresTools())
                && !isMadCreeper(config);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(footPos.getX(),
                footPos.getY() - 1 + (int) Math.ceil(entity.height), footPos.getZ());
        while (pos.getY() >= footPos.getY()) {
            if (entity.world.isBlockLoaded(pos)) {
                IBlockState state = entity.world.getBlockState(pos);
                if (targets.matches(state) && BlockBreaking.mayDestroy(state, entity, needsTool, entity.world, pos)) {
                    targetState = state;
                    doorPosition = pos.toImmutable();
                    return true;
                }
            }
            pos.setY(pos.getY() - 1);
        }
        return false;
    }

    private void clearBreakProgress() {
        if (lastProgress != BlockBreaking.NO_PROGRESS) {
            entity.world.sendBlockBreakProgress(entity.getEntityId(), doorPosition, BlockBreaking.NO_PROGRESS);
            lastProgress = BlockBreaking.NO_PROGRESS;
        }
    }

    private boolean isMadCreeper(ConfigSnapshot.DoorBreaking config) {
        return entity instanceof EntityCreeper && config.madCreepers();
    }

}
