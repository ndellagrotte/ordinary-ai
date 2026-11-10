/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Text syntax of block targets: {@code mod_id:block_id}, {@code mod_id:block_id[property=value,...]} and
 * {@code mod_id:*}. Knows nothing about the block registry.
 */
public final class BlockTargetSyntax {

    private BlockTargetSyntax() { }

    /**
     * Splits a single comma-separated line into targets, keeping commas inside brackets together.
     */
    public static List<String> splitLine(String line, Consumer<String> warn) {
        String[] fragments = line.split(",");
        List<String> targets = new ArrayList<>(fragments.length);
        for (int i = 0; i < fragments.length; i++) {
            String fragment = fragments[i].trim();
            if (fragment.contains("[") && !fragment.endsWith("]")) {
                boolean completed = false;
                StringBuilder rebuilt = new StringBuilder(fragment);
                for (i++; i < fragments.length; i++) {
                    String next = fragments[i].trim();
                    rebuilt.append(',').append(next);
                    if (next.endsWith("]")) {
                        completed = true;
                        break;
                    }
                }
                fragment = rebuilt.toString();
                if (!completed) {
                    warn.accept("Reached end of line while parsing block state '" + fragment + "' (" + line + ")");
                    continue;
                }
            }
            if (!fragment.isEmpty()) {
                targets.add(fragment);
            }
        }
        return targets;
    }

    /** Parses one target. Malformed conditions are reported and dropped, leaving a broader match. */
    public static Target parse(String text, Consumer<String> warn) {
        String target = text.trim();
        if (target.endsWith("*")) {
            return new Target(target.substring(0, target.length() - 1), true, List.of());
        }
        String[] pair = target.split("\\[", 2);
        String id = pair[0].trim();
        if (pair.length < 2) {
            return new Target(id, false, List.of());
        }
        if (!pair[1].endsWith("]")) {
            warn.accept("Ignoring properties for broken block state '" + target + "' (no end bracket found)");
            return new Target(id, false, List.of());
        }
        String body = pair[1].substring(0, pair[1].length() - 1);
        List<Condition> conditions = new ArrayList<>();
        if (!body.isEmpty()) {
            for (String combined : body.split(",")) {
                String[] entry = combined.split("=", 2);
                if (entry.length != 2) {
                    warn.accept("Invalid block property entry '" + combined + "' - format must be 'property=value'");
                } else if (!"*".equals(entry[1].trim())) {
                    conditions.add(new Condition(entry[0].trim(), entry[1].trim()));
                }
            }
        }
        return new Target(id, false, List.copyOf(conditions));
    }

    /** One {@code property=value} condition. */
    public record Condition(String property, String value) { }

    /**
     * One parsed target.
     *
     * @param id             the block id, or the namespace prefix (including the colon) when wholeNamespace is set
     * @param wholeNamespace true for {@code mod_id:*}
     * @param conditions     state conditions; empty matches every state
     */
    public record Target(String id, boolean wholeNamespace, List<Condition> conditions) { }

}
