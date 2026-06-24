package com.nelson.aicompanion.control;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nelson.aicompanion.AiCompanionMod;
import com.nelson.aicompanion.entity.CompanionEntity;
import com.nelson.aicompanion.players.CompanionPlayerList;
import com.nelson.aicompanion.players.CompanionRegistry;
import com.nelson.aicompanion.registry.ModEntities;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class CompanionControlServer {
    private static final int CONTROL_PORT = 8081;
    private static final Gson GSON = new Gson();
    private static final String[] DEFAULT_NAMES = {
            "Gwen", "Bartholomew", "Merlin", "Sir Reginald", "Arthur", "Serena", "Lyra",
            "Mira", "Rowan", "Tessa", "Cedric", "Iris", "Felix", "Nora", "Jasper",
            "Elowen", "Bram", "Vera", "Theo", "Maris", "Poppy", "Alaric", "Juniper",
            "Rook", "Selene", "Cassian", "Mabel", "Orin", "Daphne", "Quinn", "Hazel"
    };

    private static HttpServer controlServer = null;
    private static MinecraftServer minecraftServer = null;

    private CompanionControlServer() {
    }

    public static synchronized void start(MinecraftServer server) {
        minecraftServer = server;
        if (controlServer != null) {
            return;
        }

        try {
            InetSocketAddress address = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), CONTROL_PORT);
            controlServer = HttpServer.create(address, 0);
            controlServer.createContext("/api/companion/spawn", CompanionControlServer::handleSpawn);
            controlServer.createContext("/api/companion/remove", CompanionControlServer::handleRemove);
            controlServer.createContext("/api/companion/clear", CompanionControlServer::handleClear);
            controlServer.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "AI Companion Control");
                thread.setDaemon(true);
                return thread;
            }));
            controlServer.start();
            AiCompanionMod.LOGGER.info("AI Companion control server listening on http://127.0.0.1:" + CONTROL_PORT);
        } catch (IOException e) {
            AiCompanionMod.LOGGER.warn("Could not start AI Companion control server: " + e.getMessage());
            controlServer = null;
        }
    }

    public static synchronized void stop() {
        if (controlServer != null) {
            controlServer.stop(0);
            controlServer = null;
        }
        minecraftServer = null;
    }

    private static void handleSpawn(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST")) return;
        JsonObject request = readJson(exchange);
        runOnServer(exchange, () -> spawnCompanion(request));
    }

    private static void handleRemove(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST")) return;
        JsonObject request = readJson(exchange);
        runOnServer(exchange, () -> removeCompanion(request));
    }

    private static void handleClear(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST")) return;
        readJson(exchange);
        runOnServer(exchange, CompanionControlServer::clearCompanions);
    }

    private static JsonObject spawnCompanion(JsonObject request) {
        JsonObject response = new JsonObject();
        if (minecraftServer == null) {
            return error("Minecraft server is not ready.");
        }

        ServerPlayerEntity targetPlayer = findTargetPlayer(getString(request, "player"));
        ServerWorld world = targetPlayer != null ? (ServerWorld) targetPlayer.getEntityWorld() : minecraftServer.getOverworld();
        BlockPos origin = targetPlayer != null
                ? targetPlayer.getBlockPos().add(1, 0, 1)
                : world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ORIGIN);
        BlockPos spawnPos = findSafeSpawnPos(world, origin);

        CompanionEntity companion = ModEntities.COMPANION_NPC.create(world);
        if (companion == null) {
            return error("Could not create companion entity.");
        }

        String name = chooseName(world, getString(request, "name"));
        int variant = chooseVariant(world, getString(request, "role"));

        companion.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0.0F, 0.0F);
        companion.setCustomName(Text.literal(name));
        companion.setCustomNameVisible(true);
        companion.setAppearanceVariant(variant);
        companion.applyStartingLoadout();
        companion.setHomePosition(spawnPos);
        world.spawnEntity(companion);
        CompanionRegistry.upsert(minecraftServer, companion, "loaded");

        minecraftServer.getPlayerManager().broadcast(
                Text.literal("§a[AI Companion] " + name + " joined the team."), false);

        response.addProperty("ok", true);
        response.addProperty("name", name);
        response.addProperty("role", companion.getAppearanceVariantName());
        response.addProperty("uuid", companion.getUuid().toString());
        response.addProperty("x", spawnPos.getX());
        response.addProperty("y", spawnPos.getY());
        response.addProperty("z", spawnPos.getZ());
        return response;
    }

    private static JsonObject removeCompanion(JsonObject request) {
        String name = getString(request, "name").trim();
        if (name.isBlank()) {
            return error("Choose a companion to remove.");
        }

        int loadedRemoved = 0;
        for (CompanionEntity companion : findLoadedCompanions()) {
            if (companion.getName().getString().equalsIgnoreCase(name)) {
                CompanionPlayerList.remove(minecraftServer, companion.getUuid());
                companion.discard();
                loadedRemoved++;
            }
        }

        int registryRemoved = CompanionRegistry.removeByName(name);
        int pendingRemoved = AiCompanionMod.cancelPendingRespawnsByName(name);
        int removed = Math.max(loadedRemoved, registryRemoved) + pendingRemoved;

        if (removed == 0) {
            return error("No companion named '" + name + "' was found.");
        }

        minecraftServer.getPlayerManager().broadcast(
                Text.literal("§e[AI Companion] " + name + " left the team."), false);

        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("removed", removed);
        response.addProperty("name", name);
        return response;
    }

    private static JsonObject clearCompanions() {
        int loadedRemoved = 0;
        for (CompanionEntity companion : findLoadedCompanions()) {
            companion.discard();
            loadedRemoved++;
        }

        int listedRemoved = CompanionPlayerList.clear(minecraftServer);
        int pendingRemoved = AiCompanionMod.cancelPendingRespawns();
        int removed = Math.max(loadedRemoved, listedRemoved) + pendingRemoved;

        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("removed", removed);
        return response;
    }

    private static List<CompanionEntity> findLoadedCompanions() {
        List<CompanionEntity> companions = new ArrayList<>();
        if (minecraftServer == null) {
            return companions;
        }

        for (ServerWorld world : minecraftServer.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof CompanionEntity companion && !companion.isRemoved()) {
                    companions.add(companion);
                }
            }
        }
        return companions;
    }

    private static ServerPlayerEntity findTargetPlayer(String requestedPlayer) {
        if (minecraftServer == null) return null;
        if (requestedPlayer != null && !requestedPlayer.isBlank()) {
            ServerPlayerEntity player = minecraftServer.getPlayerManager().getPlayer(requestedPlayer.trim());
            if (player != null) {
                return player;
            }
        }

        List<ServerPlayerEntity> players = minecraftServer.getPlayerManager().getPlayerList();
        return players.isEmpty() ? null : players.get(0);
    }

    private static String chooseName(ServerWorld world, String requestedName) {
        Set<String> usedNames = new HashSet<>(CompanionRegistry.listedNames());
        for (CompanionEntity companion : findLoadedCompanions()) {
            usedNames.add(companion.getName().getString());
        }

        String base = sanitizeName(requestedName);
        if (!base.isBlank() && !containsIgnoreCase(usedNames, base)) {
            return base;
        }

        int start = world.random.nextInt(DEFAULT_NAMES.length);
        for (int i = 0; i < DEFAULT_NAMES.length; i++) {
            String candidate = DEFAULT_NAMES[(start + i) % DEFAULT_NAMES.length];
            if (!containsIgnoreCase(usedNames, candidate)) {
                return candidate;
            }
        }

        if (base.isBlank()) {
            base = DEFAULT_NAMES[world.random.nextInt(DEFAULT_NAMES.length)];
        }

        int suffix = usedNames.size() + 1;
        String candidate = base + " " + suffix;
        while (containsIgnoreCase(usedNames, candidate)) {
            suffix++;
            candidate = base + " " + suffix;
        }
        return candidate;
    }

    private static int chooseVariant(ServerWorld world, String requestedRole) {
        int requested = CompanionEntity.getAppearanceVariantIndex(requestedRole);
        if (requested >= 0) {
            return requested;
        }

        int variantCount = CompanionEntity.getAppearanceVariantCount();
        int[] counts = CompanionRegistry.listedVariantCounts(variantCount);
        for (CompanionEntity companion : findLoadedCompanions()) {
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

        int chosen = world.random.nextInt(Math.max(1, matches));
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] == bestCount) {
                if (chosen == 0) {
                    return i;
                }
                chosen--;
            }
        }

        return world.random.nextInt(variantCount);
    }

    private static BlockPos findSafeSpawnPos(ServerWorld world, BlockPos origin) {
        for (int radius = 0; radius <= 8; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos feet = origin.add(x, 0, z);
                    if (isSafeSpawnPos(world, feet)) {
                        return feet;
                    }
                    BlockPos surface = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, feet);
                    if (isSafeSpawnPos(world, surface)) {
                        return surface;
                    }
                }
            }
        }
        return origin;
    }

    private static boolean isSafeSpawnPos(ServerWorld world, BlockPos feet) {
        BlockPos head = feet.up();
        BlockPos ground = feet.down();
        Box box = new Box(feet.getX(), feet.getY(), feet.getZ(), feet.getX() + 1.0, feet.getY() + 2.0, feet.getZ() + 1.0);
        return world.getBlockState(feet).isAir()
                && world.getBlockState(head).isAir()
                && !world.getBlockState(ground).isAir()
                && world.getBlockState(ground).getFluidState().isEmpty()
                && world.getOtherEntities(null, box, entity -> entity.isAlive() && !entity.isRemoved()).isEmpty();
    }

    private static void runOnServer(HttpExchange exchange, ServerTask task) throws IOException {
        if (minecraftServer == null) {
            writeJson(exchange, 503, error("Minecraft server is not ready."));
            return;
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        minecraftServer.execute(() -> {
            try {
                future.complete(task.run());
            } catch (Exception e) {
                future.complete(error(e.getMessage()));
            }
        });

        try {
            JsonObject response = future.get(4, TimeUnit.SECONDS);
            writeJson(exchange, response.has("ok") && response.get("ok").getAsBoolean() ? 200 : 400, response);
        } catch (Exception e) {
            writeJson(exchange, 504, error("Timed out waiting for the Minecraft server thread."));
        }
    }

    private static boolean requireMethod(HttpExchange exchange, String method) throws IOException {
        if (method.equalsIgnoreCase(exchange.getRequestMethod())) {
            return true;
        }
        writeJson(exchange, 405, error("Use " + method + "."));
        return false;
    }

    private static JsonObject readJson(HttpExchange exchange) {
        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static void writeJson(HttpExchange exchange, int status, JsonObject body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }

    private static JsonObject error(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty("error", message == null || message.isBlank() ? "Unknown error." : message);
        return response;
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private static boolean containsIgnoreCase(Set<String> values, String candidate) {
        for (String value : values) {
            if (value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String sanitizeName(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.length() > 24) {
            trimmed = trimmed.substring(0, 24);
        }
        return trimmed.replaceAll("[\\r\\n\\t]", " ");
    }

    @FunctionalInterface
    private interface ServerTask {
        JsonObject run();
    }
}
