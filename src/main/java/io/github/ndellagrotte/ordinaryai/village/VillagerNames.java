/*
 * Copyright (c) 2016-2019 Father Toast
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.village;

import io.github.ndellagrotte.ordinaryai.OrdinaryAI;
import org.jspecify.annotations.Nullable;

import net.minecraft.entity.passive.EntityVillager;
import net.minecraftforge.fml.common.registry.VillagerRegistry.VillagerCareer;
import net.minecraftforge.fml.common.registry.VillagerRegistry.VillagerProfession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Gives villagers random names with a job title, such as "Farmer Thorald" or "Benwen the Mapmaker". Names are
 * built from syllables; some syllables and all titles depend on the villager's career.
 */
public final class VillagerNames {

    /** One-in-N chances for each successive syllable to be added. The first two are certain. */
    private static final int[] SYLLABLE_ODDS = { 1, 1, 4, 16, 64 };

    private static final List<String> COMMON_SYLLABLES = List.of("grab", "thar", "ger", "ald", "mas", "on", "o", "din",
            "thor", "jon", "ath", "an", "burb", "en", "a", "e", "i", "u", "hab", "bloo", "ena", "dit", "aph", "ern",
            "bor", "dav", "id", "toast", "son", "dottir", "for", "wen", "lob", "ed", "die", "van", "y", "zap", "ear",
            "ben", "don", "bran", "gro", "jen", "bob", "ette", "ere", "man", "qua", "bro", "cree", "per", "skel", "ton",
            "zom", "bie", "wolf", "end", "er", "pig", "sil", "ver", "fish", "cow", "chic", "ken", "sheep", "lla", "rab",
            "bit", "squid", "hell");

    /** Keyed by {@code profession#career}. */
    private static final Map<String, Career> CAREERS = new HashMap<>();
    private static final List<Career> ALL_CAREERS = new ArrayList<>();

    static {
        career("minecraft:farmer", "farmer",
                List.of("Farmer", "Farmer", "Gardener", "Gardener", "Seedmonger", "Seedmonger", "Cropper",
                        "Greenskeeper", "Agriculturalist", "Horticulturist", "Seedsman", "Landscaper", "Seedvendor"),
                List.of("farm", "garden", "soil", "seed", "plant", "crop", "green", "wheat", "carrot", "potato", "beet",
                        "hoe"));
        career("minecraft:farmer", "fisherman",
                List.of("Fisherman", "Fisherman", "Fisher", "Fisher", "Angler", "Angler", "Fishmonger", "Fishmonger",
                        "Piscator", "Rodman", "Troller", "Trawler", "Fishman", "Skipper", "Captain", "Fishvendor"),
                List.of("fish", "sea", "boat", "salt", "rod", "cast", "net", "gold", "treasure", "sunken", "oar",
                        "plank", "beard"));
        career("minecraft:farmer", "shepherd",
                List.of("Shepherd", "Shepherd", "Rancher", "Rancher", "Herder", "Herder", "Cowhand", "Herdsman",
                        "Cowpuncher", "Sheepsman", "Sheeper", "Cooper", "Stockman"),
                List.of("herd", "ranch", "brand", "cow", "sheep", "chicken", "pig", "horse", "fence", "gate", "post",
                        "hay"));
        career("minecraft:farmer", "fletcher",
                List.of("Fletcher", "Fletcher", "Bowyer", "Bowyer", "Arrowvendor", "Arrowvendor", "Arrowman", "Bowman",
                        "Arrowcrafter", "Bowcrafter", "Bowvendor", "Arrowmonger", "Bowmonger"),
                List.of("arrow", "feather", "string", "bow", "archer", "shoot", "poke"));
        career("minecraft:librarian", "librarian",
                List.of("Librarian", "Librarian", "Curator", "Curator", "Bookbinder", "Bookbinder", "Bookvendor",
                        "Bookvendor", "Cataloger", "Researcher", "Bookman", "Bibliothecary", "Recordkeeper",
                        "Bookmonger"),
                List.of("book", "shelf", "wise", "read", "bind", "record", "biblio", "libro", "lib"));
        career("minecraft:librarian", "cartographer",
                List.of("Cartographer", "Cartographer", "Mapmaker", "Mapmaker", "Explorer", "Explorer", "Mapvendor",
                        "Mapvendor", "Topographer", "Mapper", "Adventurer", "Pathfinder", "Pioneer", "Mapmonger",
                        "Wayfinder"),
                List.of("map", "dora", "venture", "carto", "topo", "way", "path", "find", "treasure"));
        career("minecraft:priest", "cleric",
                List.of("Cleric", "Cleric", "Cleric", "Priest", "Priest", "Priest", "Father", "Mother", "Preacher",
                        "Reverend", "Rabbi", "Chaplain", "Minister", "Churchman", "Healer", "Storyteller", "Mystic",
                        "Enchanter", "Diviner", "Oracle", "Augur", "Prophet", "Seer", "Holyman", "Sage", "Wiseman",
                        "Elder"),
                List.of("holy", "wise", "mystic", "secret", "cleric", "priest", "div"));
        career("minecraft:smith", "armor",
                List.of("Armorsmith", "Armorsmith", "Armorer", "Armorer", "Armorworker", "Armorworker", "Armorvendor",
                        "Armorvendor", "Armormaster", "Armorman", "Armorcrafter", "Plateworker", "Platesmith",
                        "Platecrafter", "Shieldworker", "Shieldsmith", "Shieldcrafter", "Armormonger", "Platemonger",
                        "Platevendor", "Shieldmonger", "Shieldvendor"),
                List.of("armor", "plate", "shield", "smith", "forge", "fire", "anvil"));
        career("minecraft:smith", "weapon",
                List.of("Weaponsmith", "Weaponsmith", "Swordsmith", "Swordsmith", "Weaponmonger", "Weaponmonger",
                        "Weaponcrafter", "Weaponmaker", "Weaponmaster", "Weaponman", "Swordcrafter", "Swordmaker",
                        "Swordmaster", "Weaponvendor", "Swordmonger", "Swordvendor"),
                List.of("weapon", "sword", "poker", "smith", "forge", "fire", "anvil"));
        career("minecraft:smith", "tool",
                List.of("Toolsmith", "Toolsmith", "Toolmaker", "Toolmaker", "Toolvendor", "Toolvendor", "Toolworker",
                        "Toolcrafter", "Toolman", "Toolmaster", "Tooler", "Artisan", "Toolmonger"),
                List.of("tool", "pick", "axe", "shovel", "hoe", "hammer", "smith", "forge", "fire", "anvil"));
        career("minecraft:butcher", "butcher",
                List.of("Butcher", "Butcher", "Meatman", "Meatman", "Meatmonger", "Meatmonger", "Meatvendor",
                        "Cowslayer", "Sheepslayer", "Chickenslayer", "Rabbitslayer"),
                List.of("butcher", "blood", "meat", "chop", "cleave", "hook"));
        career("minecraft:butcher", "leather",
                List.of("Tanner", "Tanner", "Leathermaker", "Leathermaker", "Leathermonger", "Leathermonger",
                        "Leatherman", "Skinner", "Cowskinner", "Sheepskinner", "Hidetanner", "Leathertanner",
                        "Leathervendor"),
                List.of("tan", "leather", "hide", "skin", "strip", "chop", "cleave", "blood"));
        career("minecraft:nitwit", "nitwit",
                List.of("Nitwit", "Blockhead", "Fool", "Dimwit", "Dork", "Dunce", "Jerk", "Moron", "Pinhead", "Ninny",
                        "Simpleton"),
                List.of("fool", "dork", "jerk", "wit", "genius", "head", "smart", "wise"));
    }

