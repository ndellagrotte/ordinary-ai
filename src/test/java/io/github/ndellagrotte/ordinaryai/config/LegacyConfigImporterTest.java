/*
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyConfigImporterTest {

    @TempDir Path configDir;

    @Test
    void copiesTheLegacyFileAndLeavesItInPlace() throws IOException {
        Path legacy = configDir.resolve(LegacyConfigImporter.LEGACY_FILE_NAME);
        Path target = configDir.resolve("specialai.cfg");
        Files.writeString(legacy, "old settings");

        assertThat(LegacyConfigImporter.importIfAbsent(configDir, target)).isTrue();
        assertThat(target).hasContent("old settings");
        assertThat(legacy).hasContent("old settings");
    }

    @Test
    void neverOverwritesAnExistingConfig() throws IOException {
        Path target = configDir.resolve("specialai.cfg");
        Files.writeString(configDir.resolve(LegacyConfigImporter.LEGACY_FILE_NAME), "old settings");
        Files.writeString(target, "current settings");

        assertThat(LegacyConfigImporter.importIfAbsent(configDir, target)).isFalse();
        assertThat(target).hasContent("current settings");
    }

    @Test
    void doesNothingWithoutALegacyFile() throws IOException {
        Path target = configDir.resolve("specialai.cfg");

        assertThat(LegacyConfigImporter.importIfAbsent(configDir, target)).isFalse();
        assertThat(target).doesNotExist();
    }

}
