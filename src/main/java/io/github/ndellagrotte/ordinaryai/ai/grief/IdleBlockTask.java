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

import net.minecraft.block.BlockFire;
import net.minecraft.block.BlockOre;
import net.minecraft.block.BlockTNT;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.loot.ILootContainer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.Random;

/**
 * What a mob does with nearby blocks while it has nothing better to do: break them (griefing) or use them
 * (fiddling). It samples random blocks around itself, walks to one it likes, and acts once it has a clear view.
 */
public final class IdleBlockTask extends EntityAIBase implements OrdinaryTask {

    private static final int GIVE_UP_AFTER = 400;
    private static final int REPATH_INTERVAL = 12;
    private static final double REACH_MARGIN = 1.5;

    private final EntityLiving mob;
    private final Random random;
    private final boolean griefs;
    private final boolean fiddles;

    // Per-mob overrides read from NBT. Null (or negative for ranges) means the config value is used.
    private final int rangeOverride;
    private final int rangeVerticalOverride;
    private final @Nullable Float breakSpeedOverride;
    private final @Nullable Boolean needsToolOverride;
    private final @Nullable Boolean breakLightsOverride;
    private final @Nullable BlockTargetMap griefTargetsOverride;
    private final @Nullable BlockTargetMap griefLootableOverride;
    private final @Nullable BlockTargetMap griefBlacklistOverride;
    private final @Nullable BlockTargetMap fiddleTargetsOverride;
    private final @Nullable BlockTargetMap fiddleBlacklistOverride;

    private Activity activity = Activity.NONE;
    private @Nullable BlockPos targetPos;
    private @Nullable IBlockState targetState;

    private long nextScanAt;
    private long fiddleReadyAt;
    private long giveUpAt;
    private long nextSightCheckAt;
    private long nextRepathAt;
    private boolean canSee;

    private int hitCounter;
    private float blockDamage;
    private int lastProgress = BlockBreaking.NO_PROGRESS;

    public IdleBlockTask(EntityLiving mob, boolean griefs, boolean fiddles, NBTTagCompound tag) {
        this.mob = mob;
        this.random = mob.getRNG();
        this.griefs = griefs;
        this.fiddles = fiddles;
        setMutexBits(TaskUtil.MUTEX_MOVEMENT | TaskUtil.MUTEX_FACING);

        rangeOverride = tag.hasKey(MobData.IDLE_RANGE_XZ) ? Math.max(1, tag.getByte(MobData.IDLE_RANGE_XZ)) : -1;
        rangeVerticalOverride = tag.hasKey(MobData.IDLE_RANGE_Y) ? Math.max(1, tag.getByte(MobData.IDLE_RANGE_Y)) : -1;
        breakSpeedOverride = tag.hasKey(MobData.GRIEF_BREAK_SPEED) ? tag.getFloat(MobData.GRIEF_BREAK_SPEED) : null;
        needsToolOverride = tag.hasKey(MobData.GRIEF_NEEDS_TOOL) ? tag.getBoolean(MobData.GRIEF_NEEDS_TOOL) : null;
        breakLightsOverride = tag.hasKey(MobData.GRIEF_LIGHTS) ? tag.getBoolean(MobData.GRIEF_LIGHTS) : null;
        griefTargetsOverride = blockOverride(tag, MobData.GRIEF_BLOCKS);
        griefLootableOverride = blockOverride(tag, MobData.GRIEF_LOOTABLE);
        griefBlacklistOverride = blockOverride(tag, MobData.GRIEF_BLACKLIST);
        fiddleTargetsOverride = blockOverride(tag, MobData.FIDDLE_BLOCKS);
        fiddleBlacklistOverride = blockOverride(tag, MobData.FIDDLE_BLACKLIST);

        // Spread scans out so mobs that load together do not all scan on the same tick
        nextScanAt = TaskUtil.now(mob) + random.nextInt(TaskUtil.EVALUATION_INTERVAL * 4);
    }

    private static @Nullable
    BlockTargetMap blockOverride(NBTTagCompound tag, String key) {
        return tag.hasKey(key) ? BlockTargetMap.parseLine(tag.getString(key), OrdinaryAI.LOGGER::warn) : null;
    }

    @Override
    public boolean shouldExecute() {
        if (mob.isRiding()) {
            return false;
        }
        long now = TaskUtil.now(mob);
        if (now < nextScanAt) {
            return false;
        }
        ConfigSnapshot config = ModConfig.get();
        nextScanAt = now + (long) config.idle().scanDelay() * TaskUtil.EVALUATION_INTERVAL;

        boolean mayGrief = griefs && config.griefing().enabled();
        boolean mayFiddle = fiddles && config.fiddling().enabled() && now >= fiddleReadyAt;
        if (!mayGrief && !mayFiddle || !ForgeEventFactory.getMobGriefingEvent(mob.world, mob)) {
            return false;
        }
        return scan(config, mayGrief, mayFiddle);
    }

