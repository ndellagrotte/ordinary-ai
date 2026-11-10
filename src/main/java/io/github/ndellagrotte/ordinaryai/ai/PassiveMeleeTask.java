/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityRabbit;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.MathHelper;

/** Melee attack for mobs that have no attack of their own, such as farm animals and villagers. */
public final class PassiveMeleeTask extends EntityAIAttackMelee implements OrdinaryTask {

    public PassiveMeleeTask(EntityCreature mob, double speed, boolean longMemory) {
        super(mob, speed, longMemory);
    }

    @Override
    protected void checkAndPerformAttack(EntityLivingBase target, double distanceSq) {
        if (attacker instanceof EntityMob) {
            super.checkAndPerformAttack(target, distanceSq);
        } else if (distanceSq <= getAttackReachSqr(target) && attackTick <= 0) {
            attackTick = 20;
            attacker.swingArm(EnumHand.MAIN_HAND);
            attack(target);
        }
    }

    /** Stands in for {@code EntityMob.attackEntityAsMob}, which passive mobs do not implement. */
    private void attack(EntityLivingBase target) {
        float damage = baseDamage() +
            EnchantmentHelper.getModifierForCreature(attacker.getHeldItemMainhand(), target.getCreatureAttribute());
        if (!target.attackEntityFrom(DamageSource.causeMobDamage(attacker), damage)) {
            return;
        }

        int knockback = EnchantmentHelper.getKnockbackModifier(attacker);
        if (knockback > 0) {
            target.knockBack(attacker, knockback * 0.5F, MathHelper.sin(attacker.rotationYaw * 0.0175F),
                -MathHelper.cos(attacker.rotationYaw * 0.0175F)
            );
            attacker.motionX *= 0.6;
            attacker.motionZ *= 0.6;
        }

        int fire = EnchantmentHelper.getFireAspectModifier(attacker) * 4;
        if (attacker.isBurning()) {
            fire += 2;
        }
        if (fire > 0) {
            target.setFire(fire);
        }

        if (target instanceof EntityPlayer player) {
            tryDisableShield(player);
        }
        EnchantmentHelper.applyThornEnchantments(target, attacker);
        EnchantmentHelper.applyArthropodEnchantments(attacker, target);
    }

    private float baseDamage() {
        IAttributeInstance attribute = attacker.getAttributeMap()
            .getAttributeInstance(SharedMonsterAttributes.ATTACK_DAMAGE);
        if (attribute != null) {
            return (float) attribute.getAttributeValue();
        }
        if (attacker instanceof EntitySheep || attacker instanceof EntityRabbit) {
            return 2.0F;
        }
        if (attacker instanceof EntityChicken) {
            return 1.0F;
        }
        if (attacker instanceof EntityCow || attacker instanceof EntityHorse) {
            return 4.0F;
        }
        return 3.0F;
    }

    private void tryDisableShield(EntityPlayer player) {
        ItemStack weapon = attacker.getHeldItemMainhand();
        ItemStack shield = player.isHandActive() ? player.getActiveItemStack() : ItemStack.EMPTY;
        if (weapon.isEmpty() ||
            shield.isEmpty() ||
            !weapon.getItem().canDisableShield(weapon, shield, player, attacker) ||
            !shield.getItem().isShield(shield, player)) {
            return;
        }
        float chance = 0.25F + EnchantmentHelper.getEfficiencyModifier(attacker) * 0.05F;
        if (attacker.getRNG().nextFloat() < chance) {
            player.getCooldownTracker().setCooldown(shield.getItem(), 100);
            attacker.world.setEntityState(player, (byte) 30);
        }
    }

}
