/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai;

/**
 * Marks an AI task as added by this mod. A mob that already carries one has been set up, which is how repeated join
 * events for the same entity are recognised.
 */
public interface OrdinaryTask {

    /**
     * Called when the owner dies while the task may still be running, since tasks are not reset on death. Tasks that
     * leave visible state behind, such as block-break progress, clear it here.
     */
    default void onOwnerDeath() {
        // Nothing to clean up by default
    }

}
