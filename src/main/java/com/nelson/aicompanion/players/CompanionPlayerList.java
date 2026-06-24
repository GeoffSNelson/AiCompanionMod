package com.nelson.aicompanion.players;

import com.mojang.authlib.GameProfile;
import com.nelson.aicompanion.entity.CompanionEntity;
import com.nelson.aicompanion.mixin.PlayerListS2CPacketAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

public final class CompanionPlayerList {
    private static final int SYNC_INTERVAL_TICKS = 100;
    private static final int REMOVE_RETRY_SYNCS = 6;
    private static final EnumSet<PlayerListS2CPacket.Action> ADD_ACTIONS = EnumSet.of(
            PlayerListS2CPacket.Action.ADD_PLAYER,
            PlayerListS2CPacket.Action.UPDATE_LISTED,
            PlayerListS2CPacket.Action.UPDATE_GAME_MODE,
            PlayerListS2CPacket.Action.UPDATE_LATENCY,
            PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME,
            PlayerListS2CPacket.Action.UPDATE_LIST_ORDER,
            PlayerListS2CPacket.Action.UPDATE_HAT
    );
    private static final String[] REPAIR_NAMES = {
            "Gwen", "Bartholomew", "Merlin", "Sir Reginald", "Arthur", "Serena", "Lyra",
            "Mira", "Rowan", "Tessa", "Cedric", "Iris", "Felix", "Nora", "Jasper",
            "Elowen", "Bram", "Vera", "Theo", "Maris", "Poppy", "Alaric", "Juniper",
            "Rook", "Selene", "Cassian", "Mabel", "Orin", "Daphne", "Quinn", "Hazel"
    };

    private static final Map<UUID, CachedCompanion> advertisedCompanions = new HashMap<>();
    private static final Map<UUID, Integer> removalRetries = new HashMap<>();

