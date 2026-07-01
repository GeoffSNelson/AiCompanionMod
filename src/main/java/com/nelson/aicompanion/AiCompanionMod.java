package com.nelson.aicompanion;

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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@Mod(AiCompanionMod.MOD_ID)
public class AiCompanionMod {
	public static final String MOD_ID = "aicompanion";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static AiClient AI_CLIENT;

	// Pending companion respawns: checked each server tick
	private static final List<PendingRespawn> pendingRespawns = new ArrayList<>();

	public record PendingRespawn(ServerLevel world, String name, int variant, BlockPos pos, boolean hasHome, int spawnAtTick) {}

	public static void scheduleRespawn(ServerLevel world, String name, int variant, BlockPos pos, boolean hasHome, int delayTicks) {
		int spawnAt = (int)(world.getServer().getTickCount()) + delayTicks;
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

	public AiCompanionMod(IEventBus modBus) {
		LOGGER.info("Initializing AI Companion Mod...");

		ModItems.register(modBus);
		ModEntities.register(modBus);
		modBus.addListener(this::registerAttributes);

		AI_CLIENT = new AiClient(8080);
		LOGGER.info("AI HTTP Client connected to local API port 8080");

		ChatListener.register();
		CompanionPlayerList.register();
		CompanionCommands.register();

		NeoForge.EVENT_BUS.addListener(this::onServerStarted);
		NeoForge.EVENT_BUS.addListener(this::onServerStopping);
		NeoForge.EVENT_BUS.addListener(this::onServerTick);
		NeoForge.EVENT_BUS.addListener(this::onLivingDeath);
	}

	private void registerAttributes(EntityAttributeCreationEvent event) {
		event.put(ModEntities.COMPANION_NPC.get(), CompanionEntity.createCompanionAttributes().build());
	}

	private void onServerStarted(ServerStartedEvent event) {
		CompanionControlServer.start(event.getServer());
	}

	private void onServerStopping(ServerStoppingEvent event) {
		CompanionControlServer.stop();
	}

	private void onServerTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
			if (pendingRespawns.isEmpty()) return;
			int now = (int) server.getTickCount();
			Iterator<PendingRespawn> it = pendingRespawns.iterator();
			while (it.hasNext()) {
				PendingRespawn r = it.next();
				if (now >= r.spawnAtTick()) {
					it.remove();
					spawnCompanion(r);
				}
			}
	}

	private void onLivingDeath(LivingDeathEvent event) {
			if (!(event.getEntity() instanceof ServerPlayer deadPlayer)) return;
			ServerLevel world = deadPlayer.serverLevel();

			JsonObject payload = new JsonObject();
			payload.addProperty("event", "player_died");
			payload.addProperty("detail", deadPlayer.getName().getString());

			// Find a nearby companion to react
			world.getAllEntities().forEach(e -> {
				if (!(e instanceof CompanionEntity companion)) return;
				if (companion .distanceToSqr(deadPlayer) > 400.0) return; // 20 block radius

				UUID companionUuid = companion.getUUID();
				String companionName = companion.getName().getString();
				payload.addProperty("npcName", companionName);
				AI_CLIENT.sendEvent(payload).thenAccept(response -> {
					if (response == null || !response.has("reply")) return;
					String reply = response.get("reply").getAsString();
					if (reply == null || reply.isBlank() || "IGNORE".equals(reply)) return;
					world.getServer().execute(() -> {
						if (!companion.isAlive() || companion.isRemoved() || !companion.getUUID().equals(companionUuid)) {
							LOGGER.debug("Ignoring stale player-death reply for " + companionName + " after death or unload.");
							return;
						}
						world.getServer().getPlayerList().broadcastSystemMessage(
								Component.literal("§e<" + companion.getName().getString() + "> §f" + reply), false);
					});
				});
			});
	}

	private static void spawnCompanion(PendingRespawn r) {
		ServerLevel world = r.world();
		if (world == null) return;
		MinecraftServer server = world.getServer();

		for (ServerLevel loadedWorld : server.getAllLevels()) {
			for (net.minecraft.world.entity.Entity entity : loadedWorld.getAllEntities()) {
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

		CompanionEntity companion = ModEntities.COMPANION_NPC.get().create(world);
		if (companion == null) return;

		companion .moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
		companion.setCustomName(Component.literal(r.name()));
		companion.setCustomNameVisible(true);
		companion.setAppearanceVariant(r.variant());
		if (r.hasHome()) {
			companion.setHomePosition(r.pos());
		}
		companion.applyStartingLoadout();
		world.addFreshEntity(companion);
		CompanionRegistry.upsert(server, companion, "loaded");

		server.getPlayerList().broadcastSystemMessage(
				Component.literal("§a[AI Companion] " + r.name() + " has returned!"), false);
		LOGGER.info("Respawned companion: " + r.name() + " at " + spawnPos.toShortString());
	}

	private static BlockPos findSafeSpawnPos(ServerLevel world, BlockPos origin) {
		for (int radius = 0; radius <= 5; radius++) {
			for (int x = -radius; x <= radius; x++) {
				for (int z = -radius; z <= radius; z++) {
					BlockPos feet = origin.offset(x, 0, z);
					BlockPos head = feet.above();
					BlockPos ground = feet.below();
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
