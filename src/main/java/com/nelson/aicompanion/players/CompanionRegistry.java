package com.nelson.aicompanion.players;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.nelson.aicompanion.AiCompanionMod;
import com.nelson.aicompanion.entity.CompanionEntity;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CompanionRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ENTRY_MAP_TYPE = new TypeToken<Map<String, Entry>>() {
    }.getType();
    private static final Map<UUID, Entry> entries = new HashMap<>();
    private static boolean loaded = false;

    private CompanionRegistry() {
    }

    public static synchronized void upsert(MinecraftServer server, CompanionEntity companion, String status) {
        load();

        UUID uuid = companion.getUUID();
        String companionName = companion.getName().getString();
        entries.entrySet().removeIf(existing -> !existing.getKey().equals(uuid)
                && existing.getValue().name != null
                && existing.getValue().name.equalsIgnoreCase(companionName));

        Entry entry = entries.computeIfAbsent(uuid, ignored -> new Entry());
        entry.uuid = uuid.toString();
        entry.name = companionName;
        entry.role = companion.getAppearanceVariantName();
        entry.variant = companion.getAppearanceVariant();
        entry.dimension = companion.level().dimension().location().toString();
        entry.x = companion.getX();
        entry.y = companion.getY();
        entry.z = companion.getZ();
        entry.health = companion.getHealth();
        entry.status = status == null || status.isBlank() ? "loaded" : status;
        entry.listed = true;
        entry.lastSeenTick = server.getTickCount();

        save();
    }

    public static synchronized void markDead(MinecraftServer server, UUID uuid) {
        load();
        Entry entry = entries.get(uuid);
        if (entry == null) return;

        entry.status = "dead";
        entry.listed = false;
        entry.lastSeenTick = server.getTickCount();
        save();
    }

    public static synchronized boolean remove(UUID uuid) {
        load();
        boolean removed = entries.remove(uuid) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public static synchronized int removeByName(String name) {
        load();
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) return 0;

        List<UUID> removed = new ArrayList<>();
        for (Map.Entry<UUID, Entry> entry : entries.entrySet()) {
            if (entry.getValue().name != null && entry.getValue().name.equalsIgnoreCase(trimmed)) {
                removed.add(entry.getKey());
            }
        }

        for (UUID uuid : removed) {
            entries.remove(uuid);
        }
        if (!removed.isEmpty()) {
            save();
        }
        return removed.size();
    }

    public static synchronized int clear() {
        load();
        int count = entries.size();
        entries.clear();
        if (count > 0) {
            save();
        }
        return count;
    }

    public static synchronized List<Entry> listedEntries() {
        load();
        List<Entry> listed = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.isListed()) {
                listed.add(entry.copy());
            }
        }
        listed.sort(Comparator.comparing((Entry entry) -> entry.name).thenComparing(entry -> entry.uuid));
        return listed;
    }

    public static synchronized Set<String> listedNames() {
        load();
        Set<String> names = new HashSet<>();
        for (Entry entry : entries.values()) {
            if (entry.isListed() && entry.name != null && !entry.name.isBlank()) {
                names.add(entry.name);
            }
        }
        return names;
    }

    public static synchronized int[] listedVariantCounts(int variantCount) {
        load();
        int[] counts = new int[Math.max(1, variantCount)];
        for (Entry entry : entries.values()) {
            if (!entry.isListed()) continue;
            if (entry.variant >= 0 && entry.variant < counts.length) {
                counts[entry.variant]++;
            }
        }
        return counts;
    }

    public static synchronized Set<UUID> allUuids() {
        load();
        return new HashSet<>(entries.keySet());
    }

    private static void load() {
        if (loaded) return;
        loaded = true;

        Path path = registryPath();
        if (!Files.exists(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, Entry> diskEntries = GSON.fromJson(reader, ENTRY_MAP_TYPE);
            if (diskEntries == null) return;

            for (Entry entry : diskEntries.values()) {
                UUID uuid = entry.uuidValue();
                if (uuid != null) {
                    entries.put(uuid, entry);
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            AiCompanionMod.LOGGER.warn("Could not load AI companion registry: " + e.getMessage());
        }
    }

    private static void save() {
        Path path = registryPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                Map<String, Entry> diskEntries = new HashMap<>();
                for (Map.Entry<UUID, Entry> entry : entries.entrySet()) {
                    diskEntries.put(entry.getKey().toString(), entry.getValue());
                }
                GSON.toJson(diskEntries, ENTRY_MAP_TYPE, writer);
            }
        } catch (IOException e) {
            AiCompanionMod.LOGGER.warn("Could not save AI companion registry: " + e.getMessage());
        }
    }

    private static Path registryPath() {
        return FMLPaths.GAMEDIR.get()
                .resolve("config")
                .resolve("aicompanion_companions.json");
    }

    public static final class Entry {
        public String uuid = "";
        public String name = "";
        public String role = "companion";
        public int variant = 0;
        public String dimension = "";
        public double x = 0.0;
        public double y = 0.0;
        public double z = 0.0;
        public float health = 20.0F;
        public String status = "loaded";
        public boolean listed = true;
        public long lastSeenTick = 0L;

        public UUID uuidValue() {
            if (uuid == null || uuid.isBlank()) return null;
            try {
                return UUID.fromString(uuid);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        public boolean isListed() {
            return listed
                    && uuidValue() != null
                    && name != null
                    && !name.isBlank()
                    && !"dead".equals(status);
        }

        private Entry copy() {
            Entry copy = new Entry();
            copy.uuid = uuid;
            copy.name = name;
            copy.role = role;
            copy.variant = variant;
            copy.dimension = dimension;
            copy.x = x;
            copy.y = y;
            copy.z = z;
            copy.health = health;
            copy.status = status;
            copy.listed = listed;
            copy.lastSeenTick = lastSeenTick;
            return copy;
        }
    }
}