    private CompanionPlayerList() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % SYNC_INTERVAL_TICKS == 0) {
                sync(server);
            }
        });
    }

    public static void remove(MinecraftServer server, UUID uuid) {
        CompanionRegistry.remove(uuid);
        unlist(server, uuid);
    }

    public static void unlist(MinecraftServer server, UUID uuid) {
        advertisedCompanions.remove(uuid);
        queueRemoval(server, List.of(uuid));
    }

    public static int removeByName(MinecraftServer server, String name) {
        List<UUID> removedUuids = new ArrayList<>();
        for (CachedCompanion companion : advertisedCompanions.values()) {
            if (companion.name().equalsIgnoreCase(name)) {
                removedUuids.add(companion.uuid());
            }
        }

        for (CompanionRegistry.Entry entry : CompanionRegistry.listedEntries()) {
            if (entry.name.equalsIgnoreCase(name)) {
                UUID uuid = entry.uuidValue();
                if (uuid != null && !removedUuids.contains(uuid)) {
                    removedUuids.add(uuid);
                }
            }
        }

        int registryRemoved = CompanionRegistry.removeByName(name);
        if (removedUuids.isEmpty() && registryRemoved == 0) {
            return 0;
        }

        for (UUID uuid : removedUuids) {
            advertisedCompanions.remove(uuid);
        }
        if (!removedUuids.isEmpty()) {
            queueRemoval(server, removedUuids);
        }
        return Math.max(removedUuids.size(), registryRemoved);
    }

    public static int clear(MinecraftServer server) {
        List<UUID> removedUuids = new ArrayList<>(advertisedCompanions.keySet());
        for (UUID uuid : CompanionRegistry.allUuids()) {
            if (!removedUuids.contains(uuid)) {
                removedUuids.add(uuid);
            }
        }
        for (CompanionRegistry.Entry entry : CompanionRegistry.listedEntries()) {
            UUID uuid = entry.uuidValue();
            if (uuid != null && !removedUuids.contains(uuid)) {
                removedUuids.add(uuid);
            }
        }

        int registryRemoved = CompanionRegistry.clear();
        if (removedUuids.isEmpty() && registryRemoved == 0) {
            return 0;
        }

        advertisedCompanions.clear();
        if (!removedUuids.isEmpty()) {
            queueRemoval(server, removedUuids);
        }
        return Math.max(removedUuids.size(), registryRemoved);
    }

    private static void sync(MinecraftServer server) {
        List<CompanionEntity> companions = new ArrayList<>();

        for (ServerWorld world : server.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof CompanionEntity companion
                        && companion.isAlive()
                        && !companion.isRemoved()) {
                    companions.add(companion);
                }
            }
        }

        repairLoadedCompanionIdentity(companions);

        Map<UUID, CachedCompanion> desiredCompanions = new HashMap<>();

        for (CompanionEntity companion : companions) {
            UUID uuid = companion.getUuid();
            CompanionRegistry.upsert(server, companion, "loaded");
            desiredCompanions.put(uuid, CachedCompanion.from(companion));
        }

        for (CompanionRegistry.Entry entry : CompanionRegistry.listedEntries()) {
            UUID uuid = entry.uuidValue();
            if (uuid != null && !desiredCompanions.containsKey(uuid)) {
                desiredCompanions.put(uuid, CachedCompanion.from(entry));
            }
        }

        List<UUID> staleUuids = new ArrayList<>();
        for (UUID uuid : advertisedCompanions.keySet()) {
            if (!desiredCompanions.containsKey(uuid)) {
                staleUuids.add(uuid);
            }
        }
        if (!staleUuids.isEmpty()) {
            queueRemoval(server, staleUuids);
        }

        advertisedCompanions.clear();
        advertisedCompanions.putAll(desiredCompanions);

        flushRemovalRetries(server);

        List<PlayerListS2CPacket.Entry> currentEntries = new ArrayList<>();
        List<CachedCompanion> cachedCompanions = new ArrayList<>(advertisedCompanions.values());
        cachedCompanions.sort(Comparator.comparing(CachedCompanion::name).thenComparing(cached -> cached.uuid().toString()));
        for (CachedCompanion cached : cachedCompanions) {
            currentEntries.add(createEntry(cached));
        }

        if (!currentEntries.isEmpty()) {
            sendToAll(server.getPlayerManager().getPlayerList(), createAddPacket(currentEntries));
        }
    }

    private static void queueRemoval(MinecraftServer server, Collection<UUID> uuids) {
        if (uuids.isEmpty()) return;

        List<UUID> unique = new ArrayList<>();
        for (UUID uuid : uuids) {
            if (uuid == null || unique.contains(uuid)) continue;
            unique.add(uuid);
            removalRetries.put(uuid, REMOVE_RETRY_SYNCS);
        }
        if (!unique.isEmpty()) {
            sendToAll(server.getPlayerManager().getPlayerList(), new PlayerRemoveS2CPacket(unique));
        }
    }

    private static void flushRemovalRetries(MinecraftServer server) {
        if (removalRetries.isEmpty()) return;

        List<UUID> uuids = new ArrayList<>(removalRetries.keySet());
        sendToAll(server.getPlayerManager().getPlayerList(), new PlayerRemoveS2CPacket(uuids));

        for (UUID uuid : new ArrayList<>(removalRetries.keySet())) {
            int remaining = removalRetries.get(uuid) - 1;
            if (remaining <= 0 || advertisedCompanions.containsKey(uuid)) {
                removalRetries.remove(uuid);
            } else {
                removalRetries.put(uuid, remaining);
            }
        }
    }

    private static void repairLoadedCompanionIdentity(List<CompanionEntity> companions) {
        companions.sort(Comparator.comparing(companion -> companion.getUuid().toString()));
        repairDuplicateNames(companions);
        repairFlatAppearanceVariants(companions);
    }

    private static void repairDuplicateNames(List<CompanionEntity> companions) {
        Set<String> usedNames = new HashSet<>();
        for (CompanionEntity companion : companions) {
            String currentName = companion.getName().getString();
            if (usedNames.add(currentName)) {
                continue;
            }

            String repairedName = chooseUnusedRepairName(usedNames, companions.size());
            companion.setCustomName(Text.literal(repairedName));
            companion.setCustomNameVisible(true);
            usedNames.add(repairedName);
        }
    }

    private static String chooseUnusedRepairName(Set<String> usedNames, int companionCount) {
        for (String name : REPAIR_NAMES) {
            if (!usedNames.contains(name)) {
                return name;
            }
        }

        int suffix = companionCount + 1;
        String candidate = "Companion " + suffix;
        while (usedNames.contains(candidate)) {
            suffix++;
            candidate = "Companion " + suffix;
        }
        return candidate;
    }

    private static void repairFlatAppearanceVariants(List<CompanionEntity> companions) {
        if (companions.size() < 2) return;

        int firstVariant = companions.get(0).getAppearanceVariant();
        for (CompanionEntity companion : companions) {
            if (companion.getAppearanceVariant() != firstVariant) {
                return;
            }
        }

        int variantCount = CompanionEntity.getAppearanceVariantCount();
        for (int i = 0; i < companions.size(); i++) {
            CompanionEntity companion = companions.get(i);
            companion.setAppearanceVariant(i % variantCount);
            companion.applyStartingLoadout();
        }
    }

    private static PlayerListS2CPacket.Entry createEntry(CachedCompanion companion) {
        UUID uuid = companion.uuid();
        Text displayName = Text.literal("[AI " + formatRole(companion.role()) + "] " + companion.name());

        return new PlayerListS2CPacket.Entry(
                uuid,
                new GameProfile(uuid, createProfileName(uuid)),
                true,
                0,
                GameMode.SURVIVAL,
                displayName,
                true,
                1000,
                null
        );
    }

    private static PlayerListS2CPacket createAddPacket(List<PlayerListS2CPacket.Entry> entries) {
        PlayerListS2CPacket packet = new PlayerListS2CPacket(EnumSet.of(PlayerListS2CPacket.Action.ADD_PLAYER), List.of());
        PlayerListS2CPacketAccessor accessor = (PlayerListS2CPacketAccessor) packet;
        accessor.aicompanion$setActions(EnumSet.copyOf(ADD_ACTIONS));
        accessor.aicompanion$setEntries(List.copyOf(entries));
        return packet;
    }

    private static void sendToAll(Collection<ServerPlayerEntity> players, net.minecraft.network.packet.Packet<?> packet) {
        for (ServerPlayerEntity player : players) {
            player.networkHandler.sendPacket(packet);
        }
    }

    private static String createProfileName(UUID uuid) {
        return "AI_" + uuid.toString().replace("-", "").substring(0, 13);
    }

    private static String formatRole(String role) {
        if (role == null || role.isBlank()) {
            return "Companion";
        }
        return role.substring(0, 1).toUpperCase() + role.substring(1);
    }

    private record CachedCompanion(UUID uuid, String name, String role) {
        private static CachedCompanion from(CompanionEntity companion) {
            return new CachedCompanion(
                    companion.getUuid(),
                    companion.getName().getString(),
                    companion.getAppearanceVariantName()
            );
        }

        private static CachedCompanion from(CompanionRegistry.Entry entry) {
            UUID uuid = entry.uuidValue();
            return new CachedCompanion(
                    uuid,
                    entry.name,
                    entry.role == null || entry.role.isBlank() ? "companion" : entry.role
            );
        }
    }
}
