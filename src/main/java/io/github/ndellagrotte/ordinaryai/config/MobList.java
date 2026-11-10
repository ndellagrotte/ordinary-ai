/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.config;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An immutable list of mob types, each with optional chance values, matched by class hierarchy.
 *
 * <p>Entries are written {@code entity_id [chance ...]}. A leading {@code ~} restricts the entry to that exact class;
 * otherwise subclasses match too. When several entries match, the most specific one wins.
 */
public final class MobList {

    private static final float[] NO_MATCH = new float[0];

    private final Entry[] entries;
    private final ConcurrentHashMap<Class<?>, float[]> matches = new ConcurrentHashMap<>();

    private MobList(Entry[] entries) {
        this.entries = entries;
    }

    /**
     * Parses a list of config lines.
     *
     * @param lines    one entry per element
     * @param resolver maps an entity id to its class, or null when the id is unknown
     * @param warn     receives a message for each problem found
     */
    public static MobList parse(String[] lines, Function<String, @Nullable Class<?>> resolver, Consumer<String> warn) {
        List<Entry> parsed = new ArrayList<>(lines.length);
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            boolean includeSubclasses = true;
            if (line.startsWith("~")) {
                line = line.substring(1).trim();
                includeSubclasses = false;
            }
            String[] parts = line.split("\\s+");
            Class<?> type = resolver.apply(parts[0]);
            if (type == null) {
                warn.accept("Invalid entity id! (" + rawLine.trim() + ")");
                continue;
            }
            float[] chances = new float[parts.length - 1];
            for (int i = 0; i < chances.length; i++) {
                chances[i] = parseChance(parts[i + 1], rawLine, warn);
            }
            parsed.add(new Entry(type, includeSubclasses, chances));
        }
        return new MobList(parsed.toArray(new Entry[0]));
    }

    private static float parseChance(String text, String line, Consumer<String> warn) {
        try {
            float value = Float.parseFloat(text);
            if (value >= 0.0F && value <= 1.0F) {
                return value;
            }
        } catch (NumberFormatException _) {
            // Reported below
        }
        warn.accept("Invalid chance '" + text + "' (must be 0.0 to 1.0), using 0 (" + line.trim() + ")");
        return 0.0F;
    }

    /** Returns true if no entry was parsed successfully. */
    public boolean isEmpty() {
        return entries.length == 0;
    }

    /** Returns true if the type matches any entry. */
    public boolean contains(Class<?> type) {
        return chancesFor(type) != null;
    }

    /**
     * Returns the chance values of the most specific entry matching the type, or null if nothing matches. The array
     * is shared and must not be modified.
     */
    public float @Nullable [] chancesFor(Class<?> type) {
        float[] found = matches.computeIfAbsent(type, this::findChances);
        return found == NO_MATCH ? null : found;
    }

    /**
     * Rolls the first chance of the matching entry. An entry without chances always passes, and consumes no random
     * number.
     */
    public boolean roll(Class<?> type, Random random) {
        float[] chances = chancesFor(type);
        return chances != null && (chances.length == 0 || random.nextFloat() < chances[0]);
    }

    private float[] findChances(Class<?> type) {
        Entry best = null;
        for (Entry entry : entries) {
            if (entry.matches(type) && (best == null || best.isLessSpecificThan(entry))) {
                best = entry;
            }
        }
        // An entry with no chances is distinct from no entry at all
        return best == null ? NO_MATCH : best.chances.length == 0 ? new float[0] : best.chances;
    }

    private record Entry(Class<?> type, boolean includeSubclasses, float[] chances) {

        boolean matches(Class<?> queried) {
            return type == queried || includeSubclasses && type.isAssignableFrom(queried);
        }

        boolean isLessSpecificThan(Entry other) {
            if (type == other.type) {
                return !other.includeSubclasses;
            }
            return includeSubclasses && type.isAssignableFrom(other.type);
        }

    }

}
