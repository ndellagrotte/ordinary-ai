/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.config.client;

import io.github.ndellagrotte.ordinaryai.OrdinaryAI;
import io.github.ndellagrotte.ordinaryai.config.ModConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Provides the in-game config screen reached from the mod list. */
@SideOnly(Side.CLIENT)
public final class ConfigGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraft) {
        // Nothing to prepare
    }

    @Override
    public boolean hasConfigGui() {
        return ModConfig.isLoaded();
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        List<IConfigElement> elements = new ArrayList<>();
        Configuration file = ModConfig.file();
        if (file != null) {
            for (String category : file.getCategoryNames()) {
                elements.add(new ConfigElement(file.getCategory(category)));
            }
        }
        return new GuiConfig(parentScreen, elements, OrdinaryAI.MOD_ID, false, false, OrdinaryAI.NAME);
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

}
