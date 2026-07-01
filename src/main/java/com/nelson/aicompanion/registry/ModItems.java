package com.nelson.aicompanion.registry;

import com.nelson.aicompanion.AiCompanionMod;
import com.nelson.aicompanion.item.NpcSpawnerItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AiCompanionMod.MOD_ID);

    public static final DeferredItem<Item> NPC_SPAWNER = ITEMS.registerItem(
            "npc_spawner",
            properties -> new NpcSpawnerItem(properties.stacksTo(1))
    );

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        AiCompanionMod.LOGGER.info("Registering AI Companion Items");
    }
}
