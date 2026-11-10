/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.ai;

import io.github.ndellagrotte.ordinaryai.OrdinaryAI;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Work that an AI task wants done once the current server tick is over.
 *
 * <p>The game is walking a mob's task list whenever it calls into a task, and some harmless-looking calls change
 * that list (giving a skeleton an item makes it swap its attack task). Such calls are queued here instead. Only
 * used from the server thread.
 */
public final class EndOfTickQueue {

    private static final Deque<Runnable> PENDING = new ArrayDeque<>();

    private EndOfTickQueue() { }

    /** Queues an action for the end of the current server tick. */
    public static void defer(Runnable action) {
        PENDING.add(action);
    }

    /** Runs everything queued so far. Called once per server tick. */
    public static void drain() {
        Runnable action;
        while ((action = PENDING.poll()) != null) {
            try {
                action.run();
            } catch (RuntimeException ex) {
                OrdinaryAI.LOGGER.warn("A deferred AI action failed", ex);
            }
        }
    }

}
