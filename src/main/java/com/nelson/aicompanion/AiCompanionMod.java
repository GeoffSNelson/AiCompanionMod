package com.nelson.aicompanion;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import com.google.gson.JsonObject;
import com.nelson.aicompanion.api.AiClient;
import com.nelson.aicompanion.chat.ChatListener;
import com.nelson.aicompanion.command.CompanionCommands;
import com.nelson.aicompanion.control.CompanionControlServer;
import com.nelson.aicompanion.entity.CompanionEntity;
import com.nelson.aicompanion.players.CompanionPlayerList;
import com.nelson.aicompanion.players.CompanionRegistry;
import com.nelson.aicompanion.registry.ModEntities;
import com.nelson.aicompanion.registry.ModItems;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class AiCompanionMod implements ModInitializer {
	public static final String MOD_ID = "aicompanion";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static AiClient AI_CLIENT;

	// Pending companion respawns: checked each server tick
	private static final List<PendingRespawn> pendingRespawns = new ArrayList<>();

	public record PendingRespawn(ServerWorld world, String name, int variant, BlockPos pos, boolean hasHome, int spawnAtTick) {}

	public static void scheduleRespawn(ServerWorld world, String name, int variant, BlockPos pos, boolean hasHome, int delayTicks) {
		int spawnAt = (int)(world.getServer().getTicks()) + delayTicks;
		pendingRespawns.removeIf(respawn -> respawn.name().equalsIgnoreCase(name));
		pendingRespawns.add(new PendingRespawn(world, name, variant, pos, hasHome, spawnAt));
	}

	public static int cancelPendingRespawns() {
		int canceled = pendingRespawns.size();
		pendingRespawns.clear();
		return canceled;
	}

	public static int cancelPendingRespawnsByName(String name) {
		int before = pendingRespawns.size();
		pendingRespawns.removeIf(respawn -> respawn.name().equalsIgnoreCase(name));
		return before - pendingRespawns.size();
	}

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing AI Companion Mod...");

		ModItems.registerItems();
		ModEntities.registerEntities();
		FabricDefaultAttributeRegistry.register(ModEntities.COMPANION_NPC, CompanionEntity.createCompanionAttributes());

		AI_CLIENT = new AiClient(8080);
		LOGGER.info("AI HTTP Client connected to local API port 8080");

		ChatListener.register();
		CompanionPlayerList.register();
		CompanionCommands.register();

		ServerLifecycleEvents.SERVER_STARTED.register(CompanionControlServer::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> CompanionControlServer.stop());

		// Process respawn queue each tick
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (pendingRespawns.isEmpty()) return;
			int now = (int) server.getTicks();
			Iterator<PendingRespawn> it = pendingRespawns.iterator();
			while (it.hasNext()) {
				PendingRespawn r = it.next();
				if (now >= r.spawnAtTick()) {
					it.remove();
					spawnCompanion(r);
				}
			}
		});

		// Broadcast to nearby companions when a player dies
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (!(entity instanceof ServerPlayerEntity deadPlayer)) return;
			if (deadPlayer.isAlive()) return; // Only fire on actual death unload

			JsonObject payload = new JsonObject();
			payload.addProperty("event", "player_died");
			payload.addProperty("detail", deadPlayer.getName().getString());

			// Find a nearby companion to react
			world.iterateEntities().forEach(e -> {
				if (!(e instanceof CompanionEntity companion)) return;
				if (companion.squaredDistanceTo(deadPlayer) > 400.0) return; // 20 block radius

				UUID companionUuid = companion.getUuid();
				String companionName = companion.getName().getString();
				payload.addProperty("npcName", companionName);
				AI_CLIENT.sendEvent(payload).thenAccept(response -> {
					if (response == null || !response.has("reply")) return;
					String reply = response.get("reply").getAsString();
					if (reply == null || reply.isBlank() || "IGNORE".equals(reply)) return;
					world.getServer().execute(() -> {
						if (!companion.isAlive() || companion.isRemoved() || !companion.getUuid().equals(companionUuid)) {
							LOGGER.debug("Ignoring stale player-death reply for " + companionName + " after death or unload.");
							return;
						}
						world.getServer().getPlayerManager().broadcast(
								Text.literal("§e<" + companion.getName().getString() + "> §f" + reply), false);
					});
				});
			});
		});
	}

	private static void spawnCompanion(PendingRespawn r) {
		ServerWorld world = r.world();
		if (world == null) return;
		MinecraftServer server = world.getServer();

		for (ServerWorld loadedWorld : server.getWorlds()) {
			for (net.minecraft.entity.Entity entity : loadedWorld.iterateEntities()) {
				if (entity instanceof CompanionEntity existing
						&& existing.isAlive()
						&& !existing.isRemoved()
						&& existing.getName().getString().equalsIgnoreCase(r.name())) {
					CompanionRegistry.upsert(server, existing, "loaded");
					LOGGER.warn("Skipped duplicate respawn for living companion: " + r.name());
					return;
				}
			}
		}

		// Remove any stale UUID left by the dead entity before advertising the replacement.
		CompanionPlayerList.removeByName(server, r.name());

		// Find a safe spawn position at or near the stored pos
		BlockPos spawnPos = findSafeSpawnPos(world, r.pos());
		if (spawnPos == null) spawnPos = r.pos();

		CompanionEntity companion = ModEntities.COMPANION_NPC.create(world);
		if (companion == null) return;

		companion.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
		companion.setCustomName(Text.literal(r.name()));
		companion.setCustomNameVisible(true);
		companion.setAppearanceVariant(r.variant());
		if (r.hasHome()) {
			companion.setHomePosition(r.pos());
		}
		companion.applyStartingLoadout();
		world.spawnEntity(companion);
		CompanionRegistry.upsert(server, companion, "loaded");

		server.getPlayerManager().broadcast(
				Text.literal("§a[AI Companion] " + r.name() + " has returned!"), false);
		LOGGER.info("Respawned companion: " + r.name() + " at " + spawnPos.toShortString());
	}

	private static BlockPos findSafeSpawnPos(ServerWorld world, BlockPos origin) {
		for (int radius = 0; radius <= 5; radius++) {
			for (int x = -radius; x <= radius; x++) {
				for (int z = -radius; z <= radius; z++) {
					BlockPos feet = origin.add(x, 0, z);
					BlockPos head = feet.up();
					BlockPos ground = feet.down();
					if (world.getBlockState(feet).isAir()
							&& world.getBlockState(head).isAir()
							&& !world.getBlockState(ground).isAir()
							&& world.getBlockState(ground).getFluidState().isEmpty()) {
						return feet;
					}
				}
			}
		}
		return null;
	}
}
