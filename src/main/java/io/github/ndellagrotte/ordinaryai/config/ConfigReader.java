/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.config;

import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Reads typed options out of a Forge {@link Configuration}, declaring each one as it goes. Category names, keys and
 * property types match Special AI 1.1.0 so its config file can be read unchanged.
 */
final class ConfigReader {

    private static final int MAX_DEFAULTS_IN_COMMENT = 12;

    private final Configuration file;
    private final Consumer<String> warn;

    private String category = "";

    ConfigReader(Configuration file, Consumer<String> warn) {
        this.file = file;
        this.warn = warn;
    }

    private static Class<?> entityClass(String id) {
        return EntityList.getClass(new ResourceLocation(id));
    }

    private static String amendRange(String comment, String type, Object fallback, Object min, Object max) {
        return comment + "\n   >> " + type + ":[ Range={ " + min + ", " + max + " }, Default=" + fallback + " ]";
    }

    private static String amendList(String comment, String type, String[] fallback, String description) {
        StringBuilder defaults = new StringBuilder("{ ");
        int shown = Math.min(fallback.length, MAX_DEFAULTS_IN_COMMENT);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                defaults.append(", ");
            }
            defaults.append(fallback[i]);
        }
        if (shown < fallback.length) {
            defaults.append(", ... (").append(fallback.length).append(" entries)");
        }
        defaults.append(" }");
        return comment + "\n   >> " + type + ":[ Value={ " + description + " }, Default=" + defaults + " ]";
    }

    void category(String name, String comment) {
        category = name;
        file.addCustomCategoryComment(name, comment);
    }

    boolean bool(String key, boolean fallback, String comment) {
        String amended = comment + "\n   >> Boolean:[ Valid_Values={ true, false }, Default=" + fallback + " ]";
        return file.get(category, key, fallback, amended).getBoolean();
    }

    int integer(String key, int fallback, String comment, int min, int max) {
        String amended = amendRange(comment, "Integer", fallback, min, max);
        Property property = file.get(category, key, fallback, amended, min, max);
        int value = property.getInt(fallback);
        int clamped = Math.max(min, Math.min(max, value));
        if (clamped != value) {
            warnOutOfRange(key, value, clamped);
            property.set(clamped);
        }
        return clamped;
    }

    double real(String key, double fallback, String comment, double min, double max) {
        return real(key, fallback, amendRange(comment, "Double", fallback, min, max), min, max, true);
    }

    float real(String key, float fallback, String comment, float min, float max) {
        // Widening through text keeps 0.33F from becoming 0.33000001311302185 in the file
        double fallbackDouble = Double.parseDouble(Float.toString(fallback));
        double minDouble = Double.parseDouble(Float.toString(min));
        double maxDouble = Double.parseDouble(Float.toString(max));
        String amended = amendRange(comment, "Float", fallback, min, max);
        return (float) real(key, fallbackDouble, amended, minDouble, maxDouble, true);
    }

    MobList mobs(String key, String[] fallback, String comment) {
        String amended = amendList(comment, "Entity_Array", fallback,
                "entity_id <extra_data>, ~entity_id <extra_data>");
        String[] lines = file.get(category, key, fallback, amended).getStringList();
        return MobList.parse(lines, ConfigReader::entityClass, message -> warn.accept(where(key) + message));
    }

    BlockTargetMap blocks(String key, String[] fallback, String comment) {
        String amended = amendList(comment, "Block_Array", fallback,
                "mod_id:block_id, mod_id:block_id[<properties>], mod_id:*");
        String[] lines = file.get(category, key, fallback, amended).getStringList();
        return BlockTargetMap.parse(Arrays.asList(lines), message -> warn.accept(where(key) + message));
    }

    private double real(String key, double fallback, String amended, double min, double max, boolean clamp) {
        Property property = file.get(category, key, fallback, amended, min, max);
        double value = property.getDouble(fallback);
        if (!clamp) {
            return value;
        }
        double clamped = Double.isNaN(value) ? fallback : Math.max(min, Math.min(max, value));
        if (clamped != value) {
            warnOutOfRange(key, value, clamped);
            property.set(clamped);
        }
        return clamped;
    }

    private String where(String key) {
        return "[" + category + "." + key + "] ";
    }

    private void warnOutOfRange(String key, Object value, Object clamped) {
        warn.accept(where(key) + "Value " + value + " is out of range, using " + clamped);
    }

}
