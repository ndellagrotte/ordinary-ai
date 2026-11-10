/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.village;

import io.github.ndellagrotte.ordinaryai.config.ConfigSnapshot;
import io.github.ndellagrotte.ordinaryai.config.ModConfig;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.village.Village;
import net.minecraft.village.VillageCollection;

/**
 * {@code /villageinfo}: tells a player about the village they are standing in and where they stand with it. The
 * text is composed on the server so that players do not need the mod installed to read it.
 */
public final class VillageInfoCommand extends CommandBase {

    private static void say(ICommandSender sender, String text) {
        sender.sendMessage(new TextComponentString(text));
    }

    private static String label(Reputation.Standing standing) {
        return switch (standing) {
            case HATED -> "Hated";
            case DISLIKED -> "Disliked";
            case NEUTRAL -> "Neutral";
            case TRUSTED -> "Trusted";
        };
    }

    private static TextFormatting color(Reputation.Standing standing) {
        return switch (standing) {
            case HATED -> TextFormatting.RED;
            case DISLIKED -> TextFormatting.YELLOW;
            case NEUTRAL -> TextFormatting.WHITE;
            case TRUSTED -> TextFormatting.GREEN;
        };
    }

    private static String[] notes(Reputation.Standing standing) {
        String lowersBy = "Your reputation can lower by:";
        return switch (standing) {
            case HATED ->
                new String[] {
                    lowersBy,
                    " Hurting villagers, Destroying homes, Breaking blocks",
                    " Village guards may attack on sight"
                };
            case DISLIKED ->
                new String[] {
                    lowersBy,
                    " Hurting villagers, Destroying homes, Breaking blocks",
                    " Breaking blocks may anger village guards"
                };
            case NEUTRAL -> new String[] { lowersBy, " Hurting villagers, Destroying homes, Breaking blocks" };
            case TRUSTED -> new String[] { lowersBy, " Hurting villagers, Destroying homes" };
        };
    }

    @Override
    public String getName() {
        return "villageinfo";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/villageinfo";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        VillageCollection villages = player.world.getVillageCollection();
        Village village = villages == null ? null : villages.getNearestVillage(new BlockPos(player), 0);
        if (village == null) {
            throw new CommandException("No village nearby");
        }
        ConfigSnapshot.Villages config = ModConfig.get().villages();

        BlockPos center = village.getCenter();
        int radius = village.getVillageRadius();
        int houses = village.getNumVillageDoors();
        int population = village.getNumVillagers();
        int golems = player.world
            .getEntitiesWithinAABB(EntityIronGolem.class,
                new AxisAlignedBB(center.getX() - radius, center.getY() - 4, center.getZ() - radius,
                    center.getX() + radius, center.getY() + 4, center.getZ() + radius
                )
            )
            .size();
        // Vanilla's own limits: one villager per 0.35 doors, one golem per ten villagers once there are 21 doors
        int maxPopulation = (int) (houses * 0.35);
        int maxGolems = houses > 20 ? population / 10 : 0;
        int reputation = village.getPlayerReputation(player.getUniqueID());

        if (config.commandIncludeCenter()) {
            say(sender, "Center XYZ: " + center.getX() + " / " + center.getY() + " / " + center.getZ());
        }
        say(sender, "Houses: " + houses + ",  Radius: " + radius);
        say(sender, "Population: " + population + " / " + maxPopulation + ",  Golems: " + golems + " / " + maxGolems);

        Reputation.Standing standing = Reputation.standing(reputation, config.blockAggressionLimit(),
            config.blockRepLossLimit()
        );
        ITextComponent line = new TextComponentString("Reputation: " + reputation + " (");
        ITextComponent label = new TextComponentString(label(standing));
        label.getStyle().setColor(color(standing));
        sender.sendMessage(line.appendSibling(label).appendText(")"));
        for (String note : notes(standing)) {
            say(sender, note);
        }
    }

}
