package com.nelson.aicompanion.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nelson.aicompanion.AiCompanionMod;
import com.nelson.aicompanion.entity.CompanionEntity;
import com.nelson.aicompanion.players.CompanionPlayerList;
import com.nelson.aicompanion.players.CompanionRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class CompanionCommands {
    private CompanionCommands() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CompanionCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("aicompanion")
                .then(literal("list")
                        .executes(context -> listCompanions(context.getSource())))
                .then(literal("remove")
                        .then(literal("nearest")
                                .executes(context -> removeNearest(context.getSource())))
                        .then(literal("all")
                                .executes(context -> removeAll(context.getSource())))
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(context -> removeByName(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                )))));
    }

    private static int listCompanions(CommandSourceStack source) {
        List<CompanionEntity> companions = findLoadedCompanions(source.getServer());

        companions.sort(Comparator.comparing(companion -> companion.getName().getString()));
        List<String> entries = new ArrayList<>();
        Set<UUID> loadedUuids = new HashSet<>();
        for (CompanionEntity companion : companions) {
            loadedUuids.add(companion.getUUID());
            entries.add(companion.getName().getString() + " (" + companion.getAppearanceVariantName() + ", loaded)");
        }

        for (CompanionRegistry.Entry entry : CompanionRegistry.listedEntries()) {
            UUID uuid = entry.uuidValue();
            if (uuid != null && !loadedUuids.contains(uuid)) {
                entries.add(entry.name + " (" + entry.role + ", registered)");
            }
        }

        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No AI companions found."), false);
            return 0;
        }

        entries.sort(String::compareToIgnoreCase);
        source.sendSuccess(() -> Component.literal("AI companions: " + String.join(", ", entries)), false);
        return entries.size();
    }

    private static int removeNearest(CommandSourceStack source) {
        List<CompanionEntity> companions = findLoadedCompanions(source.getLevel());
        if (companions.isEmpty()) {
            source.sendFailure(Component.literal("No loaded AI companions found in this dimension."));
            return 0;
        }

        Vec3 sourcePos = source.getPosition();
        CompanionEntity nearest = companions.stream()
                .min(Comparator.comparingDouble(companion -> companion .distanceToSqr(sourcePos)))
                .orElse(null);
        if (nearest == null) {
            source.sendFailure(Component.literal("No loaded AI companions found in this dimension."));
            return 0;
        }

        String name = nearest.getName().getString();
        int pending = AiCompanionMod.cancelPendingRespawnsByName(name);
        removeLoadedCompanion(source.getServer(), nearest);

        source.sendSuccess(() -> Component.literal("Removed nearest AI companion: " + name + pendingSuffix(pending)), true);
        return 1 + pending;
    }

    private static int removeAll(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        List<CompanionEntity> companions = findLoadedCompanions(server);

        for (CompanionEntity companion : companions) {
            companion.discard();
        }

        int cached = CompanionPlayerList.clear(server);
        int pending = AiCompanionMod.cancelPendingRespawns();
        int removed = companions.size();

        source.sendSuccess(() -> Component.literal(
                "Removed " + removed + " loaded AI companion" + plural(removed)
                        + cachedSuffix(cached)
                        + pendingSuffix(pending)
        ), true);
        return removed + pending + cached;
    }

    private static int removeByName(CommandSourceStack source, String rawName) {
        String name = rawName.trim();
        if (name.isBlank()) {
            source.sendFailure(Component.literal("Usage: /aicompanion remove <name>"));
            return 0;
        }

        MinecraftServer server = source.getServer();
        List<CompanionEntity> matches = new ArrayList<>();
        for (CompanionEntity companion : findLoadedCompanions(server)) {
            if (companion.getName().getString().equalsIgnoreCase(name)) {
                matches.add(companion);
            }
        }

        for (CompanionEntity companion : matches) {
            removeLoadedCompanion(server, companion);
        }

        int staleCached = CompanionPlayerList.removeByName(server, name);
        int pending = AiCompanionMod.cancelPendingRespawnsByName(name);
        int removed = matches.size();

        if (removed == 0 && staleCached == 0 && pending == 0) {
            source.sendFailure(Component.literal("No loaded, listed, or pending AI companion named '" + name + "' was found."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Removed AI companion '" + name + "': "
                        + removed + " loaded"
                        + cachedSuffix(staleCached)
                        + pendingSuffix(pending)
        ), true);
        return removed + staleCached + pending;
    }

    private static void removeLoadedCompanion(MinecraftServer server, CompanionEntity companion) {
        CompanionPlayerList.remove(server, companion.getUUID());
        companion.discard();
    }

    private static List<CompanionEntity> findLoadedCompanions(MinecraftServer server) {
        List<CompanionEntity> companions = new ArrayList<>();
        for (ServerLevel world : server.getAllLevels()) {
            companions.addAll(findLoadedCompanions(world));
        }
        return companions;
    }

    private static List<CompanionEntity> findLoadedCompanions(ServerLevel world) {
        List<CompanionEntity> companions = new ArrayList<>();
        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof CompanionEntity companion && !companion.isRemoved()) {
                companions.add(companion);
            }
        }
        return companions;
    }

    private static String plural(int count) {
        return count == 1 ? "" : "s";
    }

    private static String cachedSuffix(int count) {
        return count > 0 ? ", cleared " + count + " tab-list entr" + (count == 1 ? "y" : "ies") : "";
    }

    private static String pendingSuffix(int count) {
        return count > 0 ? ", canceled " + count + " pending respawn" + plural(count) : "";
    }
}
