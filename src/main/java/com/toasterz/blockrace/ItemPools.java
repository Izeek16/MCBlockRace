package com.toasterz.blockrace;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Builds the pools of items that can be rolled as race targets.
 *
 * Three pools:
 *  - EASY:       curated list of common, overworld, quick-to-get items. Fast rounds.
 *  - NORMAL:     everything obtainable in survival, minus "extreme" grinds
 *                (netherite, end-game, silk-touch-only blocks, ocean monuments, etc).
 *  - EVERYTHING: everything obtainable in survival. Chaos mode.
 *
 * Tuning: everything is id-string based, so you can add/remove entries below
 * without touching any other code. Use /race skip in-game to reroll a bad target.
 */
public final class ItemPools {

    public enum Pool { EASY, NORMAL, EVERYTHING }

    private static final Map<Pool, List<Item>> CACHE = new EnumMap<>(Pool.class);

    /** Items that literally cannot be obtained in survival (or are NBT-only / broken as plain items). */
    private static final Set<String> UNOBTAINABLE = new HashSet<>(Arrays.asList(
            "air", "barrier", "bedrock", "light",
            "command_block", "chain_command_block", "repeating_command_block", "command_block_minecart",
            "structure_block", "structure_void", "jigsaw", "debug_stick", "knowledge_book",
            "spawner", "end_portal_frame", "reinforced_deepslate", "petrified_oak_slab",
            "farmland", "dirt_path", "budding_amethyst",
            "suspicious_sand", "suspicious_gravel",
            "chorus_plant", "bundle", "filled_map", "written_book",
            "potion", "splash_potion", "lingering_potion", "tipped_arrow",
            "goat_horn", "player_head", "globe_banner_pattern"
    ));

    /** Substring rules for unobtainable items. */
    private static final List<String> UNOBTAINABLE_CONTAINS = Arrays.asList(
            "spawn_egg", "infested"
    );

    /** Obtainable, but a massive grind / RNG / end-game. Excluded from NORMAL, included in EVERYTHING. */
    private static final Set<String> EXTREME = new HashSet<>(Arrays.asList(
            // end-game / dimensions
            "ancient_debris", "end_rod", "end_crystal", "dragon_egg", "dragon_head", "dragon_breath",
            "elytra", "shulker_shell", "ender_eye", "ender_chest", "blaze_rod", "blaze_powder",
            "ghast_tear", "nether_star", "beacon", "magma_cream",
            // heads & rare mob drops
            "wither_skeleton_skull", "wither_rose", "skeleton_skull", "zombie_head", "creeper_head",
            "piglin_head", "totem_of_undying", "trident", "phantom_membrane", "rabbit_foot",
            "turtle_helmet", "scute", "turtle_egg",
            // ocean / structure loot
            "heart_of_the_sea", "conduit", "nautilus_shell", "sponge", "wet_sponge",
            "enchanted_golden_apple", "echo_shard", "recovery_compass", "disc_fragment_5",
            "saddle", "name_tag", "experience_bottle", "gilded_blackstone", "lodestone", "bell",
            "iron_horse_armor", "golden_horse_armor", "diamond_horse_armor",
            "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots",
            "enchanted_book", "cobweb",
            "mojang_banner_pattern", "piglin_banner_pattern", "creeper_banner_pattern", "skull_banner_pattern",
            // silk-touch-only blocks
            "ice", "packed_ice", "blue_ice", "grass_block", "mycelium", "podzol",
            "amethyst_cluster", "mushroom_stem", "bee_nest", "deepslate_emerald_ore",
            // slime chunks
            "slime_ball", "slime_block", "sticky_piston",
            // sniffer / 1.20 archaeology chain
            "sniffer_egg",
            // biome-lottery buckets
            "axolotl_bucket", "tadpole_bucket", "powder_snow_bucket"
    ));

    /** Substring rules for extreme items. */
    private static final List<String> EXTREME_CONTAINS = Arrays.asList(
            "netherite", "sculk", "prismarine", "shulker_box", "coral", "smithing_template",
            "music_disc", "purpur", "chorus", "end_stone", "nether_wart", "froglight",
            "amethyst_bud", "mushroom_block", "torchflower", "pitcher"
    );

