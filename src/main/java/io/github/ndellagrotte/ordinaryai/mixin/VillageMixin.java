/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.mixin;

import io.github.ndellagrotte.ordinaryai.config.ConfigSnapshot;
import io.github.ndellagrotte.ordinaryai.config.ModConfig;
import io.github.ndellagrotte.ordinaryai.village.VillageTicking;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.village.Village;
import net.minecraft.world.World;

/**
 * Stops a village from updating while its area is not loaded.
 *
 * <p>Vanilla ticks every village in the world regardless. An unloaded village then sees no villagers and no doors
 * being used, so it forgets every player's reputation and gradually drops its houses. Skipping those ticks keeps
 * villages intact while nobody is around.
 */
@Mixin(Village.class)
public abstract class VillageMixin {

    @Unique private boolean ordinaryai$loadChecked;

    @Unique private boolean ordinaryai$loaded;

    @Unique private int ordinaryai$knownDoors = -1;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void ordinaryai$onTick(int tickCounter, CallbackInfo ci) {
        Village self = (Village) (Object) this;
        // Made accessible by the access transformer; see the note there on why this is not a @Shadow
        World world = self.world;
        if (world == null || world.isRemote || !ModConfig.isLoaded()) {
            return;
        }

        if (!ordinaryai$loadChecked || tickCounter % VillageTicking.LOAD_CHECK_INTERVAL == 0) {
            boolean loadedNow = VillageTicking.isLoaded(world, self.getCenter());
            if (loadedNow && !ordinaryai$loaded) {
                // Time passed while the village was frozen; do not let that count against its doors
                VillageTicking.refreshDoors(self, tickCounter);
            }
            ordinaryai$loaded = loadedNow;
            ordinaryai$loadChecked = true;
        }
        if (!ordinaryai$loaded) {
            ci.cancel();
            return;
        }

        ConfigSnapshot.Villages config = ModConfig.get().villages();
        if (config.houseRep() && tickCounter % VillageTicking.HOUSE_CHECK_INTERVAL == 0) {
            int doors = self.getNumVillageDoors();
            if (ordinaryai$knownDoors >= 0 && doors != ordinaryai$knownDoors) {
                VillageTicking.changeEveryonesReputation(world, self, doors - ordinaryai$knownDoors);
            }
            ordinaryai$knownDoors = doors;
        }
        if (config.refreshHouses() && tickCounter % VillageTicking.DOOR_REFRESH_INTERVAL == 0) {
            VillageTicking.refreshDoors(self, tickCounter);
        }
    }

}
