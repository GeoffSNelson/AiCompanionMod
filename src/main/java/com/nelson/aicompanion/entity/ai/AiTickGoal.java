package com.nelson.aicompanion.entity.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.nelson.aicompanion.AiCompanionMod;
import com.nelson.aicompanion.entity.CompanionEntity;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.io.Reader;
import java.nio.file.Files;

public class AiTickGoal extends Goal {
    private static final int AI_REQUEST_INTERVAL_TICKS = 40;
    private static final int IDLE_WANDER_INTERVAL_TICKS = 240;
    private static final int ROLE_IDLE_DECISION_TICKS = 100;
    private static final int ROLE_ACTION_COOLDOWN_TICKS = 500;
    private static final int MINER_ROLE_SCAN_RADIUS = 22;
    private static final int MINER_SEARCH_SCAN_RADIUS = 16;
    private static final int MINER_STORAGE_SEARCH_RADIUS = 28;
    private static final int MINER_AUTONOMOUS_DELIVERY_ITEMS = 32;
    private static final int MINER_INVENTORY_DELIVERY_SLOTS = 20;
    private static final int MINER_SEARCH_REPATH_TICKS = 20;
    private static final int MINER_ORE_BAND_TOLERANCE = 3;
    private static final int MINER_DESCENT_STAGE_DROP = 6;
    private static final int MINER_FAILED_TARGET_COOLDOWN_TICKS = 20 * 60;
    private static final int MINER_PROGRESS_WATCHDOG_TICKS = 20 * 12;
    private static final int GUARDIAN_ROLE_PATROL_RADIUS = 24;
    private static final int SCOUT_ROLE_WALK_RADIUS = 80;
    private static final int WANDERER_ROLE_WALK_RADIUS = 96;
    private static final int WANDERER_PLAYER_VISIT_RADIUS = 128;
    private static final int WANDERER_LANDMARK_SCAN_RADIUS = 14;
    private static final double FOLLOW_START_DISTANCE_SQUARED = 36.0;
    private static final double FOLLOW_STOP_DISTANCE_SQUARED = 16.0;
    private static final double SPRINT_DISTANCE_SQUARED = 100.0; // 10 blocks — sprint beyond this
    private static final double INTERACTION_DISTANCE_SQUARED = 20.25;
    private static final int WATER_EXIT_SEARCH_RADIUS = 16;
    private static final int WATER_EXIT_REPATH_TICKS = 20;
    private static final int WATER_STUCK_CHECK_TICKS = 20;
    private static final double WATER_STUCK_PROGRESS_SQUARED = 0.09;
    private static final int WATER_EMERGENCY_RESCUE_TICKS = 300;
    private static final double LADDER_VERTICAL_THRESHOLD = 1.25;
    private static final double LADDER_ATTACH_DISTANCE_SQUARED = 2.25;
    private static final int LADDER_PROGRESS_CHECK_TICKS = 10;
    private static final double LADDER_PROGRESS_MINIMUM = 0.08;
    private static final int LADDER_RECENTER_CHECKS = 2;
    private static final int LADDER_ADVANCE_CHECKS = 4;
    private static final double LADDER_RECOVERY_STEP = 0.65;
    private static final int LADDER_EXIT_COMMIT_TICKS = 40;
    private static final double LADDER_EXIT_CLEAR_DISTANCE_SQUARED = 0.81;
    private static final int EVENT_COOLDOWN = 120;      // 6 seconds between events
    private static final int THREAT_WARNING_COOLDOWN_TICKS = 300; // 15 seconds per threat type
    private static final double THREAT_WARNING_RANGE_SQUARED = 256.0; // 16 blocks
    private static final double GREET_RANGE_SQUARED = 64.0; // 8 blocks
    private static final int VIRTUAL_INVENTORY_SLOTS = 27;
    private static final double ITEM_PICKUP_RANGE_SQUARED = 6.25;
    private static final int FOOD_COOLDOWN_TICKS = 100;
    private static final int FOOD_NEED_EVENT_COOLDOWN_TICKS = 900;
    private static final double FOOD_SEEK_HEALTH_RATIO = 0.6;
    private static final double FOOD_SEARCH_RANGE = 28.0;
    private static final int FOOD_REPATH_TICKS = 20;
    private static final int DANGEROUS_DROP_HEIGHT = 5;
    private static final double BUILD_PLACE_DISTANCE_SQUARED = 81.0;
    private static final int BUILD_STALLED_ATTEMPTS = 8;
    private static final int BUILD_PREP_BLOCKS_PER_STEP = 4;
    private static final int BUILD_PREP_MAX_PASSES = 80;
    private static final int TERRAIN_ASSIST_PLACE_COOLDOWN_TICKS = 200;
    private static final double LONG_FOLLOW_DISTANCE_SQUARED = 1024.0; // 32 blocks
    private static final double RECALL_DISTANCE_SQUARED = 22500.0; // 150 blocks
    private static final int FOLLOW_REPATH_TICKS = 30;
    private static final int FOLLOW_STUCK_ADAPT_TICKS = 90;
    private static final int FOLLOW_RECALL_STUCK_TICKS = 180;
    private static final double FOLLOW_PROGRESS_SQUARED = 0.36;
    private static final double FOLLOW_WAYPOINT_STEP = 24.0;
    private static final int MOVEMENT_HISTORY_SIZE = 8;
    private static final int GOAL_NO_PROGRESS_TICKS = 160;
    private static final int OSCILLATION_STUCK_CHECKS = 2;
    private static final double GOAL_PROGRESS_MARGIN_SQUARED = 2.25;
    private static final int BOAT_DEEP_WATER_SCAN_STEPS = 36;
    private static final int BOAT_LAUNCH_SEARCH_RADIUS = 10;
    private static final double BOAT_USE_DISTANCE_SQUARED = 16.0;
    private static final int EXPEDITION_MIN_DISTANCE = 1000;
    private static final int EXPEDITION_MAX_DISTANCE = 5000;
    private static final double EXPEDITION_WAYPOINT_STEP = 64.0;
    private static final int EXPEDITION_REPATH_TICKS = 30;
    private static final int EXPEDITION_PROGRESS_TICKS = 60;
    private static final int EXPEDITION_STUCK_TICKS = 240;
    private static final double EXPEDITION_PROGRESS_SQUARED = 1.0;
    private static final int DISCOVERY_SCAN_TICKS = 100;
    private static final int DISCOVERY_RADIUS = 18;
    private static final int DISCOVERY_COOLDOWN_TICKS = 12000;
    private static final int MINING_SWING_DURATION = 10;
    private static final int TORCH_CHECK_INTERVAL = 300;
    private static final int TORCH_AREA_RADIUS = 12;
    private static final int TORCH_AREA_VERTICAL_RADIUS = 5;
    private static final int TORCH_AREA_LIMIT = 1;
    private static final int TORCH_MAX_LIGHT_LEVEL = 3;
    private static final int FARMER_SCAN_INTERVAL_TICKS = 5;
    private static final int FARMER_SCAN_RADIUS = 24;
    private static final int FARMER_VERTICAL_SCAN_RADIUS = 4;
    private static final double FARMER_WORK_DISTANCE_SQUARED = 9.0;
    private static final int FARMER_FEED_COOLDOWN_TICKS = 100;  // rancher animal breeding cadence
    private static final int FARMER_FARM_IDLE_DISTANCE_SQUARED = 64;
    private static final double FARMER_RETURN_HOME_DISTANCE_SQUARED = 4096.0;
    private static final int FARMER_STARTER_SEEDS = 32;
    private static final int FARMER_CULL_COOLDOWN_TICKS = 12000;
    private static final int FARMER_MIN_ANIMALS_BEFORE_CULL = 8;
    private static final int FARMER_GATE_CHECK_TICKS = 40;
    private static final int FARMER_STORAGE_DELIVERY_MIN_ITEMS = 8;
    private static final double GUARDIAN_ARROW_RELIABLE_RANGE_SQUARED = 24.0 * 24.0;
    private static final double GUARDIAN_ARROW_MAX_VERTICAL_DELTA = 8.0;
    private static final int AMBIENT_CHAT_INTERVAL_MIN = 6000;
    private static final int AMBIENT_CHAT_INTERVAL_MAX = 12000;
    private static final int PASSAGE_SCAN_RADIUS = 2;
    private static final int FENCE_GATE_ESCAPE_RADIUS = 16;
    private static final int PASSAGE_CLOSE_DELAY_TICKS = 10;
    private static final int EMERGENCY_GATE_HOLD_OPEN_TICKS = 160;
    private static final int ESCAPED_GATE_REUSE_COOLDOWN_TICKS = 60;
    private static final int POST_GATE_ROUTE_TICKS = 40;
    private static final int TRAPDOOR_CLOSE_DELAY_TICKS = 40;
    private static final int PASSAGE_REOPEN_COOLDOWN_TICKS = 20;
    private static final int MANUAL_PASSAGE_CLOSE_COOLDOWN_TICKS = 100;
    private static final int FENCE_AVOIDANCE_RADIUS = 2;
    private static final int UNDERGROUND_MIN_DEPTH = 6;
    private static final int SURFACE_ESCAPE_REPATH_TICKS = 20;
    private static final int SURFACE_ESCAPE_PROGRESS_TICKS = 40;
    private static final int SURFACE_ESCAPE_CARVE_AFTER_TICKS = 80;
    private static final int SURFACE_ESCAPE_RESCUE_TICKS = 600;
    private static final double SURFACE_ESCAPE_PROGRESS_SQUARED = 0.36;
    private static final int SURFACE_ESCAPE_MAX_SEARCH_RADIUS = 64;
    private static final int FOREIGN_DIMENSION_RETURN_TICKS = 600;

    private final CompanionEntity npc;
    private int tickCounter = 0;
    private int aiRequestTimer = 0;
    private int idleWanderTimer = 0;
    private int aiRequestSequence = 0;
    private boolean brainStateRestored = false;
    private String lastCommandAction = "@idle";
    private int followRepathTicks = 0;
    private int followNoProgressTicks = 0;
    private Vec3 lastFollowProgressPos = null;
    private BlockPos currentFollowWaypoint = null;

    // Command states
    private ServerPlayer activeFollowTarget = null;
    private String currentMode = "idle";
    private String autonomousWalkPurpose = "";

    // Equipment progression
    private String currentEquipmentTier = "iron";
    private int ironMinedCount = 0;
    private int diamondMinedCount = 0;

    // Lightweight survival inventory with entity NBT persistence.
    private final Map<String, ItemStack> virtualInventory = new HashMap<>();
    private int foodCooldownTicks = 0;
    private int foodNeedEventCooldownTicks = 0;
    private ItemEntity currentFoodItemTarget = null;
    private LivingEntity currentFoodAnimalTarget = null;
    private int foodSeekTicks = 0;

    // Event / greeting cooldowns
    private int eventCooldownTicks = 0;
    private final Set<String> greetedPlayersEver = new HashSet<>();
    private final Map<String, Integer> threatWarningCooldowns = new HashMap<>();

    // Low-health alert state (only fires once per dip below threshold)
    private boolean lowHealthAlerted = false;

    // Idle animation timer
    private int idleAnimationTimer = 0;
    private int roleIdleTimer = 0;
    private int roleActionCooldownTicks = 0;
    private boolean autonomousRoleAction = false;
    
    // Mining/Building states
    private net.minecraft.core.BlockPos currentWalkTarget = null;
    private BlockPos currentWaterExitTarget = null;
    private BlockPos currentBoatTarget = null;
    private BlockPos currentBoatStandTarget = null;
    private int waterEscapeTicks = 0;
    private int waterNoProgressChecks = 0;
    private Vec3 lastWaterEscapePos = null;
    private final Map<BlockPos, Integer> failedWaterExitCooldowns = new HashMap<>();
    private BlockPos activeLadderColumn = null;
    private int ladderAssistTicks = 0;
    private int ladderNoProgressChecks = 0;
    private int ladderExitCooldownTicks = 0;
    private int ladderExitCommitTicks = 0;
    private double lastLadderY = Double.NaN;
    private BlockPos ladderExitTarget = null;
    private boolean ladderExitDownward = false;
    private net.minecraft.core.BlockPos currentMineTarget = null;
    private String targetBlockName = "";
    private String currentMood = "neutral";
    private boolean requestedMiningJob = false;
    private int requestedMiningAmount = 16;
    private int requestedMiningStartingCount = 0;
    private BlockPos minerWorkHome = null;
    private BlockPos miningSearchWaypoint = null;
    private int miningSearchTicks = 0;
    private boolean minerReturningToStorage = false;
    private BlockPos minerStorageTarget = null;
    
    private net.minecraft.core.BlockPos buildCenter = null;
    private net.minecraft.core.BlockPos buildStandPos = null;
    private boolean buildReady = false;
    private boolean buildSitePrepared = false;
    private int buildSitePreparationPasses = 0;
    private int buildEvacuationTicks = 0;
    private int buildPlacementAttempts = 0;
    private int buildRadius = 1;
    private int buildHeight = 4;
    private String buildSchematic = "small";
    private java.util.List<BuildPlacement> buildQueue = new java.util.ArrayList<>();
    private int buildTotalPlacements = 0;
    
    private int stuckTicks = 0;
    private int terrainAssistPlaceCooldownTicks = 0;
    private net.minecraft.world.phys.Vec3 lastPos = null;
    private final Deque<BlockPos> recentMovementBlocks = new ArrayDeque<>();
    private int oscillationStuckChecks = 0;
    private int goalNoProgressTicks = 0;
    private double lastGoalDistanceSq = -1.0;
    private BlockPos surfaceEscapeTarget = null;
    private int surfaceEscapeTicks = 0;
    private int surfaceEscapeNoProgressTicks = 0;
    private Vec3 lastSurfaceEscapePos = null;
    private net.minecraft.core.BlockPos explorationAnchor = null;
    private BlockPos expeditionHome = null;
    private BlockPos expeditionDestination = null;
    private final List<BlockPos> expeditionRoute = new ArrayList<>();
    private final Set<Long> expeditionTicketChunks = new HashSet<>();
    private final Map<String, Integer> discoveryCooldowns = new HashMap<>();
    private int expeditionRouteIndex = 0;
    private int expeditionTicks = 0;
    private int expeditionNoProgressTicks = 0;
    private int discoveryScanTicks = 0;
    private boolean expeditionReturningHome = false;
    private Vec3 lastExpeditionProgressPos = null;

    private int miningSwingTicks = 0;
    private int miningStuckRecoveries = 0;
    private BlockPos lastMiningStuckTarget = null;
    private final Map<BlockPos, Integer> failedMiningTargetCooldowns = new HashMap<>();
    private int miningNoProgressTicks = 0;
    private Vec3 lastMiningProgressPos = null;
    private int combatBackstepTicks = 0;
    private int bowShootCooldown = 0;
    private int torchCheckTicks = 0;
    private int farmerScanTicks = 0;
    private int farmerFeedCooldownTicks = 0;
    private int farmerCullCooldownTicks = 0;
    private int farmerGateCheckTicks = 0;
    private BlockPos currentFarmCropTarget = null;
    private BlockPos currentFarmPlantTarget = null;
    private BlockPos currentFarmTillTarget = null;
    private BlockPos farmerFarmAnchor = null;
    private final Deque<BlockPos> farmExpansionQueue = new ArrayDeque<>();
    private BlockPos currentFarmChestTarget = null;
    private BlockPos farmerStorageChest = null;  // Auto-created chest location
    private int chestCreationCooldownTicks = 0;
    private Animal currentFarmAnimalTarget = null;
    private Animal currentFarmCullTarget = null;
    private int ambientChatTicks = 0;
    private int nextAmbientChatTicks = 7200;
    private final Map<BlockPos, Integer> openedDoors = new HashMap<>();
    private final Map<BlockPos, Integer> openedFenceGates = new HashMap<>();
    private final Map<BlockPos, Integer> openedTrapdoors = new HashMap<>();
    private final Map<BlockPos, Integer> passageUseCooldowns = new HashMap<>();
    private BlockPos committedGatePos = null;
    private BlockPos committedGateExit = null;
    private int committedGateTicks = 0;
    private int postGateRouteTicks = 0;
    private Boat companionBoat = null;
    private int boatTravelTicks = 0;
    private int boatLaunchCooldownTicks = 0;
    private int foreignDimensionTicks = 0;

    public AiTickGoal(CompanionEntity npc) {
        this.npc = npc;
    }

    @Override
    public boolean canUse() {
        return true; 
    }

    @Override
    public void tick() {
        tickCounter++;
        aiRequestTimer++;
        idleWanderTimer++;
        idleAnimationTimer++;
        restorePersistedBrainStateIfNeeded();
        if (rescueFromVoid()) {
            return;
        }
        if (returnFromForeignDimensionIfNeeded()) {
            return;
        }

        // Speaker look-at: hold gaze on the player who spoke for a few seconds
        if (npc.speakerLookTicks > 0) {
            npc.speakerLookTicks--;
            if (npc.speakerLookTarget != null && !npc.speakerLookTarget.isRemoved()) {
                npc.getLookControl() .setLookAt(npc.speakerLookTarget, 30.0F, 30.0F);
            } else {
                npc.speakerLookTicks = 0;
                npc.speakerLookTarget = null;
            }
        }

        // Decay event and greeting cooldowns each tick
        if (eventCooldownTicks > 0) eventCooldownTicks--;
        if (foodCooldownTicks > 0) foodCooldownTicks--;
        if (foodNeedEventCooldownTicks > 0) foodNeedEventCooldownTicks--;
        if (farmerFeedCooldownTicks > 0) farmerFeedCooldownTicks--;
        if (farmerCullCooldownTicks > 0) farmerCullCooldownTicks--;
        if (chestCreationCooldownTicks > 0) chestCreationCooldownTicks--;
        if (roleActionCooldownTicks > 0) roleActionCooldownTicks--;
        threatWarningCooldowns.replaceAll((name, v) -> v - 1);
        threatWarningCooldowns.entrySet().removeIf(e -> e.getValue() <= 0);
        if (terrainAssistPlaceCooldownTicks > 0) terrainAssistPlaceCooldownTicks--;
        if (postGateRouteTicks > 0) postGateRouteTicks--;
        if (tickCounter % 20 == 0) {
            putTerrainBlocksAwayWhenNotBuilding();
        }

        // Ambient chat — NPC speaks up unprompted every 5-10 minutes
        ambientChatTicks++;
        if (ambientChatTicks >= nextAmbientChatTicks) {
            ambientChatTicks = 0;
            nextAmbientChatTicks = AMBIENT_CHAT_INTERVAL_MIN
                    + npc.getRandom().nextInt(AMBIENT_CHAT_INTERVAL_MAX - AMBIENT_CHAT_INTERVAL_MIN);
            sendEventToAi("ambient", currentMode);
        }

        // Low-health alert — fires once per health dip
        float health = npc.getHealth();
        if (health < 5.0f && !lowHealthAlerted) {
            lowHealthAlerted = true;
            sendEventToAi("low_health", "");
        } else if (health > 10.0f) {
            lowHealthAlerted = false;
        }

        if (tickCounter % 20 == 0) {
            checkThreatWarnings();
        }

        // Keep the conversational state current even when an emergency handler
        // returns early for many consecutive ticks.
        if (aiRequestTimer >= AI_REQUEST_INTERVAL_TICKS) {
            aiRequestTimer = 0;
            requestAiAction();
        }

        manageNearbyDoorsAndGates();

        if (tickCounter % 10 == 0) {
            pickupNearbyItems();
        }
        if (tickCounter % 20 == 0) {
            eatIfNeeded();
            decayFailedMiningTargets();
        }
        if (avoidDangerousDrop()) {
            return;
        }

        if (handleBoatTravel()) {
            return;
        }

        if (escapeWater()) {
            return;
        }

        if (assistLadderClimb()) {
            return;
        }

        if (escapeFencePenIfNeeded()) {
            return;
        }

        if (isInCombat()) {
            LivingEntity combatTarget = npc.getTarget();
            if ("scout".equals(npc.getAppearanceVariantName())) {
                handleScoutCombat(combatTarget);
            } else if ("guardian".equals(npc.getAppearanceVariantName())
                    && isGuardianRangedTarget(combatTarget)) {
                handleGuardianRangedCombat(combatTarget);
            } else {
                equipSword();
                equipShieldIfAppropriate();
            }
            npc.setSprinting(true);
            combatBackstepTicks++;
            if (combatBackstepTicks >= 20 && combatTarget != null) {
                combatBackstepTicks = 0;
                if (npc .distanceToSqr(combatTarget) < 16.0) {
                    Vec3 away = new Vec3(
                            npc.getX() - combatTarget.getX(), 0.0,
                            npc.getZ() - combatTarget.getZ());
                    if (away.lengthSqr() > 0.01) {
                        Vec3 impulse = away.normalize().scale(0.35);
                        npc .push(impulse.x, 0.12, impulse.z);
                    }
                }
            }
            currentWalkTarget = null;
            currentMineTarget = null;
            return;
        }
        if (seekFoodIfNeeded()) {
            return;
        }

        if (escapeUndergroundToSurface()) {
            return;
        }

        if (!buildQueue.isEmpty() && "idle".equals(currentMode)) {
            resumeUnfinishedBuild();
        }

        // Stuck Detection Check (Check position occasionally)
        if (!"build".equals(currentMode) && tickCounter % 20 == 0) {
            updateMovementStuckDetection();
        }

        // Player greeting check every 2 seconds when idle
        if ("idle".equals(currentMode) && tickCounter % 40 == 0) {
            if (npc.level() instanceof ServerLevel serverWorld) {
                checkPlayerGreetings(serverWorld);
            }
        }

        // Execute active physical states; combat already handled above with sprint=true
        npc.setSprinting(false);
        if ("follow".equals(currentMode) && activeFollowTarget != null) {
            doFollowLogic();
        } else if ("mine".equals(currentMode)) {
            resetFollowProgress();
            doMiningLogic();
        } else if ("build".equals(currentMode)) {
            resetFollowProgress();
            doBuildingLogic();
        } else if ("walk".equals(currentMode)) {
            resetFollowProgress();
            doWalkLogic();
        } else if ("explore".equals(currentMode)) {
            resetFollowProgress();
            doExpeditionLogic();
        } else if ("idle".equals(currentMode)) {
            resetFollowProgress();
            releaseExpeditionTickets();

            boolean roleBusy = doIdleRoleWork();

            if (!roleBusy && idleWanderTimer >= IDLE_WANDER_INTERVAL_TICKS) {
                idleWanderTimer = 0;
                if (npc.getNavigation().isDone() && npc.getRandom().nextFloat() < 0.18f) {
                    doIdleWander();
                }
            }

            // Idle animation — subtle life-like movements
            if (!roleBusy && idleAnimationTimer >= 40 && npc.getNavigation().isDone()) {
                idleAnimationTimer = 0;
                if (npc.getRandom().nextFloat() < 0.2f) {
                    performIdleAnimation();
                }
            }

            // Torch placement when standing in darkness
            torchCheckTicks++;
            if (!roleBusy && torchCheckTicks >= TORCH_CHECK_INTERVAL && npc.getNavigation().isDone()) {
                torchCheckTicks = 0;
                tryPlaceTorch();
            }
        }

    }

    private boolean doIdleRoleWork() {
        return switch (npc.getAppearanceVariantName()) {
            case "farmer" -> doFarmerIdleLogic();
            case "rancher" -> doRancherIdleLogic();
            default -> doRoleIdleLogic();
        };
    }

    private void restorePersistedBrainStateIfNeeded() {
        if (brainStateRestored) return;
        brainStateRestored = true;

        currentEquipmentTier = npc.getPersistedEquipmentTier();
        ironMinedCount = npc.getPersistedIronMinedCount();
        diamondMinedCount = npc.getPersistedDiamondMinedCount();
        restoreVirtualInventory(npc.getPersistedVirtualInventory());
        restoreGreetedPlayers(npc.getPersistedGreetedPlayers());

        lastCommandAction = npc.getPersistedAction();
        if (restorePersistedBuild()) {
            return;
        }
        if (!"@idle".equals(lastCommandAction) && !"@stop".equals(lastCommandAction)) {
            handleAiAction(lastCommandAction);
        }
    }

    private void rememberAction(String action) {
        lastCommandAction = action == null || action.isBlank() ? "@idle" : action;
        persistBrainState();
    }

    private void persistBrainState() {
        npc.setPersistedAction(lastCommandAction);
        npc.setPersistedEquipmentTier(currentEquipmentTier);
        npc.setPersistedIronMinedCount(ironMinedCount);
        npc.setPersistedDiamondMinedCount(diamondMinedCount);
        npc.setPersistedVirtualInventory(serializeVirtualInventory());
        npc.setPersistedGreetedPlayers(String.join(",", greetedPlayersEver));
        npc.setPersistedBuildSchematic(buildQueue.isEmpty() ? "" : buildSchematic);
        npc.setPersistedBuildCenter(buildQueue.isEmpty() ? null : buildCenter);
        npc.setPersistedBuildTotal(buildQueue.isEmpty() ? 0 : buildTotalPlacements);
    }

    private void restoreGreetedPlayers(String serialized) {
        greetedPlayersEver.clear();
        if (serialized == null || serialized.isBlank()) return;
        for (String name : serialized.split(",")) {
            if (!name.isBlank()) {
                greetedPlayersEver .add(name.trim().toLowerCase());
            }
        }
    }

    private boolean restorePersistedBuild() {
        String schematic = npc.getPersistedBuildSchematic();
        BlockPos center = npc.getPersistedBuildCenter();
        if (schematic.isBlank() || center == null) {
            return false;
        }

        buildSchematic = schematic;
        buildCenter = center.immutable();
        BuildPlan plan = getBuildPlan(buildSchematic);
        buildRadius = plan.radius();
        buildHeight = plan.height();
        buildQueue.clear();
        queueBuildSchematic(buildCenter, buildSchematic);
        buildTotalPlacements = Math.max(npc.getPersistedBuildTotal(), buildQueue.size());
        buildQueue.removeIf(placement -> buildPlacementMatchesWorld(placement));

        if (buildQueue.isEmpty()) {
            clearPersistedBuild();
            rememberAction("@idle");
            return false;
        }

        buildStandPos = findSafeBuildStandPos(buildCenter, buildRadius);
        buildReady = false;
        buildSitePrepared = false;
        buildSitePreparationPasses = 0;
        buildEvacuationTicks = 0;
        buildPlacementAttempts = 0;
        currentMode = "build";
        lastCommandAction = "@build " + buildSchematic;
        AiCompanionMod.LOGGER.info("Restored unfinished " + buildSchematic + " for "
                + npc.getName().getString() + ": " + buildQueue.size() + "/"
                + buildTotalPlacements + " blocks remaining.");
        return true;
    }

    private boolean buildPlacementMatchesWorld(BuildPlacement placement) {
        BlockState current = npc.level().getBlockState(placement.pos());
        return current.equals(placement.state());
    }

    private void clearPersistedBuild() {
        buildTotalPlacements = 0;
        npc.setPersistedBuildSchematic("");
        npc.setPersistedBuildCenter(null);
        npc.setPersistedBuildTotal(0);
    }

    private int getBuildProgressPercent() {
        if (buildTotalPlacements <= 0) {
            return buildQueue.isEmpty() ? 100 : 0;
        }
        int completed = Math.max(0, buildTotalPlacements - buildQueue.size());
        return Math.min(100, (int) Math.round(completed * 100.0 / buildTotalPlacements));
    }

    private String serializeVirtualInventory() {
        if (virtualInventory.isEmpty()) return "";

        StringBuilder serialized = new StringBuilder();
        for (ItemStack stack : virtualInventory.values()) {
            if (stack.isEmpty()) continue;
            if (serialized.length() > 0) {
                serialized.append(';');
            }
            serialized.append(getInventoryKey(stack)).append('=').append(stack.getCount());
        }
        return serialized.toString();
    }