    /** Substring/suffix rule: all ore BLOCK items need silk touch -> extreme. */
    private static boolean isOreBlock(String path) {
        return path.endsWith("_ore");
    }

    /** Curated fast-round pool. All common, overworld, day-one friendly. */
    private static final List<String> EASY_IDS = Arrays.asList(
            // wood & basics
            "oak_log", "birch_log", "spruce_log", "dark_oak_log",
            "oak_planks", "birch_planks", "spruce_planks",
            "stick", "crafting_table", "torch", "furnace", "chest", "ladder", "bowl",
            "oak_slab", "oak_stairs", "oak_fence", "oak_fence_gate", "oak_door", "oak_trapdoor",
            "oak_button", "oak_pressure_plate", "oak_sign", "oak_boat", "oak_sapling",
            // tools
            "wooden_pickaxe", "wooden_axe", "wooden_sword", "wooden_shovel", "wooden_hoe",
            "stone_pickaxe", "stone_axe", "stone_sword", "stone_shovel", "stone_hoe",
            "iron_pickaxe", "iron_axe", "iron_sword", "iron_shovel",
            // stone family
            "cobblestone", "stone", "smooth_stone", "stone_bricks", "cobblestone_slab",
            "cobblestone_stairs", "cobblestone_wall", "stone_button", "stone_pressure_plate",
            "andesite", "diorite", "granite", "cobbled_deepslate", "lever",
            // earth & sand
            "dirt", "coarse_dirt", "sand", "gravel", "sandstone", "smooth_sandstone", "flint",
            "clay_ball", "brick", "bricks", "terracotta",
            // ores & materials
            "coal", "charcoal", "raw_iron", "iron_ingot", "iron_nugget", "raw_copper",
            "copper_ingot", "raw_gold", "gold_ingot", "redstone", "lapis_lazuli",
            // iron utility
            "bucket", "water_bucket", "shears", "flint_and_steel", "iron_bars", "shield",
            // food & farming
            "wheat_seeds", "wheat", "bread", "apple", "sugar_cane", "sugar", "paper",
            "egg", "feather", "porkchop", "cooked_porkchop", "beef", "cooked_beef",
            "chicken", "cooked_chicken", "mutton", "cooked_mutton", "leather", "bone",
            "bone_meal", "string", "arrow", "bow",
            // wool & deco
            "white_wool", "white_bed", "white_carpet", "painting", "item_frame", "flower_pot",
            "dandelion", "poppy",
            // stations
            "campfire", "composter", "barrel", "smoker", "blast_furnace", "grindstone",
            "cartography_table", "fletching_table", "smithing_table", "loom", "stonecutter",
            // glass
            "glass", "glass_pane", "glass_bottle"
    );

    private ItemPools() {}

    public static List<Item> get(Pool pool) {
        return CACHE.computeIfAbsent(pool, ItemPools::build);
    }

    public static Item pick(Pool pool, Random random) {
        List<Item> items = get(pool);
        return items.get(random.nextInt(items.size()));
    }

    public static int size(Pool pool) {
        return get(pool).size();
    }

    private static List<Item> build(Pool pool) {
        List<Item> out = new ArrayList<>();

        if (pool == Pool.EASY) {
            for (String id : EASY_IDS) {
                Item item = Registries.ITEM.get(new Identifier("minecraft", id));
                if (item != Items.AIR) {
                    out.add(item);
                }
            }
            return out;
        }

        for (Item item : Registries.ITEM) {
            String path = Registries.ITEM.getId(item).getPath();
            if (isUnobtainable(path)) continue;
            if (pool == Pool.NORMAL && isExtreme(path)) continue;
            out.add(item);
        }
        return out;
    }

    private static boolean isUnobtainable(String path) {
        if (UNOBTAINABLE.contains(path)) return true;
        for (String s : UNOBTAINABLE_CONTAINS) {
            if (path.contains(s)) return true;
        }
        return false;
    }

    private static boolean isExtreme(String path) {
        if (EXTREME.contains(path)) return true;
        if (isOreBlock(path)) return true;
        for (String s : EXTREME_CONTAINS) {
            if (path.contains(s)) return true;
        }
        return false;
    }
}
