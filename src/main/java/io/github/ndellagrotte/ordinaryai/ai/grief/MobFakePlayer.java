/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.grief;

import org.jspecify.annotations.Nullable;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.FoodStats;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * A stand-in player that lets a mob use blocks through the normal right-click path. Anything the block does to the
 * player (damage, potion effects, food) is passed on to the mob.
 *
 * <p>One instance is kept per world and lent to a mob for the length of a single interaction.
 */
public final class MobFakePlayer extends FakePlayer {

    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("5c1f6f0a-6b0d-4d1e-9a36-0b6f2f7a3c41"),
            "[OrdinaryAI]");
    private static final Map<World, MobFakePlayer> BY_WORLD = new WeakHashMap<>();

    private @Nullable EntityLiving mob;

    private MobFakePlayer(WorldServer world) {
        super(world, PROFILE);
        foodStats = new MobFoodStats();
    }

    /** Returns the world's fake player, acting for the given mob until {@link #release()} is called. */
    public static MobFakePlayer lend(WorldServer world, EntityLiving mob) {
        MobFakePlayer player = BY_WORLD.computeIfAbsent(world, _ -> new MobFakePlayer(world));
        player.mob = mob;
        player.copyLocationAndAnglesFrom(mob);
        player.motionX = mob.motionX;
        player.motionY = mob.motionY;
        player.motionZ = mob.motionZ;
        return player;
    }

    /** Drops the fake player of a world that is being unloaded. */
    public static void forget(World world) {
        BY_WORLD.remove(world);
    }

    /** Ends the interaction, moving the mob if the block moved the player. */
    public void release() {
        EntityLiving actor = mob;
        if (actor != null) {
            actor.copyLocationAndAnglesFrom(this);
            actor.motionX = motionX;
            actor.motionY = motionY;
            actor.motionZ = motionZ;
        }
        mob = null;
    }

    @Override
    public void heal(float amount) {
        if (mob != null) {
            mob.heal(amount);
        }
    }

    @Override
    public void setHealth(float amount) {
        if (mob != null) {
            mob.setHealth(amount);
        }
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float damage) {
        return mob != null && mob.attackEntityFrom(source, damage);
    }

    @Override
    public void knockBack(Entity attacker, float force, double ratioX, double ratioZ) {
        if (mob != null) {
            mob.knockBack(attacker, force, ratioX, ratioZ);
        }
    }

    @Override
    public boolean isEntityAlive() {
        return mob == null || mob.isEntityAlive();
    }

    @Override
    public Collection<PotionEffect> getActivePotionEffects() {
        return mob != null ? mob.getActivePotionEffects() : super.getActivePotionEffects();
    }

    @Override
    public boolean isPotionActive(Potion potion) {
        return mob != null && mob.isPotionActive(potion);
    }

    @Override
    public @Nullable
    PotionEffect getActivePotionEffect(Potion potion) {
        return mob != null ? mob.getActivePotionEffect(potion) : null;
    }

    @Override
    public void addPotionEffect(PotionEffect effect) {
        if (mob != null) {
            mob.addPotionEffect(effect);
        }
    }

    @Override
    public boolean isPotionApplicable(PotionEffect effect) {
        return mob != null && mob.isPotionApplicable(effect);
    }

    @Override
    public void removePotionEffect(Potion potion) {
        if (mob != null) {
            mob.removePotionEffect(potion);
        }
    }

    @Override
    public void curePotionEffects(ItemStack curativeItem) {
        if (mob != null) {
            mob.curePotionEffects(curativeItem);
        }
    }

    @Override
    public boolean isEntityUndead() {
        return mob != null && mob.isEntityUndead();
    }

    @Override
    public ItemStack getHeldItem(EnumHand hand) {
        return mob != null ? mob.getHeldItem(hand) : ItemStack.EMPTY;
    }

    @Override
    public Iterable<ItemStack> getHeldEquipment() {
        return mob != null ? mob.getHeldEquipment() : super.getHeldEquipment();
    }

    @Override
    public Iterable<ItemStack> getArmorInventoryList() {
        return mob != null ? mob.getArmorInventoryList() : super.getArmorInventoryList();
    }

    @Override
    public void setItemStackToSlot(EntityEquipmentSlot slot, ItemStack stack) {
        if (mob != null) {
            mob.setItemStackToSlot(slot, stack);
        }
    }

    @Override
    public ItemStack getItemStackFromSlot(EntityEquipmentSlot slot) {
        return mob != null ? mob.getItemStackFromSlot(slot) : ItemStack.EMPTY;
    }

    @Override
    public IAttributeInstance getEntityAttribute(IAttribute attribute) {
        return mob != null ? mob.getEntityAttribute(attribute) : super.getEntityAttribute(attribute);
    }

    @Override
    public AbstractAttributeMap getAttributeMap() {
        return mob != null ? mob.getAttributeMap() : super.getAttributeMap();
    }

    @Override
    public boolean isEntityInvulnerable(DamageSource source) {
        return mob == null || mob.isEntityInvulnerable(source);
    }

    @Override
    public @Nullable
    Entity changeDimension(int dimension) {
        // The stand-in must stay in its own world; let the mob travel instead
        return mob != null ? mob.changeDimension(dimension) : this;
    }

    @Override
    public EnumCreatureAttribute getCreatureAttribute() {
        return mob != null ? mob.getCreatureAttribute() : super.getCreatureAttribute();
    }

    /** Turns food eaten by the stand-in (cake, for one) into healing for the mob. */
    private final class MobFoodStats extends FoodStats {

        @Override
        public void addStats(int food, float saturationModifier) {
            if (mob != null) {
                mob.heal(Math.max(food, 1.0F));
            }
        }

        @Override
        public int getFoodLevel() {
            return 10;
        }

        @Override
        public boolean needFood() {
            return true;
        }

        @Override
        public float getSaturationLevel() {
            return 10.0F;
        }

    }

}
