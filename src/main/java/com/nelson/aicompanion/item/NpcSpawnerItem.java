package com.nelson.aicompanion.item;

import com.nelson.aicompanion.AiCompanionMod;
import com.nelson.aicompanion.entity.CompanionEntity;
import com.nelson.aicompanion.players.CompanionRegistry;
import com.nelson.aicompanion.registry.ModEntities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NpcSpawnerItem extends Item {
    private static final String[] COMPANION_NAMES = {
            "Gwen", "Bartholomew", "Merlin", "Sir Reginald", "Arthur", "Serena", "Lyra",
            "Mira", "Rowan", "Tessa", "Cedric", "Iris", "Felix", "Nora", "Jasper",
            "Elowen", "Bram", "Vera", "Theo", "Maris", "Poppy", "Alaric", "Juniper",
            "Rook", "Selene", "Cassian", "Mabel", "Orin", "Daphne", "Quinn", "Hazel"
    };

    public NpcSpawnerItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (!context.getWorld().isClient()) {
            ServerWorld world = (ServerWorld) context.getWorld();
            BlockPos spawnPos = context.getBlockPos().offset(context.getSide());
            PlayerEntity player = context.getPlayer();

            // Create and spawn the companion NPC directly where clicked
            CompanionEntity npc = ModEntities.COMPANION_NPC.create(world);
            if (npc != null) {
                npc.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0.0f, 0.0f);
                
                // Prefer unique live names and balanced roles so a group feels like individuals.
                List<CompanionEntity> nearbyCompanions = getLoadedCompanionsNear(world, spawnPos);
                String randomName = chooseUnusedName(world, nearbyCompanions);
                npc.setCustomName(net.minecraft.text.Text.literal(randomName));
                npc.setCustomNameVisible(true);
                
                // Give each companion a stable role, look, and starting loadout.
                npc.setAppearanceVariant(chooseLeastUsedVariant(world, nearbyCompanions));
                npc.applyStartingLoadout();
                npc.setHomePosition(spawnPos); // register spawn point as home base

                world.spawnEntity(npc);
                CompanionRegistry.upsert(world.getServer(), npc, "loaded");
                
                AiCompanionMod.LOGGER.info(
                        "Player spawned a new " + npc.getAppearanceVariantName()
                                + " AI Companion named " + randomName
                                + " at " + spawnPos.toShortString()
                );

                // Consume the item if the player is not in creative mode
                if (player != null && !player.isCreative()) {
                    context.getStack().decrement(1);
                }
                
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }

    private List<CompanionEntity> getLoadedCompanionsNear(ServerWorld world, BlockPos spawnPos) {
        Box searchBox = new Box(spawnPos).expand(512.0);
        return world.getEntitiesByClass(
                CompanionEntity.class,
                searchBox,
                companion -> companion.isAlive() && !companion.isRemoved()
        );
    }

    private String chooseUnusedName(ServerWorld world, List<CompanionEntity> companions) {
        Set<String> usedNames = new HashSet<>(CompanionRegistry.listedNames());
        for (CompanionEntity companion : companions) {
            usedNames.add(companion.getName().getString());
        }

        int start = world.random.nextInt(COMPANION_NAMES.length);
        for (int i = 0; i < COMPANION_NAMES.length; i++) {
            String candidate = COMPANION_NAMES[(start + i) % COMPANION_NAMES.length];
            if (!usedNames.contains(candidate)) {
                return candidate;
            }
        }

        int suffix = companions.size() + 1;
        String base = COMPANION_NAMES[world.random.nextInt(COMPANION_NAMES.length)];
        String candidate = base + " " + suffix;
        while (usedNames.contains(candidate)) {
            suffix++;
            candidate = base + " " + suffix;
        }
        return candidate;
    }

    private int chooseLeastUsedVariant(ServerWorld world, List<CompanionEntity> companions) {
        int variantCount = CompanionEntity.getAppearanceVariantCount();
        int[] counts = CompanionRegistry.listedVariantCounts(variantCount);
        for (CompanionEntity companion : companions) {
            counts[companion.getAppearanceVariant()]++;
        }

        int bestCount = Integer.MAX_VALUE;
        int matches = 0;
        for (int count : counts) {
            if (count < bestCount) {
                bestCount = count;
                matches = 1;
            } else if (count == bestCount) {
                matches++;
            }
        }

        int chosenMatch = world.random.nextInt(Math.max(1, matches));
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] == bestCount) {
                if (chosenMatch == 0) {
                    return i;
                }
                chosenMatch--;
            }
        }

        return world.random.nextInt(variantCount);
    }
}
