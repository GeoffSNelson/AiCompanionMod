package com.nelson.aicompanion.registry;

import com.nelson.aicompanion.AiCompanionMod;
import com.nelson.aicompanion.item.NpcSpawnerItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {

    public static final Item NPC_SPAWNER = Registry.register(
            Registries.ITEM,
            Identifier.of(AiCompanionMod.MOD_ID, "npc_spawner"),
            new NpcSpawnerItem(new Item.Settings().maxCount(1)) // Can only hold 1 per stack
    );

    public static void registerItems() {
        AiCompanionMod.LOGGER.info("Registering AI Companion Items");
    }
}