    @Override
    public boolean shouldContinueExecuting() {
        BlockPos pos = targetPos;
        IBlockState state = targetState;
        if (activity == Activity.NONE || pos == null || state == null || mob.isRiding()) {
            return false;
        }
        boolean committed = activity == Activity.GRIEFING && blockDamage > 0.0F;
        if (!committed && TaskUtil.now(mob) > giveUpAt) {
            return false;
        }
        return mob.world.isBlockLoaded(pos) && mob.world.getBlockState(pos).getBlock() == state.getBlock();
    }

    @Override
    public boolean isInterruptible() {
        return activity == Activity.GRIEFING && !isMadCreeper() || blockDamage == 0.0F;
    }

    @Override
    public void startExecuting() {
        long now = TaskUtil.now(mob);
        giveUpAt = now + GIVE_UP_AFTER;
        nextSightCheckAt = now;
        nextRepathAt = now + REPATH_INTERVAL;
        canSee = false;
        hitCounter = 0;
        blockDamage = 0.0F;
        lastProgress = BlockBreaking.NO_PROGRESS;
        moveToTarget();
    }

    @Override
    public void resetTask() {
        if (!mob.getNavigator().noPath()) {
            mob.getNavigator().clearPath();
        }
        finish();
        blockDamage = 0.0F;
        targetState = null;
        targetPos = null;
    }

    @Override
    public void onOwnerDeath() {
        clearBreakProgress();
    }

    @Override
    public void updateTask() {
        BlockPos pos = targetPos;
        IBlockState state = targetState;
        if (activity == Activity.NONE || pos == null || state == null) {
            return;
        }
        mob.getLookHelper().setLookPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30.0F, 30.0F);

