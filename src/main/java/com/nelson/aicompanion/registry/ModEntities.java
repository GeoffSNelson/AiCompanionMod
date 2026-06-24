package com.nelson.aicompanion.registry;

import com.nelson.aicompanion.AiCompanionMod;
import com.nelson.aicompanion.entity.CompanionEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModEntities {

    private static final Identifier COMPANION_ID = Identifier.of(AiCompanionMod.MOD_ID, "npc");
    private static final RegistryKey<EntityType<?>> COMPANION_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, COMPANION_ID);

    public static final EntityType<CompanionEntity> COMPANION_NPC = Registry.register(
            Registries.ENTITY_TYPE,
            COMPANION_ID,
            EntityType.Builder.create(CompanionEntity::new, SpawnGroup.CREATURE)
                    .dimensions(0.6f, 1.8f)
                    .build(COMPANION_KEY)
    );

    public static void registerEntities() {
        AiCompanionMod.LOGGER.info("Registering AI Companion Entities");
    }
}
