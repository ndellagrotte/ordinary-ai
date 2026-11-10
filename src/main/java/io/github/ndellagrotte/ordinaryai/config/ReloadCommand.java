/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.config;

import io.github.ndellagrotte.ordinaryai.OrdinaryAI;
import org.jspecify.annotations.Nullable;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.Collections;
import java.util.List;

/** {@code /ordinaryai reload}: re-reads the config file without restarting. For operators. */
public final class ReloadCommand extends CommandBase {

    private static final String USAGE = "/" + OrdinaryAI.MOD_ID + " reload";

    @Override
    public String getName() {
        return OrdinaryAI.MOD_ID;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1 || !"reload".equals(args[0])) {
            throw new WrongUsageException(USAGE);
        }
        try {
            ModConfig.reloadFromDisk();
        } catch (RuntimeException ex) {
            OrdinaryAI.LOGGER.error("Config reload failed", ex);
            throw new CommandException("Config reload failed: " + ex.getMessage());
        }
        notifyCommandListener(sender, this, OrdinaryAI.NAME + " config reloaded");
        sender.sendMessage(
            new TextComponentString("Settings rolled per mob when it spawned are kept; new values apply from now on."));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
        @Nullable BlockPos targetPos
    ) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "reload") : Collections.emptyList();
    }

}
