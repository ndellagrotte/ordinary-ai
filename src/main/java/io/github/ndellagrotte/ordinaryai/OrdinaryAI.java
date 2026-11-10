/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai;

import io.github.ndellagrotte.ordinaryai.ai.AiInjector;
import io.github.ndellagrotte.ordinaryai.config.ModConfig;
import io.github.ndellagrotte.ordinaryai.config.ReloadCommand;
import io.github.ndellagrotte.ordinaryai.village.ReputationEvents;
import io.github.ndellagrotte.ordinaryai.village.VillageInfoCommand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

/**
 * Mod entry point. Everything the mod does happens on the logical server, so clients can connect without it.
 */
@Mod(modid = OrdinaryAI.MOD_ID, name = OrdinaryAI.NAME, version = OrdinaryAI.VERSION,
        acceptedMinecraftVersions = "[1.12.2]", acceptableRemoteVersions = "*",
        guiFactory = "io.github.ndellagrotte.ordinaryai.config.client.ConfigGuiFactory")
public final class OrdinaryAI {

    public static final String MOD_ID = "@{MOD_ID}";
    public static final String NAME = "@{MOD_NAME}";
    public static final String VERSION = "@{MOD_VERSION}";

    public static final Logger LOGGER = LogManager.getLogger(NAME);

    /** Set {@code -Dordinaryai.selftest=true} to check the mod against a live server and then stop it. */
    private static final String SELF_TEST_PROPERTY = "ordinaryai.selftest";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.locate(event.getModConfigurationDirectory());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new ModConfig.ChangeListener());
        MinecraftForge.EVENT_BUS.register(new AiInjector());
        MinecraftForge.EVENT_BUS.register(new ReputationEvents());
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        // Deliberately this late: default block lists are built from what every mod has registered
        ModConfig.load();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new VillageInfoCommand());
        event.registerServerCommand(new ReloadCommand());
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        if (Boolean.getBoolean(SELF_TEST_PROPERTY)) {
            SelfTest.runAndStop();
        }
    }

}
