/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.elite;

import io.github.ndellagrotte.ordinaryai.OrdinaryAI;
import io.github.ndellagrotte.ordinaryai.ai.OrdinaryTask;
import io.github.ndellagrotte.ordinaryai.ai.TaskUtil;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

import java.util.UUID;

/** Shared base for elite AI tasks. */
abstract class EliteTask extends EntityAIBase implements OrdinaryTask {

    /** Attribute modifier operations. */
    static final int ADD = 0;
    static final int MULTIPLY_BASE = 1;

    protected final EntityLiving mob;

    protected EliteTask(EntityLiving mob, int mutexBits) {
        this.mob = mob;
        setMutexBits(mutexBits);
    }

    /** Adds a permanent attribute modifier to a mob, if it has the attribute at all. */
    static void modify(EntityLiving mob, IAttribute attribute, String name, double amount, int operation) {
        IAttributeInstance instance = mob.getAttributeMap().getAttributeInstance(attribute);
        if (instance != null) {
            instance.applyModifier(new AttributeModifier(UUID.randomUUID(), name, amount, operation));
        }
    }

    /** Adds an attribute modifier to an item, active in the slot the item is normally worn in. */
    static void modifyItem(ItemStack stack, IAttribute attribute, double amount, int operation) {
        String name = attribute.getName();
        stack.addAttributeModifier(name, new AttributeModifier(OrdinaryAI.MOD_ID + ":" + name, amount, operation),
                EntityLiving.getSlotForItemStack(stack));
    }

    /** Creates a named, dyed piece of leather armour. */
    static ItemStack dyedLeather(ItemArmor item, String name, int color) {
        ItemStack stack = new ItemStack(item);
        stack.setStackDisplayName(name);
        item.setColor(stack, color);
        return stack;
    }

    /** Launches an entity and makes sure a player on the receiving end is told about it. */
    static void launch(Entity entity, double motionX, double motionY, double motionZ) {
        entity.motionX = motionX;
        entity.motionY = motionY;
        entity.motionZ = motionZ;
        entity.onGround = false;
        entity.fallDistance = 0.0F;
        entity.velocityChanged = true;
    }

    protected final long now() {
        return TaskUtil.now(mob);
    }

}