    private void restoreVirtualInventory(String serialized) {
        virtualInventory.clear();
        if (serialized == null || serialized.isBlank()) return;

        for (String entry : serialized.split(";")) {
            if (entry.isBlank()) continue;
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) continue;

            ResourceLocation id = ResourceLocation.tryParse(parts[0]);
            if (id == null) continue;

            try {
                int count = Integer.parseInt(parts[1]);
                if (count <= 0) continue;

                Item item = BuiltInRegistries.ITEM.get(id);
                if (item == Items.AIR) continue;
                virtualInventory.put(id.toString(), new ItemStack(item, count));
            } catch (NumberFormatException ignored) {
                // Ignore corrupt inventory entries instead of losing the whole brain state.
            }
        }
    }

    private void resumeUnfinishedBuild() {
        currentMode = "build";
        activeFollowTarget = null;
        resetFollowProgress();
        currentWalkTarget = null;
        currentWaterExitTarget = null;
        currentBoatTarget = null;
        currentBoatStandTarget = null;
        currentMineTarget = null;
        clearFarmerTargets();
        clearActiveExpedition(false);
        autonomousRoleAction = false;
        npc.getNavigation().stop();

        if (buildStandPos != null && npc .distanceToSqr(Vec3.atCenterOf(buildStandPos)) > 4.0) {
            buildReady = false;
            moveToBuildStandOrEvacuate();
        }

        rememberAction("@build " + buildSchematic);
        AiCompanionMod.LOGGER.info("NPC resuming unfinished " + buildSchematic
                + " with " + buildQueue.size() + " blocks remaining.");
    }

    private void pauseUnfinishedBuild() {
        currentMode = "stopped";
        activeFollowTarget = null;
        resetFollowProgress();
        currentWalkTarget = null;
        currentWaterExitTarget = null;
        currentBoatTarget = null;
        currentBoatStandTarget = null;
        currentMineTarget = null;
        clearFarmerTargets();
        clearActiveExpedition(false);
        npc.setTarget(null);
        npc.getNavigation().stop();
        rememberAction("@stop");
        AiCompanionMod.LOGGER.info("NPC paused the unfinished " + buildSchematic
                + " without discarding its remaining " + buildQueue.size() + " blocks.");
    }
    
    private void doBuildingLogic() {
        if (tickCounter % 15 != 0) return; // Slowed down so they don't break the server or look too crazy
        if (buildQueue.isEmpty()) {
            AiCompanionMod.LOGGER.info("NPC Finished building!");
            sendEventToAi("build_complete", "");
            currentMode = "idle";
            buildStandPos = null;
            buildReady = false;
            buildSitePrepared = false;
            buildSitePreparationPasses = 0;
            buildEvacuationTicks = 0;
            buildPlacementAttempts = 0;
            clearPersistedBuild();
            restoreDefaultMainHand();
            finishAutonomousRoleAction();
            rememberAction("@idle");
            return;
        }

        if (!buildReady) {
            if (buildStandPos == null || npc .distanceToSqr(Vec3.atCenterOf(buildStandPos)) <= 4.0) {
                buildReady = true;
                buildEvacuationTicks = 0;
            } else {
                moveToBuildStandOrEvacuate();
                return;
            }
        }

        if (!buildSitePrepared) {
            if (prepareBuildSiteStep()) {
                return;
            }
        }
        
        BuildPlacement placement = buildQueue.get(0);
        net.minecraft.core.BlockPos target = placement.pos();
        if (blockWouldTrapNpc(target)) {
            if (buildStandPos != null && npc .distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(buildStandPos)) > 4.0) {
                moveToBuildStandOrEvacuate();
            } else {
                AiCompanionMod.LOGGER.warn("Skipping unsafe build block at " + target.toShortString() + " because it intersects the companion.");
                buildQueue.remove(0);
                buildEvacuationTicks = 0;
                buildPlacementAttempts = 0;
                persistBrainState();
            }
            return;
        }

        // Keep moving around the structure when needed, but do not let one awkward
        // roof/window placement stall the entire build forever.
        double distanceToTarget = npc .distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target));
        if (distanceToTarget > BUILD_PLACE_DISTANCE_SQUARED) {
            buildPlacementAttempts++;
            if (npc.getNavigation().isDone() || buildPlacementAttempts % 4 == 0) {
                moveNearBuildTarget(target, 1.0);
            }

            if (buildPlacementAttempts < BUILD_STALLED_ATTEMPTS) {
                return;
            }

            AiCompanionMod.LOGGER.warn("Builder could not get close to " + target.toShortString() + "; resolving placement to keep build moving.");
        }

        placeNextBuildBlock(placement);
    }

    private boolean prepareBuildSiteStep() {
        if (buildQueue.isEmpty()) {
            buildSitePrepared = true;
            return false;
        }

        buildSitePreparationPasses++;
        if (buildSitePreparationPasses > BUILD_PREP_MAX_PASSES) {
            buildSitePrepared = true;
            buildSitePreparationPasses = 0;
            AiCompanionMod.LOGGER.warn("Build site preparation reached its pass limit; continuing with remaining placements.");
            return false;
        }

        net.minecraft.world.level.Level world = npc.level();
        int cleared = 0;

        for (BuildPlacement placement : new ArrayList<>(buildQueue)) {
            BlockPos target = placement.pos();
            BlockState current = world.getBlockState(target);
            if (!isBuildObstruction(world, target, current, placement.state())) {
                continue;
            }

            if (blockWouldTrapNpc(target)) {
                moveToBuildStandOrEvacuate();
                return true;
            }

            if (world.getBlockEntity(target) != null || current.getDestroySpeed(world, target) < 0.0F
                    || !current.getFluidState().isEmpty()) {
                AiCompanionMod.LOGGER.warn("Build site has an uncleared obstruction at " + target.toShortString()
                        + " (" + BuiltInRegistries.BLOCK.getKey(current.getBlock()) + ").");
                continue;
            }

            if (npc .distanceToSqr(Vec3.atCenterOf(target)) > BUILD_PLACE_DISTANCE_SQUARED) {
                moveNearBuildTarget(target, 1.0);
                return true;
            }

            equipToolFor(current);
            npc.getLookControl() .setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
            npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);

            String blockName = BuiltInRegistries.BLOCK.getKey(current.getBlock()).getPath();
            world.destroyBlock(target, true, npc);
            updateEquipmentProgress(blockName);

            cleared++;
            if (cleared >= BUILD_PREP_BLOCKS_PER_STEP) {
                return true;
            }
        }

        buildSitePrepared = true;
        buildSitePreparationPasses = 0;
        return false;
    }

    private boolean isBuildObstruction(net.minecraft.world.level.Level world, BlockPos pos, BlockState current, BlockState desired) {
        if ("bridge".equals(buildSchematic) && desired.is(Blocks.OAK_PLANKS)
                && !current.getFluidState().isEmpty()) {
            return false;
        }
        if (isProtectedFromBuildClearing(world, pos, current)) {
            return false;
        }
        return !current.is(desired.getBlock()) && !isReplaceableForBuild(current);
    }

    private void placeNextBuildBlock(BuildPlacement placement) {
        net.minecraft.core.BlockPos target = placement.pos();
        npc.getLookControl() .setLookAt(target.getX(), target.getY(), target.getZ());
        npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        Item placementItem = placement.state().getBlock().asItem();
        npc .setItemSlot(EquipmentSlot.MAINHAND, placementItem == Items.AIR
                ? new ItemStack(Items.OAK_PLANKS)
                : new ItemStack(placementItem));

        if (canPlaceBuildBlock(target, placement.state())) {
            npc.level() .setBlockAndUpdate(target, placement.state());
        } else {
            AiCompanionMod.LOGGER.warn("Skipping blocked build placement at " + target.toShortString());
        }

        buildQueue.remove(0);
        buildPlacementAttempts = 0;
        persistBrainState();
        restoreDefaultMainHand();
    }

    private void doWalkLogic() {
        if (currentWalkTarget == null) {
            currentMode = "idle";
            autonomousWalkPurpose = "";
            finishAutonomousRoleAction();
            rememberAction("@idle");
            return;
        }

        if (autonomousRoleAction && "wanderer".equals(npc.getAppearanceVariantName())) {
            scanForWandererLandmarks();
        }

        if (npc .distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(currentWalkTarget)) < 4.0) {
            npc.getNavigation().stop();
            finishWandererJourney();
            currentWalkTarget = null;
            currentMode = "idle";
            finishAutonomousRoleAction();
            rememberAction("@idle");
            return;
        }

        if (npc.getNavigation().isDone()) {
            moveNear(currentWalkTarget, 1.0);
        }
    }

    private void doFollowLogic() {
        if (activeFollowTarget == null || activeFollowTarget.isRemoved() || activeFollowTarget.hasDisconnected()) {
            activeFollowTarget = null;
            currentMode = "idle";
            npc.getNavigation().stop();
            resetFollowProgress();
            rememberAction("@idle");
            return;
        }

        followRepathTicks++;
        double distSq = npc .distanceToSqr(activeFollowTarget);

        if (distSq < FOLLOW_STOP_DISTANCE_SQUARED) {
            npc.getNavigation().stop();
            npc.setSprinting(false);
            currentFollowWaypoint = null;
            followNoProgressTicks = 0;
            npc.getLookControl() .setLookAt(activeFollowTarget, 20.0F, 20.0F);
            return;
        }

        if (distSq < FOLLOW_START_DISTANCE_SQUARED) {
            npc.getNavigation().stop();
            npc.setSprinting(false);
            currentFollowWaypoint = null;
            npc.getLookControl() .setLookAt(activeFollowTarget, 20.0F, 20.0F);
            return;
        }

        trackFollowProgress();

        if (followNoProgressTicks >= FOLLOW_STUCK_ADAPT_TICKS && followNoProgressTicks % FOLLOW_STUCK_ADAPT_TICKS == 0) {
            adaptTerrainWhenStuck();
        }

        if (distSq > RECALL_DISTANCE_SQUARED && followNoProgressTicks >= FOLLOW_RECALL_STUCK_TICKS) {
            if (recallNearFollowTarget()) {
                return;
            }
        }

        double speed = distSq > SPRINT_DISTANCE_SQUARED ? 1.45 : 1.15;
        npc.setSprinting(distSq > SPRINT_DISTANCE_SQUARED);
        if (distSq > LONG_FOLLOW_DISTANCE_SQUARED) {
            if (currentFollowWaypoint == null
                    || npc .distanceToSqr(Vec3.atCenterOf(currentFollowWaypoint)) < 9.0
                    || followRepathTicks % FOLLOW_REPATH_TICKS == 0) {
                currentFollowWaypoint = findFollowWaypointToward(activeFollowTarget);
            }

            if (currentFollowWaypoint != null) {
                startPathTo(currentFollowWaypoint, speed);
                return;
            }
        }

        currentFollowWaypoint = null;
        if (npc.getNavigation().isDone() || followRepathTicks % FOLLOW_REPATH_TICKS == 0) {
            npc.getNavigation() .moveTo(activeFollowTarget, speed);
        }
    }

    private void trackFollowProgress() {
        if (followRepathTicks % FOLLOW_REPATH_TICKS != 0) return;

        Vec3 current = new Vec3(npc.getX(), npc.getY(), npc.getZ());
        if (lastFollowProgressPos != null && current.distanceToSqr(lastFollowProgressPos) < FOLLOW_PROGRESS_SQUARED) {
            followNoProgressTicks += FOLLOW_REPATH_TICKS;
        } else {
            followNoProgressTicks = 0;
        }
        lastFollowProgressPos = current;
    }

    private void resetFollowProgress() {
        followRepathTicks = 0;
        followNoProgressTicks = 0;
        lastFollowProgressPos = null;
        currentFollowWaypoint = null;
    }

    private BlockPos findFollowWaypointToward(ServerPlayer target) {
        Vec3 from = new Vec3(npc.getX(), npc.getY(), npc.getZ());
        Vec3 to = new Vec3(target.getX(), target.getY(), target.getZ());
        Vec3 horizontal = new Vec3(to.x - from.x, 0.0, to.z - from.z);
        if (horizontal.lengthSqr() < 1.0) {
            return findSafeStandAround(target.blockPosition(), 5, 3);
        }

        Vec3 step = horizontal.normalize().scale(Math.min(FOLLOW_WAYPOINT_STEP, horizontal.length()));
        BlockPos estimate = BlockPos.containing(from.x + step.x, target.getY(), from.z + step.z);
        BlockPos waypoint = findSafeStandAround(estimate, 6, 8);
        if (waypoint != null) {
            return waypoint;
        }

        return findSafeStandAround(target.blockPosition(), 8, 4);
    }

    private boolean recallNearFollowTarget() {
        if (!(npc.level() instanceof ServerLevel)) return false;

        BlockPos safePos = findSafeStandAround(activeFollowTarget.blockPosition(), 5, 3);
        if (safePos == null) return false;

        AiCompanionMod.LOGGER.warn("Companion follow path failed for too long; recalling near " + activeFollowTarget.getName().getString() + ".");
        npc.getNavigation().stop();
        npc .moveTo(
                safePos.getX() + 0.5,
                safePos.getY(),
                safePos.getZ() + 0.5,
                activeFollowTarget.getYRot(),
                npc.getXRot()
        );
        resetFollowProgress();
        return true;
    }

    private void startPathTo(BlockPos target, double speed) {
        Path path = npc.getNavigation() .createPath(target, 0);
        if (path != null && path.canReach()) {
            npc.getNavigation() .moveTo(path, speed);
        } else {
            npc.getNavigation() .moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
        }
    }

    private boolean rescueFromVoid() {
        if (!(npc.level() instanceof ServerLevel serverWorld)) return false;
        if (npc.getY() >= serverWorld.getMinBuildHeight() - 4) return false;

        BlockPos rescue = null;
        if (npc.getHomePosition() != null) {
            rescue = findSafeStandAround(npc.getHomePosition(), 8, 8);
        }

        if (rescue == null) {
            net.minecraft.world.entity.player.Player nearest = serverWorld.getNearestPlayer(npc, 256.0);
            if (nearest != null) {
                rescue = findSafeStandAround(nearest.blockPosition(), 8, 6);
            }
        }

        BlockPos worldSpawn = serverWorld.getSharedSpawnPos();
        if (rescue == null) {
            rescue = findSafeStandAround(worldSpawn, 12, 12);
        }
        if (rescue == null) {
            rescue = worldSpawn;
        }

        AiCompanionMod.LOGGER.warn(npc.getName().getString() + " fell below the world; rescuing to " + rescue.toShortString() + ".");
        npc.stopRiding();
        npc.getNavigation().stop();
        npc .setDeltaMovement(Vec3.ZERO);
        npc.fallDistance = 0.0F;
        npc .moveTo(rescue.getX() + 0.5, rescue.getY(), rescue.getZ() + 0.5, npc.getYRot(), npc.getXRot());
        currentMode = "idle";
        activeFollowTarget = null;
        currentWalkTarget = null;
        currentWaterExitTarget = null;
        currentBoatTarget = null;
        currentBoatStandTarget = null;
        currentMineTarget = null;
        buildQueue.clear();
        clearFarmerTargets();
        rememberAction("@idle");
        return true;
    }

    private boolean returnFromForeignDimensionIfNeeded() {
        if (!(npc.level() instanceof ServerLevel currentWorld)) return false;
        if (currentWorld.dimension().equals(Level.OVERWORLD)) {
            foreignDimensionTicks = 0;
            return false;
        }

        boolean deliberatelyFollowingHere = "follow".equals(currentMode)
                && activeFollowTarget != null
                && activeFollowTarget.getCommandSenderWorld() == currentWorld;
        if (deliberatelyFollowingHere) {
            foreignDimensionTicks = 0;
            return false;
        }

        foreignDimensionTicks++;
        if (foreignDimensionTicks < FOREIGN_DIMENSION_RETURN_TICKS) {
            return false;
        }

        ServerLevel overworld = currentWorld.getServer().overworld();
        BlockPos anchor = npc.getHomePosition() != null
                ? npc.getHomePosition()
                : overworld.getSharedSpawnPos();
        BlockPos safe = findSafeStandInWorld(overworld, anchor, 12, 12);
        if (safe == null) {
            safe = overworld.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    anchor);
        }

        String dimensionName = currentWorld.dimension().equals(Level.NETHER) ? "Nether" : "End";
        AiCompanionMod.LOGGER.warn(npc.getName().getString()
                + " remained in the " + dimensionName + " without a player; returning to the overworld.");
        persistBrainState();
        npc.stopRiding();
        npc.getNavigation().stop();
        npc.teleportTo(
                overworld,
                safe.getX() + 0.5,
                safe.getY(),
                safe.getZ() + 0.5,
                java.util.Set.of(),
                npc.getYRot(),
                npc.getXRot());
        currentMode = "idle";
        activeFollowTarget = null;
        currentWalkTarget = null;
        currentMineTarget = null;
        miningSearchWaypoint = null;
        clearActiveExpedition(false);
        rememberAction("@idle");
        foreignDimensionTicks = 0;
        return true;
    }

    private BlockPos findSafeStandInWorld(
            net.minecraft.world.level.Level world,
            BlockPos center,
            int horizontalRadius,
            int verticalRadius) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int y = verticalRadius; y >= -verticalRadius; y--) {
            for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    BlockPos candidate = center.offset(x, y, z);
                    if (!isSafeStandPosition(world, candidate)) continue;
                    double distance = candidate.distSqr(center);
                    if (distance < bestDistance) {
                        best = candidate.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private boolean handleBoatTravel() {
        if (!"follow".equals(currentMode) || activeFollowTarget == null || activeFollowTarget.isRemoved()) {
            currentBoatTarget = null;
            currentBoatStandTarget = null;
            companionBoat = null;
            boatTravelTicks = 0;
            return false;
        }

        if (npc.isPassenger() && npc.getVehicle() instanceof Boat boat) {
            companionBoat = boat;
            return steerBoatTowardFollowTarget(boat);
        }

        if (!shouldUseBoatForTarget(activeFollowTarget.blockPosition())) {
            currentBoatTarget = null;
            currentBoatStandTarget = null;
            boatTravelTicks = 0;
            return false;
        }

        if (currentBoatTarget == null || !isBoatLaunchWater(currentBoatTarget)) {
            currentBoatTarget = findBoatLaunchPosToward(activeFollowTarget.blockPosition());
            currentBoatStandTarget = currentBoatTarget == null ? null : findBoatStandPosBeside(currentBoatTarget);
        } else if (currentBoatStandTarget == null || !isSafeStandPosition(npc.level(), currentBoatStandTarget)) {
            currentBoatStandTarget = findBoatStandPosBeside(currentBoatTarget);
        }

        if (currentBoatTarget == null || currentBoatStandTarget == null) {
            return false;
        }

        // Decrement launch cooldown; if a boat was recently spawned, wait before trying again
        if (boatLaunchCooldownTicks > 0) {
            boatLaunchCooldownTicks--;
            // If the previously spawned boat is still alive but npc isn't riding it, try to ride it
            if (companionBoat != null && !companionBoat.isRemoved()) {
                npc.startRiding(companionBoat);
            }
            return true;
        }

        // Clear a stale companionBoat reference if it's already gone
        if (companionBoat != null && companionBoat.isRemoved()) {
            companionBoat = null;
        }

        double standDistance = npc .distanceToSqr(Vec3.atCenterOf(currentBoatStandTarget));
        if (standDistance > 4.0 || npc.isInWater()) {
            if (npc.getNavigation().isDone() || followRepathTicks % FOLLOW_REPATH_TICKS == 0) {
                startPathTo(currentBoatStandTarget, 1.15);
            }
            return true;
        }

        return launchBoatAt(currentBoatTarget, currentBoatStandTarget);
    }

    private boolean launchBoatAt(BlockPos launchPos, BlockPos standPos) {
        if (!(npc.level() instanceof ServerLevel serverWorld)) {
            return false;
        }

        Boat boat = EntityType.BOAT.create(serverWorld);
        if (boat == null) {
            return false;
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(
                launchPos.getZ() + 0.5 - (standPos.getZ() + 0.5),
                launchPos.getX() + 0.5 - (standPos.getX() + 0.5))) - 90.0);
        boat.moveTo(launchPos.getX() + 0.5, launchPos.getY() + 1.0, launchPos.getZ() + 0.5, yaw, 0.0F);
        boat.setYRot(yaw);
        serverWorld.addFreshEntity(boat);

        npc.getNavigation().stop();
        npc.getLookControl() .setLookAt(launchPos.getX() + 0.5, launchPos.getY() + 0.5, launchPos.getZ() + 0.5);
        npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        npc.startRiding(boat);
        companionBoat = boat;
        boatTravelTicks = 0;
        boatLaunchCooldownTicks = 40; // prevent re-spawning if startRiding fails this tick
        return true;
    }

    private boolean steerBoatTowardFollowTarget(Boat boat) {
        if (activeFollowTarget == null || activeFollowTarget.isRemoved() || activeFollowTarget.hasDisconnected()) {
            dismountBoatNear(boat, boat.blockPosition());
            return false;
        }

        boatTravelTicks++;
        Vec3 target = new Vec3(activeFollowTarget.getX(), activeFollowTarget.getY(), activeFollowTarget.getZ());
        Vec3 boatPos = new Vec3(boat.getX(), boat.getY(), boat.getZ());
        Vec3 delta = target.subtract(boatPos);
        Vec3 horizontal = new Vec3(delta.x, 0.0, delta.z);

        if (boatTravelTicks > 30 && (horizontal.lengthSqr() < FOLLOW_START_DISTANCE_SQUARED || !hasDeepWaterBetween(boat.blockPosition(), activeFollowTarget.blockPosition()))) {
            dismountBoatNear(boat, activeFollowTarget.blockPosition());
            return true;
        }

        if (horizontal.lengthSqr() < 0.25) {
            boat.setDeltaMovement(0.0, boat.getDeltaMovement().y, 0.0);
            return true;
        }

        Vec3 push = horizontal.normalize().scale(0.36);
        float yaw = (float) (Math.toDegrees(Math.atan2(push.z, push.x)) - 90.0);
        boat.setYRot(yaw);
        boat.setInput(true, false, false, false);
        boat.setDeltaMovement(push.x, boat.getDeltaMovement().y, push.z);
        npc.getLookControl() .setLookAt(target.x, target.y, target.z);
        return true;
    }

    private void dismountBoatNear(Boat boat, BlockPos preferredLanding) {
        BlockPos landing = findSafeStandAround(preferredLanding, 5, 4);
        if (landing == null) {
            landing = findSafeStandAround(boat.blockPosition(), 5, 4);
        }

        npc.stopRiding();
        if (landing != null) {
            npc .moveTo(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5, npc.getYRot(), npc.getXRot());
        }

        if (boat == companionBoat && !boat.isRemoved()) {
            boat.discard();
        }
        companionBoat = null;
        currentBoatTarget = null;
        currentBoatStandTarget = null;
        boatTravelTicks = 0;
    }

    private boolean shouldUseBoatForTarget(BlockPos target) {
        if (npc .distanceToSqr(Vec3.atCenterOf(target)) < BOAT_USE_DISTANCE_SQUARED) {
            return false;
        }

        return hasDeepWaterBetween(npc.blockPosition(), target);
    }

    private boolean hasDeepWaterBetween(BlockPos from, BlockPos to) {
        net.minecraft.world.level.Level world = npc.level();
        Vec3 start = Vec3.atCenterOf(from);
        Vec3 end = Vec3.atCenterOf(to);
        Vec3 delta = end.subtract(start);
        int steps = Math.min(BOAT_DEEP_WATER_SCAN_STEPS, Math.max(8, (int) Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        int deepSamples = 0;

        for (int i = 1; i <= steps; i++) {
            double t = (double) i / (double) steps;
            BlockPos sample = BlockPos.containing(start.x + delta.x * t, start.y + delta.y * t, start.z + delta.z * t);
            if (isDeepWaterColumn(world, sample)) {
                deepSamples++;
                if (deepSamples >= 3) {
                    return true;
                }
            }
        }

        return false;
    }

    private BlockPos findBoatLaunchPosToward(BlockPos target) {
        net.minecraft.world.level.Level world = npc.level();
        BlockPos origin = npc.blockPosition();
        Vec3 from = Vec3.atCenterOf(origin);
        Vec3 to = Vec3.atCenterOf(target);
        Vec3 horizontal = new Vec3(to.x - from.x, 0.0, to.z - from.z);
        if (horizontal.lengthSqr() < 0.01) {
            return isBoatLaunchWater(origin) ? origin : null;
        }

        Vec3 direction = horizontal.normalize();
        for (int step = 0; step <= BOAT_LAUNCH_SEARCH_RADIUS; step++) {
            BlockPos linePos = BlockPos.containing(from.x + direction.x * step, from.y, from.z + direction.z * step);
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    for (int y = 2; y >= -3; y--) {
                        BlockPos candidate = linePos.offset(x, y, z);
                        if (isBoatLaunchWater(candidate)
                                && isDeepWaterColumn(world, candidate)
                                && findBoatStandPosBeside(candidate) != null) {
                            return candidate;
                        }
                    }
                }
            }
        }

        return null;
    }

    private BlockPos findBoatStandPosBeside(BlockPos waterPos) {
        net.minecraft.world.level.Level world = npc.level();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos edge = waterPos.relative(direction);
            for (int y = -1; y <= 2; y++) {
                BlockPos candidate = edge.offset(0, y, 0);
                if (!isSafeStandPosition(world, candidate)) continue;

                double distance = candidate.distSqr(npc.blockPosition());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }

        return best;
    }

    private boolean isBoatLaunchWater(BlockPos pos) {
        net.minecraft.world.level.Level world = npc.level();
        return world.getFluidState(pos).is(FluidTags.WATER)
                && world.getFluidState(pos.below()).is(FluidTags.WATER)
                && world.getFluidState(pos.above()).isEmpty()
                && world.getBlockState(pos.above()).isAir();
    }

    private boolean isDeepWaterColumn(net.minecraft.world.level.Level world, BlockPos column) {
        for (int y = 3; y >= -4; y--) {
            BlockPos water = column.offset(0, y, 0);
            if (world.getFluidState(water).is(FluidTags.WATER)
                    && world.getFluidState(water.below()).is(FluidTags.WATER)) {
                return true;
            }
        }

        return false;
    }

    private int getWaterDepthAt(BlockPos pos) {
        if (!(npc.level() instanceof ServerLevel world)) return 0;

        int depth = 0;
        // Count water blocks going down from the position
        for (int y = 0; y >= -3; y--) {
            BlockPos checkPos = pos.offset(0, y, 0);
            if (world.getFluidState(checkPos).is(FluidTags.WATER)) {
                depth++;
            } else {
                break; // Stop counting at first non-water block
            }
        }

        return depth;
    }

    private void doExpeditionLogic() {
        if (!(npc.level() instanceof ServerLevel serverWorld)) {
            currentMode = "idle";
            finishAutonomousRoleAction();
            rememberAction("@idle");
            releaseExpeditionTickets();
            return;
        }

        expeditionTicks++;
        updateExpeditionTickets(serverWorld);
        scanForCoolDiscoveries();

        BlockPos waypoint = getCurrentExpeditionWaypoint();
        if (waypoint == null) {
            if (expeditionReturningHome) {
                finishExpedition();
            } else {
                beginReturnHome("reached destination");
            }
            return;
        }

        if (!isSafeStandPosition(serverWorld, waypoint)) {
            BlockPos safer = findSafeStandAround(waypoint, 10, 24);
            if (safer != null) {
                expeditionRoute.set(expeditionRouteIndex, safer);
                waypoint = safer;
            }
        }

        if (npc .distanceToSqr(Vec3.atCenterOf(waypoint)) < 16.0) {
            expeditionRouteIndex++;
            expeditionNoProgressTicks = 0;
            lastExpeditionProgressPos = null;
            if (expeditionRouteIndex >= expeditionRoute.size()) {
                if (expeditionReturningHome) {
                    finishExpedition();
                } else {
                    beginReturnHome("reached destination");
                }
            }
            return;
        }

        trackExpeditionProgress();

        if (expeditionNoProgressTicks >= EXPEDITION_STUCK_TICKS) {
            adaptTerrainWhenStuck();
            if (expeditionNoProgressTicks >= EXPEDITION_STUCK_TICKS * 2 && expeditionRouteIndex < expeditionRoute.size() - 1) {
                AiCompanionMod.LOGGER.warn("Expedition skipped a blocked waypoint near " + waypoint.toShortString());
                expeditionRouteIndex++;
                expeditionNoProgressTicks = 0;
            }
        }

        if (npc.getNavigation().isDone() || expeditionTicks % EXPEDITION_REPATH_TICKS == 0) {
            startPathTo(waypoint, 1.25);
        }
    }

    private void trackExpeditionProgress() {
        if (expeditionTicks % EXPEDITION_PROGRESS_TICKS != 0) return;

        Vec3 current = new Vec3(npc.getX(), npc.getY(), npc.getZ());
        if (lastExpeditionProgressPos != null && current.distanceToSqr(lastExpeditionProgressPos) < EXPEDITION_PROGRESS_SQUARED) {
            expeditionNoProgressTicks += EXPEDITION_PROGRESS_TICKS;
        } else {
            expeditionNoProgressTicks = 0;
        }
        lastExpeditionProgressPos = current;
    }

    private BlockPos getCurrentExpeditionWaypoint() {
        if (expeditionRouteIndex < 0 || expeditionRouteIndex >= expeditionRoute.size()) {
            return null;
        }
        return expeditionRoute.get(expeditionRouteIndex);
    }

    private void startExpedition(int requestedDistance) {
        int distance = Math.max(EXPEDITION_MIN_DISTANCE, Math.min(EXPEDITION_MAX_DISTANCE, requestedDistance));
        if (expeditionHome == null) {
            expeditionHome = npc.blockPosition();
        }
        explorationAnchor = expeditionHome;
        expeditionReturningHome = false;
        expeditionDestination = chooseExpeditionDestination(expeditionHome, distance);
        rebuildExpeditionRoute(expeditionDestination);
        currentMode = "explore";
        npc.setTarget(null);
        sendEventToAi("expedition_start", formatExpeditionDetail(distance, expeditionDestination));
    }

    private void beginReturnHome(String reason) {
        if (expeditionHome == null) {
            expeditionHome = explorationAnchor != null ? explorationAnchor : npc.blockPosition();
        }
        expeditionReturningHome = true;
        expeditionDestination = expeditionHome;
        rebuildExpeditionRoute(expeditionHome);
        sendEventToAi("expedition_turnaround", reason + "; home " + formatBlockPos(expeditionHome));
    }

    private void finishExpedition() {
        sendEventToAi("expedition_return", "home " + formatBlockPos(expeditionHome));
        currentMode = "idle";
        finishAutonomousRoleAction();
        rememberAction("@idle");
        expeditionRoute.clear();
        expeditionRouteIndex = 0;
        expeditionDestination = null;
        expeditionReturningHome = false;
        expeditionTicks = 0;
        expeditionNoProgressTicks = 0;
        lastExpeditionProgressPos = null;
        releaseExpeditionTickets();
    }

    private void rebuildExpeditionRoute(BlockPos destination) {
        expeditionRoute.clear();
        expeditionRouteIndex = 0;
        expeditionTicks = 0;
        expeditionNoProgressTicks = 0;
        lastExpeditionProgressPos = null;

        BlockPos start = npc.blockPosition();
        double dx = destination.getX() - start.getX();
        double dz = destination.getZ() - start.getZ();
        double horizontalDistance = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
        int steps = Math.max(1, (int) Math.ceil(horizontalDistance / EXPEDITION_WAYPOINT_STEP));

        for (int step = 1; step <= steps; step++) {
            double t = (double) step / (double) steps;
            int x = (int) Math.round(start.getX() + dx * t);
            int z = (int) Math.round(start.getZ() + dz * t);
            int y = step == steps ? destination.getY() : start.getY();
            expeditionRoute .add(new BlockPos(x, y, z));
        }
    }

    private BlockPos chooseExpeditionDestination(BlockPos home, int distance) {
        double angle = npc.getRandom().nextFloat() * Math.PI * 2.0;
        double distanceVariance = 0.85 + npc.getRandom().nextFloat() * 0.3;
        int x = home.getX() + (int) Math.round(Math.cos(angle) * distance * distanceVariance);
        int z = home.getZ() + (int) Math.round(Math.sin(angle) * distance * distanceVariance);
        return new BlockPos(x, home.getY(), z);
    }

    private String formatExpeditionDetail(int distance, BlockPos destination) {
        return "target " + formatBlockPos(destination) + "; planned distance " + distance + "; home " + formatBlockPos(expeditionHome);
    }

    private String formatBlockPos(BlockPos pos) {
        if (pos == null) return "unknown";
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private void updateExpeditionTickets(ServerLevel serverWorld) {
        Set<Long> desired = new HashSet<>();
        desired .add(new ChunkPos(npc.blockPosition()).toLong());

        BlockPos waypoint = getCurrentExpeditionWaypoint();
        if (waypoint != null) {
            desired .add(new ChunkPos(waypoint).toLong());
        }
        if (expeditionRouteIndex + 1 < expeditionRoute.size()) {
            desired .add(new ChunkPos(expeditionRoute.get(expeditionRouteIndex + 1)).toLong());
        }

        for (Long chunkKey : new HashSet<>(expeditionTicketChunks)) {
            if (!desired.contains(chunkKey)) {
                ChunkPos chunkPos = new ChunkPos(chunkKey);
                serverWorld.getChunkSource().removeRegionTicket(TicketType.UNKNOWN, chunkPos, 2, chunkPos);
                expeditionTicketChunks.remove(chunkKey);
            }
        }

        for (Long chunkKey : desired) {
            if (expeditionTicketChunks .add(chunkKey)) {
                ChunkPos chunkPos = new ChunkPos(chunkKey);
                serverWorld.getChunkSource().addRegionTicket(TicketType.UNKNOWN, chunkPos, 2, chunkPos);
            }
        }
    }

    private void releaseExpeditionTickets() {
        if (!(npc.level() instanceof ServerLevel serverWorld)) {
            expeditionTicketChunks.clear();
            return;
        }

        for (Long chunkKey : new HashSet<>(expeditionTicketChunks)) {
            ChunkPos chunkPos = new ChunkPos(chunkKey);
            serverWorld.getChunkSource().removeRegionTicket(TicketType.UNKNOWN, chunkPos, 2, chunkPos);
        }
        expeditionTicketChunks.clear();
    }
    
    private void doMiningLogic() {
        if (!(npc.level() instanceof ServerLevel world)) return;
        updateMiningProgressWatchdog();

        if (minerReturningToStorage) {
            handleMinerStorageReturn(world);
            return;
        }

        if (shouldMinerReturnToStorage()) {
            beginMinerStorageReturn(world);
            return;
        }

        if (currentMineTarget == null || world.getBlockState(currentMineTarget).isAir()) {
            miningSwingTicks = 0;
            if (tickCounter % 10 != 0) return; // Throttle target search only
            int radius = MINER_SEARCH_SCAN_RADIUS;
            int preferredY = Math.max(
                    world.getMinBuildHeight() + 5,
                    Math.min(world.getMaxBuildHeight() - 6, getPreferredOreY(targetBlockName)));
            int verticalScanRadius = npc.getBlockY() > preferredY + MINER_ORE_BAND_TOLERANCE
                    ? 4
                    : radius;
            for (int x = -radius; x <= radius; x++) {
                for (int y = -verticalScanRadius; y <= verticalScanRadius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        net.minecraft.core.BlockPos pos = npc.blockPosition().offset(x, y, z);
                        if (failedMiningTargetCooldowns.containsKey(pos)) continue;
                        net.minecraft.world.level.block.state.BlockState state = world.getBlockState(pos);
                        if (!isSafeMiningTarget(world, pos, state)) continue;
                        String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                        if (name.contains(targetBlockName)) {
                            currentMineTarget = pos;
                            miningSearchWaypoint = null;
                            miningSearchTicks = 0;
                            miningNoProgressTicks = 0;
                            lastMiningProgressPos = new Vec3(npc.getX(), npc.getY(), npc.getZ());
                            moveNear(pos, 1.0);
                            return;
                        }
                    }
                }
            }

            continueMiningSearch(world);
        } else {
            if (npc .distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(currentMineTarget)) < INTERACTION_DISTANCE_SQUARED) {
                BlockState targetState = world.getBlockState(currentMineTarget);
                if (!isSafeMiningTarget(world, currentMineTarget, targetState)) {
                    currentMineTarget = null;
                    miningSwingTicks = 0;
                    return;
                }
                equipToolFor(targetState);
                npc.getLookControl() .setLookAt(currentMineTarget.getX() + 0.5, currentMineTarget.getY() + 0.5, currentMineTarget.getZ() + 0.5);
                miningSwingTicks++;
                if (miningSwingTicks % 2 == 0) {
                    npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                }
                if (miningSwingTicks >= MINING_SWING_DURATION) {
                    String minedBlockName = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(targetState.getBlock()).getPath();
                    world.destroyBlock(currentMineTarget, true, npc);
                    currentMineTarget = null;
                    miningSwingTicks = 0;
                    miningSearchTicks = 0;
                    miningNoProgressTicks = 0;
                    lastMiningProgressPos = new Vec3(npc.getX(), npc.getY(), npc.getZ());
                    updateEquipmentProgress(minedBlockName);
                }
            } else {
                miningSwingTicks = 0;
                if (tickCounter % 10 == 0) {
                    if (npc.getNavigation().isDone()) {
                        moveNear(currentMineTarget, 1.0);
                    }
                    if (npc.getNavigation().isDone() || tickCounter % 40 == 0) {
                        tryMinePathObstacle(currentMineTarget);
                    }
                }
            }
        }
    }

    private void continueMiningSearch(ServerLevel world) {
        miningSearchTicks++;
        int preferredY = Math.max(
                world.getMinBuildHeight() + 5,
                Math.min(world.getMaxBuildHeight() - 6, getPreferredOreY(targetBlockName)));
        if (npc.getBlockY() > preferredY + MINER_ORE_BAND_TOLERANCE) {
            if (miningSearchWaypoint == null
                    || miningSearchWaypoint.getY() >= npc.getBlockY()
                    || npc .distanceToSqr(Vec3.atCenterOf(miningSearchWaypoint)) < 6.0
                    || miningSearchTicks % 160 == 0) {
                miningSearchWaypoint = chooseMiningDescentWaypoint(preferredY);
            }
            if (npc.getNavigation().isDone()
                    || miningSearchTicks % MINER_SEARCH_REPATH_TICKS == 0) {
                startPathTo(miningSearchWaypoint, 1.0);
            }
            if (npc.getNavigation().isDone() || miningSearchTicks % 8 == 0) {
                carveTowardMiningSearch(world, miningSearchWaypoint);
            }
            return;
        }

        if (miningSearchWaypoint == null
                || npc .distanceToSqr(Vec3.atCenterOf(miningSearchWaypoint)) < 9.0
                || miningSearchTicks % 100 == 0) {
            miningSearchWaypoint = chooseMiningSearchWaypoint(world);
        }

        if (miningSearchWaypoint == null) {
            beginMinerStorageReturn(world);
            return;
        }

        if (npc.getNavigation().isDone() || miningSearchTicks % MINER_SEARCH_REPATH_TICKS == 0) {
            startPathTo(miningSearchWaypoint, 1.0);
        }
        if (npc.getNavigation().isDone() || miningSearchTicks % 10 == 0) {
            carveTowardMiningSearch(world, miningSearchWaypoint);
        }
    }

    private BlockPos chooseMiningDescentWaypoint(int preferredY) {
        int drop = Math.min(MINER_DESCENT_STAGE_DROP, npc.getBlockY() - preferredY);
        int distance = drop + 4 + npc.getRandom().nextInt(5);
        double angle = npc.getRandom().nextDouble() * Math.PI * 2.0;
        int x = npc.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = npc.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        return new BlockPos(x, npc.getBlockY() - drop, z);
    }

    private BlockPos chooseMiningSearchWaypoint(ServerLevel world) {
        int targetY = Math.max(world.getMinBuildHeight() + 5, Math.min(world.getMaxBuildHeight() - 6, getPreferredOreY(targetBlockName)));
        int distance = 10 + npc.getRandom().nextInt(11);
        double angle = npc.getRandom().nextDouble() * Math.PI * 2.0;
        int x = npc.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = npc.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        return new BlockPos(x, targetY, z);
    }

    private int getPreferredOreY(String blockName) {
        if (blockName.contains("coal")) return 48;
        if (blockName.contains("iron")) return 16;
        if (blockName.contains("copper")) return 48;
        if (blockName.contains("lapis")) return 0;
        if (blockName.contains("gold")) return -16;
        if (blockName.contains("redstone")) return -54;
        if (blockName.contains("diamond")) return -54;
        if (blockName.contains("emerald")) return 96;
        if (blockName.contains("ancient_debris")) return 15;
        if (blockName.contains("quartz")) return 32;
        return 16;
    }

    private boolean carveTowardMiningSearch(ServerLevel world, BlockPos target) {
        Direction direction = getHorizontalDirectionToward(target);
        BlockPos feet = npc.blockPosition();
        BlockPos ahead = feet.relative(direction);
        int vertical = Integer.compare(target.getY(), feet.getY());

        List<BlockPos> clearance = new ArrayList<>();
        clearance .add(ahead);
        clearance .add(ahead.above());
        if (vertical < 0) {
            clearance .add(ahead.below());
        } else if (vertical > 0) {
            clearance .add(ahead.above(2));
            clearance .add(feet.above(2));
        }

        for (BlockPos pos : clearance) {
            BlockState state = world.getBlockState(pos);
            if (!isMiningObstacle(world, pos, state)) continue;
            if (hasDangerousMiningFluidNearby(world, pos)) {
                miningSearchWaypoint = null;
                return false;
            }

            equipToolFor(state);
            npc.getNavigation().stop();
            npc.getLookControl() .setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            world.destroyBlock(pos, true, npc);
            return true;
        }

        BlockPos step = vertical < 0 ? ahead.below() : vertical > 0 ? ahead.above() : ahead;
        if (isSafeStandPosition(world, step)) {
            startPathTo(step, 1.0);
        }
        return false;
    }

    private boolean hasDangerousMiningFluidNearby(ServerLevel world, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (world.getFluidState(pos.relative(direction)).is(FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryMinePathObstacle(BlockPos target) {
        if (target == null) return false;

        net.minecraft.world.level.Level world = npc.level();
        Direction direction = getHorizontalDirectionToward(target);
        BlockPos feet = npc.blockPosition();
        BlockPos ahead = feet.relative(direction);
        int verticalDelta = Integer.compare(target.getY(), feet.getY());

        List<BlockPos> candidates = new ArrayList<>();
        candidates .add(ahead);
        candidates .add(ahead.above());
        candidates .add(feet.above());
        if (verticalDelta > 0) {
            candidates .add(ahead.above(2));
            candidates .add(feet.above(2));
        }

        Set<BlockPos> checked = new HashSet<>();
        for (BlockPos pos : candidates) {
            if (!checked .add(pos)) continue;

            BlockState state = world.getBlockState(pos);
            if (!isMiningObstacle(world, pos, state)) continue;

            equipToolFor(state);
            npc.getNavigation().stop();
            npc.getLookControl() .setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            world.destroyBlock(pos, true, npc);
            npc.getNavigation().recomputePath();
            return true;
        }

        return false;
    }

    private boolean shouldMinerReturnToStorage() {
        if (requestedMiningJob && getRequestedMiningItemCount() >= requestedMiningAmount) {
            return true;
        }
        if (getInventorySlotsUsed() >= MINER_INVENTORY_DELIVERY_SLOTS) {
            return true;
        }
        return !requestedMiningJob && getMinerStorageItemCount() >= MINER_AUTONOMOUS_DELIVERY_ITEMS;
    }

    private int getRequestedMiningItemCount() {
        Item item = getPrimaryMiningDrop(targetBlockName);
        return item == null
                ? 0
                : Math.max(0, getVirtualItemCount(item) - requestedMiningStartingCount);
    }

    private Item getPrimaryMiningDrop(String blockName) {
        if (blockName.contains("coal")) return Items.COAL;
        if (blockName.contains("iron")) return Items.RAW_IRON;
        if (blockName.contains("copper")) return Items.RAW_COPPER;
        if (blockName.contains("gold")) return Items.RAW_GOLD;
        if (blockName.contains("redstone")) return Items.REDSTONE;
        if (blockName.contains("lapis")) return Items.LAPIS_LAZULI;
        if (blockName.contains("diamond")) return Items.DIAMOND;
        if (blockName.contains("emerald")) return Items.EMERALD;
        if (blockName.contains("ancient_debris")) return Items.ANCIENT_DEBRIS;
        if (blockName.contains("quartz")) return Items.QUARTZ;
        return null;
    }

    private int getVirtualItemCount(Item item) {
        ItemStack stack = virtualInventory.get(getInventoryKey(item));
        return stack == null ? 0 : stack.getCount();
    }

    private int getMinerStorageItemCount() {
        int count = 0;
        for (ItemStack stack : virtualInventory.values()) {
            if (isMinerStorageItem(stack)) count += stack.getCount();
        }
        return count;
    }

    private void beginMinerStorageReturn(ServerLevel world) {
        minerReturningToStorage = true;
        currentMineTarget = null;
        miningSearchWaypoint = null;
        npc.getNavigation().stop();
        minerStorageTarget = findNearbyMinerStorage(world);
        if (minerStorageTarget == null) {
            minerStorageTarget = createMinerStorage(world);
        }
        if (minerStorageTarget != null) {
            moveNear(minerStorageTarget, 1.05);
        }
    }

    private void handleMinerStorageReturn(ServerLevel world) {
        if (minerStorageTarget == null
                || !isMinerStorageBlock(world.getBlockState(minerStorageTarget))
                || !(world.getBlockEntity(minerStorageTarget) instanceof Container inventory)) {
            minerStorageTarget = findNearbyMinerStorage(world);
            if (minerStorageTarget == null) minerStorageTarget = createMinerStorage(world);
            if (minerStorageTarget == null) {
                finishMinerDelivery();
                return;
            }
            moveNear(minerStorageTarget, 1.05);
            return;
        }

        if (npc .distanceToSqr(Vec3.atCenterOf(minerStorageTarget)) > INTERACTION_DISTANCE_SQUARED) {
            moveNear(minerStorageTarget, 1.05);
            return;
        }

        boolean deposited = depositMinerGoods(inventory);
        if (deposited) {
            npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            persistBrainState();
            sendEventToAi("storage_stocked", "mined materials in storage");
        }
        finishMinerDelivery();
    }

    private void finishMinerDelivery() {
        boolean completedRequest = requestedMiningJob;
        minerReturningToStorage = false;
        minerStorageTarget = null;
        miningSearchWaypoint = null;
        miningSearchTicks = 0;
        currentMineTarget = null;
        currentMode = "idle";
        requestedMiningJob = false;
        requestedMiningAmount = 16;
        requestedMiningStartingCount = 0;
        finishAutonomousRoleAction();
        roleActionCooldownTicks = completedRequest ? 200 : ("tired".equals(currentMood) ? 300 : 80);
        rememberAction("@idle");
    }

    private BlockPos findNearbyMinerStorage(ServerLevel world) {
        BlockPos origin = minerWorkHome != null ? minerWorkHome : getRoleHomeAnchor();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -MINER_STORAGE_SEARCH_RADIUS; x <= MINER_STORAGE_SEARCH_RADIUS; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -MINER_STORAGE_SEARCH_RADIUS; z <= MINER_STORAGE_SEARCH_RADIUS; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!isMinerStorageBlock(world.getBlockState(pos))
                            || !(world.getBlockEntity(pos) instanceof Container)) continue;
                    double distance = pos.distSqr(origin);
                    if (distance < bestDistance) {
                        best = pos.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private boolean isMinerStorageBlock(BlockState state) {
        return state.is(Blocks.CHEST)
                || state.is(Blocks.TRAPPED_CHEST)
                || state.is(Blocks.BARREL);
    }

    private BlockPos createMinerStorage(ServerLevel world) {
        BlockPos origin = minerWorkHome != null ? minerWorkHome : getRoleHomeAnchor();
        BlockPos safe = findSafeStandInWorld(world, origin, 8, 6);
        if (safe == null) return null;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = safe.relative(direction);
            BlockState ground = world.getBlockState(candidate.below());
            if (!world.getBlockState(candidate).isAir()
                    || !world.getBlockState(candidate.above()).isAir()
                    || ground.isAir()
                    || !ground.getFluidState().isEmpty()) continue;
            world.setBlock(candidate, Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);
            AiCompanionMod.LOGGER.info(npc.getName().getString()
                    + " created shared mining storage at " + candidate.toShortString() + ".");
            return candidate.immutable();
        }
        return null;
    }

    private boolean depositMinerGoods(Container inventory) {
        boolean deposited = false;
        for (String key : new ArrayList<>(virtualInventory.keySet())) {
            ItemStack stored = virtualInventory.get(key);
            if (stored == null || stored.isEmpty() || !isMinerStorageItem(stored)) continue;
            ItemStack moving = stored.copy();
            if (!insertStackIntoInventory(inventory, moving)) continue;
            int moved = stored.getCount() - moving.getCount();
            if (moved <= 0) continue;
            stored.shrink(moved);
            if (stored.isEmpty()) virtualInventory.remove(key);
            deposited = true;
        }
        return deposited;
    }

    private boolean isMinerStorageItem(ItemStack stack) {
        return stack.is(Items.COAL)
                || stack.is(Items.RAW_IRON)
                || stack.is(Items.RAW_COPPER)
                || stack.is(Items.RAW_GOLD)
                || stack.is(Items.REDSTONE)
                || stack.is(Items.LAPIS_LAZULI)
                || stack.is(Items.DIAMOND)
                || stack.is(Items.EMERALD)
                || stack.is(Items.QUARTZ)
                || stack.is(Items.ANCIENT_DEBRIS)
                || stack.is(Items.COBBLESTONE)
                || stack.is(Items.COBBLED_DEEPSLATE)
                || stack.is(Items.FLINT);
    }

    private void doIdleWander() {
        BlockPos anchor = getExplorationAnchor();
        int radius = getExplorationRadius();
        net.minecraft.core.BlockPos targetPos = findExplorationTarget(anchor, radius);

        if (targetPos != null) {
            // Look toward destination before moving for a natural feel
            npc.getLookControl() .setLookAt(targetPos.getX() + 0.5, targetPos.getY() + 1.0, targetPos.getZ() + 0.5);
            // Varied walk speed so movement doesn't feel mechanical
            double speed = 0.72 + npc.getRandom().nextFloat() * 0.18;
            boolean success = npc.getNavigation() .moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, speed);
            if (!success) {
                npc.getNavigation().stop();
                AiCompanionMod.LOGGER.warn("NPC navigation FAILED to find path to: " + targetPos.toShortString());
            }
        } else {
            AiCompanionMod.LOGGER.warn("NPC failed to find an exploration waypoint within " + radius + " blocks!");
        }
    }

    private boolean doRoleIdleLogic() {
        String role = npc.getAppearanceVariantName();
        if ("farmer".equals(role) || "rancher".equals(role)) {
            return false;
        }
        if (!npc.getNavigation().isDone() || roleActionCooldownTicks > 0) {
            return false;
        }

        roleIdleTimer++;
        if (roleIdleTimer < ROLE_IDLE_DECISION_TICKS) {
            return false;
        }
        roleIdleTimer = 0;

        return switch (role) {
            case "miner" -> doMinerIdleRole();
            case "guardian" -> doGuardianIdleRole();
            case "scout" -> doScoutIdleRole();
            case "wanderer" -> doWandererIdleRole();
            default -> false;
        };
    }

    private boolean doMinerIdleRole() {
        if (!(npc.level() instanceof ServerLevel world)) return false;

        if ("tired".equals(currentMood) && npc.getRandom().nextFloat() < 0.55f) {
            return false;
        }
        if ("cautious".equals(currentMood) && npc.getRandom().nextFloat() < 0.2f) {
            return false;
        }

        if (getMinerStorageItemCount() >= MINER_AUTONOMOUS_DELIVERY_ITEMS) {
            minerWorkHome = getRoleHomeAnchor().immutable();
            currentMode = "mine";
            beginMinerStorageReturn(world);
            return true;
        }

        BlockPos ore = findRoleOreTarget(world, MINER_ROLE_SCAN_RADIUS);
        if (ore != null) {
            String blockName = BuiltInRegistries.BLOCK.getKey(world.getBlockState(ore).getBlock()).getPath();
            startAutonomousMining(blockName, ore);
            AiCompanionMod.LOGGER.info(npc.getName().getString() + " found role mining target: " + blockName + " at " + ore.toShortString());
            return true;
        }

        String[] workTargets = {"coal_ore", "iron_ore", "copper_ore", "lapis_ore", "gold_ore"};
        String selected = workTargets[npc.getRandom().nextInt(workTargets.length)];
        startAutonomousMining(selected, null);
        AiCompanionMod.LOGGER.info(npc.getName().getString()
                + " found no exposed ore and started a " + selected + " search expedition.");
        return true;
    }

    private boolean doGuardianIdleRole() {
        if (!(npc.level() instanceof ServerLevel world)) return false;

        Monster threat = findNearestHostile(world, 28.0);
        if (threat != null) {
            equipSword();
            equipShieldIfAppropriate();
            npc.setTarget(threat);
            AiCompanionMod.LOGGER.info(npc.getName().getString() + " acquired guardian target: "
                    + BuiltInRegistries.ENTITY_TYPE.getKey(threat.getType()).getPath());
            return true;
        }

        if (npc.getRandom().nextFloat() > 0.65f) {
            return false;
        }

        BlockPos patrol = findExplorationTarget(getRoleHomeAnchor(), GUARDIAN_ROLE_PATROL_RADIUS);
        if (patrol != null) {
            startAutonomousWalk(patrol, 0.85);
            return true;
        }

        return false;
    }

    private boolean doScoutIdleRole() {
        if (npc.getRandom().nextFloat() < 0.35f) {
            int distance = npc.getRandom().nextBoolean() ? 1000 : 2500;
            startAutonomousExpedition(distance);
            return true;
        }

        BlockPos scoutTarget = findExplorationTarget(getRoleHomeAnchor(), SCOUT_ROLE_WALK_RADIUS);
        if (scoutTarget != null) {
            startAutonomousWalk(scoutTarget, 1.0);
            return true;
        }

        return false;
    }

    private boolean doWandererIdleRole() {
        if (!(npc.level() instanceof ServerLevel world)) return false;
        if ("tired".equals(currentMood) && npc.getRandom().nextFloat() < 0.55f) {
            return false;
        }

        ServerPlayer visitor = findWandererVisitPlayer(world);
        if (visitor != null && npc.getRandom().nextFloat() < 0.55f) {
            BlockPos visitTarget = findSafeStandAround(visitor.blockPosition(), 5, 3);
            if (visitTarget != null) {
                startAutonomousWalk(
                        visitTarget,
                        0.9,
                        "visiting player " + visitor.getName().getString());
                return true;
            }
        }

        BlockPos wanderTarget = findExplorationTarget(getRoleHomeAnchor(), WANDERER_ROLE_WALK_RADIUS);
        if (wanderTarget != null) {
            startAutonomousWalk(wanderTarget, 0.85, "touring the local area");
            return true;
        }

        return false;
    }

    private ServerPlayer findWandererVisitPlayer(ServerLevel world) {
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        double maxDistanceSq = WANDERER_PLAYER_VISIT_RADIUS * WANDERER_PLAYER_VISIT_RADIUS;
        for (ServerPlayer player : world.players()) {
            double distance = npc .distanceToSqr(player);
            if (distance < 144.0 || distance > maxDistanceSq || distance >= bestDistance) continue;
            best = player;
            bestDistance = distance;
        }
        return best;
    }

    private BlockPos findRoleOreTarget(ServerLevel world, int radius) {
        BlockPos origin = npc.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -16; y <= 8; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    int priority = getRoleOrePriority(name);
                    if (priority <= 0) continue;
                    if (!isSafeMiningTarget(world, pos, state)) continue;

                    double score = origin.distSqr(pos) - priority * 35.0;
                    if (score < bestScore) {
                        bestScore = score;
                        best = pos;
                    }
                }
            }
        }

        return best;
    }

    private int getRoleOrePriority(String blockName) {
        if (blockName.contains("ancient_debris")) return 12;
        if (blockName.contains("diamond_ore")) return 10;
        if (blockName.contains("emerald_ore")) return 9;
        if (blockName.contains("gold_ore")) return 7;
        if (blockName.contains("iron_ore")) return 6;
        if (blockName.contains("lapis_ore") || blockName.contains("redstone_ore")) return 5;
        if (blockName.contains("copper_ore")) return 4;
        if (blockName.contains("coal_ore")) return 3;
        return 0;
    }

    private Monster findNearestHostile(ServerLevel world, double range) {
        List<Monster> hostiles = world.getEntitiesOfClass(
                Monster.class,
                npc.getBoundingBox() .inflate(range),
                e -> e.isAlive() && !e.isRemoved());

        Monster best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Monster hostile : hostiles) {
            double distance = npc .distanceToSqr(hostile);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = hostile;
            }
        }
        return best;
    }

    private BlockPos getRoleHomeAnchor() {
        BlockPos home = npc.getHomePosition();
        if (home != null) {
            return home;
        }
        if (explorationAnchor == null) {
            explorationAnchor = npc.blockPosition();
        }
        return explorationAnchor;
    }

    private void startAutonomousMining(String blockName, BlockPos target) {
        activeFollowTarget = null;
        resetFollowProgress();
        currentMode = "mine";
        currentWalkTarget = null;
        currentWaterExitTarget = null;
        currentBoatTarget = null;
        currentBoatStandTarget = null;
        targetBlockName = blockName;
        currentMineTarget = target;
        requestedMiningJob = false;
        requestedMiningAmount = MINER_AUTONOMOUS_DELIVERY_ITEMS;
        requestedMiningStartingCount = 0;
        minerWorkHome = getRoleHomeAnchor().immutable();
        miningSearchWaypoint = null;
        miningSearchTicks = 0;
        minerReturningToStorage = false;
        minerStorageTarget = null;
        buildStandPos = null;
        buildReady = false;
        buildSitePrepared = false;
        buildSitePreparationPasses = 0;
        buildEvacuationTicks = 0;
        buildPlacementAttempts = 0;
        buildQueue.clear();
        clearFarmerTargets();
        clearActiveExpedition(false);
        autonomousRoleAction = true;
        roleActionCooldownTicks = "tired".equals(currentMood) ? 300 : 100;
        if (target != null) {
            moveNear(target, 1.0);
        }
        rememberAction("@mine " + blockName);
    }

    private void startAutonomousWalk(BlockPos target, double speed) {
        startAutonomousWalk(target, speed, "");
    }

    private void startAutonomousWalk(BlockPos target, double speed, String purpose) {
        activeFollowTarget = null;
        resetFollowProgress();
        currentMode = "walk";
        autonomousWalkPurpose = purpose == null ? "" : purpose;
        currentWalkTarget = target;
        currentWaterExitTarget = null;
        currentBoatTarget = null;
        currentBoatStandTarget = null;
        currentMineTarget = null;
        buildStandPos = null;
        buildReady = false;
        buildSitePrepared = false;
        buildSitePreparationPasses = 0;
        buildEvacuationTicks = 0;
        buildPlacementAttempts = 0;
        buildQueue.clear();
        clearFarmerTargets();
        clearActiveExpedition(false);
        autonomousRoleAction = true;
        roleActionCooldownTicks = ROLE_ACTION_COOLDOWN_TICKS;
        moveNear(target, speed);
        rememberAction("@walk " + target.getX() + " " + target.getY() + " " + target.getZ());
    }

    private void finishWandererJourney() {
        if (!autonomousRoleAction
                || !"wanderer".equals(npc.getAppearanceVariantName())
                || autonomousWalkPurpose.isBlank()) {
            autonomousWalkPurpose = "";
            return;
        }

        String detail = autonomousWalkPurpose + " near " + formatBlockPos(npc.blockPosition());
        sendEventToAi("wanderer_report", detail);
        autonomousWalkPurpose = "";
    }

    private void startAutonomousExpedition(int distance) {
        activeFollowTarget = null;
        resetFollowProgress();
        currentWalkTarget = null;
        currentWaterExitTarget = null;
        currentBoatTarget = null;
        currentBoatStandTarget = null;
        currentMineTarget = null;
        buildStandPos = null;
        buildReady = false;
        buildSitePrepared = false;
        buildSitePreparationPasses = 0;
        buildEvacuationTicks = 0;
        buildPlacementAttempts = 0;
        buildQueue.clear();
        clearFarmerTargets();
        autonomousRoleAction = true;
        roleActionCooldownTicks = ROLE_ACTION_COOLDOWN_TICKS * 2;
        startExpedition(distance);
        rememberAction("@explore " + distance);
    }

    private boolean shouldIgnoreIdleForAutonomousRole() {
        if (!autonomousRoleAction) {
            return false;
        }
        return "mine".equals(currentMode)
                || ("build".equals(currentMode) && !buildQueue.isEmpty())
                || ("walk".equals(currentMode) && currentWalkTarget != null)
                || ("explore".equals(currentMode) && getCurrentExpeditionWaypoint() != null);
    }

    private void finishAutonomousRoleAction() {
        autonomousRoleAction = false;
        roleActionCooldownTicks = Math.max(roleActionCooldownTicks, ROLE_ACTION_COOLDOWN_TICKS);
    }

    private BlockPos getExplorationAnchor() {
        if (explorationAnchor == null) {
            explorationAnchor = npc.blockPosition();
        }

        if (npc.level() instanceof ServerLevel serverWorld) {
            ServerPlayer nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (ServerPlayer player : serverWorld.players()) {
                double distance = npc .distanceToSqr(player);
                if (distance < nearestDistance) {
                    nearest = player;
                    nearestDistance = distance;
                }
            }

            if (nearest != null && nearestDistance <= 2304.0) { // 48 blocks
                return nearest.blockPosition();
            }
        }

        return explorationAnchor;
    }

    private int getExplorationRadius() {
        return switch (npc.getAppearanceVariantName()) {
            case "scout" -> 48;
            case "miner" -> 30;
            case "guardian" -> 18;
            case "builder" -> 16;
            default -> 32;
        };
    }

    private BlockPos findExplorationTarget(BlockPos anchor, int radius) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = npc.getRandom().nextFloat() * Math.PI * 2.0;
            int minDistance = attempt < 6 ? Math.min(8, radius) : Math.min(16, radius);
            int distance = minDistance + npc.getRandom().nextInt(Math.max(1, radius - minDistance + 1));
            int x = anchor.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos estimate = new BlockPos(x, anchor.getY(), z);
            BlockPos safe = findSafeStandAround(estimate, 4, 8);
            if (safe != null
                    && !isNearFenceOrGate(safe, FENCE_AVOIDANCE_RADIUS)
                    && safe.distSqr(anchor) <= (double) radius * radius + 25.0) {
                return safe;
            }
        }

        BlockPos fallback = findSafeStandAround(anchor, Math.min(radius, 10), 5);
        return fallback != null && !isNearFenceOrGate(fallback, FENCE_AVOIDANCE_RADIUS) ? fallback : null;
    }

    private void scanForCoolDiscoveries() {
        discoveryScanTicks++;
        if (discoveryScanTicks < DISCOVERY_SCAN_TICKS) return;
        discoveryScanTicks = 0;

        discoveryCooldowns.replaceAll((key, cooldown) -> cooldown - DISCOVERY_SCAN_TICKS);
        discoveryCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);

        if (!(npc.level() instanceof ServerLevel world)) return;

        BlockPos origin = npc.blockPosition();
        BlockPos bestPos = null;
        String bestName = null;
        int bestScore = 0;

        for (int x = -DISCOVERY_RADIUS; x <= DISCOVERY_RADIUS; x++) {
            for (int y = -10; y <= 10; y++) {
                for (int z = -DISCOVERY_RADIUS; z <= DISCOVERY_RADIUS; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    String blockName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    int score = getDiscoveryScore(blockName);
                    if (score <= bestScore) continue;

                    String key = blockName + ":" + new ChunkPos(pos).toLong();
                    if (discoveryCooldowns.containsKey(key)) continue;

                    bestScore = score;
                    bestName = blockName;
                    bestPos = pos;
                }
            }
        }

        if (bestPos != null && bestName != null) {
            discoveryCooldowns.put(bestName + ":" + new ChunkPos(bestPos).toLong(), DISCOVERY_COOLDOWN_TICKS);
            String detail = describeDiscovery(bestName) + " at " + formatBlockPos(bestPos)
                    + "; distance from home " + getDistanceFromExpeditionHome();
            sendEventToAi("discovery", detail);
        }
    }

    private void scanForWandererLandmarks() {
        discoveryScanTicks++;
        if (discoveryScanTicks < DISCOVERY_SCAN_TICKS) return;
        discoveryScanTicks = 0;

        discoveryCooldowns.replaceAll((key, cooldown) -> cooldown - DISCOVERY_SCAN_TICKS);
        discoveryCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);
        if (!(npc.level() instanceof ServerLevel world)) return;

        BlockPos origin = npc.blockPosition();
        BlockPos bestPos = null;
        String bestName = null;
        int bestScore = 0;
        for (int x = -WANDERER_LANDMARK_SCAN_RADIUS; x <= WANDERER_LANDMARK_SCAN_RADIUS; x++) {
            for (int y = -5; y <= 7; y++) {
                for (int z = -WANDERER_LANDMARK_SCAN_RADIUS; z <= WANDERER_LANDMARK_SCAN_RADIUS; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    String blockName = BuiltInRegistries.BLOCK.getKey(world.getBlockState(pos).getBlock()).getPath();
                    int score = getWandererLandmarkScore(blockName);
                    if (score <= bestScore) continue;

                    String key = "wanderer:" + blockName + ":" + new ChunkPos(pos).toLong();
                    if (discoveryCooldowns.containsKey(key)) continue;
                    bestScore = score;
                    bestName = blockName;
                    bestPos = pos;
                }
            }
        }

        if (bestPos != null && bestName != null) {
            discoveryCooldowns.put(
                    "wanderer:" + bestName + ":" + new ChunkPos(bestPos).toLong(),
                    DISCOVERY_COOLDOWN_TICKS);
            sendEventToAi(
                    "wanderer_report",
                    describeWandererLandmark(bestName) + " near " + formatBlockPos(bestPos));
        }
    }

    private int getWandererLandmarkScore(String blockName) {
        if (blockName.equals("bell")) return 90;
        if (blockName.contains("beehive") || blockName.contains("bee_nest")) return 80;
        if (blockName.equals("campfire") || blockName.equals("soul_campfire")) return 72;
        if (blockName.equals("hay_block")) return 68;
        if (blockName.equals("bookshelf") || blockName.equals("lectern")) return 64;
        if (blockName.contains("lantern")) return 58;
        if (blockName.contains("coral_block") || blockName.contains("coral_fan")) return 52;
        if (blockName.contains("sunflower") || blockName.contains("lilac")
                || blockName.contains("peony") || blockName.contains("rose_bush")) return 44;
        return 0;
    }

    private String describeWandererLandmark(String blockName) {
        if (blockName.equals("bell")) return "a village gathering place";
        if (blockName.contains("beehive") || blockName.contains("bee_nest")) return "an active bee nest";
        if (blockName.contains("campfire")) return "a campfire";
        if (blockName.equals("hay_block")) return "stored hay, probably near a farm or village";
        if (blockName.equals("bookshelf") || blockName.equals("lectern")) return "books and a lectern";
        if (blockName.contains("lantern")) return "a lit path or settlement";
        if (blockName.contains("coral")) return "a patch of coral";
        return blockName.replace("_", " ");
    }

    private int getDiscoveryScore(String blockName) {
        if (blockName.contains("ancient_debris")) return 100;
        if (blockName.contains("diamond_ore") || blockName.contains("deepslate_diamond_ore")) return 95;
        if (blockName.contains("emerald_ore") || blockName.contains("deepslate_emerald_ore")) return 90;
        if (blockName.equals("spawner") || blockName.contains("trial_spawner")) return 88;
        if (blockName.equals("vault") || blockName.contains("ominous_vault")) return 86;
        if (blockName.contains("chest")) return 72;
        if (blockName.equals("bell")) return 68;
        if (blockName.contains("amethyst_cluster")) return 64;
        if (blockName.contains("gold_ore") || blockName.contains("deepslate_gold_ore")) return 58;
        if (blockName.contains("lapis_ore") || blockName.contains("redstone_ore")) return 50;
        if (blockName.contains("suspicious_sand") || blockName.contains("suspicious_gravel")) return 48;
        return 0;
    }

    private String describeDiscovery(String blockName) {
        return switch (blockName) {
            case "spawner" -> "mob spawner";
            case "bell" -> "village bell";
            case "vault", "ominous_vault" -> "vault";
            default -> blockName.replace("_", " ");
        };
    }

    private int getDistanceFromExpeditionHome() {
        BlockPos home = expeditionHome != null ? expeditionHome : explorationAnchor;
        if (home == null) return 0;
        double dx = npc.getX() - home.getX();
        double dz = npc.getZ() - home.getZ();
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    private void requestAiAction() {
        final int requestId = ++aiRequestSequence;
        JsonObject context = new JsonObject();
        context.addProperty("requestId", requestId);
        context.addProperty("npcName", npc.getName().getString());
        context.addProperty("x", npc.getX());
        context.addProperty("y", npc.getY());
        context.addProperty("z", npc.getZ());
        context.addProperty("health", npc.getHealth());
        context.addProperty("equipmentTier", currentEquipmentTier);
        context.addProperty("appearanceVariant", npc.getAppearanceVariantName());
        context.addProperty("activeAction", lastCommandAction);
        context.addProperty("inventorySlotsUsed", getInventorySlotsUsed());
        context.addProperty("inventoryCapacity", VIRTUAL_INVENTORY_SLOTS);
        context.addProperty("distanceFromHome", getDistanceFromExpeditionHome());
        context.addProperty("expeditionReturningHome", expeditionReturningHome);
        context.addProperty("expeditionRouteRemaining", Math.max(0, expeditionRoute.size() - expeditionRouteIndex));
        context.addProperty("buildBlocksTotal", buildTotalPlacements);
        context.addProperty("buildBlocksRemaining", buildQueue.size());
        context.addProperty("buildProgressPercent", getBuildProgressPercent());
        if (expeditionHome != null) {
            context.addProperty("expeditionHome", formatBlockPos(expeditionHome));
        }
        if (expeditionDestination != null) {
            context.addProperty("expeditionDestination", formatBlockPos(expeditionDestination));
        }
        context .add("inventory", createInventorySummary());

        String currentTask = "wandering";
        if (npc.isInWater() && getWaterDepthAt(npc.blockPosition()) >= 2) {
            currentTask = "escaping deep water";
        } else if (surfaceEscapeTarget != null) {
            currentTask = "escaping the underground and returning to the surface";
        } else if (isSeekingFood()) {
            currentTask = "searching for food";
        } else if ("explore".equals(currentMode)) {
            currentTask = expeditionReturningHome
                    ? "returning home from an expedition"
                    : "exploring on a long expedition";
        } else if (!buildQueue.isEmpty()) {
            currentTask = ("build".equals(currentMode) ? "building " : "unfinished ")
                    + buildSchematic + " (" + getBuildProgressPercent() + "% complete, "
                    + buildQueue.size() + " blocks remaining)";
        } else if ("mine".equals(currentMode)) {
            currentTask = "mining " + targetBlockName;
        } else if ("follow".equals(currentMode) && activeFollowTarget != null) {
            currentTask = "following " + activeFollowTarget.getName().getString();
        } else if ("walk".equals(currentMode)) {
            currentTask = autonomousWalkPurpose.isBlank()
                    ? "walking to a destination"
                    : autonomousWalkPurpose;
        } else if ("idle".equals(currentMode) && "farmer".equals(npc.getAppearanceVariantName())
                && (currentFarmCropTarget != null || currentFarmPlantTarget != null
                || currentFarmTillTarget != null || currentFarmChestTarget != null)) {
            currentTask = "tending crops";
        } else if ("idle".equals(currentMode) && "rancher".equals(npc.getAppearanceVariantName())
                && (currentFarmAnimalTarget != null || currentFarmCullTarget != null)) {
            currentTask = "tending livestock";
        }
        context.addProperty("task", currentTask);

        // Nearby players and threats for richer AI context
        if (npc.level() instanceof ServerLevel serverWorld) {
            JsonArray nearbyPlayers = new JsonArray();
            for (ServerPlayer player : serverWorld.players()) {
                if (npc .distanceToSqr(player) <= 225.0) { // 15-block radius
                    nearbyPlayers .add(player.getName().getString());
                }
            }
            context .add("nearbyPlayers", nearbyPlayers);

            JsonArray nearbyThreats = new JsonArray();
            List<Monster> hostiles = serverWorld.getEntitiesOfClass(
                    Monster.class,
                    npc.getBoundingBox() .inflate(16),
                    e -> e.isAlive() && !e.isRemoved());
            for (Monster hostile : hostiles) {
                nearbyThreats .add(BuiltInRegistries.ENTITY_TYPE.getKey(hostile.getType()).getPath());
            }
            context .add("nearbyThreats", nearbyThreats);
        }

        AiCompanionMod.AI_CLIENT.sendContextAndGetAction(context).thenAccept(response -> {
            if (response != null && response.has("action")) {
                if (response.has("mood")) {
                    currentMood = response.get("mood").getAsString();
                }
                if (response.has("requestId") && response.get("requestId").getAsInt() != requestId) {
                    return;
                }
                if (requestId != aiRequestSequence) {
                    AiCompanionMod.LOGGER.debug("Ignoring stale AI action response for " + npc.getName().getString());
                    return;
                }
                if (!canAcceptAsyncReply()) {
                    return;
                }
                handleAiAction(response.get("action").getAsString());
            }
        });
    }

    private boolean isSeekingFood() {
        return isValidFoodItemTarget(currentFoodItemTarget) || isValidFoodAnimalTarget(currentFoodAnimalTarget);
    }

    private void handleAiAction(String action) {
        AiCompanionMod.LOGGER.info("NPC received AI instruction: " + action);
        if (action == null || action.trim().isEmpty()) return;
        
        String[] parts = action.split(" ");
        String command = parts[0].toLowerCase();
        
        // Ensure this modifies world on the main server thread
        if (npc.level().isClientSide()) return;
        net.minecraft.server.MinecraftServer server = npc.level().getServer();
        if (server == null) return;

        server.execute(() -> {
            if (!canAcceptAsyncReply()) {
                return;
            }
            if (!buildQueue.isEmpty()) {
                if ("@resume_build".equals(command) || "@build".equals(command)) {
                    resumeUnfinishedBuild();
                    return;
                }
                if ("@stop".equals(command)) {
                    pauseUnfinishedBuild();
                    return;
                }

                AiCompanionMod.LOGGER.info("Deferring " + command + " until the unfinished "
                        + buildSchematic + " is complete (" + buildQueue.size() + " blocks remaining).");
                return;
            }
            if ("explore".equals(currentMode) && "@idle".equals(command)) {
                AiCompanionMod.LOGGER.info("Ignoring idle instruction while expedition is still in progress.");
                return;
            }
            if ("explore".equals(currentMode) && "@explore".equals(command)
                    && !(parts.length > 1 && "return".equalsIgnoreCase(parts[1]))) {
                AiCompanionMod.LOGGER.info("Ignoring duplicate explore instruction while expedition is still in progress.");
                return;
            }
            if (!"@idle".equals(command)) {
                autonomousRoleAction = false;
            }

            switch (command) {
                case "@follow":
                    if (parts.length > 1) {
                        String targetName = parts[1];
                        ServerPlayer player = server.getPlayerList().getPlayerByName(targetName);
                        if (player != null) {
                            if ("follow".equals(currentMode) && activeFollowTarget == player) {
                                return;
                            }
                            AiCompanionMod.LOGGER.info("NPC instructed to follow " + targetName);
                            activeFollowTarget = player;
                            currentMode = "follow";
                            resetFollowProgress();
                            currentWalkTarget = null;
                            currentWaterExitTarget = null;
                            currentBoatTarget = null;
                            currentBoatStandTarget = null;
                            currentMineTarget = null;
                            buildStandPos = null;
                            buildReady = false;
                            buildSitePrepared = false;
                            buildSitePreparationPasses = 0;
                            buildEvacuationTicks = 0;
                            buildPlacementAttempts = 0;
                            buildQueue.clear();
                            buildSchematic = "small";
                            clearFarmerTargets();
                            clearActiveExpedition(false);
                            npc.setTarget(null); // Stop attacking if following
                            rememberAction(action);
                        }
                    }
                    break;
                    
                case "@walk":
                    if (parts.length > 3) {
                        try {
                            double x = Double.parseDouble(parts[1]);
                            double y = Double.parseDouble(parts[2]);
                            double z = Double.parseDouble(parts[3]);
                            AiCompanionMod.LOGGER.info("NPC walking to " + x + ", " + y + ", " + z);
                            
                            // Cancel following if manually walking
                            activeFollowTarget = null;
                            resetFollowProgress();
                            currentMode = "walk";
                            currentWalkTarget = new net.minecraft.core.BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
                            currentWaterExitTarget = null;
                            currentBoatTarget = null;
                            currentBoatStandTarget = null;
                            currentMineTarget = null;
                            buildStandPos = null;
                            buildReady = false;
                            buildSitePrepared = false;
                            buildSitePreparationPasses = 0;
                            buildEvacuationTicks = 0;
                            buildPlacementAttempts = 0;
                            buildQueue.clear();
                            clearFarmerTargets();
                            clearActiveExpedition(false);
                            moveNear(currentWalkTarget, 1.0);
                            rememberAction(action);
                        } catch (NumberFormatException e) {
                            AiCompanionMod.LOGGER.error("Invalid coordinates for @walk command");
                        }
                    }
                    break;
                    
                case "@mine":
                    if (parts.length > 1) {
                        String targetBlock = parts[1].toLowerCase();
                        if (!"mine".equals(currentMode) || !targetBlock.equals(targetBlockName) || !requestedMiningJob) {
                            AiCompanionMod.LOGGER.info("NPC instructed to mine " + targetBlock);
                            activeFollowTarget = null;
                            resetFollowProgress();
                            currentMode = "mine";
                            currentWalkTarget = null;
                            currentWaterExitTarget = null;
                            currentBoatTarget = null;
                            currentBoatStandTarget = null;
                            targetBlockName = targetBlock;
                            currentMineTarget = null;
                            requestedMiningJob = true;
                            requestedMiningAmount = parts.length > 2 ? parseMiningAmount(parts[2]) : 16;
                            requestedMiningStartingCount = getVirtualItemCount(getPrimaryMiningDrop(targetBlockName));
                            minerWorkHome = npc.blockPosition().immutable();
                            miningSearchWaypoint = null;
                            miningSearchTicks = 0;
                            minerReturningToStorage = false;
                            minerStorageTarget = null;
                            buildStandPos = null;
                            buildReady = false;
                            buildSitePrepared = false;
                            buildSitePreparationPasses = 0;
                            buildEvacuationTicks = 0;
                            buildPlacementAttempts = 0;
                            buildQueue.clear();
                            clearFarmerTargets();
                            clearActiveExpedition(false);
                            rememberAction(action);
                        }
                    }
                    break;
                    
                case "@build":
                    String requestedSchematic = parseBuildSchematic(parts);
                    if (!"build".equals(currentMode) || !requestedSchematic.equals(buildSchematic)) {
                        activeFollowTarget = null;
                        resetFollowProgress();
                        currentMode = "build";
                        currentWalkTarget = null;
                        currentWaterExitTarget = null;
                        currentBoatTarget = null;
                        currentBoatStandTarget = null;
                        buildQueue.clear();
                        clearActiveExpedition(false);
                        buildReady = false;
                        buildSitePrepared = false;
                        buildSitePreparationPasses = 0;
                        buildEvacuationTicks = 0;
                        buildPlacementAttempts = 0;
                        clearFarmerTargets();
                        
                        buildSchematic = requestedSchematic;
                        BuildPlan plan = getBuildPlan(buildSchematic);
                        buildRadius = plan.radius();
                        buildHeight = plan.height();
                        buildCenter = "bridge".equals(buildSchematic)
                                ? findBridgeCenterNearBuilder(npc.blockPosition())
                                : findBuildCenterNearBuilder(npc.blockPosition(), buildRadius);
                        if (!"bridge".equals(buildSchematic) && !isDryBuildFootprint(buildCenter, buildRadius)) {
                            AiCompanionMod.LOGGER.warn("Refusing to build " + buildSchematic + " on wet or unsupported ground near "
                                    + buildCenter.toShortString() + ".");
                            currentMode = "idle";
                            buildCenter = null;
                            buildStandPos = null;
                            rememberAction("@idle");
                            break;
                        }
                        buildStandPos = findSafeBuildStandPos(buildCenter, buildRadius);
                        if (buildStandPos != null) {
                            npc.getNavigation() .moveTo(buildStandPos.getX() + 0.5, buildStandPos.getY(), buildStandPos.getZ() + 0.5, 1.0);
                        }

                        queueBuildSchematic(buildCenter, buildSchematic);
                        buildTotalPlacements = buildQueue.size();
                        AiCompanionMod.LOGGER.info("NPC instructed to build " + buildSchematic + ". Queued " + buildQueue.size() + " blocks.");
                        rememberAction(action);
                    }
                    break;

                case "@resume_build":
                    AiCompanionMod.LOGGER.info("NPC was asked to resume building, but has no unfinished build.");
                    break;

                case "@explore":
                    activeFollowTarget = null;
                    resetFollowProgress();
                    currentWalkTarget = null;
                    currentWaterExitTarget = null;
                    currentBoatTarget = null;
                    currentBoatStandTarget = null;
                    currentMineTarget = null;
                    buildStandPos = null;
                    buildReady = false;
                    buildSitePrepared = false;
                    buildSitePreparationPasses = 0;
                    buildEvacuationTicks = 0;
                    buildPlacementAttempts = 0;
                    buildQueue.clear();
                    clearFarmerTargets();
                    if (parts.length > 1 && "return".equalsIgnoreCase(parts[1])) {
                        if (expeditionHome == null) {
                            expeditionHome = explorationAnchor != null ? explorationAnchor : npc.blockPosition();
                        }
                        beginReturnHome("return requested");
                        currentMode = "explore";
                    } else {
                        startExpedition(parseExploreDistance(parts));
                    }
                    rememberAction(action);
                    break;
                    
                case "@stop":
                    AiCompanionMod.LOGGER.info("NPC instructed to stop all actions.");
                    activeFollowTarget = null;
                    resetFollowProgress();
                    currentMode = "stopped";
                    currentWalkTarget = null;
                    currentWaterExitTarget = null;
                    currentBoatTarget = null;
                    currentBoatStandTarget = null;
                    currentMineTarget = null;
                    buildStandPos = null;
                    buildReady = false;
                    buildSitePrepared = false;
                    buildSitePreparationPasses = 0;
                    buildEvacuationTicks = 0;
                    buildPlacementAttempts = 0;
                    buildQueue.clear();
                    clearFarmerTargets();
                    clearActiveExpedition(false);
                    npc.setTarget(null);
                    npc.getNavigation().stop();
                    rememberAction("@stop");
                    break;

                case "@forgive":
                    if (parts.length > 1) {
                        String targetName = parts[1];
                        ServerPlayer player = server.getPlayerList().getPlayerByName(targetName);
                        if (player != null) {
                            AiCompanionMod.LOGGER.info("NPC forgiving " + targetName + " and dropping combat.");
                            npc.forgivePlayer(player);
                            if (npc.getTarget() == null) {
                                currentMode = "idle";
                                activeFollowTarget = null;
                                resetFollowProgress();
                                currentWalkTarget = null;
                                currentWaterExitTarget = null;
                                currentBoatTarget = null;
                                currentBoatStandTarget = null;
                                currentMineTarget = null;
                                npc.setSprinting(false);
                                restoreDefaultMainHand();
                                rememberAction("@idle");
                            }
                        }
                    }
                    break;
                    
                case "@idle":
                    if ("mine".equals(currentMode) && requestedMiningJob) {
                        AiCompanionMod.LOGGER.debug("Ignoring idle instruction while a requested mining job is active.");
                        return;
                    }
                    if (shouldIgnoreIdleForAutonomousRole()) {
                        AiCompanionMod.LOGGER.debug("Ignoring idle instruction while autonomous role action is active.");
                        return;
                    }
                    if (!"idle".equals(currentMode)) {
                        AiCompanionMod.LOGGER.info("NPC entering idle mode.");
                    }
                    autonomousRoleAction = false;
                    currentMode = "idle";
                    activeFollowTarget = null;
                    resetFollowProgress();
                    currentWalkTarget = null;
                    currentWaterExitTarget = null;
                    currentBoatTarget = null;
                    currentBoatStandTarget = null;
                    currentMineTarget = null;
                    buildStandPos = null;
                    buildReady = false;
                    buildSitePrepared = false;
                    buildSitePreparationPasses = 0;
                    buildEvacuationTicks = 0;
                    buildPlacementAttempts = 0;
                    buildQueue.clear();
                    clearActiveExpedition(false);
                    npc.setTarget(null);
                    rememberAction("@idle");
                    break;
                    
                default:
                    AiCompanionMod.LOGGER.warn("Unknown AI command: " + command);
                    break;
            }
        });
    }

    private int parseExploreDistance(String[] parts) {
        if (parts.length <= 1) {
            return switch (npc.getAppearanceVariantName()) {
                case "scout" -> 3000;
                case "miner" -> 1800;
                default -> 1200;
            };
        }

        String option = parts[1].toLowerCase();
        return switch (option) {
            case "near", "short" -> 1000;
            case "far", "long" -> 2500;
            case "deep", "veryfar", "very_far" -> 5000;
            default -> {
                try {
                    yield Integer.parseInt(option);
                } catch (NumberFormatException e) {
                    yield 1500;
                }
            }
        };
    }

    private int parseMiningAmount(String value) {
        try {
            return Math.max(1, Math.min(64, Integer.parseInt(value)));
        } catch (NumberFormatException ignored) {
            return 16;
        }
    }

    private String parseBuildSchematic(String[] parts) {
        if (parts.length <= 1) {
            return "small";
        }

        for (int i = 1; i < parts.length; i++) {
            String option = parts[i].toLowerCase().replace("-", "_");
            switch (option) {
                case "small", "5x5", "house", "starter", "tiny_house", "tiny_cabin", "micro_house", "micro_cabin", "mini_house", "mini_cabin" -> {
                    return "small";
                }
                case "cottage", "cabin", "7x7", "small_cottage", "small_cabin", "medium_cottage", "medium_cabin", "large_cottage", "large_cabin" -> {
                    return "cottage";
                }
                case "tower", "watchtower", "spire", "obelisk", "minaret", "steeple", "pinnacle", "turret" -> {
                    return "tower";
                }
                case "hall", "longhouse", "lodge", "great_hall", "banquet_hall", "meeting_hall", "dining_hall", "assembly_hall" -> {
                    return "hall";
                }
                case "castle", "keep", "fort", "stronghold", "citadel", "fortress", "palace", "manor", "mansion" -> {
                    return "castle";
                }
                case "bridge", "crossing", "overpass", "viaduct", "aqueduct", "causeway", "footbridge", "span", "arch", "trestle" -> {
                    return "bridge";
                }
                case "wall", "barrier", "rampart", "fortification", "defense", "barricade", "parapet", "bulwark", "entrenchment", "palisade" -> {
                    return "wall";
                }
                case "base", "outpost", "camp", "home_base", "headquarters", "garrison", "command_center" -> {
                    return "base";
                }
                case "pen", "livestock", "livestock_pen", "animal_pen", "corral", "fence", "enclosure", "paddock", "stockyard", "pasture" -> {
                    return "pen";
                }
                default -> {
                    if ("big".equals(option) || "large".equals(option)) {
                        return "hall";
                    }
                    if (loadCustomSchematic(option) != null) {
                        return option;
                    }
                }
            }
        }

        return "small";
    }

    private BuildPlan getBuildPlan(String schematic) {
        CustomSchematic custom = loadCustomSchematic(schematic);
        if (custom != null) {
            return new BuildPlan(Math.max(1, custom.radius()), Math.max(1, custom.height()));
        }
        return switch (schematic) {
            case "cottage" -> new BuildPlan(3, 4);
            case "tower" -> new BuildPlan(3, 8);
            case "hall" -> new BuildPlan(4, 5);
            case "castle" -> new BuildPlan(7, 7);
            case "bridge" -> new BuildPlan(6, 2);
            case "wall" -> new BuildPlan(6, 4);
            case "base" -> new BuildPlan(5, 5);
            case "pen" -> new BuildPlan(5, 6);
            default -> new BuildPlan(2, 4);
        };
    }

    private void clearActiveExpedition(boolean keepHome) {
        expeditionRoute.clear();
        expeditionRouteIndex = 0;
        expeditionDestination = null;
        expeditionReturningHome = false;
        expeditionTicks = 0;
        expeditionNoProgressTicks = 0;
        lastExpeditionProgressPos = null;
        releaseExpeditionTickets();
        if (!keepHome && expeditionHome == null && explorationAnchor != null) {
            expeditionHome = explorationAnchor;
        }
    }

    private void moveNear(net.minecraft.core.BlockPos target, double speed) {
        net.minecraft.core.BlockPos standPos = findStandPositionNear(target);
        if (standPos != null) {
            startPathTo(standPos, speed);
        } else {
            startPathTo(target, speed);
        }
    }

    private boolean escapeUndergroundToSurface() {
        if (!(npc.level() instanceof ServerLevel world)) {
            resetSurfaceEscape();
            return false;
        }

        // Explicit work and player commands take priority. Surface recovery begins
        // when a companion has finished its task and would otherwise idle underground.
        if (!"idle".equals(currentMode) || npc.isPassenger() || npc.isInWater()) {
            resetSurfaceEscape();
            return false;
        }

        if (!isMeaningfullyUnderground(world, npc.blockPosition())) {
            if (surfaceEscapeTarget != null) {
                AiCompanionMod.LOGGER.info(npc.getName().getString() + " reached the surface.");
            }
            resetSurfaceEscape();
            return false;
        }

        if (surfaceEscapeTarget == null || !isSafeSurfaceStand(world, surfaceEscapeTarget)) {
            surfaceEscapeTarget = findSurfaceEscapeTarget(world);
            surfaceEscapeTicks = 0;
            surfaceEscapeNoProgressTicks = 0;
            lastSurfaceEscapePos = null;

            if (surfaceEscapeTarget == null) {
                return rescueToSurface(world);
            }

            AiCompanionMod.LOGGER.info(npc.getName().getString() + " is underground and is returning to the surface at "
                    + surfaceEscapeTarget.toShortString() + ".");
        }

        surfaceEscapeTicks++;
        npc.setSprinting(true);
        npc.getLookControl() .setLookAt(
                surfaceEscapeTarget.getX() + 0.5,
                surfaceEscapeTarget.getY() + 0.5,
                surfaceEscapeTarget.getZ() + 0.5);

        if (surfaceEscapeTicks % SURFACE_ESCAPE_PROGRESS_TICKS == 0) {
            Vec3 current = new Vec3(npc.getX(), npc.getY(), npc.getZ());
            if (lastSurfaceEscapePos != null
                    && current.distanceToSqr(lastSurfaceEscapePos) < SURFACE_ESCAPE_PROGRESS_SQUARED) {
                surfaceEscapeNoProgressTicks += SURFACE_ESCAPE_PROGRESS_TICKS;
            } else {
                surfaceEscapeNoProgressTicks = 0;
            }
            lastSurfaceEscapePos = current;
        }

        Path path = npc.getNavigation() .createPath(surfaceEscapeTarget, 0);
        boolean hasSurfacePath = path != null && path.canReach();
        if (hasSurfacePath) {
            if (npc.getNavigation().isDone() || surfaceEscapeTicks % SURFACE_ESCAPE_REPATH_TICKS == 0) {
                npc.getNavigation() .moveTo(path, 1.1);
            }
        } else if (surfaceEscapeTicks % 10 == 0
                && (surfaceEscapeTicks >= SURFACE_ESCAPE_CARVE_AFTER_TICKS
                || surfaceEscapeNoProgressTicks >= SURFACE_ESCAPE_CARVE_AFTER_TICKS)) {
            carveSurfaceEscapeStep(world);
        } else if (npc.getNavigation().isDone() || surfaceEscapeTicks % SURFACE_ESCAPE_REPATH_TICKS == 0) {
            startPathTo(surfaceEscapeTarget, 1.05);
        }

        if (surfaceEscapeTicks >= SURFACE_ESCAPE_RESCUE_TICKS) {
            return rescueToSurface(world);
        }

        return true;
    }

    private boolean isMeaningfullyUnderground(ServerLevel world, BlockPos feet) {
        if (world.canSeeSky(feet.above(2))) return false;

        BlockPos surface = world.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet);
        return surface.getY() - feet.getY() >= UNDERGROUND_MIN_DEPTH;
    }

    private BlockPos findSurfaceEscapeTarget(ServerLevel world) {
        BlockPos origin = npc.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int localDepth = Math.max(UNDERGROUND_MIN_DEPTH,
                world.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin).getY()
                        - origin.getY());
        int searchRadius = Math.min(SURFACE_ESCAPE_MAX_SEARCH_RADIUS, Math.max(24, localDepth + 12));

        // Sample several rays instead of loading every column in a large square.
        for (int distance = 0; distance <= searchRadius; distance += 4) {
            int samples = distance == 0 ? 1 : 16;
            for (int sample = 0; sample < samples; sample++) {
                double angle = sample * (Math.PI * 2.0 / samples);
                int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
                int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
                BlockPos column = new BlockPos(x, origin.getY(), z);
                BlockPos surface = world.getHeightmapPos(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        column);
                BlockPos candidate = findSafeSurfaceStandAround(world, surface, 3);
                if (candidate == null) continue;

                double verticalClimb = Math.max(0, candidate.getY() - origin.getY());
                double horizontalDistance = Math.sqrt(
                        Math.pow(candidate.getX() - origin.getX(), 2)
                                + Math.pow(candidate.getZ() - origin.getZ(), 2));
                double staircaseDeficit = Math.max(0.0, verticalClimb * 0.7 - horizontalDistance);
                double score = origin.distSqr(candidate)
                        + verticalClimb * 4.0
                        + staircaseDeficit * staircaseDeficit * 8.0;
                if (score < bestScore) {
                    best = candidate.immutable();
                    bestScore = score;
                }
            }
        }

        return best;
    }

    private BlockPos findSafeSurfaceStandAround(ServerLevel world, BlockPos center, int radius) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos column = center.offset(x, 0, z);
                BlockPos candidate = world.getHeightmapPos(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        column);
                if (!isSafeSurfaceStand(world, candidate)) continue;

                double distance = candidate.distSqr(center);
                if (distance < bestDistance) {
                    best = candidate.immutable();
                    bestDistance = distance;
                }
            }
        }

        return best;
    }

    private boolean isSafeSurfaceStand(ServerLevel world, BlockPos feet) {
        return isSafeStandPosition(world, feet)
                && world.canSeeSky(feet.above())
                && !isNearFenceOrGate(feet, FENCE_AVOIDANCE_RADIUS);
    }

    private boolean carveSurfaceEscapeStep(ServerLevel world) {
        if (surfaceEscapeTarget == null) return false;

        Direction preferred = getHorizontalDirectionToward(surfaceEscapeTarget);
        Direction[] directions = new Direction[] {
                preferred,
                preferred.getClockWise(),
                preferred.getCounterClockWise(),
                preferred.getOpposite()
        };

        for (Direction direction : directions) {
            if (tryCarveAscendingStep(world, direction)) {
                return true;
            }
        }

        return tryDigUpwardEscape(preferred);
    }

    private boolean tryCarveAscendingStep(ServerLevel world, Direction direction) {
        BlockPos feet = npc.blockPosition();
        BlockPos stepBlock = feet.relative(direction);
        BlockState stepState = world.getBlockState(stepBlock);

        if (stepState.isAir() || !stepState.getFluidState().isEmpty()
                || !isMiningObstacle(world, stepBlock, stepState)) {
            return false;
        }

        BlockPos climbFeet = stepBlock.above();
        BlockPos climbHead = stepBlock.above(2);
        BlockPos[] clearance = new BlockPos[] {climbFeet, climbHead};

        for (BlockPos pos : clearance) {
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) continue;
            if (!isMiningObstacle(world, pos, state)) {
                return false;
            }
        }

        for (BlockPos pos : clearance) {
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) continue;

            equipToolFor(state);
            npc.getNavigation().stop();
            npc.getLookControl() .setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            world.destroyBlock(pos, true, npc);
            npc.getNavigation().recomputePath();
            return true;
        }

        npc.getJumpControl().jump();
        startPathTo(climbFeet, 1.1);
        return true;
    }

    private boolean rescueToSurface(ServerLevel world) {
        BlockPos rescue = surfaceEscapeTarget;
        if (rescue == null || !isSafeSurfaceStand(world, rescue)) {
            rescue = findSurfaceEscapeTarget(world);
        }
        if (rescue == null) {
            return false;
        }

        AiCompanionMod.LOGGER.warn(npc.getName().getString()
                + " could not path out of the underground; rescuing to "
                + rescue.toShortString() + ".");
        npc.stopRiding();
        npc.getNavigation().stop();
        npc .setDeltaMovement(Vec3.ZERO);
        npc.fallDistance = 0.0F;
        npc .moveTo(
                rescue.getX() + 0.5,
                rescue.getY(),
                rescue.getZ() + 0.5,
                npc.getYRot(),
                npc.getXRot());
        resetSurfaceEscape();
        return true;
    }

    private void resetSurfaceEscape() {
        surfaceEscapeTarget = null;
        surfaceEscapeTicks = 0;
        surfaceEscapeNoProgressTicks = 0;
        lastSurfaceEscapePos = null;
    }

    private void updateMovementStuckDetection() {
        Vec3 currentPos = new Vec3(npc.getX(), npc.getY(), npc.getZ());
        BlockPos activeTarget = getActiveMovementTarget();
        boolean hasMovementGoal = activeTarget != null;
        boolean navigationActive = !npc.getNavigation().isDone() || hasMovementGoal;

        rememberMovementBlock(npc.blockPosition());
        boolean barelyMoved = lastPos != null
                && currentPos.distanceToSqr(lastPos) < 0.1
                && navigationActive;
        boolean oscillating = navigationActive && isMovementOscillating();
        boolean notClosingGoal = hasMovementGoal && isNotClosingOnGoal(activeTarget);

        if (barelyMoved || oscillating || notClosingGoal) {
            stuckTicks++;
            oscillationStuckChecks = oscillating ? oscillationStuckChecks + 1 : 0;
            if (stuckTicks > 3 || oscillationStuckChecks >= OSCILLATION_STUCK_CHECKS || notClosingGoal) {
                if (abandonUnreachableMiningTarget()) {
                    lastPos = currentPos;
                    return;
                }
                AiCompanionMod.LOGGER.info("NPC detects blocked movement; adapting terrain or route.");
                adaptTerrainWhenStuck();
                stuckTicks = 0;
                oscillationStuckChecks = 0;
                goalNoProgressTicks = 0;
                lastGoalDistanceSq = -1.0;
                recentMovementBlocks.clear();
            }
        } else {
            stuckTicks = 0;
            oscillationStuckChecks = 0;
            if (!"mine".equals(currentMode)) {
                miningStuckRecoveries = 0;
                lastMiningStuckTarget = null;
            }
        }

        if (!hasMovementGoal) {
            goalNoProgressTicks = 0;
            lastGoalDistanceSq = -1.0;
        }

        lastPos = currentPos;
    }

    private boolean abandonUnreachableMiningTarget() {
        if (!"mine".equals(currentMode) || currentMineTarget == null) {
            miningStuckRecoveries = 0;
            lastMiningStuckTarget = null;
            return false;
        }

        if (!currentMineTarget.equals(lastMiningStuckTarget)) {
            lastMiningStuckTarget = currentMineTarget.immutable();
            miningStuckRecoveries = 1;
            return false;
        }

        miningStuckRecoveries++;
        if (miningStuckRecoveries < 4) {
            return false;
        }

        AiCompanionMod.LOGGER.warn(npc.getName().getString()
                + " abandoned unreachable mining target at "
                + currentMineTarget.toShortString() + ".");
        failedMiningTargetCooldowns.put(
                currentMineTarget.immutable(),
                MINER_FAILED_TARGET_COOLDOWN_TICKS);
        npc.getNavigation().stop();
        currentMineTarget = null;
        miningSearchWaypoint = null;
        miningSearchTicks = 0;
        miningSwingTicks = 0;
        miningStuckRecoveries = 0;
        lastMiningStuckTarget = null;
        miningNoProgressTicks = 0;
        lastMiningProgressPos = new Vec3(npc.getX(), npc.getY(), npc.getZ());
        return true;
    }

    private void decayFailedMiningTargets() {
        failedMiningTargetCooldowns.replaceAll((pos, ticks) -> ticks - 20);
        failedMiningTargetCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }

    private void updateMiningProgressWatchdog() {
        if (!"mine".equals(currentMode) || minerReturningToStorage) {
            miningNoProgressTicks = 0;
            lastMiningProgressPos = null;
            return;
        }

        Vec3 current = new Vec3(npc.getX(), npc.getY(), npc.getZ());
        boolean activelyDigging = miningSwingTicks > 0;
        boolean moved = lastMiningProgressPos == null
                || current.distanceToSqr(lastMiningProgressPos) >= 0.25;
        if (activelyDigging || moved) {
            miningNoProgressTicks = 0;
            lastMiningProgressPos = current;
            return;
        }

        miningNoProgressTicks++;
        if (miningNoProgressTicks < MINER_PROGRESS_WATCHDOG_TICKS) return;

        if (currentMineTarget != null) {
            failedMiningTargetCooldowns.put(
                    currentMineTarget.immutable(),
                    MINER_FAILED_TARGET_COOLDOWN_TICKS);
        }
        AiCompanionMod.LOGGER.warn(npc.getName().getString()
                + " made no mining progress; selecting a fresh search route.");
        npc.getNavigation().stop();
        currentMineTarget = null;
        miningSearchWaypoint = null;
        miningSearchTicks = 0;
        miningStuckRecoveries = 0;
        lastMiningStuckTarget = null;
        miningNoProgressTicks = 0;
        lastMiningProgressPos = current;
    }

    private void rememberMovementBlock(BlockPos pos) {
        recentMovementBlocks.addLast(pos.immutable());
        while (recentMovementBlocks.size() > MOVEMENT_HISTORY_SIZE) {
            recentMovementBlocks.removeFirst();
        }
    }

    private boolean isMovementOscillating() {
        if (recentMovementBlocks.size() < 6) return false;

        List<BlockPos> points = new ArrayList<>(recentMovementBlocks);
        Set<BlockPos> unique = new HashSet<>(points);
        BlockPos first = points.get(0);
        BlockPos last = points.get(points.size() - 1);
        if (unique.size() <= 3 && first.distSqr(last) <= 9.0) {
            return true;
        }

        int n = points.size();
        return sameMovementBlock(points.get(n - 1), points.get(n - 3))
                && sameMovementBlock(points.get(n - 2), points.get(n - 4))
                && !sameMovementBlock(points.get(n - 1), points.get(n - 2));
    }

    private boolean sameMovementBlock(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) <= 1
                && Math.abs(a.getY() - b.getY()) <= 1
                && Math.abs(a.getZ() - b.getZ()) <= 1;
    }

    private boolean isNotClosingOnGoal(BlockPos target) {
        double distanceSq = npc .distanceToSqr(Vec3.atCenterOf(target));
        if (lastGoalDistanceSq < 0.0 || distanceSq < lastGoalDistanceSq - GOAL_PROGRESS_MARGIN_SQUARED) {
            goalNoProgressTicks = 0;
            lastGoalDistanceSq = distanceSq;
            return false;
        }

        goalNoProgressTicks += 20;
        lastGoalDistanceSq = Math.min(lastGoalDistanceSq, distanceSq);
        return goalNoProgressTicks >= GOAL_NO_PROGRESS_TICKS;
    }

    private void adaptTerrainWhenStuck() {
        npc.getNavigation().recomputePath();

        Direction direction = getHorizontalDirectionTowardActiveTarget();
        if (tryDigUpwardEscape(direction)) return;
        if (tryBreakBlockingTerrain(direction)) return;
        if (canPlaceTerrainAssistBlock()) {
            if (tryBridgeGap(direction)) return;
            if (tryPlaceRamp(direction)) return;
        }
        if (tryClimbAssist(direction)) return;

        BlockPos target = getActiveMovementTarget();
        if (target != null) {
            moveNear(target, 1.1);
        }
    }

    private boolean escapeFencePenIfNeeded() {
        // Fence gates are usually animal-pen boundaries. Opening them automatically
        // lets livestock out and makes companions look like they are raiding pens,
        // so gates stay player-controlled unless a future rancher-specific routine
        // deliberately manages a pen.
        if (!autoFenceGateRoutingEnabled()) {
            clearCommittedGateEscape();
            return false;
        }

        if (npc.isPassenger() || npc.isInWater()) {
            clearCommittedGateEscape();
            return false;
        }
        if (committedGatePos != null && committedGateExit != null) {
            return continueCommittedGateEscape();
        }

        BlockPos target = getActiveMovementTarget();
        if (target == null || postGateRouteTicks > 0) {
            return false;
        }
        boolean enclosed = isLikelyInsideFencePen();
        if (!enclosed) {
            return false;
        }
        Path direct = npc.getNavigation() .createPath(target, 0);
        if (direct != null && direct.canReach()) {
            return false;
        }

        return tryRouteThroughNearbyFenceGate(target, 1.0);
    }

    private boolean autoFenceGateRoutingEnabled() {
        return false;
    }

    private boolean tryRouteThroughNearbyFenceGate(BlockPos target, double speed) {
        if (postGateRouteTicks > 0) return false;

        net.minecraft.world.level.Level world = npc.level();
        BlockPos origin = npc.blockPosition();
        BlockPos bestGate = null;
        BlockPos bestExit = null;
        double bestScore = Double.MAX_VALUE;

        for (int x = -FENCE_GATE_ESCAPE_RADIUS; x <= FENCE_GATE_ESCAPE_RADIUS; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -FENCE_GATE_ESCAPE_RADIUS; z <= FENCE_GATE_ESCAPE_RADIUS; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (!(state.getBlock() instanceof FenceGateBlock)) continue;
                    if (!isGateAttachedToFence(world, pos, state)) continue;
                    if (passageUseCooldowns.containsKey(pos)) continue;
                    BlockPos exit = findGatePassThroughTarget(pos);
                    if (exit == null) continue;

                    double score = npc .distanceToSqr(Vec3.atCenterOf(pos));
                    if (target != null) {
                        score += exit.distSqr(target) * 0.15;
                    }
                    if (score < bestScore) {
                        bestGate = pos.immutable();
                        bestExit = exit.immutable();
                        bestScore = score;
                    }
                }
            }
        }

        if (bestGate == null) {
            return false;
        }

        BlockState gateState = world.getBlockState(bestGate);
        if (gateState.getBlock() instanceof FenceGateBlock && !gateState.getValue(FenceGateBlock.OPEN)) {
            world.setBlock(bestGate, gateState.setValue(FenceGateBlock.OPEN, true), Block.UPDATE_ALL);
            openedFenceGates.put(bestGate, EMERGENCY_GATE_HOLD_OPEN_TICKS);
        }

        committedGatePos = bestGate;
        committedGateExit = bestExit;
        committedGateTicks = 0;
        startPathTo(committedGateExit, speed);
        return true;
    }

    private boolean continueCommittedGateEscape() {
        net.minecraft.world.level.Level world = npc.level();
        BlockState gateState = world.getBlockState(committedGatePos);
        if (!(gateState.getBlock() instanceof FenceGateBlock)) {
            clearCommittedGateEscape();
            return false;
        }

        committedGateTicks++;
        if (!gateState.getValue(FenceGateBlock.OPEN)) {
            world.setBlock(
                    committedGatePos,
                    gateState.setValue(FenceGateBlock.OPEN, true),
                    Block.UPDATE_ALL);
        }
        openedFenceGates.put(committedGatePos, EMERGENCY_GATE_HOLD_OPEN_TICKS);

        if (npc .distanceToSqr(Vec3.atCenterOf(committedGateExit)) < 3.0 && !isEntityInPassage(committedGatePos)) {
            npc.getNavigation().stop();
            closeEscapedGate(world);
            passageUseCooldowns.put(committedGatePos, ESCAPED_GATE_REUSE_COOLDOWN_TICKS);
            postGateRouteTicks = POST_GATE_ROUTE_TICKS;
            clearCommittedGateEscape();
            return false;
        }

        if (npc.getNavigation().isDone() || committedGateTicks % 10 == 0) {
            startPathTo(committedGateExit, 1.05);
        }

        if (committedGateTicks >= 200 && isSafeStandPosition(world, committedGateExit)) {
            AiCompanionMod.LOGGER.warn(npc.getName().getString()
                    + " stalled inside a fence gate; moving clear to "
                    + committedGateExit.toShortString() + ".");
            npc.getNavigation().stop();
            npc .setDeltaMovement(Vec3.ZERO);
            npc .moveTo(
                    committedGateExit.getX() + 0.5,
                    committedGateExit.getY(),
                    committedGateExit.getZ() + 0.5,
                    npc.getYRot(),
                    npc.getXRot());
            closeEscapedGate(world);
            passageUseCooldowns.put(committedGatePos, ESCAPED_GATE_REUSE_COOLDOWN_TICKS);
            postGateRouteTicks = POST_GATE_ROUTE_TICKS;
            clearCommittedGateEscape();
            return false;
        }
        return true;
    }

    private void closeEscapedGate(net.minecraft.world.level.Level world) {
        if (committedGatePos == null || isEntityInPassage(committedGatePos)) return;
        BlockState state = world.getBlockState(committedGatePos);
        if (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN)) {
            world.setBlock(
                    committedGatePos,
                    state.setValue(FenceGateBlock.OPEN, false),
                    Block.UPDATE_ALL);
        }
        openedFenceGates.remove(committedGatePos);
    }

    private void clearCommittedGateEscape() {
        committedGatePos = null;
        committedGateExit = null;
        committedGateTicks = 0;
    }

    private BlockPos findGatePassThroughTarget(BlockPos gatePos) {
        net.minecraft.world.level.Level world = npc.level();
        BlockState state = world.getBlockState(gatePos);
        if (!(state.getBlock() instanceof FenceGateBlock)) {
            return findStandPositionNear(gatePos);
        }

        Direction facing = state.getValue(FenceGateBlock.FACING);
        BlockPos forward = gatePos.relative(facing);
        BlockPos backward = gatePos.relative(facing.getOpposite());

        // Cross to the opposite side of the gate from the companion. Choosing a
        // side from the distant final target can point back into the same pen when
        // farms contain airlocks, parallel gates, or internal walkways.
        Direction preferredDirection = forward.distSqr(npc.blockPosition())
                <= backward.distSqr(npc.blockPosition())
                ? facing.getOpposite()
                : facing;

        BlockPos deepPreferred = gatePos.relative(preferredDirection, 2);
        BlockPos stand = findGateSideStandPosition(world, deepPreferred);
        if (stand != null) {
            return stand;
        }

        stand = findGateSideStandPosition(world, gatePos.relative(preferredDirection));
        if (stand != null) {
            return stand;
        }

        return null;
    }

    private boolean isLikelyInsideFencePen() {
        net.minecraft.world.level.Level world = npc.level();
        BlockPos origin = npc.blockPosition();
        int fenceCount = 0;
        boolean gateFound = false;
        for (int x = -5; x <= 5; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -5; z <= 5; z++) {
                    Block block = world.getBlockState(origin.offset(x, y, z)).getBlock();
                    if (block instanceof FenceBlock) fenceCount++;
                    if (block instanceof FenceGateBlock) gateFound = true;
                }
            }
        }
        return gateFound && fenceCount >= 3;
    }

    private boolean isGateAttachedToFence(
            net.minecraft.world.level.Level world,
            BlockPos gatePos,
            BlockState gateState) {
        if (!(gateState.getBlock() instanceof FenceGateBlock)) return false;
        Direction facing = gateState.getValue(FenceGateBlock.FACING);
        Direction side = facing.getClockWise();
        Block sideBlock = world.getBlockState(gatePos.relative(side)).getBlock();
        Block oppBlock = world.getBlockState(gatePos.relative(side.getOpposite())).getBlock();
        return sideBlock instanceof FenceBlock || sideBlock instanceof FenceGateBlock
                || oppBlock instanceof FenceBlock || oppBlock instanceof FenceGateBlock;
    }

    private BlockPos findGateSideStandPosition(net.minecraft.world.level.Level world, BlockPos sidePos) {
        if (isSafeStandPosition(world, sidePos)) {
            return sidePos.immutable();
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = sidePos.relative(direction);
            if (isSafeStandPosition(world, candidate)) {
                return candidate.immutable();
            }
        }

        return null;
    }

    private boolean canPlaceTerrainAssistBlock() {
        return false;
    }

    private Direction getHorizontalDirectionTowardActiveTarget() {
        BlockPos target = getActiveMovementTarget();
        if (target != null) {
            return getHorizontalDirectionToward(target);
        }

        Vec3 velocity = npc.getDeltaMovement();
        Vec3 delta = velocity.lengthSqr() > 0.01 ? velocity : npc .getViewVector(1.0F);
        if (Math.abs(delta.x) > Math.abs(delta.z)) {
            return delta.x >= 0 ? Direction.EAST : Direction.WEST;
        }
        return delta.z >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private Direction getHorizontalDirectionToward(BlockPos target) {
        Vec3 delta = Vec3.atCenterOf(target).subtract(new Vec3(npc.getX(), npc.getY(), npc.getZ()));
        if (Math.abs(delta.x) > Math.abs(delta.z)) {
            return delta.x >= 0 ? Direction.EAST : Direction.WEST;
        }
        return delta.z >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private boolean tryDigUpwardEscape(Direction direction) {
        net.minecraft.world.level.Level world = npc.level();
        BlockPos feet = npc.blockPosition();
        BlockPos target = getActiveMovementTarget();
        boolean targetIsHigher = target != null && target.getY() > feet.getY() + 2;
        boolean underground = !world.canSeeSky(feet.above(2));
        if (!targetIsHigher && !underground) return false;

        BlockPos step = feet.relative(direction);
        List<BlockPos> blockers = new ArrayList<>();
        if (targetIsHigher) {
            blockers .add(step);
            blockers .add(step.above());
            blockers .add(step.above(2));
            blockers .add(feet.above(2));
        } else {
            blockers .add(feet.above(2));
            blockers .add(step.above());
            blockers .add(step);
        }

        for (BlockPos pos : blockers) {
            BlockState state = world.getBlockState(pos);
            if (isMiningObstacle(world, pos, state)) {
                equipToolFor(state);
                npc.getNavigation().stop();
                npc.getLookControl() .setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                world.destroyBlock(pos, true, npc);
                npc.getNavigation().recomputePath();
                return true;
            }
        }

        if (canPlaceTerrainAssistBlock() && canPlaceEscapeStair(world, step)) {
            BlockState stair = Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, direction)
                    .setValue(StairBlock.HALF, Half.BOTTOM);
            npc.getLookControl() .setLookAt(step.getX() + 0.5, step.getY() + 0.5, step.getZ() + 0.5);
            npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            npc .setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.OAK_STAIRS));
            world.setBlockAndUpdate(step, stair);
            terrainAssistPlaceCooldownTicks = TERRAIN_ASSIST_PLACE_COOLDOWN_TICKS;
            restoreDefaultMainHand();
            npc.getJumpControl().jump();
            npc.getNavigation().recomputePath();
            return true;
        }

        return false;
    }

    private boolean canPlaceEscapeStair(net.minecraft.world.level.Level world, BlockPos pos) {
        BlockState feetState = world.getBlockState(pos);
        BlockState headState = world.getBlockState(pos.above());
        BlockState highHeadState = world.getBlockState(pos.above(2));
        BlockState supportState = world.getBlockState(pos.below());

        return isReplaceableForBuild(feetState)
                && (headState.isAir() || isReplaceableForBuild(headState))
                && (highHeadState.isAir() || isReplaceableForBuild(highHeadState))
                && !supportState.isAir()
                && supportState.getFluidState().isEmpty()
                && supportState.getDestroySpeed(world, pos.below()) >= 0
                && !new AABB(pos).intersects(npc.getBoundingBox() .inflate(0.05));
    }

    private boolean tryBreakBlockingTerrain(Direction direction) {
        net.minecraft.world.level.Level world = npc.level();
        BlockPos feet = npc.blockPosition();
        BlockPos ahead = feet.relative(direction);
        BlockPos[] candidates = new BlockPos[] {
                feet.above(),
                ahead,
                ahead.above(),
                ahead.above(2)
        };

        for (BlockPos pos : candidates) {
            BlockState state = world.getBlockState(pos);
            // Use isMiningObstacle for safety - excludes fences, decorations, etc.
            if (isMiningObstacle(world, pos, state)) {
                equipToolFor(state);
                npc.getLookControl() .setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                world.destroyBlock(pos, true, npc);
                return true;
            }
        }

        return false;
    }

    private boolean tryBridgeGap(Direction direction) {
        net.minecraft.world.level.Level world = npc.level();
        BlockPos ahead = npc.blockPosition().relative(direction);
        BlockPos bridge = ahead.below();

        if (!isReplaceableForBuild(world.getBlockState(ahead))) return false;
        if (!world.getBlockState(ahead.above()).isAir()) return false;
        if (!isReplaceableForBuild(world.getBlockState(bridge))) return false;

        BlockPos support = bridge.below();
        if (world.getBlockState(support).isAir() && !world.getFluidState(support).is(FluidTags.WATER)) {
            return false;
        }

        npc.getLookControl() .setLookAt(bridge.getX() + 0.5, bridge.getY() + 0.5, bridge.getZ() + 0.5);
        npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        npc .setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.OAK_PLANKS));
        world.setBlockAndUpdate(bridge, Blocks.OAK_PLANKS.defaultBlockState());
        terrainAssistPlaceCooldownTicks = TERRAIN_ASSIST_PLACE_COOLDOWN_TICKS;
        restoreDefaultMainHand();
        npc.getNavigation().recomputePath();
        return true;
    }

    private boolean tryPlaceRamp(Direction direction) {
        net.minecraft.world.level.Level world = npc.level();
        BlockPos ramp = npc.blockPosition().relative(direction);
        BlockPos target = getActiveMovementTarget();
        boolean targetIsHigher = target != null && target.getY() > npc.blockPosition().getY() + 1;

        if (!targetIsHigher) return false;
        if (!isReplaceableForBuild(world.getBlockState(ramp))) return false;
        if (!world.getBlockState(ramp.above()).isAir()) return false;
        if (new AABB(ramp).intersects(npc.getBoundingBox() .inflate(0.05))) return false;

        BlockState stair = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, direction)
                .setValue(StairBlock.HALF, Half.BOTTOM);
        npc.getLookControl() .setLookAt(ramp.getX() + 0.5, ramp.getY() + 0.5, ramp.getZ() + 0.5);
        npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        npc .setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.OAK_STAIRS));
        world.setBlockAndUpdate(ramp, stair);
        terrainAssistPlaceCooldownTicks = TERRAIN_ASSIST_PLACE_COOLDOWN_TICKS;
        restoreDefaultMainHand();
        npc.getJumpControl().jump();
        npc.getNavigation().recomputePath();
        return true;
    }

    private boolean tryClimbAssist(Direction direction) {
        BlockPos target = getActiveMovementTarget();
        if (target == null || target.getY() <= npc.blockPosition().getY() + 2) return false;

        npc .push(direction.getStepX() * 0.08, 0.22, direction.getStepZ() * 0.08);
        npc.getJumpControl().jump();
        return true;
    }

    private boolean isBreakableObstacle(net.minecraft.world.level.Level world, BlockPos pos, BlockState state) {
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && state.getDestroySpeed(world, pos) >= 0
                && !state.is(Blocks.BEDROCK)
                && !state.is(Blocks.BARRIER);
    }

    private boolean isProtectedFromBuildClearing(net.minecraft.world.level.Level world, BlockPos pos, BlockState state) {
        return isProtectedWorldBlock(world, pos, state) || isLikelyPlayerBuiltBlock(state);
    }

    private boolean isProtectedWorldBlock(net.minecraft.world.level.Level world, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        Block block = state.getBlock();
        String id = BuiltInRegistries.BLOCK.getKey(block).getPath();

        if (world.getBlockEntity(pos) != null) return true;
        if (block instanceof DoorBlock
                || block instanceof FenceGateBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceBlock
                || block instanceof net.minecraft.world.level.block.WallBlock
                || block instanceof CropBlock
                || block instanceof BedBlock
                || block instanceof net.minecraft.world.level.block.StandingSignBlock
                || block instanceof net.minecraft.world.level.block.CeilingHangingSignBlock) {
            return true;
        }

        if (state.is(Blocks.FARMLAND)
                || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.LADDER)
                || state.is(Blocks.RAIL)
                || state.is(Blocks.POWERED_RAIL)
                || state.is(Blocks.DETECTOR_RAIL)
                || state.is(Blocks.ACTIVATOR_RAIL)) {
            return true;
        }

        return id.contains("torch")
                || id.contains("lantern")
                || id.contains("lamp")
                || id.contains("candle")
                || id.equals("redstone_wire")
                || id.equals("repeater")
                || id.equals("comparator")
                || id.contains("button")
                || id.contains("lever")
                || id.contains("pressure_plate");
    }

    private boolean isLikelyPlayerBuiltBlock(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (id.contains("_ore") || id.contains("ancient_debris")) return false;
        return id.contains("planks")
                || id.contains("_log")
                || id.contains("_wood")
                || id.contains("_stem")
                || id.contains("_hyphae")
                || id.contains("glass")
                || id.contains("brick")
                || id.contains("stairs")
                || id.contains("slab")
                || id.contains("fence")
                || id.contains("gate")
                || id.contains("door")
                || id.contains("trapdoor")
                || id.contains("wool")
                || id.contains("carpet")
                || id.contains("concrete")
                || id.contains("terracotta")
                || id.contains("polished")
                || id.contains("cut_")
                || id.contains("chiseled")
                || id.contains("cut_copper")
                || id.contains("copper_grate")
                || id.contains("copper_bulb")
                || id.contains("hay_block")
                || id.equals("crafting_table")
                || id.equals("furnace")
                || id.equals("blast_furnace")
                || id.equals("smoker")
                || id.equals("bookshelf");
    }

    private boolean isSafeMiningTarget(net.minecraft.world.level.Level world, BlockPos pos, BlockState state) {
        String blockName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (targetBlockName.contains("log") && blockName.contains("log")) {
            return !isProtectedWorldBlock(world, pos, state)
                    && hasLeavesNearby(world, pos, 4)
                    && !isNearProtectedStructure(world, pos, 4);
        }
        if (isProtectedWorldBlock(world, pos, state) || isLikelyPlayerBuiltBlock(state)) return false;
        return true;
    }

    private boolean isNearProtectedStructure(net.minecraft.world.level.Level world, BlockPos center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) continue;
                    if (isProtectedWorldBlock(world, pos, state) || isLikelyPlayerBuiltBlock(state)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasLeavesNearby(net.minecraft.world.level.Level world, BlockPos pos, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    String id = BuiltInRegistries.BLOCK.getKey(world.getBlockState(pos.offset(x, y, z)).getBlock()).getPath();
                    if (id.contains("leaves")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isMiningObstacle(net.minecraft.world.level.Level world, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        String id = BuiltInRegistries.BLOCK.getKey(block).getPath();

        if (isProtectedWorldBlock(world, pos, state) || isLikelyPlayerBuiltBlock(state)) {
            return false;
        }
        if (isNearProtectedStructure(world, pos, 3)) {
            return false;
        }
        if (id.contains("_log") || id.contains("_wood") || id.contains("_stem") || id.contains("_hyphae")) {
            return false;
        }

        // Never break doors, gates, trapdoors, or block entities
        if (block instanceof DoorBlock
                || block instanceof FenceGateBlock
                || block instanceof TrapDoorBlock
                || world.getBlockEntity(pos) != null) {
            return false;
        }

        // Never break fences, walls, or fence-like blocks
        if (block instanceof net.minecraft.world.level.block.FenceBlock
                || block instanceof net.minecraft.world.level.block.WallBlock
                || block instanceof net.minecraft.world.level.block.FenceGateBlock) {
            return false;
        }

        // Never break decorative blocks: signs, hanging signs, flower pots, etc.
        if (block instanceof net.minecraft.world.level.block.StandingSignBlock
                || block instanceof net.minecraft.world.level.block.CeilingHangingSignBlock
                || block.toString().contains("Sign")
                || block.toString().contains("FlowerPot")
                || block.toString().contains("Candle")
                || block.toString().contains("Lantern")) {
            return false;
        }

        // General obstacle check - must be breakable
        return isBreakableObstacle(world, pos, state);
    }

    private boolean assistLadderClimb() {
        if (ladderExitTarget != null && continueCommittedLadderExit()) {
            return true;
        }

        if (ladderExitCooldownTicks > 0) {
            ladderExitCooldownTicks--;
            return false;
        }

        BlockPos target = getActiveMovementTarget();
        if (target == null) {
            resetLadderAssist();
            return false;
        }

        double verticalDelta = target.getY() + 0.5 - npc.getY();
        if (Math.abs(verticalDelta) < LADDER_VERTICAL_THRESHOLD) {
            resetLadderAssist();
            return false;
        }

        BlockPos climbable = findActiveLadderRung();
        if (climbable == null) {
            climbable = findNearbyClimbableToward(target);
            if (climbable != null) {
                activeLadderColumn = climbable.immutable();
            }
        }
        if (climbable == null) {
            resetLadderAssist();
            return false;
        }

        double distanceToClimbable = npc .distanceToSqr(Vec3.atCenterOf(climbable));
        if (distanceToClimbable > LADDER_ATTACH_DISTANCE_SQUARED && !npc.onClimbable()) {
            if (npc.getNavigation().isDone() || tickCounter % 20 == 0) {
                npc.getNavigation() .moveTo(climbable.getX() + 0.5, climbable.getY(), climbable.getZ() + 0.5, 1.0);
            }
            return true;
        }

        openTrapdoorForLadder(climbable, verticalDelta);
        ladderAssistTicks++;

        Vec3 ladderCenter = Vec3.atCenterOf(climbable);
        Vec3 current = new Vec3(npc.getX(), npc.getY(), npc.getZ());
        Vec3 horizontalCorrection = new Vec3(ladderCenter.x - current.x, 0.0, ladderCenter.z - current.z);
        if (horizontalCorrection.lengthSqr() > 0.0025) {
            horizontalCorrection = horizontalCorrection.normalize().scale(0.045);
        }

        npc.getNavigation().stop();
        npc.getLookControl() .setLookAt(ladderCenter.x, target.getY() + 0.5, ladderCenter.z);
        npc .getMoveControl().setWantedPosition(ladderCenter.x, npc.getY(), ladderCenter.z, 1.0);

        if (ladderAssistTicks % LADDER_PROGRESS_CHECK_TICKS == 0) {
            if (!Double.isNaN(lastLadderY)
                    && Math.abs(npc.getY() - lastLadderY) < LADDER_PROGRESS_MINIMUM) {
                ladderNoProgressChecks++;
            } else {
                ladderNoProgressChecks = 0;
            }
            lastLadderY = npc.getY();
        }

        if (verticalDelta > 0) {
            BlockPos ladderExit = findLadderExit(climbable, target, true);
            if (ladderExit != null && isAtEndOfLadderColumn(climbable, true)) {
                npc.setJumping(true);
                npc.getJumpControl().jump();
                beginCommittedLadderExit(ladderExit, false);
                return true;
            }

            if (ladderNoProgressChecks >= LADDER_RECENTER_CHECKS) {
                recoverStalledLadderClimb(climbable, true);
            }

            npc.setJumping(true);
            npc.getJumpControl().jump();
            double climbSpeed = ladderNoProgressChecks > 0 ? 0.26 : 0.18;
            npc .setDeltaMovement(
                    horizontalCorrection.x,
                    Math.max(npc.getDeltaMovement().y, climbSpeed),
                    horizontalCorrection.z);
        } else {
            BlockPos ladderExit = findLadderExit(climbable, target, false);
            if (ladderExit != null && isAtEndOfLadderColumn(climbable, false)) {
                beginCommittedLadderExit(ladderExit, true);
                return true;
            }

            if (ladderNoProgressChecks >= LADDER_RECENTER_CHECKS) {
                recoverStalledLadderClimb(climbable, false);
            }

            npc.setJumping(false);
            npc .setDeltaMovement(
                    horizontalCorrection.x,
                    Math.min(npc.getDeltaMovement().y, -0.12),
                    horizontalCorrection.z);
        }

        return true;
    }

    private void beginCommittedLadderExit(BlockPos exit, boolean downward) {
        resetLadderAssist();
        ladderExitTarget = exit.immutable();
        ladderExitDownward = downward;
        ladderExitCommitTicks = LADDER_EXIT_COMMIT_TICKS;
        startPathTo(ladderExitTarget, downward ? 1.0 : 1.05);
        continueCommittedLadderExit();
    }

    private boolean continueCommittedLadderExit() {
        if (ladderExitTarget == null) return false;
        ladderExitCommitTicks--;

        double exitX = ladderExitTarget.getX() + 0.5;
        double exitZ = ladderExitTarget.getZ() + 0.5;
        double dx = exitX - npc.getX();
        double dz = exitZ - npc.getZ();
        double horizontalDistanceSq = dx * dx + dz * dz;

        if (horizontalDistanceSq <= LADDER_EXIT_CLEAR_DISTANCE_SQUARED && !npc.onClimbable()) {
            finishCommittedLadderExit();
            return false;
        }

        if (ladderExitCommitTicks <= 0) {
            finishCommittedLadderExit();
            return false;
        }

        if (npc.getNavigation().isDone() || tickCounter % 8 == 0) {
            startPathTo(ladderExitTarget, ladderExitDownward ? 1.0 : 1.05);
        }

        if (horizontalDistanceSq > 0.0025) {
            double distance = Math.sqrt(horizontalDistanceSq);
            double pushX = dx / distance * 0.16;
            double pushZ = dz / distance * 0.16;
            double verticalVelocity = ladderExitDownward
                    ? Math.min(npc.getDeltaMovement().y, -0.06)
                    : Math.max(npc.getDeltaMovement().y, 0.12);
            npc .setDeltaMovement(pushX, verticalVelocity, pushZ);
        }

        npc.setJumping(!ladderExitDownward);
        npc.getLookControl() .setLookAt(exitX, ladderExitTarget.getY() + 0.5, exitZ);
        return true;
    }

    private void finishCommittedLadderExit() {
        ladderExitTarget = null;
        ladderExitDownward = false;
        ladderExitCommitTicks = 0;
        ladderExitCooldownTicks = 20;
        npc.setJumping(false);
        npc.getNavigation().stop();
    }

    private BlockPos findActiveLadderRung() {
        if (activeLadderColumn == null) return null;

        net.minecraft.world.level.Level world = npc.level();
        int npcY = npc.getBlockY();
        BlockPos best = null;
        int bestVerticalDistance = Integer.MAX_VALUE;

        for (int y = npcY - 2; y <= npcY + 2; y++) {
            BlockPos candidate = new BlockPos(activeLadderColumn.getX(), y, activeLadderColumn.getZ());
            if (!world.getBlockState(candidate).is(BlockTags.CLIMBABLE)) continue;

            int verticalDistance = Math.abs(y - npcY);
            if (verticalDistance < bestVerticalDistance) {
                best = candidate;
                bestVerticalDistance = verticalDistance;
            }
        }

        return best;
    }

    private void recoverStalledLadderClimb(BlockPos climbable, boolean upward) {
        net.minecraft.world.level.Level world = npc.level();
        double centeredX = climbable.getX() + 0.5;
        double centeredZ = climbable.getZ() + 0.5;

        // First recovery stage: pull the companion back onto the rung center.
        npc .moveTo(
                centeredX,
                npc.getY(),
                centeredZ,
                npc.getYRot(),
                npc.getXRot());
        npc.fallDistance = 0.0F;

        // Second stage: after repeated failed checks, advance partway toward the
        // next rung. This clears tiny collision lips without skipping the ladder.
        if (ladderNoProgressChecks >= LADDER_ADVANCE_CHECKS) {
            double nextY = npc.getY() + (upward ? LADDER_RECOVERY_STEP : -LADDER_RECOVERY_STEP);
            BlockPos nextRung = new BlockPos(
                    climbable.getX(),
                    (int) Math.floor(nextY),
                    climbable.getZ());
            BlockState nextState = world.getBlockState(nextRung);
            BlockState headState = world.getBlockState(nextRung.above());
            boolean nextIsClimbable = nextState.is(BlockTags.CLIMBABLE);
            boolean bodyClear = nextIsClimbable || nextState.isAir();
            boolean headClear = headState.is(BlockTags.CLIMBABLE) || headState.isAir();

            if (bodyClear && headClear) {
                npc .moveTo(
                        centeredX,
                        nextY,
                        centeredZ,
                        npc.getYRot(),
                        npc.getXRot());
                ladderNoProgressChecks = 0;
                lastLadderY = nextY;
            }
        }
    }

    private boolean isAtEndOfLadderColumn(BlockPos climbable, boolean upward) {
        net.minecraft.world.level.Level world = npc.level();
        BlockPos next = upward ? climbable.above() : climbable.below();
        if (world.getBlockState(next).is(BlockTags.CLIMBABLE)) {
            return false;
        }

        double rungY = climbable.getY() + 0.5;
        return upward ? npc.getY() >= rungY - 0.35 : npc.getY() <= rungY + 0.35;
    }

    private BlockPos findLadderExit(BlockPos climbable, BlockPos target, boolean upward) {
        net.minecraft.world.level.Level world = npc.level();
        int exitY = upward ? climbable.getY() + 1 : climbable.getY();
        BlockPos column = new BlockPos(climbable.getX(), exitY, climbable.getZ());
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = column.relative(direction);
            if (!isSafeStandPosition(world, candidate)) continue;

            double score = candidate.distSqr(target);
            if (score < bestScore) {
                best = candidate.immutable();
                bestScore = score;
            }
        }

        return best;
    }

    private void resetLadderProgressOnly() {
        ladderAssistTicks = 0;
        ladderNoProgressChecks = 0;
        lastLadderY = Double.NaN;
    }

    private void resetLadderAssist() {
        activeLadderColumn = null;
        resetLadderProgressOnly();
    }

    private boolean openTrapdoorForLadder(BlockPos climbable, double verticalDelta) {
        net.minecraft.world.level.Level world = npc.level();
        boolean upward = verticalDelta > 0;

        for (int offset = 0; offset <= 2; offset++) {
            BlockPos pos = upward ? climbable.above(offset + 1) : climbable.below(offset);
            BlockState state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof TrapDoorBlock) || state.getValue(TrapDoorBlock.OPEN)) {
                continue;
            }

            if (!shouldControlTrapdoor(pos) && npc .distanceToSqr(Vec3.atCenterOf(pos)) > 4.0) {
                continue;
            }

            npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            world.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, true), Block.UPDATE_ALL);
            openedTrapdoors.put(pos.immutable(), TRAPDOOR_CLOSE_DELAY_TICKS);
            return true;
        }

        return false;
    }

    private BlockPos getActiveMovementTarget() {
        if (surfaceEscapeTarget != null) {
            return surfaceEscapeTarget;
        }
        if ("follow".equals(currentMode) && activeFollowTarget != null && !activeFollowTarget.isRemoved()) {
            return activeFollowTarget.blockPosition();
        }
        if ("walk".equals(currentMode)) {
            return currentWalkTarget;
        }
        if ("mine".equals(currentMode)) {
            if (minerReturningToStorage) return minerStorageTarget;
            return currentMineTarget != null ? currentMineTarget : miningSearchWaypoint;
        }
        if ("build".equals(currentMode)) {
            return buildStandPos;
        }
        if ("explore".equals(currentMode)) {
            return getCurrentExpeditionWaypoint();
        }
        if ("idle".equals(currentMode)
                && ("farmer".equals(npc.getAppearanceVariantName()) || "rancher".equals(npc.getAppearanceVariantName()))) {
            if (currentFarmCropTarget != null) return currentFarmCropTarget;
            if (currentFarmPlantTarget != null) return currentFarmPlantTarget;
            if (currentFarmTillTarget != null) return currentFarmTillTarget;
            if (currentFarmChestTarget != null) return currentFarmChestTarget;
            if (currentFarmCullTarget != null && !currentFarmCullTarget.isRemoved()) {
                return currentFarmCullTarget.blockPosition();
            }
            if (currentFarmAnimalTarget != null && !currentFarmAnimalTarget.isRemoved()) {
                return currentFarmAnimalTarget.blockPosition();
            }
        }

        return null;
    }

    private BlockPos findNearbyClimbableToward(BlockPos target) {
        net.minecraft.world.level.Level world = npc.level();
        BlockPos origin = npc.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int verticalDirection = Integer.compare(target.getY(), origin.getY());

        for (int x = -4; x <= 4; x++) {
            for (int y = -3; y <= 5; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos candidate = origin.offset(x, y, z);
                    if (!world.getBlockState(candidate).is(BlockTags.CLIMBABLE)) continue;
                    if (verticalDirection > 0 && candidate.getY() < origin.getY() - 1) continue;
                    if (verticalDirection < 0 && candidate.getY() > origin.getY() + 2) continue;

                    double distanceToNpc = npc .distanceToSqr(Vec3.atCenterOf(candidate));
                    double horizontalToTarget = candidate.distSqr(new BlockPos(target.getX(), candidate.getY(), target.getZ()));
                    double verticalScore = Math.abs(candidate.getY() - npc.getY());
                    double score = distanceToNpc * 2.0 + horizontalToTarget * 0.25 + verticalScore;

                    if (score < bestScore) {
                        best = candidate;
                        bestScore = score;
                    }
                }
            }
        }

        return best;
    }

    private boolean escapeWater() {
        if (!npc.isInWater()) {
            resetWaterEscapeState();
            return false;
        }

        // Check water depth: only activate swimming for deep water (2+ blocks)
        int waterDepth = getWaterDepthAt(npc.blockPosition());
        if (waterDepth <= 1) {
            // Shallow water is ordinary walking. Explicitly cancel the SwimGoal's
            // upward impulse so companions do not bob in one-block streams.
            npc.setSwimming(false);
            npc.setJumping(false);
            Vec3 velocity = npc.getDeltaMovement();
            if (velocity.y > 0.0) {
                npc .setDeltaMovement(velocity.x, 0.0, velocity.z);
            }
            if (waterEscapeTicks > 0) resetWaterEscapeState();
            return false;
        }

        waterEscapeTicks++;
        decayFailedWaterExits();

        if (npc.getTarget() != null) {
            npc.setTarget(null);
        }

        if (waterEscapeTicks == 1) {
            npc.getNavigation().stop();
        }

        if (waterEscapeTicks % WATER_STUCK_CHECK_TICKS == 0) {
            Vec3 currentPos = new Vec3(npc.getX(), npc.getY(), npc.getZ());
            if (lastWaterEscapePos != null && currentPos.distanceToSqr(lastWaterEscapePos) < WATER_STUCK_PROGRESS_SQUARED) {
                waterNoProgressChecks++;
                markCurrentWaterExitFailed();
            } else {
                waterNoProgressChecks = 0;
            }
            lastWaterEscapePos = currentPos;
        }

        if (waterNoProgressChecks >= 2) {
            npc.getNavigation().stop();
            digWaterEscapeChannel();
            currentWaterExitTarget = null;
            waterNoProgressChecks = 0;
        }

        if (currentWaterExitTarget == null
                || !isSafeWaterExit(npc.level(), currentWaterExitTarget)
                || waterEscapeTicks % WATER_EXIT_REPATH_TICKS == 0) {
            currentWaterExitTarget = findBestWaterExit();
        }

        npc.setSwimming(true);
        npc.setJumping(true);
        npc.getJumpControl().jump();

        if (currentWaterExitTarget != null) {
            swimTowardWaterExit(currentWaterExitTarget);
        } else {
            if (waterEscapeTicks % 20 == 0) {
                digWaterEscapeChannel();
            }
            npc .push(0.0, 0.13, 0.0);
            if (waterEscapeTicks >= WATER_EMERGENCY_RESCUE_TICKS) {
                rescueFromOpenWater();
            }
        }

        return true;
    }

    private boolean digWaterEscapeChannel() {
        if (!(npc.level() instanceof ServerLevel world)) return false;
        BlockPos feet = npc.blockPosition();
        Direction preferred = currentWaterExitTarget != null
                ? getHorizontalDirectionToward(currentWaterExitTarget)
                : getHorizontalDirectionTowardActiveTarget();
        Direction[] directions = new Direction[] {
                preferred,
                preferred.getClockWise(),
                preferred.getCounterClockWise(),
                preferred.getOpposite()
        };

        for (Direction direction : directions) {
            BlockPos ahead = feet.relative(direction);
            BlockPos[] candidates = new BlockPos[] {ahead.above(), ahead, ahead.below()};
            for (BlockPos pos : candidates) {
                BlockState state = world.getBlockState(pos);
                if (!isBreakableObstacle(world, pos, state)) continue;
                if (isProtectedWorldBlock(world, pos, state) || isLikelyPlayerBuiltBlock(state)) continue;
                if (hasDangerousMiningFluidNearby(world, pos)) continue;

                equipToolFor(state);
                npc.getNavigation().stop();
                npc.getLookControl() .setLookAt(
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5);
                npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                world.destroyBlock(pos, true, npc);
                npc .push(
                        direction.getStepX() * 0.12,
                        0.1,
                        direction.getStepZ() * 0.12);
                return true;
            }
        }
        return false;
    }

    private void rescueFromOpenWater() {
        if (!(npc.level() instanceof ServerLevel serverWorld)) return;

        BlockPos rescue = null;
        if (npc.getHomePosition() != null) {
            rescue = findSafeStandAround(npc.getHomePosition(), 10, 8);
        }
        if (rescue == null) {
            net.minecraft.world.entity.player.Player nearest = serverWorld.getNearestPlayer(npc, 256.0);
            if (nearest != null) {
                rescue = findSafeStandAround(nearest.blockPosition(), 10, 8);
            }
        }
        if (rescue == null) return;

        AiCompanionMod.LOGGER.warn(npc.getName().getString()
                + " could not find shore; rescuing from open water to " + rescue.toShortString() + ".");
        npc.getNavigation().stop();
        npc .setDeltaMovement(Vec3.ZERO);
        npc .moveTo(
                rescue.getX() + 0.5,
                rescue.getY(),
                rescue.getZ() + 0.5,
                npc.getYRot(),
                npc.getXRot());
        currentMode = "idle";
        currentMineTarget = null;
        targetBlockName = "";
        rememberAction("@idle");
        resetWaterEscapeState();
    }

    private void resetWaterEscapeState() {
        if (waterEscapeTicks > 0) {
            npc.getNavigation().stop();
            currentWaterExitTarget = null;
            npc.setJumping(false);
            npc.setSwimming(false);
            waterEscapeTicks = 0;
            waterNoProgressChecks = 0;
            lastWaterEscapePos = null;
            failedWaterExitCooldowns.clear();
        }
    }

    private void swimTowardWaterExit(BlockPos target) {
        Vec3 exitCenter = Vec3.atCenterOf(target);
        Vec3 direction = exitCenter.subtract(new Vec3(npc.getX(), npc.getY(), npc.getZ()));
        Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);

        npc.getLookControl() .setLookAt(exitCenter.x, exitCenter.y, exitCenter.z);
        npc .getMoveControl().setWantedPosition(exitCenter.x, exitCenter.y, exitCenter.z, 1.25);

        if (horizontal.lengthSqr() > 0.01) {
            Vec3 push = horizontal.normalize().scale(0.09);
            npc .push(push.x, direction.y > 0.25 ? 0.12 : 0.08, push.z);
        } else {
            npc .push(0.0, 0.12, 0.0);
        }

        if (npc.getNavigation().isDone() || waterEscapeTicks % WATER_EXIT_REPATH_TICKS == 0) {
            Path path = npc.getNavigation() .createPath(target, 0);
            if (path != null && path.canReach()) {
                npc.getNavigation() .moveTo(path, 1.25);
            } else {
                npc.getNavigation() .moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.25);
            }
        }
    }

    private BlockPos findBestWaterExit() {
        net.minecraft.world.level.Level world = npc.level();
        BlockPos origin = npc.blockPosition();
        BlockPos bestReachable = null;
        BlockPos bestFallback = null;
        double bestReachableScore = Double.MAX_VALUE;
        double bestFallbackScore = Double.MAX_VALUE;

        for (int radius = 1; radius <= WATER_EXIT_SEARCH_RADIUS; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) continue;

                    for (int y = 4; y >= -4; y--) {
                        BlockPos candidate = origin.offset(x, y, z);
                        if (!isSafeWaterExit(world, candidate)) continue;
                        if (failedWaterExitCooldowns.containsKey(candidate)) continue;

                        double score = npc .distanceToSqr(Vec3.atCenterOf(candidate)) + Math.max(0, candidate.getY() - origin.getY()) * 2.0;
                        Path path = npc.getNavigation() .createPath(candidate, 0);
                        if (path != null && path.canReach()) {
                            if (score < bestReachableScore) {
                                bestReachable = candidate;
                                bestReachableScore = score;
                            }
                        } else if (score < bestFallbackScore) {
                            bestFallback = candidate;
                            bestFallbackScore = score;
                        }
                    }
                }
            }

            if (bestReachable != null) {
                return bestReachable;
            }
        }

        return bestFallback;
    }

    private boolean isSafeWaterExit(net.minecraft.world.level.Level world, BlockPos feet) {
        return isSafeStandPosition(world, feet)
                && !world.getFluidState(feet).is(FluidTags.WATER)
                && !world.getFluidState(feet.above()).is(FluidTags.WATER)
                && hasWaterNearby(world, feet);
    }

    private boolean hasWaterNearby(net.minecraft.world.level.Level world, BlockPos pos) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 0; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (world.getFluidState(pos.offset(x, y, z)).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void markCurrentWaterExitFailed() {
        if (currentWaterExitTarget != null) {
            failedWaterExitCooldowns.put(currentWaterExitTarget, 120);
            currentWaterExitTarget = null;
        }
    }

    private void decayFailedWaterExits() {
        failedWaterExitCooldowns.replaceAll((pos, cooldown) -> cooldown - 1);
        failedWaterExitCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }

    private void moveNearBuildTarget(net.minecraft.core.BlockPos target, double speed) {
        net.minecraft.core.BlockPos standPos = findBuildStandPositionNear(target);
        if (standPos != null) {
            npc.getNavigation() .moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, speed);
        } else if (buildStandPos != null) {
            npc.getNavigation() .moveTo(buildStandPos.getX() + 0.5, buildStandPos.getY(), buildStandPos.getZ() + 0.5, speed);
        } else {
            moveNear(target, speed);
        }
    }

    private void moveToBuildStandOrEvacuate() {
        if (buildStandPos == null) return;

        buildEvacuationTicks += 15;
        if (npc.getNavigation().isDone() || buildEvacuationTicks % 45 == 0) {
            npc.getNavigation() .moveTo(buildStandPos.getX() + 0.5, buildStandPos.getY(), buildStandPos.getZ() + 0.5, 1.0);
        }

        if (buildEvacuationTicks >= 120 && isSafeStandPosition(npc.level(), buildStandPos)) {
            AiCompanionMod.LOGGER.warn("Builder could not path out of the house footprint; moving companion to build stand position.");
            npc.getNavigation().stop();
            npc .moveTo(buildStandPos.getX() + 0.5, buildStandPos.getY(), buildStandPos.getZ() + 0.5, npc.getYRot(), npc.getXRot());
            buildEvacuationTicks = 0;
        }
    }

    private net.minecraft.core.BlockPos findBuildCenterNearBuilder(net.minecraft.core.BlockPos builderPos, int radius) {
        net.minecraft.core.BlockPos best = resolveBuildCenterOnGround(builderPos.offset(0, 0, -radius - 4), radius);
        int bestScore = Integer.MIN_VALUE;

        for (int distance = radius + 4; distance <= radius + 24; distance += 4) {
            net.minecraft.core.BlockPos[] candidates = new net.minecraft.core.BlockPos[] {
                    builderPos.offset(0, 0, -distance),
                    builderPos.offset(distance, 0, 0),
                    builderPos.offset(0, 0, distance),
                    builderPos.offset(-distance, 0, 0),
                    builderPos.offset(distance, 0, distance),
                    builderPos.offset(distance, 0, -distance),
                    builderPos.offset(-distance, 0, distance),
                    builderPos.offset(-distance, 0, -distance)
            };

            for (net.minecraft.core.BlockPos candidate : candidates) {
                net.minecraft.core.BlockPos grounded = resolveBuildCenterOnGround(candidate, radius);
                int score = scoreBuildCenter(grounded, radius);
                if (isDryBuildFootprint(grounded, radius)) {
                    score += 1000;
                }
                if (score > bestScore) {
                    best = grounded;
                    bestScore = score;
                }
            }
        }

        return best;
    }

    private net.minecraft.core.BlockPos resolveBuildCenterOnGround(net.minecraft.core.BlockPos estimate, int radius) {
        net.minecraft.world.level.Level world = npc.level();

        for (int y = 8; y >= -12; y--) {
            BlockPos candidate = estimate.offset(0, y, 0);
            if (isSafeStandPosition(world, candidate)) {
                return candidate;
            }
        }

        BlockPos nearby = findSafeStandAround(estimate, Math.max(2, radius + 2), 12);
        return nearby != null ? nearby : estimate;
    }

    private net.minecraft.core.BlockPos findBridgeCenterNearBuilder(net.minecraft.core.BlockPos builderPos) {
        net.minecraft.world.level.Level world = npc.level();
        net.minecraft.core.BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -10; x <= 10; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -10; z <= 10; z++) {
                    net.minecraft.core.BlockPos candidate = builderPos.offset(x, y, z);
                    if (!world.getFluidState(candidate).is(FluidTags.WATER)) continue;

                    net.minecraft.core.BlockPos deck = candidate.above();
                    if (!isReplaceableForBuild(world.getBlockState(deck))) continue;

                    double distance = candidate.distSqr(builderPos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = deck;
                    }
                }
            }
        }

        return best != null ? best : findBuildCenterNearBuilder(builderPos, 6);
    }

    private int scoreBuildCenter(net.minecraft.core.BlockPos center, int radius) {
        net.minecraft.world.level.Level world = npc.level();
        int score = 0;

        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int z = -radius - 1; z <= radius + 1; z++) {
                net.minecraft.core.BlockPos ground = center.offset(x, -1, z);
                net.minecraft.core.BlockPos feet = center.offset(x, 0, z);
                net.minecraft.core.BlockPos head = center.offset(x, 1, z);

                BlockState groundState = world.getBlockState(ground);
                if (!groundState.isAir()
                        && groundState.getFluidState().isEmpty()
                        && groundState.getDestroySpeed(world, ground) >= 0) {
                    score += 2;
                } else {
                    score -= 6;
                }
                if (isReplaceableForBuild(world.getBlockState(feet))) score += 2;
                if (isReplaceableForBuild(world.getBlockState(head))) score += 1;
                if (!world.getBlockState(feet).getFluidState().isEmpty()
                        || !world.getBlockState(head).getFluidState().isEmpty()) score -= 8;
            }
        }

        return score;
    }

    private boolean isDryBuildFootprint(net.minecraft.core.BlockPos center, int radius) {
        net.minecraft.world.level.Level world = npc.level();

        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int z = -radius - 1; z <= radius + 1; z++) {
                BlockPos ground = center.offset(x, -1, z);
                BlockPos feet = center.offset(x, 0, z);
                BlockPos head = center.offset(x, 1, z);
                BlockState groundState = world.getBlockState(ground);

                if (groundState.isAir()
                        || !groundState.getFluidState().isEmpty()
                        || groundState.getDestroySpeed(world, ground) < 0
                        || !world.getBlockState(feet).getFluidState().isEmpty()
                        || !world.getBlockState(head).getFluidState().isEmpty()) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isReplaceableForBuild(BlockState state) {
        return state.isAir()
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.SNOW);
    }

    private net.minecraft.core.BlockPos findBuildStandPositionNear(net.minecraft.core.BlockPos target) {
        net.minecraft.world.level.Level world = npc.level();
        net.minecraft.core.BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -3; x <= 3; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -3; z <= 3; z++) {
                    net.minecraft.core.BlockPos candidate = target.offset(x, y, z);
                    if (isInsideBuildFootprint(candidate) || !isSafeStandPosition(world, candidate)) continue;

                    double distance = npc .distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(candidate));
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }

        return best;
    }

    private net.minecraft.core.BlockPos findSafeBuildStandPos(net.minecraft.core.BlockPos center, int radius) {
        net.minecraft.world.level.Level world = npc.level();
        net.minecraft.core.BlockPos[] candidates = new net.minecraft.core.BlockPos[] {
                center.offset(0, 0, radius + 2),
                center.offset(radius + 2, 0, 0),
                center.offset(0, 0, -radius - 2),
                center.offset(-radius - 2, 0, 0)
        };

        for (net.minecraft.core.BlockPos candidate : candidates) {
            if (isSafeStandPosition(world, candidate)) {
                return candidate;
            }
        }

        return findBuildStandPositionNear(center);
    }

    private boolean isInsideBuildFootprint(net.minecraft.core.BlockPos pos) {
        if (buildCenter == null) return false;
        return pos.getY() >= buildCenter.getY() - 1
                && pos.getY() <= buildCenter.getY() + buildHeight
                && Math.abs(pos.getX() - buildCenter.getX()) <= buildRadius
                && Math.abs(pos.getZ() - buildCenter.getZ()) <= buildRadius;
    }

    private boolean blockWouldTrapNpc(net.minecraft.core.BlockPos target) {
        return new AABB(target).intersects(npc.getBoundingBox() .inflate(0.15));
    }

    private boolean canPlaceBuildBlock(net.minecraft.core.BlockPos target, BlockState state) {
        BlockState current = npc.level().getBlockState(target);
        if (current.is(state.getBlock())) {
            return true;
        }
        if (!"bridge".equals(buildSchematic) && !current.getFluidState().isEmpty()) {
            return false;
        }
        if (state.is(Blocks.OAK_PLANKS) && target.getY() == buildCenter.getY() - 1) {
            return current.getFluidState().isEmpty();
        }
        if (state.is(Blocks.OAK_PLANKS) && !current.getFluidState().isEmpty()) {
            return true;
        }

        return isReplaceableForBuild(current);
    }

    private void queueBuildSchematic(net.minecraft.core.BlockPos center, String schematic) {
        CustomSchematic custom = loadCustomSchematic(schematic);
        if (custom != null) {
            List<CustomSchematicPlacement> placements = new ArrayList<>();
            Map<String, CustomSchematicPlacement> byRelativePos = new HashMap<>();
            for (CustomSchematicBlock placement : custom.blocks()) {
                ResourceLocation id = ResourceLocation.tryParse(placement.block());
                if (id == null) continue;
                Block block = BuiltInRegistries.BLOCK.get(id);
                if (block == Blocks.AIR) {
                    AiCompanionMod.LOGGER.warn("Skipping unknown custom schematic block: " + placement.block());
                    continue;
                }
                BlockState state = applyCustomBlockProperties(block.defaultBlockState(), placement.properties());
                CustomSchematicPlacement resolved = new CustomSchematicPlacement(
                        placement.x(), placement.y(), placement.z(), state);
                placements .add(resolved);
                byRelativePos.put(relativeBlockKey(placement.x(), placement.y(), placement.z()), resolved);
            }
            for (CustomSchematicPlacement placement : placements) {
                queueBuild(
                        center.offset(placement.x(), placement.y(), placement.z()),
                        connectCustomSchematicChest(placement, byRelativePos));
            }
            return;
        }

        switch (schematic) {
            case "cottage" -> queueHouseBuild(center, 3);
            case "tower" -> queueWatchtowerBuild(center);
            case "hall" -> queueHallBuild(center);
            case "castle" -> queueCastleBuild(center);
            case "bridge" -> queueBridgeBuild(center);
            case "wall" -> queueWallBuild(center);
            case "base" -> queueBaseBuild(center);
            case "pen" -> queueLivestockPenBuild(center);
            default -> queueHouseBuild(center, 2);
        }
    }

    private CustomSchematic loadCustomSchematic(String requestedName) {
        if (requestedName == null || requestedName.isBlank()) return null;

        java.nio.file.Path path = FMLPaths.CONFIGDIR.get().resolve("aicompanion_schematics.json");
        if (!Files.exists(path)) return null;

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject schematics = root.has("schematics") && root.get("schematics").isJsonObject()
                    ? root.getAsJsonObject("schematics")
                    : null;
            if (schematics == null || !schematics.has(requestedName)) return null;

            JsonObject data = schematics.getAsJsonObject(requestedName);
            JsonArray rawBlocks = data.getAsJsonArray("blocks");
            List<CustomSchematicBlock> blocks = new ArrayList<>();
            if (rawBlocks != null) {
                for (JsonElement element : rawBlocks) {
                    if (!element.isJsonObject()) continue;
                    JsonObject entry = element.getAsJsonObject();
                    if (!entry.has("x") || !entry.has("y") || !entry.has("z") || !entry.has("block")) continue;
                    blocks .add(new CustomSchematicBlock(
                            entry.get("x").getAsInt(),
                            entry.get("y").getAsInt(),
                            entry.get("z").getAsInt(),
                            entry.get("block").getAsString(),
                            readCustomBlockProperties(entry)));
                }
            }
            if (blocks.isEmpty()) return null;

            int radius = data.has("radius") ? data.get("radius").getAsInt() : 4;
            int height = data.has("height") ? data.get("height").getAsInt() : 4;
            return new CustomSchematic(requestedName, radius, height, blocks);
        } catch (Exception e) {
            AiCompanionMod.LOGGER.error("Could not load custom schematic " + requestedName + ": " + e.getMessage());
            return null;
        }
    }

    private Map<String, String> readCustomBlockProperties(JsonObject entry) {
        Map<String, String> properties = new HashMap<>();
        if (!entry.has("properties") || !entry.get("properties").isJsonObject()) {
            return properties;
        }
        for (Map.Entry<String, JsonElement> property : entry.getAsJsonObject("properties").entrySet()) {
            if (property.getValue().isJsonPrimitive()) {
                properties.put(property.getKey(), property.getValue().getAsString());
            }
        }
        return properties;
    }

    private BlockState applyCustomBlockProperties(BlockState state, Map<String, String> values) {
        BlockState result = state;
        for (Map.Entry<String, String> value : values.entrySet()) {
            Property<?> property = result.getBlock().getStateDefinition().getProperty(value.getKey());
            if (property == null) {
                AiCompanionMod.LOGGER.warn("Ignoring unsupported block property "
                        + value.getKey() + " on " + BuiltInRegistries.BLOCK.getKey(result.getBlock()));
                continue;
            }
            result = applyCustomBlockProperty(result, property, value.getValue());
        }
        return result;
    }

    private BlockState connectCustomSchematicChest(
            CustomSchematicPlacement placement,
            Map<String, CustomSchematicPlacement> placements) {
        BlockState state = placement.state();
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return state;
        }

        Direction facing = state.getValue(ChestBlock.FACING);
        Direction right = facing.getClockWise();
        Direction left = facing.getCounterClockWise();
        boolean hasRight = isCompatibleSchematicChest(placement, placements, right, facing);
        boolean hasLeft = isCompatibleSchematicChest(placement, placements, left, facing);

        if (hasRight == hasLeft) {
            return state;
        }

        return state.setValue(ChestBlock.TYPE, hasRight ? ChestType.LEFT : ChestType.RIGHT);
    }

    private boolean isCompatibleSchematicChest(
            CustomSchematicPlacement placement,
            Map<String, CustomSchematicPlacement> placements,
            Direction offset,
            Direction facing) {
        CustomSchematicPlacement neighbor = placements.get(relativeBlockKey(
                placement.x() + offset.getStepX(),
                placement.y(),
                placement.z() + offset.getStepZ()));
        return neighbor != null
                && neighbor.state().getBlock() instanceof ChestBlock
                && neighbor.state().getValue(ChestBlock.TYPE) == ChestType.SINGLE
                && neighbor.state().getValue(ChestBlock.FACING) == facing;
    }

    private String relativeBlockKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private <T extends Comparable<T>> BlockState applyCustomBlockProperty(
            BlockState state,
            Property<T> property,
            String value) {
        return property.getValue(value)
                .map(parsed -> state.setValue(property, parsed))
                .orElseGet(() -> {
                    AiCompanionMod.LOGGER.warn("Ignoring invalid value " + value
                            + " for block property " + property.getName());
                    return state;
                });
    }

    private void queueBridgeBuild(net.minecraft.core.BlockPos center) {
        BlockState deck = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState rail = connectedFence(false, true, false, true);
        BlockState westStair = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.WEST)
                .setValue(StairBlock.HALF, Half.BOTTOM);
        BlockState eastStair = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.EAST)
                .setValue(StairBlock.HALF, Half.BOTTOM);

        for (int x = -5; x <= 5; x++) {
            for (int z = -1; z <= 1; z++) {
                queueBuild(center.offset(x, 0, z), deck);
            }
            queueBuild(center.offset(x, 1, -1), rail);
            queueBuild(center.offset(x, 1, 1), rail);
        }

        for (int z = -1; z <= 1; z++) {
            queueBuild(center.offset(-6, 0, z), westStair);
            queueBuild(center.offset(6, 0, z), eastStair);
        }

        queueBuild(center.offset(-5, 2, -1), Blocks.TORCH.defaultBlockState());
        queueBuild(center.offset(5, 2, -1), Blocks.TORCH.defaultBlockState());
        queueBuild(center.offset(-5, 2, 1), Blocks.TORCH.defaultBlockState());
        queueBuild(center.offset(5, 2, 1), Blocks.TORCH.defaultBlockState());
    }

    private void queueWallBuild(net.minecraft.core.BlockPos center) {
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        int halfLength = 6;

        for (int x = -halfLength; x <= halfLength; x++) {
            queueBuild(center.offset(x, -1, 0), stone);
            for (int y = 0; y < 3; y++) {
                queueBuild(center.offset(x, y, 0), stone);
            }
            if (x % 2 == 0) {
                queueBuild(center.offset(x, 3, 0), stone);
            }
        }

        for (int x : new int[] {-halfLength, halfLength}) {
            for (int y = 0; y <= 4; y++) {
                queueBuild(center.offset(x, y, 0), stone);
            }
        }

        queueBuild(center.offset(-halfLength + 1, 3, 0), Blocks.TORCH.defaultBlockState());
        queueBuild(center.offset(halfLength - 1, 3, 0), Blocks.TORCH.defaultBlockState());
    }

    private void queueBaseBuild(net.minecraft.core.BlockPos center) {
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        int radius = 4;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                queueBuild(center.offset(x, -1, z), stone);
            }
        }

        for (int y = 0; y < 5; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean wall = x == -radius || x == radius || z == -radius || z == radius;
                    if (!wall) continue;
                    if (x == 0 && z == radius && y < 2) continue;
                    boolean window = y == 2 && ((Math.abs(x) == radius && z % 2 == 0)
                            || (Math.abs(z) == radius && Math.abs(x) == 2));
                    queueBuild(center.offset(x, y, z), window ? glass : stone);
                }
            }
        }

        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int z = -radius - 1; z <= radius + 1; z++) {
                queueBuild(center.offset(x, 5, z), planks);
            }
        }

        queueBuildDoor(center, radius);
        queueBuild(center.offset(-2, 0, -2), Blocks.CHEST.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        queueBuild(center.offset(-1, 0, -2), Blocks.CRAFTING_TABLE.defaultBlockState());
        queueBuild(center.offset(0, 0, -2), Blocks.FURNACE.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        queueBuild(center.offset(-2, 1, 1), Blocks.TORCH.defaultBlockState());
        queueBuild(center.offset(2, 1, 1), Blocks.TORCH.defaultBlockState());

        BlockState bedFoot = Blocks.RED_BED.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(BedBlock.PART, BedPart.FOOT);
        queueBuild(center.offset(2, 0, 1), bedFoot);
        queueBuild(center.offset(2, 0, 0), bedFoot.setValue(BedBlock.PART, BedPart.HEAD));
    }

    private void queueLivestockPenBuild(net.minecraft.core.BlockPos center) {
        int radius = 4;
        BlockState gate = Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(FenceGateBlock.FACING, Direction.SOUTH)
                .setValue(FenceGateBlock.OPEN, false);

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                boolean perimeter = Math.abs(x) == radius || Math.abs(z) == radius;
                if (!perimeter) continue;
                if (x == 0 && z == radius) {
                    queueBuild(center.offset(x, 0, z), gate);
                    continue;
                }
                queueBuild(center.offset(x, 0, z), penFenceState(x, z, radius));
            }
        }

        queueBuild(center.offset(-2, 0, -2), Blocks.HAY_BLOCK.defaultBlockState());
        queueBuild(center.offset(2, 0, -2), Blocks.HAY_BLOCK.defaultBlockState());
        queueBuild(center.offset(-radius + 1, 1, radius - 1), Blocks.TORCH.defaultBlockState());
        queueBuild(center.offset(radius - 1, 1, radius - 1), Blocks.TORCH.defaultBlockState());
    }

    private BlockState penFenceState(int x, int z, int radius) {
        boolean north = Math.abs(x) == radius && z > -radius;
        boolean south = Math.abs(x) == radius && z < radius;
        boolean west = Math.abs(z) == radius && x > -radius;
        boolean east = Math.abs(z) == radius && x < radius;
        return connectedFence(north, east, south, west);
    }

    private BlockState connectedFence(boolean north, boolean east, boolean south, boolean west) {
        return Blocks.OAK_FENCE.defaultBlockState()
                .setValue(FenceBlock.NORTH, north)
                .setValue(FenceBlock.EAST, east)
                .setValue(FenceBlock.SOUTH, south)
                .setValue(FenceBlock.WEST, west);
    }

    private void queueHouseBuild(net.minecraft.core.BlockPos center, int radius) {
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();

        // Floor: 5x5.
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                queueBuild(center.offset(x, -1, z), planks);
            }
        }

        // Walls: 5 wide, 4 high, with front door and simple glass windows.
        for (int y = 0; y < 4; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean wall = x == -radius || x == radius || z == -radius || z == radius;
                    if (!wall) continue;
                    if (x == 0 && z == radius && y < 2) continue; // Door opening.

                    boolean sideWindow = y == 2 && z == 0 && (x == -radius || x == radius);
                    boolean backWindow = y == 2 && z == -radius && x == 0;
                    boolean frontWindows = y == 2 && z == radius && Math.abs(x) == 1;
                    queueBuild(center.offset(x, y, z), sideWindow || backWindow || frontWindows ? glass : planks);
                }
            }
        }

        // Flat roof with a one-block overhang.
        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int z = -radius - 1; z <= radius + 1; z++) {
                queueBuild(center.offset(x, 4, z), planks);
            }
        }

        // Door.
        BlockState doorLower = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                .setValue(DoorBlock.OPEN, false);
        BlockState doorUpper = doorLower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        queueBuild(center.offset(0, 0, radius), doorLower);
        queueBuild(center.offset(0, 1, radius), doorUpper);

        // Interior torch and bed.
        queueBuild(center.offset(-1, 0, -1), Blocks.TORCH.defaultBlockState());
        BlockState bedFoot = Blocks.RED_BED.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(BedBlock.PART, BedPart.FOOT);
        BlockState bedHead = bedFoot.setValue(BedBlock.PART, BedPart.HEAD);
        queueBuild(center.offset(1, 0, 0), bedFoot);
        queueBuild(center.offset(1, 0, -1), bedHead);
    }

    private void queueWatchtowerBuild(net.minecraft.core.BlockPos center) {
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState roofHatch = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, Direction.SOUTH)
                .setValue(TrapDoorBlock.HALF, Half.TOP)
                .setValue(TrapDoorBlock.OPEN, false);
        int radius = 2;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                queueBuild(center.offset(x, -1, z), stone);
            }
        }

        for (int y = 0; y < 8; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean wall = x == -radius || x == radius || z == -radius || z == radius;
                    if (!wall) continue;
                    if (x == 0 && z == radius && y < 2) continue;
                    boolean ladderSupport = x == 0 && z == -radius;
                    boolean window = !ladderSupport && (y == 3 || y == 5) && (x == 0 || z == 0);
                    queueBuild(center.offset(x, y, z), window ? glass : stone);
                }
            }
        }

        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int z = -radius - 1; z <= radius + 1; z++) {
                boolean hatchOpening = x == 0 && z == -radius + 1;
                queueBuild(center.offset(x, 8, z), hatchOpening ? roofHatch : planks);
                if ((Math.abs(x) == radius + 1 || Math.abs(z) == radius + 1) && (x + z) % 2 == 0) {
                    queueBuild(center.offset(x, 9, z), stone);
                }
            }
        }

        BlockState ladder = Blocks.LADDER.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
        for (int y = 0; y < 8; y++) {
            queueBuild(center.offset(0, y, -radius + 1), ladder);
        }

        queueBuildDoor(center, radius);
        queueBuild(center.offset(1, 1, -radius + 1), Blocks.TORCH.defaultBlockState());
    }

    private void queueHallBuild(net.minecraft.core.BlockPos center) {
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        int halfX = 4;
        int halfZ = 3;

        for (int x = -halfX; x <= halfX; x++) {
            for (int z = -halfZ; z <= halfZ; z++) {
                queueBuild(center.offset(x, -1, z), planks);
            }
        }

        for (int y = 0; y < 5; y++) {
            for (int x = -halfX; x <= halfX; x++) {
                for (int z = -halfZ; z <= halfZ; z++) {
                    boolean wall = x == -halfX || x == halfX || z == -halfZ || z == halfZ;
                    if (!wall) continue;
                    if (x == 0 && z == halfZ && y < 2) continue;
                    boolean window = y == 2 && ((Math.abs(x) == halfX && z % 2 == 0) || (Math.abs(z) == halfZ && Math.abs(x) == 2));
                    queueBuild(center.offset(x, y, z), window ? glass : planks);
                }
            }
        }

        for (int x = -halfX - 1; x <= halfX + 1; x++) {
            for (int z = -halfZ - 1; z <= halfZ + 1; z++) {
                queueBuild(center.offset(x, 5, z), planks);
            }
        }

        queueBuildDoor(center, halfZ);
        queueBuild(center.offset(-2, 0, -1), Blocks.TORCH.defaultBlockState());
        queueBuild(center.offset(2, 0, -1), Blocks.TORCH.defaultBlockState());
        BlockState bedFoot = Blocks.RED_BED.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(BedBlock.PART, BedPart.FOOT);
        queueBuild(center.offset(2, 0, 1), bedFoot);
        queueBuild(center.offset(2, 0, 0), bedFoot.setValue(BedBlock.PART, BedPart.HEAD));
    }

    private void queueCastleBuild(net.minecraft.core.BlockPos center) {
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        int radius = 7;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.abs(x) == radius || Math.abs(z) == radius || Math.abs(x) <= 2 && Math.abs(z) <= 2) {
                    queueBuild(center.offset(x, -1, z), stone);
                }
            }
        }

        for (int y = 0; y < 5; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean outerWall = Math.abs(x) == radius || Math.abs(z) == radius;
                    boolean gateOpening = z == radius && Math.abs(x) <= 1 && y < 3;
                    if (outerWall && !gateOpening) {
                        queueBuild(center.offset(x, y, z), stone);
                    }
                }
            }
        }

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                boolean battlement = (Math.abs(x) == radius || Math.abs(z) == radius) && (x + z) % 2 == 0;
                if (battlement) {
                    queueBuild(center.offset(x, 5, z), stone);
                }
            }
        }

        int[][] corners = new int[][] {
                {-radius, -radius}, {-radius, radius}, {radius, -radius}, {radius, radius}
        };
        for (int[] corner : corners) {
            queueCastleTower(center.offset(corner[0], 0, corner[1]), stone, planks);
        }

        BlockState stair = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.EAST)
                .setValue(StairBlock.HALF, Half.BOTTOM);
        for (int step = 0; step <= 5; step++) {
            queueBuild(center.offset(-6 + step, step, -5), stair);
        }
        for (int x = -1; x <= 2; x++) {
            queueBuild(center.offset(x, 5, -5), stone);
        }

        queueBuild(center.offset(0, 1, radius - 1), Blocks.TORCH.defaultBlockState());
        queueBuild(center.offset(-3, 1, 0), Blocks.TORCH.defaultBlockState());
        queueBuild(center.offset(3, 1, 0), Blocks.TORCH.defaultBlockState());
    }

    private void queueCastleTower(net.minecraft.core.BlockPos cornerCenter, BlockState stone, BlockState planks) {
        BlockState roofHatch = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, Direction.SOUTH)
                .setValue(TrapDoorBlock.HALF, Half.TOP)
                .setValue(TrapDoorBlock.OPEN, false);

        for (int y = 0; y < 8; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (Math.abs(x) == 1 || Math.abs(z) == 1) {
                        queueBuild(cornerCenter.offset(x, y, z), stone);
                    }
                }
            }
        }

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                queueBuild(cornerCenter.offset(x, 8, z), x == 0 && z == 0 ? roofHatch : planks);
            }
        }

        BlockState ladder = Blocks.LADDER.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
        for (int y = 0; y < 8; y++) {
            queueBuild(cornerCenter.offset(0, y, 0), ladder);
        }
    }

    private void queueBuildDoor(net.minecraft.core.BlockPos center, int frontZ) {
        BlockState doorLower = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                .setValue(DoorBlock.OPEN, false);
        BlockState doorUpper = doorLower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        queueBuild(center.offset(0, 0, frontZ), doorLower);
        queueBuild(center.offset(0, 1, frontZ), doorUpper);
    }

    private void queueBuild(net.minecraft.core.BlockPos pos, BlockState state) {
        buildQueue .add(new BuildPlacement(pos, state));
    }

    private net.minecraft.core.BlockPos findStandPositionNear(net.minecraft.core.BlockPos target) {
        net.minecraft.world.level.Level world = npc.level();
        net.minecraft.core.BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    net.minecraft.core.BlockPos candidate = target.offset(x, y, z);
                    if (!isSafeStandPosition(world, candidate)) continue;

                    double distance = npc .distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(candidate));
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }

        return best;
    }

    private net.minecraft.core.BlockPos findSafeStandAround(net.minecraft.core.BlockPos center, int horizontalRadius, int verticalRadius) {
        net.minecraft.world.level.Level world = npc.level();
        net.minecraft.core.BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int y = verticalRadius; y >= -verticalRadius; y--) {
            for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    net.minecraft.core.BlockPos candidate = center.offset(x, y, z);
                    if (!isSafeStandPosition(world, candidate)) continue;

                    double distance = candidate.distSqr(center);
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }

        return best;
    }

    private boolean isSafeStandPosition(net.minecraft.world.level.Level world, net.minecraft.core.BlockPos feet) {
        BlockState feetState = world.getBlockState(feet);
        BlockState headState = world.getBlockState(feet.above());
        BlockState groundState = world.getBlockState(feet.below());
        return feetState.getCollisionShape(world, feet).isEmpty()
                && headState.getCollisionShape(world, feet.above()).isEmpty()
                && !groundState.isAir()
                && groundState.getFluidState().isEmpty()
                && groundState.getDestroySpeed(world, feet.below()) >= 0;
    }

    private boolean isNearFenceOrGate(BlockPos center, int radius) {
        net.minecraft.world.level.Level world = npc.level();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockState(center.offset(x, y, z)).getBlock();
                    if (block instanceof FenceBlock || block instanceof FenceGateBlock) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void pickupNearbyItems() {
        if (!(npc.level() instanceof ServerLevel serverWorld)) return;

        List<ItemEntity> nearbyItems = serverWorld.getEntitiesOfClass(
                ItemEntity.class,
                npc.getBoundingBox() .inflate(2.5),
                item -> item.isAlive()
                        && !item.isRemoved()
                        && !item.hasPickUpDelay()
                        && npc .distanceToSqr(item) <= ITEM_PICKUP_RANGE_SQUARED
                        && !item.getItem().isEmpty());

        for (ItemEntity itemEntity : nearbyItems) {
            ItemStack stack = itemEntity.getItem();
            if (addToVirtualInventory(stack)) {
                npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                itemEntity.discard();

                String itemName = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                if (itemName.contains("diamond")) {
                    sendEventToAi("found_diamonds", "");
                }
            }
        }
    }

    private boolean addToVirtualInventory(ItemStack incoming) {
        if (incoming.isEmpty()) return false;

        String key = getInventoryKey(incoming);
        if (!virtualInventory.containsKey(key) && getInventorySlotsUsed() >= VIRTUAL_INVENTORY_SLOTS) {
            dropLeastUsefulInventoryItem();
        }

        if (!virtualInventory.containsKey(key) && getInventorySlotsUsed() >= VIRTUAL_INVENTORY_SLOTS) {
            return false;
        }

        ItemStack stored = virtualInventory.get(key);
        if (stored == null) {
            virtualInventory.put(key, incoming.copy());
        } else {
            stored.grow(incoming.getCount());
        }

        persistBrainState();
        return true;
    }

    private boolean seekFoodIfNeeded() {
        if (!(npc.level() instanceof ServerLevel serverWorld)) return false;
        if ("build".equals(currentMode) || "follow".equals(currentMode)) return false;
        if (npc.getHealth() >= npc.getMaxHealth() * FOOD_SEEK_HEALTH_RATIO) {
            clearFoodTargets();
            return false;
        }
        if (hasFoodInInventory()) {
            clearFoodTargets();
            return false;
        }

        foodSeekTicks++;

        if (isValidFoodItemTarget(currentFoodItemTarget)) {
            moveToFoodItem(currentFoodItemTarget);
            return true;
        }
        currentFoodItemTarget = findNearestFoodItem(serverWorld);
        if (currentFoodItemTarget != null) {
            announceFoodSearch("seeking dropped food");
            moveToFoodItem(currentFoodItemTarget);
            return true;
        }

        if (isValidFoodAnimalTarget(currentFoodAnimalTarget)) {
            moveToFoodAnimal(serverWorld, currentFoodAnimalTarget);
            return true;
        }
        currentFoodAnimalTarget = findNearestFoodAnimal(serverWorld);
        if (currentFoodAnimalTarget != null) {
            announceFoodSearch("hunting " + BuiltInRegistries.ENTITY_TYPE.getKey(currentFoodAnimalTarget.getType()).getPath());
            moveToFoodAnimal(serverWorld, currentFoodAnimalTarget);
            return true;
        }

        announceFoodSearch("no nearby food source");
        return false;
    }

    private void moveToFoodItem(ItemEntity item) {
        if (npc .distanceToSqr(item) <= ITEM_PICKUP_RANGE_SQUARED) {
            pickupNearbyItems();
            currentFoodItemTarget = null;
            return;
        }

        npc.getLookControl() .setLookAt(item.getX(), item.getY(), item.getZ());
        if (npc.getNavigation().isDone() || foodSeekTicks % FOOD_REPATH_TICKS == 0) {
            npc.getNavigation() .moveTo(item.getX(), item.getY(), item.getZ(), 1.15);
        }
    }

    private void moveToFoodAnimal(ServerLevel serverWorld, LivingEntity animal) {
        npc.getLookControl() .setLookAt(animal, 30.0F, 30.0F);
        if (npc .distanceToSqr(animal) <= 4.0) {
            equipSword();
            npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            npc.doHurtTarget(animal);
            if (!animal.isAlive() || animal.getHealth() <= 0.0F) {
                ItemStack food = createFoodStackForAnimal(animal);
                if (!food.isEmpty()) {
                    addToVirtualInventory(food);
                }
                currentFoodAnimalTarget = null;
            }
            return;
        }

        if (npc.getNavigation().isDone() || foodSeekTicks % FOOD_REPATH_TICKS == 0) {
            npc.getNavigation() .moveTo(animal, 1.2);
        }
    }

    private ItemEntity findNearestFoodItem(ServerLevel serverWorld) {
        List<ItemEntity> items = serverWorld.getEntitiesOfClass(
                ItemEntity.class,
                npc.getBoundingBox() .inflate(FOOD_SEARCH_RANGE),
                this::isValidFoodItemTarget
        );

        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemEntity item : items) {
            double distance = npc .distanceToSqr(item);
            if (distance < bestDistance) {
                best = item;
                bestDistance = distance;
            }
        }
        return best;
    }

    private LivingEntity findNearestFoodAnimal(ServerLevel serverWorld) {
        List<Animal> animals = serverWorld.getEntitiesOfClass(
                Animal.class,
                npc.getBoundingBox() .inflate(FOOD_SEARCH_RANGE),
                this::isValidFoodAnimalTarget
        );

        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Animal animal : animals) {
            double distance = npc .distanceToSqr(animal);
            if (distance < bestDistance) {
                best = animal;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean isValidFoodItemTarget(ItemEntity item) {
        return item != null
                && item.isAlive()
                && !item.isRemoved()
                && !item.hasPickUpDelay()
                && item.getItem().has(net.minecraft.core.component.DataComponents.FOOD);
    }

    private boolean isValidFoodAnimalTarget(LivingEntity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved() || entity == npc) {
            return false;
        }

        String name = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        return "cow".equals(name)
                || "pig".equals(name)
                || "chicken".equals(name)
                || "sheep".equals(name)
                || "rabbit".equals(name)
                || "mooshroom".equals(name);
    }

    private ItemStack createFoodStackForAnimal(LivingEntity animal) {
        String name = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).getPath();
        return switch (name) {
            case "cow", "mooshroom" -> new ItemStack(Items.COOKED_BEEF, 2);
            case "pig" -> new ItemStack(Items.COOKED_PORKCHOP, 2);
            case "chicken" -> new ItemStack(Items.COOKED_CHICKEN, 1);
            case "sheep" -> new ItemStack(Items.COOKED_MUTTON, 2);
            case "rabbit" -> new ItemStack(Items.COOKED_RABBIT, 1);
            default -> ItemStack.EMPTY;
        };
    }

    private boolean hasFoodInInventory() {
        for (ItemStack stack : virtualInventory.values()) {
            if (stack.has(net.minecraft.core.component.DataComponents.FOOD)) {
                return true;
            }
        }
        return false;
    }

    private void clearFoodTargets() {
        currentFoodItemTarget = null;
        currentFoodAnimalTarget = null;
        foodSeekTicks = 0;
    }

    private void announceFoodSearch(String detail) {
        if (foodNeedEventCooldownTicks > 0) return;
        sendEventToAi("seeking_food", detail);
        foodNeedEventCooldownTicks = FOOD_NEED_EVENT_COOLDOWN_TICKS;
    }

    private void eatIfNeeded() {
        if (foodCooldownTicks > 0) return;
        if (npc.getHealth() >= npc.getMaxHealth() * 0.65F) return;

        String bestFoodKey = null;
        int bestNutrition = 0;
        for (Map.Entry<String, ItemStack> entry : virtualInventory.entrySet()) {
            FoodProperties food = entry.getValue().get(net.minecraft.core.component.DataComponents.FOOD);
            if (food != null && food.nutrition() > bestNutrition) {
                bestFoodKey = entry.getKey();
                bestNutrition = food.nutrition();
            }
        }

        if (bestFoodKey == null) {
            return;
        }

        ItemStack foodStack = virtualInventory.get(bestFoodKey);
        foodStack.shrink(1);
        if (foodStack.isEmpty()) {
            virtualInventory.remove(bestFoodKey);
        }

        npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        npc.heal(Math.max(2.0F, bestNutrition * 1.2F));
        clearFoodTargets();
        foodCooldownTicks = FOOD_COOLDOWN_TICKS;
        persistBrainState();
    }

    private boolean doFarmerIdleLogic() {
        if (!"farmer".equals(npc.getAppearanceVariantName())) {
            clearCropFarmerTargets();
            return false;
        }
        if (!(npc.level() instanceof ServerLevel serverWorld)) return false;

        BlockPos home = npc.getHomePosition();
        if (home != null && npc .distanceToSqr(Vec3.atCenterOf(home)) > FARMER_RETURN_HOME_DISTANCE_SQUARED) {
            moveNear(home, 1.0);
            return true;
        }

        if (currentFarmCropTarget != null && handleFarmCropTarget(serverWorld)) return true;
        if (currentFarmPlantTarget != null && handleFarmPlantTarget(serverWorld)) return true;
        if (currentFarmTillTarget != null && handleFarmTillTarget(serverWorld)) return true;
        if (currentFarmChestTarget != null && handleFarmChestTarget(serverWorld)) return true;

        farmerScanTicks++;
        if (farmerScanTicks < FARMER_SCAN_INTERVAL_TICKS) return false;
        farmerScanTicks = 0;
        farmerGateCheckTicks += FARMER_SCAN_INTERVAL_TICKS;
        farmerFarmAnchor = findNearbyFarmAnchor(serverWorld);
        if (farmerGateCheckTicks >= FARMER_GATE_CHECK_TICKS) {
            farmerGateCheckTicks = 0;
            closeNearbyFarmGates(serverWorld);
        }
        if (!hasAnyPlantingItem() && tryWithdrawPlantingItemsFromNearbyChest(serverWorld)) {
            return true;
        }
        if (!hasAnyPlantingItem()) {
            ensureFarmerStarterSeeds();
        }

        // If no farm found nearby, attempt to create one
        if (farmerFarmAnchor == null && npc.getRandom().nextFloat() < 0.15f) {
            BlockPos farmCenter = findFarmCreationSpot(serverWorld);
            if (farmCenter != null) {
                startFarmCreation(serverWorld, farmCenter);
                return true;
            }
        }

        // Deliver a useful batch before starting more harvesting or expansion.
        // This keeps active farms from starving the storage step indefinitely.
        if (getMovableFarmGoodsCount() >= FARMER_STORAGE_DELIVERY_MIN_ITEMS) {
            if (beginFarmStorageRun(serverWorld)) {
                return true;
            }
        }

        currentFarmCropTarget = findMatureCropNear(serverWorld);
        if (currentFarmCropTarget != null) {
            moveNear(currentFarmCropTarget, 1.0);
            return true;
        }

        currentFarmPlantTarget = findPlantableFarmlandNear(serverWorld);
        if (currentFarmPlantTarget != null) {
            moveNear(currentFarmPlantTarget, 1.0);
            return true;
        }

        currentFarmTillTarget = nextFarmExpansionTarget(serverWorld);
        if (currentFarmTillTarget == null) {
            currentFarmTillTarget = findTillableFarmGroundNear(serverWorld);
        }
        if (currentFarmTillTarget != null) {
            moveNear(currentFarmTillTarget.above(), 1.0);
            return true;
        }

        if (hasFarmGoodsForChest() && beginFarmStorageRun(serverWorld)) {
            return true;
        }

        if (farmerFarmAnchor != null) {
            if (npc .distanceToSqr(Vec3.atCenterOf(farmerFarmAnchor)) > FARMER_FARM_IDLE_DISTANCE_SQUARED) {
                moveNear(farmerFarmAnchor, 1.0);
            }
            return true;
        }

        return false;
    }

    private boolean doRancherIdleLogic() {
        if (!"rancher".equals(npc.getAppearanceVariantName())) {
            clearRancherTargets();
            return false;
        }
        if (!(npc.level() instanceof ServerLevel serverWorld)) return false;

        BlockPos home = npc.getHomePosition();
        if (home != null && npc .distanceToSqr(Vec3.atCenterOf(home)) > FARMER_RETURN_HOME_DISTANCE_SQUARED) {
            moveNear(home, 1.0);
            return true;
        }

        if (currentFarmAnimalTarget != null && handleFarmAnimalTarget(serverWorld)) return true;
        if (currentFarmCullTarget != null && handleFarmCullTarget(serverWorld)) return true;

        farmerScanTicks++;
        if (farmerScanTicks < FARMER_SCAN_INTERVAL_TICKS) return false;
        farmerScanTicks = 0;
        farmerGateCheckTicks += FARMER_SCAN_INTERVAL_TICKS;
        if (farmerGateCheckTicks >= FARMER_GATE_CHECK_TICKS) {
            farmerGateCheckTicks = 0;
            closeNearbyFarmGates(serverWorld);
        }

        if (!hasAnyAnimalFeedItem()) {
            ensureRancherStarterFeed();
        }

        if (farmerFeedCooldownTicks <= 0) {
            currentFarmAnimalTarget = findFeedableAnimal(serverWorld);
            if (currentFarmAnimalTarget != null) {
                npc.getNavigation() .moveTo(currentFarmAnimalTarget, 1.0);
                return true;
            }
        }

        if (farmerCullCooldownTicks <= 0) {
            currentFarmCullTarget = findCullableFarmAnimal(serverWorld);
            if (currentFarmCullTarget != null) {
                npc.getNavigation() .moveTo(currentFarmCullTarget, 1.0);
                return true;
            }
        }

        return false;
    }

    private boolean handleFarmCropTarget(ServerLevel world) {
        if (currentFarmCropTarget == null) return false;

        BlockState state = world.getBlockState(currentFarmCropTarget);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
            currentFarmCropTarget = null;
            return false;
        }

        if (npc .distanceToSqr(Vec3.atCenterOf(currentFarmCropTarget)) > FARMER_WORK_DISTANCE_SQUARED) {
            moveNear(currentFarmCropTarget, 1.0);
            return true;
        }

        npc.getLookControl() .setLookAt(
                currentFarmCropTarget.getX() + 0.5,
                currentFarmCropTarget.getY() + 0.5,
                currentFarmCropTarget.getZ() + 0.5);
        npc .setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
        npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);

        for (ItemStack drop : createCropHarvest(state)) {
            addToVirtualInventory(drop);
        }
        world.setBlock(currentFarmCropTarget, crop.getStateForAge(0), Block.UPDATE_ALL);
        currentFarmCropTarget = null;
        wakeFarmerScan();
        restoreDefaultMainHand();
        return true;
    }

    private boolean beginFarmStorageRun(ServerLevel world) {
        currentFarmChestTarget = findNearbyFarmChest(world);
        if (currentFarmChestTarget != null) {
            moveNear(currentFarmChestTarget, 1.05);
            return true;
        }

        if (farmerFarmAnchor != null && chestCreationCooldownTicks <= 0) {
            BlockPos storageSpot = findFarmChestCreationSpot(world);
            if (storageSpot != null) {
                startChestCreation(world, storageSpot);
                currentFarmChestTarget = storageSpot;
                return true;
            }
        }
        return false;
    }

    private List<ItemStack> createCropHarvest(BlockState state) {
        List<ItemStack> drops = new ArrayList<>();
        int bonus = 1 + npc.getRandom().nextInt(3);

        if (state.is(Blocks.WHEAT)) {
            drops .add(new ItemStack(Items.WHEAT, 1));
            drops .add(new ItemStack(Items.WHEAT_SEEDS, bonus));
        } else if (state.is(Blocks.CARROTS)) {
            drops .add(new ItemStack(Items.CARROT, 1 + bonus));
        } else if (state.is(Blocks.POTATOES)) {
            drops .add(new ItemStack(Items.POTATO, 1 + bonus));
        } else if (state.is(Blocks.BEETROOTS)) {
            drops .add(new ItemStack(Items.BEETROOT, 1));
            drops .add(new ItemStack(Items.BEETROOT_SEEDS, Math.max(1, bonus - 1)));
        }

        return drops;
    }

    private boolean handleFarmPlantTarget(ServerLevel world) {
        if (currentFarmPlantTarget == null) return false;
        if (!world.getBlockState(currentFarmPlantTarget).isAir()
                || !world.getBlockState(currentFarmPlantTarget.below()).is(Blocks.FARMLAND)) {
            currentFarmPlantTarget = null;
            return false;
        }

        BlockState cropState = chooseCropToPlant();
        if (cropState == null) {
            currentFarmPlantTarget = null;
            return false;
        }

        if (npc .distanceToSqr(Vec3.atCenterOf(currentFarmPlantTarget)) > FARMER_WORK_DISTANCE_SQUARED) {
            moveNear(currentFarmPlantTarget, 1.0);
            return true;
        }

        Item seedItem = getPlantingItem(cropState);
        if (seedItem == null || !removeOneVirtualItem(seedItem)) {
            currentFarmPlantTarget = null;
            return false;
        }

        npc.getLookControl() .setLookAt(
                currentFarmPlantTarget.getX() + 0.5,
                currentFarmPlantTarget.getY() + 0.5,
                currentFarmPlantTarget.getZ() + 0.5);
        npc .setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(seedItem));
        npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        world.setBlock(currentFarmPlantTarget, cropState, Block.UPDATE_ALL);
        currentFarmPlantTarget = null;
        wakeFarmerScan();
        restoreDefaultMainHand();
        return true;
    }

    private boolean handleFarmTillTarget(ServerLevel world) {
        if (currentFarmTillTarget == null) return false;
        BlockState ground = world.getBlockState(currentFarmTillTarget);
        BlockPos cropPos = currentFarmTillTarget.above();

        if (isProtectedWorldBlock(world, currentFarmTillTarget, ground) || isLikelyPlayerBuiltBlock(ground)) {
            currentFarmTillTarget = null;
            return false;
        }

        if (!world.getBlockState(cropPos).isAir()) {
            currentFarmTillTarget = null;
            return false;
        }

        if (!ground.is(Blocks.FARMLAND) && !canTillFarmGround(ground)) {
            currentFarmTillTarget = null;
            return false;
        }

        BlockState cropState = chooseCropToPlant();
        if (cropState == null) {
            currentFarmTillTarget = null;
            return false;
        }

        if (npc .distanceToSqr(Vec3.atCenterOf(cropPos)) > FARMER_WORK_DISTANCE_SQUARED) {
            moveNear(cropPos, 1.0);
            return true;
        }

        Item seedItem = getPlantingItem(cropState);
        if (seedItem == null || !removeOneVirtualItem(seedItem)) {
            currentFarmTillTarget = null;
            return false;
        }

        npc.getLookControl() .setLookAt(
                currentFarmTillTarget.getX() + 0.5,
                currentFarmTillTarget.getY() + 0.5,
                currentFarmTillTarget.getZ() + 0.5);
        npc .setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
        npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (!ground.is(Blocks.FARMLAND)) {
            world.setBlock(currentFarmTillTarget, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
        }
        world.setBlock(cropPos, cropState, Block.UPDATE_ALL);
        farmerFarmAnchor = currentFarmTillTarget;
        currentFarmTillTarget = null;
        wakeFarmerScan();
        restoreDefaultMainHand();
        return true;
    }

    private BlockState chooseCropToPlant() {
        if (hasVirtualItem(Items.WHEAT_SEEDS)) return Blocks.WHEAT.defaultBlockState();
        if (hasVirtualItem(Items.CARROT)) return Blocks.CARROTS.defaultBlockState();
        if (hasVirtualItem(Items.POTATO)) return Blocks.POTATOES.defaultBlockState();
        if (hasVirtualItem(Items.BEETROOT_SEEDS)) return Blocks.BEETROOTS.defaultBlockState();
        return null;
    }

    private Item getPlantingItem(BlockState cropState) {
        if (cropState.is(Blocks.WHEAT)) return Items.WHEAT_SEEDS;
        if (cropState.is(Blocks.CARROTS)) return Items.CARROT;
        if (cropState.is(Blocks.POTATOES)) return Items.POTATO;
        if (cropState.is(Blocks.BEETROOTS)) return Items.BEETROOT_SEEDS;
        return null;
    }

    private BlockPos findMatureCropNear(ServerLevel world) {
        BlockPos origin = npc.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -FARMER_SCAN_RADIUS; x <= FARMER_SCAN_RADIUS; x++) {
            for (int y = -FARMER_VERTICAL_SCAN_RADIUS; y <= FARMER_VERTICAL_SCAN_RADIUS; y++) {
                for (int z = -FARMER_SCAN_RADIUS; z <= FARMER_SCAN_RADIUS; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) continue;

                    double distance = pos.distSqr(origin);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }

        return best;
    }

    private BlockPos findPlantableFarmlandNear(ServerLevel world) {
        if (chooseCropToPlant() == null) return null;

        BlockPos origin = npc.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -FARMER_SCAN_RADIUS; x <= FARMER_SCAN_RADIUS; x++) {
            for (int y = -FARMER_VERTICAL_SCAN_RADIUS; y <= FARMER_VERTICAL_SCAN_RADIUS; y++) {
                for (int z = -FARMER_SCAN_RADIUS; z <= FARMER_SCAN_RADIUS; z++) {
                    BlockPos farmland = origin.offset(x, y, z);
                    BlockPos cropPos = farmland.above();
                    if (!world.getBlockState(farmland).is(Blocks.FARMLAND)
                            || !world.getBlockState(cropPos).isAir()) {
                        continue;
                    }

                    double distance = cropPos.distSqr(origin);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cropPos;
                    }
                }
            }
        }

        return best;
    }

    private BlockPos findNearbyFarmAnchor(ServerLevel world) {
        BlockPos origin = npc.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -FARMER_SCAN_RADIUS; x <= FARMER_SCAN_RADIUS; x++) {
            for (int y = -FARMER_VERTICAL_SCAN_RADIUS; y <= FARMER_VERTICAL_SCAN_RADIUS; y++) {
                for (int z = -FARMER_SCAN_RADIUS; z <= FARMER_SCAN_RADIUS; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (!state.is(Blocks.FARMLAND) && !(state.getBlock() instanceof CropBlock)) {
                        continue;
                    }

                    double distance = pos.distSqr(origin);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }

        return best;
    }

    private BlockPos findTillableFarmGroundNear(ServerLevel world) {
        if (chooseCropToPlant() == null) return null;

        BlockPos origin = farmerFarmAnchor != null ? farmerFarmAnchor : npc.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -FARMER_SCAN_RADIUS; x <= FARMER_SCAN_RADIUS; x++) {
            for (int y = -FARMER_VERTICAL_SCAN_RADIUS; y <= FARMER_VERTICAL_SCAN_RADIUS; y++) {
                for (int z = -FARMER_SCAN_RADIUS; z <= FARMER_SCAN_RADIUS; z++) {
                    BlockPos ground = origin.offset(x, y, z);
                    BlockPos cropPos = ground.above();
                    BlockState groundState = world.getBlockState(ground);
                    if (!world.getBlockState(cropPos).isAir()) continue;
                    if (isProtectedWorldBlock(world, ground, groundState) || isLikelyPlayerBuiltBlock(groundState)) continue;
                    if (!groundState.is(Blocks.FARMLAND) && !canTillFarmGround(groundState)) continue;
                    if (!hasWaterWithinFarmRange(world, ground)) continue;
                    if (findStandPositionNear(cropPos) == null) continue;

                    double distance = ground.distSqr(npc.blockPosition());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = ground;
                    }
                }
            }
        }

        return best;
    }

    private BlockPos nextFarmExpansionTarget(ServerLevel world) {
        while (!farmExpansionQueue.isEmpty()) {
            BlockPos target = farmExpansionQueue.poll();
            BlockState groundState = world.getBlockState(target);
            if (canTillFarmGround(groundState)
                    && world.getBlockState(target.above()).isAir()
                    && hasWaterWithinFarmRange(world, target)
                    && findStandPositionNear(target.above()) != null
                    && !isProtectedWorldBlock(world, target, groundState)
                    && !isLikelyPlayerBuiltBlock(groundState)) {
                return target;
            }
        }
        return null;
    }

    private boolean hasWaterWithinFarmRange(ServerLevel world, BlockPos ground) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = -1; y <= 1; y++) {
                    if (world.getFluidState(ground.offset(x, y, z)).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean canTillFarmGround(BlockState state) {
        return state.is(Blocks.DIRT)
                || state.is(Blocks.GRASS_BLOCK);
    }

    private void closeNearbyFarmGates(ServerLevel world) {
        BlockPos origin = farmerFarmAnchor != null ? farmerFarmAnchor : npc.blockPosition();
        for (int x = -FARMER_SCAN_RADIUS; x <= FARMER_SCAN_RADIUS; x++) {
            for (int y = -FARMER_VERTICAL_SCAN_RADIUS; y <= FARMER_VERTICAL_SCAN_RADIUS; y++) {
                for (int z = -FARMER_SCAN_RADIUS; z <= FARMER_SCAN_RADIUS; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN)) {
                        if (!isEntityInPassage(pos)) {
                            world.setBlock(pos, state.setValue(FenceGateBlock.OPEN, false), Block.UPDATE_ALL);
                        }
                    }
                }
            }
        }
    }

    private boolean handleFarmChestTarget(ServerLevel world) {
        if (currentFarmChestTarget == null) return false;
        if (!isFarmStorageBlock(world.getBlockState(currentFarmChestTarget))
                || !(world.getBlockEntity(currentFarmChestTarget) instanceof Container inventory)) {
            currentFarmChestTarget = null;
            return false;
        }

        if (npc .distanceToSqr(Vec3.atCenterOf(currentFarmChestTarget)) > FARMER_WORK_DISTANCE_SQUARED) {
            moveNear(currentFarmChestTarget, 1.0);
            return true;
        }

        npc.getLookControl() .setLookAt(
                currentFarmChestTarget.getX() + 0.5,
                currentFarmChestTarget.getY() + 0.5,
                currentFarmChestTarget.getZ() + 0.5);
        boolean withdrew = !hasAnyPlantingItem() && withdrawPlantingItemsFromInventory(inventory);
        boolean deposited = depositFarmGoodsInChest(inventory);
        if (withdrew || deposited) {
            npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            persistBrainState();
            if (deposited) {
                sendEventToAi("storage_stocked", "farm goods in storage");
            }
        }
        currentFarmChestTarget = null;
        return withdrew || deposited;
    }

    private BlockPos findNearbyFarmChest(ServerLevel world) {
        if (farmerStorageChest != null
                && isFarmStorageBlock(world.getBlockState(farmerStorageChest))
                && world.getBlockEntity(farmerStorageChest) instanceof Container) {
            return farmerStorageChest;
        }

        BlockPos origin = farmerFarmAnchor != null ? farmerFarmAnchor : npc.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -FARMER_SCAN_RADIUS; x <= FARMER_SCAN_RADIUS; x++) {
            for (int y = -FARMER_VERTICAL_SCAN_RADIUS; y <= FARMER_VERTICAL_SCAN_RADIUS; y++) {
                for (int z = -FARMER_SCAN_RADIUS; z <= FARMER_SCAN_RADIUS; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!isFarmStorageBlock(world.getBlockState(pos))
                            || !(world.getBlockEntity(pos) instanceof Container)) {
                        continue;
                    }

                    double distance = pos.distSqr(origin);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos.immutable();
                    }
                }
            }
        }

        farmerStorageChest = best;
        return best;
    }

    private boolean tryWithdrawPlantingItemsFromNearbyChest(ServerLevel world) {
        BlockPos chest = findNearbyFarmChest(world);
        if (chest == null) return false;
        if (!(world.getBlockEntity(chest) instanceof Container inventory)
                || !inventoryHasPlantingItems(inventory)) {
            return false;
        }

        currentFarmChestTarget = chest;
        moveNear(chest, 1.0);
        return true;
    }

    private boolean inventoryHasPlantingItems(Container inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && isPlantingItem(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    private boolean isFarmStorageBlock(BlockState state) {
        return state.is(Blocks.CHEST)
                || state.is(Blocks.TRAPPED_CHEST)
                || state.is(Blocks.BARREL);
    }

    private boolean hasFarmGoodsForChest() {
        for (ItemStack stack : virtualInventory.values()) {
            if (isFarmInventoryItem(stack) && stack.getCount() > getFarmStorageReserve(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    private int getMovableFarmGoodsCount() {
        int movable = 0;
        for (ItemStack stack : virtualInventory.values()) {
            if (!isFarmInventoryItem(stack)) continue;
            movable += Math.max(0, stack.getCount() - getFarmStorageReserve(stack.getItem()));
        }
        return movable;
    }

    private boolean withdrawPlantingItemsFromInventory(Container inventory) {
        boolean withdrew = false;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !isPlantingItem(stack.getItem())) continue;

            int move = Math.min(stack.getCount(), 16);
            addToVirtualInventory(stack.copyWithCount(move));
            stack.shrink(move);
            inventory.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
            withdrew = true;

            if (hasAnyPlantingItem()) {
                break;
            }
        }

        if (withdrew) {
            inventory.setChanged();
        }
        return withdrew;
    }

    private boolean depositFarmGoodsInChest(Container inventory) {
        boolean deposited = false;

        for (String key : new ArrayList<>(virtualInventory.keySet())) {
            ItemStack stored = virtualInventory.get(key);
            if (stored == null || stored.isEmpty() || !isFarmInventoryItem(stored)) continue;

            int reserve = getFarmStorageReserve(stored.getItem());
            int movable = stored.getCount() - reserve;
            if (movable <= 0) continue;

            ItemStack moving = stored.copyWithCount(movable);
            if (insertStackIntoInventory(inventory, moving)) {
                int moved = movable - moving.getCount();
                if (moved > 0) {
                    stored.shrink(moved);
                    if (stored.isEmpty()) {
                        virtualInventory.remove(key);
                    }
                    deposited = true;
                }
            }
        }

        return deposited;
    }

    private boolean insertStackIntoInventory(Container inventory, ItemStack source) {
        boolean changed = false;

        for (int slot = 0; slot < inventory.getContainerSize() && !source.isEmpty(); slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty() || !ItemStack.isSameItem(existing, source)) continue;

            int maxCount = Math.min(existing.getMaxStackSize(), inventory.getMaxStackSize());
            int move = Math.min(source.getCount(), maxCount - existing.getCount());
            if (move <= 0) continue;

            existing.grow(move);
            source.shrink(move);
            inventory.setItem(slot, existing);
            changed = true;
        }

        for (int slot = 0; slot < inventory.getContainerSize() && !source.isEmpty(); slot++) {
            if (!inventory.getItem(slot).isEmpty()) continue;

            int move = Math.min(source.getCount(), Math.min(source.getMaxStackSize(), inventory.getMaxStackSize()));
            inventory.setItem(slot, source.copyWithCount(move));
            source.shrink(move);
            changed = true;
        }

        if (changed) {
            inventory.setChanged();
        }
        return changed;
    }

    private int getFarmStorageReserve(Item item) {
        if (item == Items.WHEAT || item == Items.CARROT || item == Items.WHEAT_SEEDS) return 8;
        if (item == Items.POTATO || item == Items.BEETROOT_SEEDS) return 4;
        return 0;
    }

    private boolean hasAnyPlantingItem() {
        return hasVirtualItem(Items.WHEAT_SEEDS)
                || hasVirtualItem(Items.CARROT)
                || hasVirtualItem(Items.POTATO)
                || hasVirtualItem(Items.BEETROOT_SEEDS);
    }

    private boolean isPlantingItem(Item item) {
        return item == Items.WHEAT_SEEDS
                || item == Items.CARROT
                || item == Items.POTATO
                || item == Items.BEETROOT_SEEDS;
    }

    private boolean isFarmInventoryItem(ItemStack stack) {
        return stack.is(Items.WHEAT)
                || stack.is(Items.WHEAT_SEEDS)
                || stack.is(Items.CARROT)
                || stack.is(Items.POTATO)
                || stack.is(Items.BEETROOT)
                || stack.is(Items.BEETROOT_SEEDS);
    }

    private boolean handleFarmAnimalTarget(ServerLevel world) {
        if (currentFarmAnimalTarget == null) return false;
        if (!isValidFeedableAnimal(currentFarmAnimalTarget)
                || isNearFenceOrGate(currentFarmAnimalTarget.blockPosition(), FENCE_AVOIDANCE_RADIUS)) {
            currentFarmAnimalTarget = null;
            return false;
        }

        if (npc .distanceToSqr(currentFarmAnimalTarget) > FARMER_WORK_DISTANCE_SQUARED) {
            npc.getLookControl() .setLookAt(currentFarmAnimalTarget, 30.0F, 30.0F);
            if (npc.getNavigation().isDone() || tickCounter % 20 == 0) {
                npc.getNavigation() .moveTo(currentFarmAnimalTarget, 1.0);
            }
            return true;
        }

        Item feed = getFeedForAnimal(currentFarmAnimalTarget);
        if (feed == null || !removeOneVirtualItem(feed)) {
            currentFarmAnimalTarget = null;
            return false;
        }

        npc.getLookControl() .setLookAt(currentFarmAnimalTarget, 30.0F, 30.0F);
        npc .setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(feed));
        npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        currentFarmAnimalTarget.setInLoveTime(600);
        farmerFeedCooldownTicks = FARMER_FEED_COOLDOWN_TICKS;
        currentFarmAnimalTarget = null;
        restoreDefaultMainHand();
        return true;
    }

    private boolean handleFarmCullTarget(ServerLevel world) {
        if (currentFarmCullTarget == null) return false;
        if (!currentFarmCullTarget.isAlive()
                || currentFarmCullTarget.isRemoved()
                || isNearFenceOrGate(currentFarmCullTarget.blockPosition(), FENCE_AVOIDANCE_RADIUS)) {
            currentFarmCullTarget = null;
            return false;
        }

        if (npc .distanceToSqr(currentFarmCullTarget) > FARMER_WORK_DISTANCE_SQUARED) {
            npc.getLookControl() .setLookAt(currentFarmCullTarget, 30.0F, 30.0F);
            if (npc.getNavigation().isDone() || tickCounter % 20 == 0) {
                npc.getNavigation() .moveTo(currentFarmCullTarget, 1.0);
            }
            return true;
        }

        equipSword();
        npc.getLookControl() .setLookAt(currentFarmCullTarget, 30.0F, 30.0F);
        npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        npc.doHurtTarget(currentFarmCullTarget);
        if (!currentFarmCullTarget.isAlive() || currentFarmCullTarget.getHealth() <= 0.0F) {
            ItemStack food = createFoodStackForAnimal(currentFarmCullTarget);
            if (!food.isEmpty()) {
                addToVirtualInventory(food);
            }
            addSecondaryAnimalProducts(currentFarmCullTarget);
            currentFarmCullTarget = null;
            farmerCullCooldownTicks = FARMER_CULL_COOLDOWN_TICKS;
            restoreDefaultMainHand();
        }
        return true;
    }

    private void addSecondaryAnimalProducts(Animal animal) {
        String typeName = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).getPath();
        switch (typeName) {
            case "cow", "mooshroom" -> addToVirtualInventory(new ItemStack(Items.LEATHER, 1));
            case "sheep" -> addToVirtualInventory(new ItemStack(Items.WHITE_WOOL, 1));
            case "chicken" -> addToVirtualInventory(new ItemStack(Items.FEATHER, 1));
            default -> {
            }
        }
    }

    private Animal findFeedableAnimal(ServerLevel world) {
        List<Animal> animals = world.getEntitiesOfClass(
                Animal.class,
                npc.getBoundingBox() .inflate(FARMER_SCAN_RADIUS),
                this::isValidFeedableAnimal);

        Animal best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Animal animal : animals) {
            if (isNearFenceOrGate(animal.blockPosition(), FENCE_AVOIDANCE_RADIUS)) continue;
            double distance = npc .distanceToSqr(animal);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = animal;
            }
        }
        return best;
    }

    private Animal findCullableFarmAnimal(ServerLevel world) {
        List<Animal> animals = world.getEntitiesOfClass(
                Animal.class,
                npc.getBoundingBox() .inflate(FARMER_SCAN_RADIUS),
                animal -> animal.isAlive()
                        && !animal.isRemoved()
                        && getFeedForAnimal(animal) != null
                        && !animal.isBaby());

        Map<String, Integer> counts = new HashMap<>();
        for (Animal animal : animals) {
            String typeName = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).getPath();
            counts.put(typeName, counts.getOrDefault(typeName, 0) + 1);
        }

        Animal best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Animal animal : animals) {
            String typeName = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).getPath();
            if (counts.getOrDefault(typeName, 0) < FARMER_MIN_ANIMALS_BEFORE_CULL) continue;
            if (isNearFenceOrGate(animal.blockPosition(), FENCE_AVOIDANCE_RADIUS)) continue;

            double distance = npc .distanceToSqr(animal);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = animal;
            }
        }
        return best;
    }

    private boolean isValidFeedableAnimal(Animal animal) {
        if (animal == null || !animal.isAlive() || animal.isRemoved() || !animal.canFallInLove()) return false;
        Item feed = getFeedForAnimal(animal);
        return feed != null
                && hasVirtualItem(feed)
                && animal.isFood(new ItemStack(feed));
    }

    private Item getFeedForAnimal(Animal animal) {
        String typeName = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).getPath();
        return switch (typeName) {
            case "cow", "sheep", "mooshroom" -> Items.WHEAT;
            case "pig" -> Items.CARROT;
            case "chicken" -> Items.WHEAT_SEEDS;
            default -> null;
        };
    }

    private boolean hasAnyAnimalFeedItem() {
        return hasVirtualItem(Items.WHEAT)
                || hasVirtualItem(Items.CARROT)
                || hasVirtualItem(Items.WHEAT_SEEDS);
    }

    private void ensureRancherStarterFeed() {
        addToVirtualInventory(new ItemStack(Items.WHEAT, 16));
        addToVirtualInventory(new ItemStack(Items.CARROT, 8));
        addToVirtualInventory(new ItemStack(Items.WHEAT_SEEDS, 8));
    }

    private void ensureFarmerStarterSeeds() {
        if (hasVirtualItem(Items.WHEAT_SEEDS)
                || hasVirtualItem(Items.CARROT)
                || hasVirtualItem(Items.POTATO)
                || hasVirtualItem(Items.BEETROOT_SEEDS)) {
            return;
        }

        addToVirtualInventory(new ItemStack(Items.WHEAT_SEEDS, FARMER_STARTER_SEEDS));
    }

    private boolean hasVirtualItem(Item item) {
        ItemStack stack = virtualInventory.get(getInventoryKey(item));
        if (stack != null && !stack.isEmpty()) return true;

        ItemStack mainHand = npc .getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack offHand = npc .getItemBySlot(EquipmentSlot.OFFHAND);
        return mainHand.is(item) && !mainHand.isEmpty()
                || offHand.is(item) && !offHand.isEmpty();
    }

    private boolean removeOneVirtualItem(Item item) {
        String key = getInventoryKey(item);
        ItemStack stack = virtualInventory.get(key);
        if (stack == null || stack.isEmpty()) {
            return removeOneEquippedItem(item);
        }

        stack.shrink(1);
        if (stack.isEmpty()) {
            virtualInventory.remove(key);
        }
        persistBrainState();
        return true;
    }

    private boolean removeOneEquippedItem(Item item) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.OFFHAND, EquipmentSlot.MAINHAND}) {
            ItemStack stack = npc .getItemBySlot(slot);
            if (!stack.is(item) || stack.isEmpty()) continue;

            stack.shrink(1);
            npc .setItemSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
            return true;
        }
        return false;
    }

    private String getInventoryKey(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private void clearFarmerTargets() {
        clearCropFarmerTargets();
        clearRancherTargets();
    }

    private void clearCropFarmerTargets() {
        currentFarmCropTarget = null;
        currentFarmPlantTarget = null;
        currentFarmTillTarget = null;
        farmerFarmAnchor = null;
        farmExpansionQueue.clear();
        currentFarmChestTarget = null;
        farmerScanTicks = 0;
    }

    private void clearRancherTargets() {
        currentFarmAnimalTarget = null;
        currentFarmCullTarget = null;
        farmerScanTicks = 0;
    }

    private void wakeFarmerScan() {
        farmerScanTicks = FARMER_SCAN_INTERVAL_TICKS;
    }

    private BlockPos findFarmCreationSpot(ServerLevel world) {
        BlockPos origin = npc.blockPosition();
        // Search for a flat, open area near the farmer to create a new farm
        for (int radius = 10; radius <= 40; radius += 10) {
            for (int x = -radius; x <= radius; x += radius) {
                for (int z = -radius; z <= radius; z += radius) {
                    BlockPos candidate = origin.offset(x, 0, z);
                    if (isSuitableForFarmCreation(world, candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean isSuitableForFarmCreation(ServerLevel world, BlockPos pos) {
        // Check if area is mostly grass/dirt and relatively flat
        int grassCount = 0;
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                BlockPos ground = pos.offset(x, 0, z);
                BlockState state = world.getBlockState(ground);
                if (isProtectedWorldBlock(world, ground, state) || isLikelyPlayerBuiltBlock(state)) {
                    return false;
                }
                if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)) {
                    grassCount++;
                }
            }
        }
        // Need at least 70% grass/dirt in the area
        return grassCount >= 85 && hasWaterWithinFarmRange(world, pos);
    }

    private void startFarmCreation(ServerLevel world, BlockPos center) {
        sendEventToAi("farm_creation", "creating new farm at " + center.getX() + ", " + center.getZ());
        npc.getNavigation() .moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 1.0);
        farmerFarmAnchor = center;
        farmExpansionQueue.clear();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                BlockPos tilePos = center.offset(x, 0, z);
                if (hasWaterWithinFarmRange(world, tilePos)
                        && canTillFarmGround(world.getBlockState(tilePos))
                        && !isProtectedWorldBlock(world, tilePos, world.getBlockState(tilePos))
                        && !isLikelyPlayerBuiltBlock(world.getBlockState(tilePos))) {
                    farmExpansionQueue .add(tilePos);
                }
            }
        }
        currentFarmTillTarget = nextFarmExpansionTarget(world);
    }

    private BlockPos findFarmChestCreationSpot(ServerLevel world) {
        if (farmerFarmAnchor == null) return null;
        BlockPos origin = farmerFarmAnchor;

        // Keep shared storage near the farm edge so both farmers and players can
        // reach it without a long delivery walk.
        for (int radius = 5; radius <= 9; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) continue;
                    for (int y = -2; y <= 2; y++) {
                        BlockPos candidate = origin.offset(x, y, z);
                        if (isSuitableForChestCreation(world, candidate)) {
                            return candidate.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isSuitableForChestCreation(ServerLevel world, BlockPos pos) {
        BlockPos groundPos = pos.below();
        BlockState feet = world.getBlockState(pos);
        BlockState ground = world.getBlockState(groundPos);
        return feet.isAir()
                && world.getBlockState(pos.above()).isAir()
                && !ground.isAir()
                && ground.getFluidState().isEmpty()
                && ground.getDestroySpeed(world, groundPos) >= 0.0F
                && !isProtectedWorldBlock(world, groundPos, ground);
    }

    private void startChestCreation(ServerLevel world, BlockPos chestCenter) {
        sendEventToAi("storage_creation", "building storage at " + chestCenter.getX() + ", " + chestCenter.getY() + ", " + chestCenter.getZ());
        npc.getNavigation() .moveTo(chestCenter.getX() + 0.5, chestCenter.getY(), chestCenter.getZ() + 0.5, 1.0);
        farmerStorageChest = chestCenter;
        chestCreationCooldownTicks = 1200;

        if (isSuitableForChestCreation(world, chestCenter)) {
            npc.getLookControl() .setLookAt(chestCenter.getX() + 0.5, chestCenter.getY() + 0.5, chestCenter.getZ() + 0.5);
            npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            world.setBlock(chestCenter, Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private boolean avoidDangerousDrop() {
        if (!npc.onGround() || npc.getNavigation().isDone()) {
            npc .setShiftKeyDown(false);
            return false;
        }
        if (npc.isInWater()) {
            npc .setShiftKeyDown(false);
            return false;
        }

        Vec3 direction = getCurrentTravelDirection();
        if (direction.lengthSqr() < 0.01) {
            npc .setShiftKeyDown(false);
            return false;
        }

        direction = direction.normalize();
        BlockPos nextFeet = BlockPos.containing(npc.getX() + direction.x, npc.getY(), npc.getZ() + direction.z);
        int drop = getDropDepth(nextFeet);
        if (drop < DANGEROUS_DROP_HEIGHT || hasWaterLandingBelow(nextFeet)) {
            npc .setShiftKeyDown(false);
            return false;
        }

        npc.getNavigation().stop();
        npc .setShiftKeyDown(true);
        npc.setJumping(false);
        npc .push(-direction.x * 0.12, 0.0, -direction.z * 0.12);
        if (eventCooldownTicks == 0) {
            sendEventToAi("dangerous_drop", "");
        }
        return true;
    }

    private Vec3 getCurrentTravelDirection() {
        Path path = npc.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            BlockPos node = path.getNextNodePos();
            if (node != null) {
                return Vec3.atCenterOf(node).subtract(new Vec3(npc.getX(), npc.getY(), npc.getZ()));
            }
        }

        Vec3 velocity = npc.getDeltaMovement();
        return new Vec3(velocity.x, 0.0, velocity.z);
    }

    private int getDropDepth(BlockPos feet) {
        net.minecraft.world.level.Level world = npc.level();
        for (int depth = 0; depth <= DANGEROUS_DROP_HEIGHT + 2; depth++) {
            BlockPos check = feet.below(depth + 1);
            BlockState state = world.getBlockState(check);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return depth;
            }
        }

        return DANGEROUS_DROP_HEIGHT + 3;
    }

    private boolean hasWaterLandingBelow(BlockPos feet) {
        net.minecraft.world.level.Level world = npc.level();
        for (int depth = 1; depth <= DANGEROUS_DROP_HEIGHT + 8; depth++) {
            BlockPos check = feet.below(depth);
            if (world.getFluidState(check).is(FluidTags.WATER)) {
                return true;
            }

            BlockState state = world.getBlockState(check);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return false;
            }
        }

        return false;
    }

    private int getInventorySlotsUsed() {
        int slots = 0;
        for (ItemStack stack : virtualInventory.values()) {
            slots += Math.max(1, (int) Math.ceil(stack.getCount() / 64.0D));
        }
        return slots;
    }

    private JsonArray createInventorySummary() {
        JsonArray inventory = new JsonArray();
        for (ItemStack stack : virtualInventory.values()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", getInventoryKey(stack));
            item.addProperty("count", stack.getCount());
            item.addProperty("food", stack.has(net.minecraft.core.component.DataComponents.FOOD));
            inventory .add(item);
        }
        return inventory;
    }

    private void dropLeastUsefulInventoryItem() {
        if (!(npc.level() instanceof ServerLevel serverWorld)) return;

        String worstKey = null;
        int worstScore = Integer.MAX_VALUE;
        for (Map.Entry<String, ItemStack> entry : virtualInventory.entrySet()) {
            int score = getItemUsefulnessScore(entry.getValue());
            if (score < worstScore) {
                worstScore = score;
                worstKey = entry.getKey();
            }
        }

        if (worstKey == null) return;

        ItemStack stored = virtualInventory.get(worstKey);
        ItemStack dropped = stored.copyWithCount(Math.min(stored.getCount(), 16));
        stored.shrink(dropped.getCount());
        if (stored.isEmpty()) {
            virtualInventory.remove(worstKey);
        }

        ItemEntity droppedEntity = new ItemEntity(serverWorld, npc.getX(), npc.getY() + 0.5, npc.getZ(), dropped);
        serverWorld.addFreshEntity(droppedEntity);
        persistBrainState();
    }

    private int getItemUsefulnessScore(ItemStack stack) {
        String id = getInventoryKey(stack);
        int score = 10;
        if (stack.has(net.minecraft.core.component.DataComponents.FOOD)) score += 50;
        if (id.contains("diamond")) score += 100;
        if (id.contains("iron")) score += 70;
        if (id.contains("log") || id.contains("planks")) score += 45;
        if (id.contains("stone") || id.contains("cobblestone")) score += 20;
        if (id.contains("dirt") || id.contains("gravel") || id.contains("sand")) score -= 5;
        return score;
    }

    private String getInventoryKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private void restoreDefaultMainHand() {
        boolean diamond = "miner".equals(npc.getAppearanceVariantName())
                || "diamond".equals(currentEquipmentTier);
        ItemStack desired = switch (npc.getAppearanceVariantName()) {
            case "miner" -> new ItemStack(diamond ? Items.DIAMOND_PICKAXE : Items.IRON_PICKAXE);
            case "guardian" -> new ItemStack(diamond ? Items.DIAMOND_SWORD : Items.IRON_SWORD);
            case "scout" -> new ItemStack(Items.BOW);
            case "builder" -> new ItemStack(diamond ? Items.DIAMOND_AXE : Items.IRON_AXE);
            case "farmer" -> new ItemStack(diamond ? Items.DIAMOND_HOE : Items.IRON_HOE);
            case "rancher" -> new ItemStack(Items.WHEAT);
            default -> new ItemStack(diamond ? Items.DIAMOND_AXE : Items.IRON_AXE);
        };

        ItemStack current = npc .getItemBySlot(EquipmentSlot.MAINHAND);
        if (!ItemStack.isSameItem(current, desired)) {
            npc .setItemSlot(EquipmentSlot.MAINHAND, desired);
        }
    }

    private void putTerrainBlocksAwayWhenNotBuilding() {
        if ("build".equals(currentMode)) return;

        ItemStack current = npc .getItemBySlot(EquipmentSlot.MAINHAND);
        if (current.is(Items.OAK_PLANKS) || current.is(Items.OAK_STAIRS)) {
            restoreDefaultMainHand();
        }
    }

    private void equipToolFor(BlockState state) {
        boolean diamond = "miner".equals(npc.getAppearanceVariantName())
                || "diamond".equals(currentEquipmentTier);
        ItemStack desiredTool;
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            desiredTool = new ItemStack(diamond ? Items.DIAMOND_PICKAXE : Items.IRON_PICKAXE);
        } else if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            desiredTool = new ItemStack(diamond ? Items.DIAMOND_AXE : Items.IRON_AXE);
        } else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            desiredTool = new ItemStack(diamond ? Items.DIAMOND_SHOVEL : Items.IRON_SHOVEL);
        } else if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            desiredTool = new ItemStack(diamond ? Items.DIAMOND_HOE : Items.IRON_HOE);
        } else {
            desiredTool = new ItemStack(diamond ? Items.DIAMOND_PICKAXE : Items.IRON_PICKAXE);
        }

        ItemStack current = npc .getItemBySlot(EquipmentSlot.MAINHAND);
        if (!ItemStack.isSameItem(current, desiredTool)) {
            npc .setItemSlot(EquipmentSlot.MAINHAND, desiredTool);
        }
    }

    private void equipSword() {
        ItemStack sword = new ItemStack("diamond".equals(currentEquipmentTier) ? Items.DIAMOND_SWORD : Items.IRON_SWORD);
        ItemStack current = npc .getItemBySlot(EquipmentSlot.MAINHAND);
        if (!ItemStack.isSameItem(current, sword)) {
            npc .setItemSlot(EquipmentSlot.MAINHAND, sword);
        }
    }

    private boolean isInCombat() {
        LivingEntity target = npc.getTarget();
        if (target == null || !target.isAlive() || target.isRemoved()) {
            return false;
        }

        if (!npc.canTargetHostile(target)) {
            AiCompanionMod.LOGGER.info("NPC dropped unreachable combat target across water: " + target.getName().getString());
            npc.setTarget(null);
            npc.getNavigation().stop();
            return false;
        }

        return true;
    }

    private void manageNearbyDoorsAndGates() {
        decayPassageCooldowns();
        openNeededDoorsAndGates();
        closeRememberedDoors();
        closeRememberedFenceGates();
        closeRememberedTrapdoors();
    }

    private void openNeededDoorsAndGates() {
        net.minecraft.world.level.Level world = npc.level();
        net.minecraft.core.BlockPos origin = npc.blockPosition();

        for (int x = -PASSAGE_SCAN_RADIUS; x <= PASSAGE_SCAN_RADIUS; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -PASSAGE_SCAN_RADIUS; z <= PASSAGE_SCAN_RADIUS; z++) {
                    net.minecraft.core.BlockPos pos = origin.offset(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    Block block = state.getBlock();

                    if (block instanceof DoorBlock) {
                        BlockPos doorPos = getDoorBasePos(pos, state);
                        if (passageUseCooldowns.containsKey(doorPos) || !shouldControlPassage(doorPos)) {
                            continue;
                        }

                        BlockState doorState = world.getBlockState(doorPos);
                        if (doorState.getBlock() instanceof DoorBlock door
                                && DoorBlock.isWoodenDoor(doorState)
                                && !door.isOpen(doorState)) {
                            door.setOpen(npc, world, doorState, doorPos, true);
                            openedDoors.put(doorPos.immutable(), PASSAGE_CLOSE_DELAY_TICKS);
                        }
                    } else if (block instanceof TrapDoorBlock) {
                        BlockPos trapdoorPos = pos.immutable();
                        if (!passageUseCooldowns.containsKey(trapdoorPos)
                                && shouldControlTrapdoor(trapdoorPos)
                                && !state.getValue(TrapDoorBlock.OPEN)) {
                            npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                            world.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, true), Block.UPDATE_ALL);
                            openedTrapdoors.put(trapdoorPos, TRAPDOOR_CLOSE_DELAY_TICKS);
                        }
                    }
                }
            }
        }
    }

    private void closeRememberedDoors() {
        net.minecraft.world.level.Level world = npc.level();

        openedDoors.replaceAll((pos, ticks) -> ticks - 1);
        for (Map.Entry<BlockPos, Integer> entry : new ArrayList<>(openedDoors.entrySet())) {
            BlockPos pos = entry.getKey();
            BlockState state = world.getBlockState(pos);

            if (!(state.getBlock() instanceof DoorBlock door)) {
                openedDoors.remove(pos);
                continue;
            }

            if (!door.isOpen(state)) {
                openedDoors.remove(pos);
                continue;
            }

            boolean closeDelayElapsed = entry.getValue() <= 0;
            if (closeDelayElapsed && !isEntityInPassage(pos) && !shouldControlPassage(pos)) {
                door.setOpen(npc, world, state, pos, false);
                openedDoors.remove(pos);
                passageUseCooldowns.put(pos, PASSAGE_REOPEN_COOLDOWN_TICKS);
            }
        }
    }

    private void closeRememberedFenceGates() {
        net.minecraft.world.level.Level world = npc.level();

        openedFenceGates.replaceAll((pos, ticks) -> ticks - 1);
        for (Map.Entry<BlockPos, Integer> entry : new ArrayList<>(openedFenceGates.entrySet())) {
            BlockPos pos = entry.getKey();
            BlockState state = world.getBlockState(pos);

            if (!(state.getBlock() instanceof FenceGateBlock)) {
                openedFenceGates.remove(pos);
                continue;
            }

            if (!state.getValue(FenceGateBlock.OPEN)) {
                openedFenceGates.remove(pos);
                passageUseCooldowns.put(pos, MANUAL_PASSAGE_CLOSE_COOLDOWN_TICKS);
                continue;
            }

            boolean closeDelayElapsed = entry.getValue() <= 0;
            if (closeDelayElapsed && !isEntityInPassage(pos)) {
                world.setBlock(pos, state.setValue(FenceGateBlock.OPEN, false), Block.UPDATE_ALL);
                openedFenceGates.remove(pos);
                passageUseCooldowns.put(pos, PASSAGE_REOPEN_COOLDOWN_TICKS);
            }
        }
    }

    private void closeRememberedTrapdoors() {
        net.minecraft.world.level.Level world = npc.level();

        openedTrapdoors.replaceAll((pos, ticks) -> ticks - 1);
        for (Map.Entry<BlockPos, Integer> entry : new ArrayList<>(openedTrapdoors.entrySet())) {
            BlockPos pos = entry.getKey();
            BlockState state = world.getBlockState(pos);

            if (!(state.getBlock() instanceof TrapDoorBlock)) {
                openedTrapdoors.remove(pos);
                continue;
            }

            if (!state.getValue(TrapDoorBlock.OPEN)) {
                openedTrapdoors.remove(pos);
                continue;
            }

            boolean closeDelayElapsed = entry.getValue() <= 0;
            if (closeDelayElapsed && !isEntityInTrapdoorPassage(pos) && !shouldControlTrapdoor(pos)) {
                world.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, false), Block.UPDATE_ALL);
                openedTrapdoors.remove(pos);
                passageUseCooldowns.put(pos, PASSAGE_REOPEN_COOLDOWN_TICKS);
            }
        }
    }

    private void decayPassageCooldowns() {
        passageUseCooldowns.replaceAll((pos, ticks) -> ticks - 1);
        passageUseCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }

    private BlockPos getDoorBasePos(BlockPos pos, BlockState state) {
        return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below().immutable() : pos.immutable();
    }

    private boolean shouldControlPassage(BlockPos passagePos) {
        BlockPos target = getActiveMovementTarget();
        if (target == null || npc.isPassenger()) {
            return false;
        }

        double npcX = npc.getX();
        double npcZ = npc.getZ();
        double targetX = target.getX() + 0.5;
        double targetZ = target.getZ() + 0.5;
        double passageX = passagePos.getX() + 0.5;
        double passageZ = passagePos.getZ() + 0.5;

        double targetDx = targetX - npcX;
        double targetDz = targetZ - npcZ;
        double targetDistSq = targetDx * targetDx + targetDz * targetDz;
        if (targetDistSq < 4.0) {
            return false;
        }

        double passageDx = passageX - npcX;
        double passageDz = passageZ - npcZ;
        double passageDistSq = passageDx * passageDx + passageDz * passageDz;
        if (passageDistSq > 10.0) {
            return false;
        }

        double projection = (passageDx * targetDx + passageDz * targetDz) / targetDistSq;
        if (projection < -0.1 || projection > 1.1) {
            return false;
        }

        double closestX = targetDx * projection;
        double closestZ = targetDz * projection;
        double lateralX = passageDx - closestX;
        double lateralZ = passageDz - closestZ;
        double lateralDistSq = lateralX * lateralX + lateralZ * lateralZ;
        if (lateralDistSq > 3.25) {
            return false;
        }

        double passageToTargetX = targetX - passageX;
        double passageToTargetZ = targetZ - passageZ;
        double passageToTargetSq = passageToTargetX * passageToTargetX + passageToTargetZ * passageToTargetZ;
        return passageToTargetSq + 0.5 < targetDistSq || passageDistSq < 2.25;
    }

    private boolean shouldControlTrapdoor(BlockPos trapdoorPos) {
        BlockPos target = getActiveMovementTarget();
        if (target == null || npc.isPassenger()) {
            return false;
        }

        double trapdoorDistanceSq = npc .distanceToSqr(Vec3.atCenterOf(trapdoorPos));
        if (trapdoorDistanceSq > 12.25) {
            return false;
        }

        BlockPos npcPos = npc.blockPosition();
        int verticalDirection = Integer.compare(target.getY(), npcPos.getY());
        double trapdoorX = trapdoorPos.getX() + 0.5;
        double trapdoorZ = trapdoorPos.getZ() + 0.5;
        double horizontalDx = trapdoorX - npc.getX();
        double horizontalDz = trapdoorZ - npc.getZ();
        double horizontalDistanceSq = horizontalDx * horizontalDx + horizontalDz * horizontalDz;

        if (verticalDirection != 0) {
            boolean nearColumn = horizontalDistanceSq <= 3.25;
            boolean betweenHeights = verticalDirection > 0
                    ? trapdoorPos.getY() >= npcPos.getY() && trapdoorPos.getY() <= target.getY() + 1
                    : trapdoorPos.getY() <= npcPos.getY() + 1 && trapdoorPos.getY() >= target.getY() - 1;
            return nearColumn && betweenHeights;
        }

        return shouldControlPassage(trapdoorPos);
    }

    private boolean isEntityInPassage(BlockPos pos) {
        AABB passageBox = new AABB(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 2.0, pos.getZ() + 1.0
        ).inflate(0.15);
        if (passageBox.intersects(npc.getBoundingBox())) {
            return true;
        }

        return !npc.level()
                 .getEntities(npc, passageBox, entity -> entity.isAlive() && !entity.isRemoved())
                .isEmpty();
    }

    private boolean isEntityInTrapdoorPassage(BlockPos pos) {
        AABB passageBox = new AABB(
                pos.getX() - 0.1, pos.getY() - 1.0, pos.getZ() - 0.1,
                pos.getX() + 1.1, pos.getY() + 2.0, pos.getZ() + 1.1
        );
        if (passageBox.intersects(npc.getBoundingBox())) {
            return true;
        }

        return !npc.level()
                 .getEntities(npc, passageBox, entity -> entity.isAlive() && !entity.isRemoved())
                .isEmpty();
    }

    // -------------------------------------------------------------------------
    // NEW: Idle animation — subtle life-like movements when standing still
    // -------------------------------------------------------------------------
    private void performIdleAnimation() {
        if (!(npc.level() instanceof ServerLevel serverWorld)) return;

        float roll = npc.getRandom().nextFloat();

        if (roll < 0.25f) {
            // Look toward nearest player
            for (ServerPlayer player : serverWorld.players()) {
                if (npc .distanceToSqr(player) < 144.0) {
                    npc.getLookControl() .setLookAt(player, 30.0F, 30.0F);
                    break;
                }
            }
        } else if (roll < 0.4f) {
            // Glance upward, like checking the sky
            npc.getLookControl() .setLookAt(npc.getX(), npc.getY() + 8, npc.getZ());
        } else if (roll < 0.55f) {
            // Look down briefly, like checking footing or an item.
            npc.getLookControl() .setLookAt(npc.getX(), npc.getY() - 1, npc.getZ());
        } else if (roll < 0.7f) {
            // Swing arm (inspecting an item or scratching head)
            npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        } else {
            // Look in a random horizontal direction
            double angle = npc.getRandom().nextFloat() * Math.PI * 2;
            npc.getLookControl() .setLookAt(
                    npc.getX() + Math.cos(angle) * 8,
                    npc.getY(),
                    npc.getZ() + Math.sin(angle) * 8);
        }
    }

    // -------------------------------------------------------------------------
    // NEW: Player greeting — say hi when a player walks close
    // -------------------------------------------------------------------------
    private void checkPlayerGreetings(ServerLevel serverWorld) {
        for (ServerPlayer player : serverWorld.players()) {
            String name = player.getName().getString();
            if (npc .distanceToSqr(player) <= GREET_RANGE_SQUARED
                    && !greetedPlayersEver.contains(name.toLowerCase())
                    && !npc.hasSpokenToPlayer(name)) {
                greetedPlayersEver .add(name.toLowerCase());
                npc.rememberSpokenPlayer(name);
                persistBrainState();
                sendEventToAi("player_nearby", name);
                return; // Greet one player per check to avoid spam
            }
        }
    }

    private void checkThreatWarnings() {
        if (!(npc.level() instanceof ServerLevel serverWorld)) return;

        Monster closest = null;
        String closestType = "";
        double closestDistance = Double.MAX_VALUE;

        List<Monster> hostiles = serverWorld.getEntitiesOfClass(
                Monster.class,
                npc.getBoundingBox() .inflate(16),
                e -> e.isAlive() && !e.isRemoved());

        for (Monster hostile : hostiles) {
            double distance = npc .distanceToSqr(hostile);
            if (distance > THREAT_WARNING_RANGE_SQUARED || distance >= closestDistance) continue;

            String type = BuiltInRegistries.ENTITY_TYPE.getKey(hostile.getType()).getPath();
            if (!"creeper".equals(type) && !"witch".equals(type)) continue;
            if (threatWarningCooldowns.containsKey(type)) continue;

            closest = hostile;
            closestType = type;
            closestDistance = distance;
        }

        if (closest == null) return;

        int blocksAway = Math.max(1, (int) Math.round(Math.sqrt(closestDistance)));
        String detail = closestType + " " + blocksAway + " blocks away";
        if (sendEventToAi("threat_warning", detail)) {
            threatWarningCooldowns.put(closestType, THREAT_WARNING_COOLDOWN_TICKS);
        }
    }

    // -------------------------------------------------------------------------
    // NEW: Equipment progression — track mined ore and upgrade tool tier
    // -------------------------------------------------------------------------
    private void updateEquipmentProgress(String minedBlockName) {
        boolean changed = false;
        if (minedBlockName.contains("diamond")) {
            diamondMinedCount++;
            changed = true;
            if (diamondMinedCount >= 2 && !"diamond".equals(currentEquipmentTier)) {
                currentEquipmentTier = "diamond";
                AiCompanionMod.LOGGER.info("Equipment upgraded to DIAMOND tier!");
                sendEventToAi("found_diamonds", "");
            }
        } else if (minedBlockName.contains("iron")) {
            ironMinedCount++;
            changed = true;
        }

        if (changed) {
            persistBrainState();
        }
    }

    // -------------------------------------------------------------------------
    // NEW: Event fire — send an in-game event to Python and broadcast reply
    // -------------------------------------------------------------------------
    private boolean sendEventToAi(String event, String detail) {
        if (eventCooldownTicks > 0) return false;
        if (!(npc.level() instanceof ServerLevel serverWorld)) return false;

        eventCooldownTicks = EVENT_COOLDOWN;
        UUID eventNpcUuid = npc.getUUID();
        String eventNpcName = npc.getName().getString();

        // Append coordinates to detail for all events
        BlockPos pos = npc.blockPosition();
        String detailWithCoords = detail.isEmpty()
            ? String.format("(at %d, %d, %d)", pos.getX(), pos.getY(), pos.getZ())
            : detail + String.format(" (at %d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());

        JsonObject payload = new JsonObject();
        payload.addProperty("npcName", eventNpcName);
        payload.addProperty("event", event);
        payload.addProperty("detail", detailWithCoords);

        AiCompanionMod.AI_CLIENT.sendEvent(payload).thenAccept(response -> {
            if (response == null || !response.has("reply")) return;
            String reply = response.get("reply").getAsString();
            if (reply == null || reply.isBlank() || "IGNORE".equals(reply)) return;

            serverWorld.getServer().execute(() -> {
                if (!canAcceptAsyncReply() || !npc.getUUID().equals(eventNpcUuid)) {
                    AiCompanionMod.LOGGER.debug("Ignoring stale event reply for " + eventNpcName + " after death or unload.");
                    return;
                }
                serverWorld.getServer().getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal("§e<" + npc.getName().getString() + "> §f" + reply),
                        false);
            });
        });
        return true;
    }

    private boolean canAcceptAsyncReply() {
        return npc.isAlive() && !npc.isRemoved() && !npc.level().isClientSide();
    }

    private void equipShieldIfAppropriate() {
        String variant = npc.getAppearanceVariantName();
        if ("scout".equals(variant) || "miner".equals(variant)) return;
        ItemStack current = npc .getItemBySlot(EquipmentSlot.OFFHAND);
        if (!current.is(Items.SHIELD)) {
            npc .setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        }
    }

    private void handleScoutCombat(LivingEntity target) {
        if (target == null) return;
        double distSq = npc .distanceToSqr(target);
        if (distSq > 64.0) {
            if (!npc .getItemBySlot(EquipmentSlot.MAINHAND) .is(Items.BOW)) {
                npc .setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                npc .setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 16));
            }
            bowShootCooldown--;
            if (bowShootCooldown <= 0 && npc.level() instanceof ServerLevel serverWorld) {
                bowShootCooldown = 40;
                shootArrowAt(serverWorld, target);
            }
        } else {
            equipSword();
            ItemStack offhand = npc .getItemBySlot(EquipmentSlot.OFFHAND);
            if (offhand.is(Items.ARROW)) {
                npc .setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            }
        }
    }

    private boolean isGuardianRangedTarget(LivingEntity target) {
        return target instanceof Creeper || target instanceof Witch;
    }

    private void handleGuardianRangedCombat(LivingEntity target) {
        if (target == null) return;

        npc.getNavigation().stop();
        npc.setSprinting(false);
        npc.getLookControl() .setLookAt(target, 30.0F, 30.0F);
        if (!npc .getItemBySlot(EquipmentSlot.MAINHAND) .is(Items.BOW)) {
            npc .setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        }
        if (!npc .getItemBySlot(EquipmentSlot.OFFHAND) .is(Items.ARROW)) {
            npc .setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 64));
        }

        double distanceSq = npc .distanceToSqr(target);
        if (!isReliableGuardianArrowShot(target, distanceSq)) {
            bowShootCooldown = Math.min(bowShootCooldown, 8);
              if (distanceSq > GUARDIAN_ARROW_RELIABLE_RANGE_SQUARED
                      || !npc.getSensing().hasLineOfSight(target)) {
                npc.getNavigation() .moveTo(target, 1.0);
                npc.setSprinting(distanceSq > 16.0 * 16.0);
            }
            return;
        }

        if (distanceSq < 64.0) {
            Vec3 away = new Vec3(
                    npc.getX() - target.getX(),
                    0.0,
                    npc.getZ() - target.getZ());
            if (away.lengthSqr() > 0.01) {
                Vec3 retreat = away.normalize().scale(distanceSq < 25.0 ? 0.32 : 0.18);
                npc .setDeltaMovement(retreat.x, npc.getDeltaMovement().y, retreat.z);
            }
        }

        bowShootCooldown--;
        if (bowShootCooldown <= 0 && npc.level() instanceof ServerLevel serverWorld) {
            bowShootCooldown = target instanceof Creeper ? 24 : 32;
            shootArrowAt(serverWorld, target);
        }
    }

    private boolean isReliableGuardianArrowShot(LivingEntity target, double distanceSq) {
        if (distanceSq > GUARDIAN_ARROW_RELIABLE_RANGE_SQUARED) {
            return false;
        }
        if (Math.abs(target.getEyeY() - npc.getEyeY()) > GUARDIAN_ARROW_MAX_VERTICAL_DELTA) {
            return false;
        }
        return npc.getSensing().hasLineOfSight(target);
    }

    private void shootArrowAt(ServerLevel serverWorld, LivingEntity target) {
        npc.getLookControl() .setLookAt(target, 30.0F, 30.0F);
        npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(
                net.minecraft.world.entity.EntityType.ARROW,
                serverWorld);
        arrow.setOwner(npc);
        arrow.setPos(npc.getX(), npc.getEyeY() - 0.1, npc.getZ());
        double dx = target.getX() - npc.getX();
        double dy = (target.getY() + target.getBbHeight() * 0.33) - npc.getEyeY();
        double dz = target.getZ() - npc.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizDist * 0.2, dz, 1.6F, 14 - serverWorld.getDifficulty().getId() * 4);
        serverWorld.addFreshEntity(arrow);
    }

    private void tryPlaceTorch() {
        net.minecraft.world.level.Level world = npc.level();
        BlockPos feet = npc.blockPosition();
        String variant = npc.getAppearanceVariantName();
        if (!"miner".equals(variant)) return;
        if (world.canSeeSky(feet.above(2))) return;
        if (world.getMaxLocalRawBrightness(feet) > TORCH_MAX_LIGHT_LEVEL) return;
        if (countNearbyTorches(world, feet) >= TORCH_AREA_LIMIT) return;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos torchPos = feet.relative(dir);
            if (world.getBlockState(torchPos).isAir()
                    && !world.getBlockState(torchPos.below()).isAir()
                    && world.getBlockState(torchPos.below()).getFluidState().isEmpty()) {
                npc.getLookControl() .setLookAt(torchPos.getX() + 0.5, torchPos.getY() + 0.5, torchPos.getZ() + 0.5);
                npc .swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                world.setBlockAndUpdate(torchPos, Blocks.TORCH.defaultBlockState());
                return;
            }
        }
    }

    private int countNearbyTorches(net.minecraft.world.level.Level world, BlockPos center) {
        int count = 0;
        for (int x = -TORCH_AREA_RADIUS; x <= TORCH_AREA_RADIUS; x++) {
            for (int y = -TORCH_AREA_VERTICAL_RADIUS; y <= TORCH_AREA_VERTICAL_RADIUS; y++) {
                for (int z = -TORCH_AREA_RADIUS; z <= TORCH_AREA_RADIUS; z++) {
                    BlockState state = world.getBlockState(center.offset(x, y, z));
                    if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
                        count++;
                        if (count >= TORCH_AREA_LIMIT) return count;
                    }
                }
            }
        }
        return count;
    }

    private record BuildPlacement(net.minecraft.core.BlockPos pos, BlockState state) {
    }

    private record BuildPlan(int radius, int height) {
    }

    private record CustomSchematic(String name, int radius, int height, List<CustomSchematicBlock> blocks) {
    }

    private record CustomSchematicBlock(int x, int y, int z, String block, Map<String, String> properties) {
    }

    private record CustomSchematicPlacement(int x, int y, int z, BlockState state) {
    }
}
