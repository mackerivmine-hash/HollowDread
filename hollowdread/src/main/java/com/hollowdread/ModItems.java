package com.hollowdread;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public final class ModItems {
    public static final Item WARDING_CHARM = register("warding_charm",
            new WardingCharmItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

    public static final Item STALKER_SPAWN_EGG = register("stalker_spawn_egg",
            new SpawnEggItem(ModEntities.STALKER, 0x0A0A0A, 0x8B0000, new Item.Settings()));

    private ModItems() {}

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(HollowDread.MOD_ID, name), item);
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> e.add(WARDING_CHARM));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(e -> e.add(STALKER_SPAWN_EGG));
    }
}
