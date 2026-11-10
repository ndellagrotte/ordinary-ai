/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai;

import io.github.ndellagrotte.ordinaryai.OrdinaryAI;
import io.github.ndellagrotte.ordinaryai.ai.elite.Elites;
import io.github.ndellagrotte.ordinaryai.ai.grief.BreakDoorTask;
import io.github.ndellagrotte.ordinaryai.ai.grief.EatBreedingItemTask;
import io.github.ndellagrotte.ordinaryai.ai.grief.IdleBlockTask;
import io.github.ndellagrotte.ordinaryai.ai.grief.MobFakePlayer;
import io.github.ndellagrotte.ordinaryai.ai.grief.ScanBudget;
import io.github.ndellagrotte.ordinaryai.ai.react.AvoidExplosionsTask;
import io.github.ndellagrotte.ordinaryai.ai.react.DodgeArrowsTask;
import io.github.ndellagrotte.ordinaryai.ai.react.FuseBeaconTask;
import io.github.ndellagrotte.ordinaryai.ai.react.ThreatTracker;
import io.github.ndellagrotte.ordinaryai.config.ConfigSnapshot;
import io.github.ndellagrotte.ordinaryai.config.MobList;
import io.github.ndellagrotte.ordinaryai.config.ModConfig;
import io.github.ndellagrotte.ordinaryai.village.DefendVillageTask;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBreakDoor;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;

/**
 * Gives mobs their extra AI as they enter the world.
 *
 * <p>What a mob gets is rolled once and stored in its NBT (see {@link MobData}), so it keeps the same behaviour for
 * life. Setting a mob up only looks at the mob itself: it never reads blocks, searches for entities or computes
 * paths, because mobs can join while their surroundings are still being generated.
 */
public final class AiInjector {

    private static final int DEPACIFY_NEUTRAL = 1;
    private static final int DEPACIFY_AGGRESSIVE = 2;
    private static final int THREAT_PRUNE_INTERVAL = 20;

    private int ticksSincePrune;

    private static void offerArrow(Entity arrow) {
        NBTTagCompound data = arrow.getEntityData();
        if (data.getBoolean(MobData.ARROW_CHECKED) || data.getBoolean(MobData.LEGACY_ARROW_CHECKED)) {
            return;
        }
        data.setBoolean(MobData.ARROW_CHECKED, true);
        DodgeArrowsTask.onArrowFired(arrow);
    }

    private static void setUp(EntityLiving mob, ConfigSnapshot config) {
        NBTTagCompound tag = MobData.of(mob);
        if (mob instanceof EntityCreature creature) {
            guarded("reactions", mob, () -> addReactions(creature, tag, config));
            guarded("combat", mob, () -> addCombat(creature, tag, config));
        }
        if (mob instanceof EntityCreeper creeper) {
            creeper.tasks.addTask(8, new FuseBeaconTask(creeper));
        }
        guarded("jockey", mob, () -> addRider(mob, tag, config));
        if (mob instanceof EntityAnimal animal) {
            animal.tasks.addTask(TaskUtil.idlePriority(animal), new EatBreedingItemTask(animal));
        }
        guarded("idle", mob, () -> addIdleBlockUse(mob, tag, config));
        guarded("door breaking", mob, () -> addDoorBreaking(mob, tag, config));
        guarded("elite", mob, () -> addElite(mob, tag, config));
    }

