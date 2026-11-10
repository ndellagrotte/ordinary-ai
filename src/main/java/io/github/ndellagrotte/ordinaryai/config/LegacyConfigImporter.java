/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Carries a Special AI config file over to this mod's file name. */
public final class LegacyConfigImporter {

    /** File name used by Special AI 1.1.0. */
    public static final String LEGACY_FILE_NAME = "Special_AI.cfg";

    private LegacyConfigImporter() { }

    /**
     * Copies the legacy file to the target if the target does not exist yet. The legacy file is left in place.
     *
     * @return true if a copy was made
     */
    public static boolean importIfAbsent(Path configDir, Path target) throws IOException {
        Path legacy = configDir.resolve(LEGACY_FILE_NAME);
        if (Files.exists(target) || !Files.isRegularFile(legacy)) {
            return false;
        }
        Files.copy(legacy, target);
        return true;
    }

}
