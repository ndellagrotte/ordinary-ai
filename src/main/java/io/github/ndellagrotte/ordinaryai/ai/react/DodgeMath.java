/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai.react;

/** Geometry for deciding whether a mob stands in an arrow's path. Works in the horizontal plane only. */
public final class DodgeMath {

    private DodgeMath() { }

    /**
     * Returns true if a point lies ahead of a ray and within its width.
     *
     * @param dx       offset of the point from the ray origin on x
     * @param dz       offset of the point from the ray origin on z
     * @param ux       x component of the ray's unit direction
     * @param uz       z component of the ray's unit direction
     * @param halfWidth how far from the ray's centre line still counts as in the path
     * @param maxRange how far along the ground the ray is considered
     */
    public static boolean isInPath(double dx, double dz, double ux, double uz, double halfWidth, double maxRange) {
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance == 0.0 || distance >= maxRange) {
            return false;
        }
        double along = ux * dx + uz * dz;
        if (along <= 0.0) {
            // Behind the ray
            return false;
        }
        double cos = along / distance;
        double sin = Math.sqrt(Math.max(0.0, 1.0 - cos * cos));
        return halfWidth > distance * sin;
    }

    /**
     * Returns the horizontal speed for a motion, which is also the factor to divide by to get a unit direction. A
     * result of zero means there is no direction to dodge.
     */
    public static double horizontalSpeed(double motionX, double motionZ) {
        return Math.sqrt(motionX * motionX + motionZ * motionZ);
    }

}