    private VillagerNames() { }

    /** Sets a random name on the villager. */
    public static void assign(EntityVillager villager, boolean logUnknownCareers) {
        Random random = villager.getRNG();
        String key = careerKey(villager);
        Career career = key == null ? null : CAREERS.get(key);

        List<String> syllables = new ArrayList<>(COMMON_SYLLABLES);
        List<String> titles;
        if (career != null) {
            syllables.addAll(career.syllables());
            titles = career.titles();
        } else {
            // Careers added by other mods are not known here; borrow a title rather than leave them nameless
            if (logUnknownCareers) {
                OrdinaryAI.LOGGER.info("Naming villager with unknown profession#career '{}'", key);
            }
            titles = ALL_CAREERS.get(random.nextInt(ALL_CAREERS.size())).titles();
        }
        villager.setCustomNameTag(compose(random, syllables, titles));
    }

    /** Builds a name from the given syllables and titles. */
    static String compose(Random random, List<String> syllables, List<String> titles) {
        StringBuilder name = new StringBuilder(32);
        for (int odds : SYLLABLE_ODDS) {
            if (random.nextInt(odds) == 0) {
                name.append(syllables.get(random.nextInt(syllables.size())));
            }
        }
        name.setCharAt(0, Character.toUpperCase(name.charAt(0)));
        if (random.nextInt(100) == 0) {
            name.insert(0, random.nextBoolean() ? "Mc" : "Mac");
        }

        String title = titles.get(random.nextInt(titles.size()));
        if (random.nextInt(3) == 0) {
            name.append(" the ").append(title);
        } else {
            name.insert(0, " ").insert(0, title);
        }
        return name.toString();
    }

    private static void career(String profession, String name, List<String> titles, List<String> syllables) {
        Career career = new Career(titles, syllables);
        CAREERS.put(profession + "#" + name, career);
        ALL_CAREERS.add(career);
    }

    private static @Nullable
    String careerKey(EntityVillager villager) {
        try {
            VillagerProfession profession = villager.getProfessionForge();
            // A villager that has not traded yet has no career; Forge then answers with the profession's first one
            VillagerCareer career = profession.getCareer(villager.careerId - 1);
            return profession.getRegistryName() + "#" + career.getName();
        } catch (RuntimeException _) {
            // A profession from another mod with no careers registered
            return null;
        }
    }

    private record Career(List<String> titles, List<String> syllables) { }

}