        if (!canSee) {
            approach();
        } else if (!isWithinReach(pos)) {
            // Pushed or carried away from the block; start over rather than keep working from afar
            mob.getNavigator().clearPath();
            finish();
        } else if (activity == Activity.GRIEFING) {
            grief(pos, state);
        } else {
            fiddle(pos, state);
        }
    }

    /** Samples random nearby blocks, without loading chunks, until one is worth targeting or the allowance runs out. */
    private boolean scan(ConfigSnapshot config, boolean mayGrief, boolean mayFiddle) {
        ConfigSnapshot.IdleActivities idle = config.idle();
        int range = rangeOverride > 0 ? rangeOverride : idle.range();
        int rangeVertical = rangeVerticalOverride > 0 ? rangeVerticalOverride : idle.rangeVertical();
        int originX = (int) Math.floor(mob.posX);
        int originY = (int) Math.floor(mob.posY);
        int originZ = (int) Math.floor(mob.posZ);

        World world = mob.world;
        IChunkProvider chunks = world.getChunkProvider();
        Chunk chunk = null;
        boolean chunkKnown = false;
        int chunkX = 0;
        int chunkZ = 0;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < idle.scanCount() && ScanBudget.tryTake(idle.scanCountGlobal()); i++) {
            pos.setPos(originX + random.nextInt(range) - random.nextInt(range),
                    originY + random.nextInt(rangeVertical) - random.nextInt(rangeVertical),
                    originZ + random.nextInt(range) - random.nextInt(range));
            if (world.isOutsideBuildHeight(pos)) {
                continue;
            }
            int x = pos.getX() >> 4;
            int z = pos.getZ() >> 4;
            if (!chunkKnown || x != chunkX || z != chunkZ) {
                chunk = chunks.getLoadedChunk(x, z);
                chunkKnown = true;
                chunkX = x;
                chunkZ = z;
            }
            if (chunk == null) {
                continue;
            }
            IBlockState state = chunk.getBlockState(pos);
            if (mayGrief && tryTargetForGriefing(config, state, pos)
                    || mayFiddle && tryTargetForFiddling(config, state, pos)) {
                return true;
            }
        }
        return false;
    }

    /** Ends the current activity, undoing anything it left in the world. Safe to call more than once. */
    private void finish() {
        if (activity != Activity.NONE) {
            // Take a breather before looking for the next block: longer after getting to one, shorter after giving up
            long now = TaskUtil.now(mob);
            long pause = canSee ? 13L + random.nextInt(5) : Math.max(0L, nextSightCheckAt - now);
            nextScanAt = Math.max(nextScanAt, now + pause * TaskUtil.EVALUATION_INTERVAL);
        }
        if (activity == Activity.GRIEFING) {
            if (isMadCreeper()) {
                ((EntityCreeper) mob).setCreeperState(-1);
            }
            clearBreakProgress();
        } else if (activity == Activity.FIDDLING) {
            fiddleReadyAt = TaskUtil.now(mob) + (80L + random.nextInt(81)) * TaskUtil.EVALUATION_INTERVAL;
        }
        activity = Activity.NONE;
    }

    private void clearBreakProgress() {
        if (lastProgress != BlockBreaking.NO_PROGRESS && targetPos != null) {
            mob.world.sendBlockBreakProgress(mob.getEntityId(), targetPos, BlockBreaking.NO_PROGRESS);
        }
        lastProgress = BlockBreaking.NO_PROGRESS;
    }

    private void approach() {
        long now = TaskUtil.now(mob);
        if (now >= nextSightCheckAt) {
            nextSightCheckAt = now + 10 + random.nextInt(5);
            if (checkSight()) {
                nextSightCheckAt += 3;
            }
        }
        if (canSee) {
            return;
        }
        if (now > giveUpAt) {
            mob.getNavigator().clearPath();
            finish();
        } else if (mob.getNavigator().noPath() && now >= nextRepathAt) {
            nextRepathAt = now + REPATH_INTERVAL;
            moveToTarget();
        }
    }

    private void moveToTarget() {
        if (targetPos != null) {
            mob.getNavigator().setPath(mob.getNavigator().getPathToPos(targetPos), 1.0);
        }
    }

    private void grief(BlockPos pos, IBlockState state) {
        if (!mob.getNavigator().noPath()) {
            mob.getNavigator().clearPath();
        }
        ConfigSnapshot.Griefing config = ModConfig.get().griefing();
        if (isMadCreeper()) {
            ((EntityCreeper) mob).setCreeperState(1);
            blockDamage = 1.0F;
            return;
        }

        World world = mob.world;
        if (hitCounter == 0) {
            mob.swingArm(EnumHand.MAIN_HAND);
            SoundType sound = state.getBlock().getSoundType(state, world, pos, mob);
            world.playSound(null, pos, sound.getBreakSound(), mob.getSoundCategory(), sound.getVolume(),
                    sound.getPitch() * 0.8F);
        }
        if (++hitCounter >= 5) {
            hitCounter = 0;
        }

        float speed = breakSpeedOverride != null ? breakSpeedOverride : config.breakSpeed();
        blockDamage += BlockBreaking.damagePerTick(state, mob, world, pos) * speed;
        if (blockDamage >= 1.0F) {
            if (state.getBlock() == Blocks.FARMLAND) {
                world.setBlockState(pos, Blocks.DIRT.getDefaultState(), 3);
            } else {
                world.destroyBlock(pos, config.leaveDrops());
                if (config.breakSound()) {
                    world.playEvent(BlockBreaking.EVENT_BREAK_DOOR_WOOD, pos, 0);
                }
                mob.swingArm(EnumHand.MAIN_HAND);
            }
            blockDamage = 0.0F;
            finish();
            return;
        }

        int progress = BlockBreaking.progressStage(blockDamage);
        if (progress != lastProgress) {
            world.sendBlockBreakProgress(mob.getEntityId(), pos, progress);
            lastProgress = progress;
        }
    }

    private void fiddle(BlockPos pos, IBlockState state) {
        if (mob.world instanceof WorldServer world) {
            MobFakePlayer player = MobFakePlayer.lend(world, mob);
            try {
                use(world, pos, state, player);
            } catch (RuntimeException ex) {
                OrdinaryAI.LOGGER.warn("Failed to fiddle with block '{}'", state.getBlock().getRegistryName(), ex);
            } finally {
                player.release();
            }
        }
        mob.swingArm(EnumHand.MAIN_HAND);
        mob.getNavigator().clearPath();
        finish();
    }

    private void use(WorldServer world, BlockPos pos, IBlockState state, MobFakePlayer player) {
        // Give protection mods the same chance to refuse that they get for a real player
        PlayerInteractEvent.RightClickBlock click = ForgeHooks.onRightClickBlock(player, EnumHand.MAIN_HAND, pos,
                EnumFacing.DOWN, new Vec3d(0.5, 0.5, 0.5));
        if (click.isCanceled() || click.getUseBlock() == Event.Result.DENY) {
            return;
        }
        if (state.getBlock() instanceof BlockTNT tnt) {
            tnt.explode(world, pos, state.withProperty(BlockTNT.EXPLODE, true), mob);
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 11);
        } else {
            state.getBlock()
                    .onBlockActivated(world, pos, state, player, EnumHand.MAIN_HAND, EnumFacing.DOWN, 0.5F, 0.5F, 0.5F);
        }
    }

    private boolean isWithinReach(BlockPos pos) {
        double limit = ModConfig.get().idle().reach() + REACH_MARGIN;
        return mob.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5 - mob.getEyeHeight(), pos.getZ() + 0.5)
                <= limit * limit;
    }

    /**
     * Looks for a clear line to the target block once the mob is within reach.
     *
     * @return true if the mob was close enough for the (costly) ray traces to be made
     */
    private boolean checkSight() {
        BlockPos pos = targetPos;
        if (pos == null) {
            return false;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        float reach = ModConfig.get().idle().reach();
        if (mob.getDistanceSq(x, y - mob.getEyeHeight(), z) > reach * reach) {
            return false;
        }
        // Aim at the three faces of the block that are turned towards the mob
        Vec3d eyes = new Vec3d(mob.posX, mob.posY + mob.getEyeHeight(), mob.posZ);
        if (hasClearLine(eyes, x, y + (mob.posY > y ? 0.5 : -0.5), z)
                || hasClearLine(eyes, x + (mob.posX > x ? 0.5 : -0.5), y, z)
                || hasClearLine(eyes, x, y, z + (mob.posZ > z ? 0.5 : -0.5))) {
            canSee = true;
        }
        return true;
    }

    private boolean hasClearLine(Vec3d eyes, double x, double y, double z) {
        RayTraceResult hit = mob.world.rayTraceBlocks(eyes, new Vec3d(x, y, z));
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return true;
        }
        BlockPos hitPos = hit.getBlockPos();
        if (hitPos.equals(targetPos)) {
            return true;
        }
        // Something is in the way. If it is also worth acting on, switch to it.
        ConfigSnapshot config = ModConfig.get();
        IBlockState blocking = mob.world.getBlockState(hitPos);
        return activity == Activity.GRIEFING && tryTargetForGriefing(config, blocking, hitPos)
                || activity == Activity.FIDDLING && tryTargetForFiddling(config, blocking, hitPos);
    }

    private boolean tryTargetForGriefing(ConfigSnapshot config, IBlockState state, BlockPos pos) {
        if (!isGriefTarget(config.griefing(), state, pos)) {
            return false;
        }
        setTarget(Activity.GRIEFING, state, pos);
        return true;
    }

    private boolean tryTargetForFiddling(ConfigSnapshot config, IBlockState state, BlockPos pos) {
        ConfigSnapshot.Fiddling fiddling = config.fiddling();
        BlockTargetMap blacklist = fiddleBlacklistOverride != null
                ? fiddleBlacklistOverride
                : fiddling.targetBlacklist();
        BlockTargetMap targets = fiddleTargetsOverride != null ? fiddleTargetsOverride : fiddling.targetBlocks();
        if (state.getBlock() == Blocks.AIR || blacklist.matches(state) || !targets.matches(state)) {
            return false;
        }
        setTarget(Activity.FIDDLING, state, pos);
        return true;
    }

    private void setTarget(Activity newActivity, IBlockState state, BlockPos pos) {
        // Retargeting mid-task must not leave a crack overlay on the block being abandoned
        clearBreakProgress();
        activity = newActivity;
        targetPos = pos.toImmutable();
        targetState = state;
    }

    private boolean isGriefTarget(ConfigSnapshot.Griefing config, IBlockState state, BlockPos pos) {
        if (state.getBlock() == Blocks.AIR) {
            return false;
        }
        BlockTargetMap blacklist = griefBlacklistOverride != null ? griefBlacklistOverride : config.targetBlacklist();
        if (blacklist.matches(state)) {
            return false;
        }
        BlockTargetMap targets = griefTargetsOverride != null ? griefTargetsOverride : config.targetBlocks();
        BlockTargetMap lootable = griefLootableOverride != null ? griefLootableOverride : config.targetLootable();
        boolean breakLights = breakLightsOverride != null ? breakLightsOverride : config.breakLights();
        if (!(breakLights && isUnnaturalLight(state, pos)
                || targets.matches(state)
                || lootable.matches(state) && isLootedContainer(pos))) {
            return false;
        }
        boolean needsTool = needsToolOverride != null ? needsToolOverride : config.requiresTools();
        return BlockBreaking.mayDestroy(state, mob, needsTool && !isMadCreeper(), mob.world, pos);
    }

    private boolean isUnnaturalLight(IBlockState state, BlockPos pos) {
        return state.getLightValue(mob.world, pos) > 1
                && !(state.getBlock() instanceof BlockFire)
                && state.getBlock() != Blocks.LIT_REDSTONE_ORE
                && !(state.getBlock() instanceof BlockOre)
                && state.getBlock() != Blocks.MAGMA;
    }

    /** Containers that still hold an unrolled loot table are left alone, so dungeon chests survive until opened. */
    private boolean isLootedContainer(BlockPos pos) {
        TileEntity container = mob.world.getTileEntity(pos);
        return container instanceof ILootContainer loot && loot.getLootTable() == null;
    }

    private boolean isMadCreeper() {
        return mob instanceof EntityCreeper && ModConfig.get().griefing().madCreepers();
    }

    private enum Activity {

        NONE,
        GRIEFING,
        FIDDLING

    }

}
