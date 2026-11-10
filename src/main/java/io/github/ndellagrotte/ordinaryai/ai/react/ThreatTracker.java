/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.react;

import org.jspecify.annotations.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.projectile.EntityDragonFireball;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps a short per-world list of things that are about to explode, so that mobs can check for danger without each
 * of them searching the world. Explosives are added as they appear; creepers add themselves while their fuse burns.
 * Only used from the server thread.
 */
public final class ThreatTracker {

    private static final Map<World, ThreatTracker> BY_WORLD = new WeakHashMap<>();

    private final List<Entity> threats = new ArrayList<>();

    private ThreatTracker() { }

    /** Returns the tracker for a world. */
    public static ThreatTracker of(World world) {
        return BY_WORLD.computeIfAbsent(world, _ -> new ThreatTracker());
    }

    /** Forgets a world that is being unloaded. */
    public static void forget(World world) {
        BY_WORLD.remove(world);
    }

    /** Returns true for entities that are dangerous from the moment they exist. */
    public static boolean isExplosive(Entity entity) {
        return entity instanceof EntityTNTPrimed ||
            entity instanceof EntityLargeFireball ||
            entity instanceof EntityDragonFireball ||
            entity instanceof EntityWitherSkull;
    }

    private static boolean isStillDangerous(Entity threat) {
        if (threat.isDead || !threat.isAddedToWorld()) {
            return false;
        }
        return !(threat instanceof EntityCreeper creeper) || creeper.getCreeperState() > 0;
    }

    /**
     * Drops threats that are over from every world. Mobs do this as they look for danger, but nothing guarantees
     * that any mob is looking.
     */
    public static void pruneAll() {
        for (ThreatTracker tracker : BY_WORLD.values()) {
            tracker.threats.removeIf(threat -> !isStillDangerous(threat));
        }
    }

    /** Starts tracking an entity. Adding one that is already tracked does nothing. */
    public void add(Entity threat) {
        for (Entity tracked : threats) {
            if (tracked == threat) {
                return;
            }
        }
        threats.add(threat);
    }

    /** Returns true if nothing is being tracked, which is the common case. */
    public boolean isEmpty() {
        return threats.isEmpty();
    }

    /**
     * Returns a live threat inside the area other than the one asking, or null. Threats that are over are dropped
     * along the way.
     */
    public @Nullable
    Entity findWithin(AxisAlignedBB area, Entity asking) {
        Entity found = null;
        for (int i = threats.size(); i-- > 0;) {
            Entity threat = threats.get(i);
            if (!isStillDangerous(threat)) {
                threats.remove(i);
            } else if (found == null && threat != asking && area.intersects(threat.getEntityBoundingBox())) {
                found = threat;
            }
        }
        return found;
    }

}