    /** Keeps a failure in one feature, most likely from an unusual modded mob, from costing the mob the others. */
    private static void guarded(String feature, EntityLiving mob, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException ex) {
            OrdinaryAI.LOGGER.warn("Could not add {} AI to '{}'", feature, EntityList.getKey(mob), ex);
        }
    }

    private static void addReactions(EntityCreature mob, NBTTagCompound tag, ConfigSnapshot config) {
        if (!MobData.hasNumber(tag, MobData.AVOID_EXPLOSIONS)) {
            tag.setBoolean(MobData.AVOID_EXPLOSIONS, config.reactions().avoidExplosions());
        }
        if (tag.getBoolean(MobData.AVOID_EXPLOSIONS)) {
            mob.tasks.addTask(-1, new AvoidExplosionsTask(mob));
        }

        if (!MobData.hasNumber(tag, MobData.DODGE_ARROWS)) {
            tag.setFloat(MobData.DODGE_ARROWS, (float) config.reactions().dodgeArrows());
        }
        float dodgeChance = tag.getFloat(MobData.DODGE_ARROWS);
        if (dodgeChance > 0.0F) {
            mob.tasks.addTask(-1, new DodgeArrowsTask(mob, dodgeChance));
        }
    }

    private static void addCombat(EntityCreature mob, NBTTagCompound tag, ConfigSnapshot config) {
        boolean hasMelee = false;

        if (!MobData.hasNumber(tag, MobData.DEFEND_VILLAGE)) {
            tag.setBoolean(MobData.DEFEND_VILLAGE, config.villages().villagersDefend() && mob instanceof EntityVillager
            );
        }
        if (tag.getBoolean(MobData.DEFEND_VILLAGE)) {
            mob.tasks.addTask(0, new PassiveMeleeTask(mob, 0.7, false));
            mob.targetTasks.addTask(0, new DefendVillageTask(mob));
            hasMelee = true;
        }

        if (!MobData.hasNumber(tag, MobData.DEPACIFY)) {
            int rolled = 0;
            if (config.general().depacifyList().roll(mob.getClass(), mob.getRNG())) {
                boolean aggressive = mob.getRNG().nextDouble() < config.general().depacifyAggressiveChance();
                rolled = aggressive ? DEPACIFY_AGGRESSIVE : DEPACIFY_NEUTRAL;
            }
            tag.setByte(MobData.DEPACIFY, (byte) rolled);
        }
        int depacify = tag.getByte(MobData.DEPACIFY);
        if (depacify >= DEPACIFY_NEUTRAL) {
            if (!hasMelee) {
                mob.tasks.addTask(0, new PassiveMeleeTask(mob, mob instanceof EntityChicken ? 1.8 : 1.4, false));
            }
            mob.targetTasks.addTask(0, new RetaliateTask(mob, config.reactions().callForHelp()));
            if (depacify >= DEPACIFY_AGGRESSIVE) {
                mob.targetTasks.addTask(1, new HuntPlayersTask(mob));
            }
        } else if (config.reactions().callForHelp()) {
            enableCallForHelp(mob);
        }
    }

    /** Switches on the vanilla "call nearby allies when hurt" behaviour of the mob's own retaliation task. */
    private static void enableCallForHelp(EntityCreature mob) {
        for (EntityAITasks.EntityAITaskEntry entry : mob.targetTasks.taskEntries) {
            // Subclasses have their own ideas about who to alert, so only the plain task is touched
            if (entry.action.getClass() == EntityAIHurtByTarget.class) {
                ((EntityAIHurtByTarget) entry.action).entityCallsForHelp = true;
                return;
            }
        }
    }

    private static void addRider(EntityLiving mob, NBTTagCompound tag, ConfigSnapshot config) {
        boolean small = config.jockeys().riderListSmall().contains(mob.getClass());
        if (!MobData.hasNumber(tag, MobData.RIDER)) {
            MobList riders = small ? config.jockeys().riderListSmall() : config.jockeys().riderList();
            tag.setBoolean(MobData.RIDER, riders.roll(mob.getClass(), mob.getRNG()));
        }
        if (tag.getBoolean(MobData.RIDER)) {
            mob.tasks.addTask(TaskUtil.idlePriority(mob), new MountSeekTask(mob, small));
        }
    }

    private static void addIdleBlockUse(EntityLiving mob, NBTTagCompound tag, ConfigSnapshot config) {
        if (!MobData.hasNumber(tag, MobData.GRIEFING)) {
            tag.setBoolean(MobData.GRIEFING, config.griefing().mobList().roll(mob.getClass(), mob.getRNG()));
        }
        if (!MobData.hasNumber(tag, MobData.FIDDLING)) {
            tag.setBoolean(MobData.FIDDLING, config.fiddling().mobList().roll(mob.getClass(), mob.getRNG()));
        }
        boolean griefs = tag.getBoolean(MobData.GRIEFING);
        boolean fiddles = tag.getBoolean(MobData.FIDDLING);
        if (griefs || fiddles) {
            // Whether each behaviour is switched on is checked when the task runs, so the config can change it live
            mob.tasks.addTask(TaskUtil.idlePriority(mob), new IdleBlockTask(mob, griefs, fiddles, tag));
        }
    }

    private static void addDoorBreaking(EntityLiving mob, NBTTagCompound tag, ConfigSnapshot config) {
        if (!MobData.hasNumber(tag, MobData.DOOR_BREAKING)) {
            tag.setBoolean(MobData.DOOR_BREAKING, config.doorBreaking().mobList().roll(mob.getClass(), mob.getRNG()));
        }
        if (!config.doorBreaking().enabled() || !tag.getBoolean(MobData.DOOR_BREAKING)) {
            return;
        }
        if (!(mob.getNavigator() instanceof PathNavigateGround navigator)) {
            if (config.general().debugMode()) {
                OrdinaryAI.LOGGER.info("Skipping door-breaking AI for '{}': it does not walk on the ground",
                    EntityList.getKey(mob)
                );
            }
            return;
        }
        // Replace the vanilla task rather than run alongside it
        EntityAITasks.EntityAITaskEntry[] entries = mob.tasks.taskEntries.toArray(new EntityAITasks.EntityAITaskEntry[0]
        );
        for (EntityAITasks.EntityAITaskEntry entry : entries) {
            if (entry.action instanceof EntityAIBreakDoor) {
                mob.tasks.removeTask(entry.action);
            }
        }
        navigator.setBreakDoors(true);
        mob.tasks.addTask(1, new BreakDoorTask(mob, tag));
    }

    private static void addElite(EntityLiving mob, NBTTagCompound tag, ConfigSnapshot config) {
        NBTTagCompound eliteTag;
        if (tag.hasKey(MobData.ELITE_AI, Constants.NBT.TAG_COMPOUND)) {
            eliteTag = tag.getCompoundTag(MobData.ELITE_AI);
        } else {
            eliteTag = new NBTTagCompound();
            tag.setTag(MobData.ELITE_AI, eliteTag);
            Elites.roll(mob, eliteTag, config.elite());
            // First time through: hand out gear, unless the tag says otherwise
            if (!MobData.hasNumber(tag, MobData.FORCE_INIT)) {
                tag.setBoolean(MobData.FORCE_INIT, true);
            }
        }
        boolean equip = tag.getBoolean(MobData.FORCE_INIT);
        tag.removeTag(MobData.FORCE_INIT);
        Elites.attach(mob, eliteTag, equip, config.elite());
    }

    /** Sends idle mobs of a related type after whatever killed one of their own. */
    private static void callForHelpOnDeath(EntityLiving mob, Entity killer, double chance) {
        if (chance <= 0.0 || !(killer instanceof EntityLivingBase attacker) || mob.getRNG().nextDouble() >= chance) {
            return;
        }
        if (attacker instanceof EntityPlayer player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        double range = mob.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue();
        AxisAlignedBB area = new AxisAlignedBB(mob.posX, mob.posY, mob.posZ, mob.posX + 1.0, mob.posY + 1.0,
            mob.posZ + 1.0
        ).grow(range, 10.0, range);
        Class<?> type = mob.getClass();
        List<EntityLiving> allies = mob.world.getEntitiesWithinAABB(EntityLiving.class, area,
            ally -> ally != mob &&
                ally.getAttackTarget() == null &&
                (ally.getClass().isAssignableFrom(type) || type.isAssignableFrom(ally.getClass())) &&
                !ally.isOnSameTeam(attacker)
        );
        for (EntityLiving ally : allies) {
            ally.setAttackTarget(attacker);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        EndOfTickQueue.drain();
        if (++ticksSincePrune >= THREAT_PRUNE_INTERVAL) {
            ticksSincePrune = 0;
            ThreatTracker.pruneAll();
        }
        if (ModConfig.isLoaded()) {
            ScanBudget.refill(ModConfig.get().idle().scanCountGlobal());
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            return;
        }
        ThreatTracker.forget(event.getWorld());
        MobFakePlayer.forget(event.getWorld());
    }

    // Lowest priority so that tasks other mods add or remove on join are already in place
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        World world = event.getWorld();
        if (world.isRemote || !ModConfig.isLoaded()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof EntityArrow) {
            offerArrow(entity);
        } else if (ThreatTracker.isExplosive(entity)) {
            ThreatTracker.of(world).add(entity);
        } else if (entity instanceof EntityLiving mob && !TaskUtil.isSetUp(mob)) {
            // The same entity can join more than once (Forge keeps unloaded chunks around for a while and revives
            // them whole); a mob that already carries our tasks must not get a second set
            setUp(mob, ModConfig.get());
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().world.isRemote || !(event.getEntityLiving() instanceof EntityLiving mob)) {
            return;
        }
        // Tasks are not reset when their owner dies, so let them tidy up
        for (EntityAITasks.EntityAITaskEntry entry : mob.tasks.taskEntries) {
            if (entry.action instanceof OrdinaryTask task) {
                task.onOwnerDeath();
            }
        }
        if (ModConfig.isLoaded()) {
            callForHelpOnDeath(mob, event.getSource().getTrueSource(), ModConfig.get().reactions().callForHelpOnDeath()
            );
        }
    }

    /** Retaliation for mobs that are passive in vanilla. */
    private static final class RetaliateTask extends EntityAIHurtByTarget implements OrdinaryTask {

        RetaliateTask(EntityCreature mob, boolean callsForHelp) {
            super(mob, callsForHelp);
        }

    }

    /** Makes a normally passive mob attack players on sight. */
    private static final class HuntPlayersTask extends EntityAINearestAttackableTarget<
            EntityPlayer> implements OrdinaryTask {

        HuntPlayersTask(EntityCreature mob) {
            super(mob, EntityPlayer.class, true);
        }

    }

}
