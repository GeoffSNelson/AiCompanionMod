package com.nelson.aicompanion.entity;

import com.nelson.aicompanion.AiCompanionMod;
import com.nelson.aicompanion.entity.ai.AiTickGoal;
import com.nelson.aicompanion.players.CompanionPlayerList;
import com.nelson.aicompanion.players.CompanionRegistry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class CompanionEntity extends PathAwareEntity {
    private static final int FORGIVEN_PLAYER_TICKS = 20 * 30;
    private static final TrackedData<Integer> APPEARANCE_VARIANT = DataTracker.registerData(
            CompanionEntity.class,
            TrackedDataHandlerRegistry.INTEGER
    );
    private static final String[] VARIANT_NAMES = {
            "wanderer",
            "miner",
            "guardian",
            "builder",
            "scout",
            "farmer"
    };

    // Look-at-speaker: AiTickGoal reads these each tick to hold gaze on the player who spoke
    public PlayerEntity speakerLookTarget = null;
    public int speakerLookTicks = 0;

    // Registered home: companions respawn here after death.
    private BlockPos homePosition = null;
    private String persistedAction = "@idle";
    private String persistedEquipmentTier = "iron";
    private String persistedVirtualInventory = "";
    private int persistedIronMinedCount = 0;
    private int persistedDiamondMinedCount = 0;
    private String persistedBuildSchematic = "";
    private BlockPos persistedBuildCenter = null;
    private int persistedBuildTotal = 0;
    private String persistedGreetedPlayers = "";
    private final Map<UUID, Integer> forgivenPlayers = new HashMap<>();

    public CompanionEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        configureNavigation();
    }

    public static DefaultAttributeContainer.Builder createCompanionAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0D)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3F)
                .add(EntityAttributes.FOLLOW_RANGE, 128.0D)
                .add(EntityAttributes.ATTACK_DAMAGE, 6.0D);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(APPEARANCE_VARIANT, 0);
    }

    @Override
    protected void writeCustomData(WriteView view) {
        super.writeCustomData(view);
        view.putInt("AppearanceVariant", getAppearanceVariant());
        if (homePosition != null) {
            view.putInt("HomeX", homePosition.getX());
            view.putInt("HomeY", homePosition.getY());
            view.putInt("HomeZ", homePosition.getZ());
        }
        view.putString("AiAction", persistedAction);
        view.putString("AiEquipmentTier", persistedEquipmentTier);
        view.putString("AiVirtualInventory", persistedVirtualInventory);
        view.putInt("AiIronMined", persistedIronMinedCount);
        view.putInt("AiDiamondMined", persistedDiamondMinedCount);
        view.putString("AiBuildSchematic", persistedBuildSchematic);
        if (persistedBuildCenter != null) {
            view.putInt("AiBuildX", persistedBuildCenter.getX());
            view.putInt("AiBuildY", persistedBuildCenter.getY());
            view.putInt("AiBuildZ", persistedBuildCenter.getZ());
        }
        view.putInt("AiBuildTotal", persistedBuildTotal);
        view.putString("AiGreetedPlayers", persistedGreetedPlayers);
    }

    @Override
    protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        setAppearanceVariant(view.getInt("AppearanceVariant", 0));
        int homeX = view.getInt("HomeX", Integer.MIN_VALUE);
        if (homeX != Integer.MIN_VALUE) {
            homePosition = new BlockPos(homeX, view.getInt("HomeY", 64), view.getInt("HomeZ", 0));
        }
        persistedAction = view.getString("AiAction", "@idle");
        persistedEquipmentTier = view.getString("AiEquipmentTier", "iron");
        persistedVirtualInventory = view.getString("AiVirtualInventory", "");
        persistedIronMinedCount = view.getInt("AiIronMined", 0);
        persistedDiamondMinedCount = view.getInt("AiDiamondMined", 0);
        persistedBuildSchematic = view.getString("AiBuildSchematic", "");
        int buildX = view.getInt("AiBuildX", Integer.MIN_VALUE);
        persistedBuildCenter = buildX == Integer.MIN_VALUE
                ? null
                : new BlockPos(buildX, view.getInt("AiBuildY", 64), view.getInt("AiBuildZ", 0));
        persistedBuildTotal = view.getInt("AiBuildTotal", 0);
        persistedGreetedPlayers = view.getString("AiGreetedPlayers", "");
    }

    public int getAppearanceVariant() {
        return Math.floorMod(this.dataTracker.get(APPEARANCE_VARIANT), VARIANT_NAMES.length);
    }

    public void setAppearanceVariant(int variant) {
        this.dataTracker.set(APPEARANCE_VARIANT, Math.floorMod(variant, VARIANT_NAMES.length));
    }

    public String getAppearanceVariantName() {
        return VARIANT_NAMES[getAppearanceVariant()];
    }

    public static int getAppearanceVariantCount() {
        return VARIANT_NAMES.length;
    }

    public static int getAppearanceVariantIndex(String role) {
        if (role == null || role.isBlank()) {
            return -1;
        }

        String normalized = role.trim().toLowerCase();
        for (int i = 0; i < VARIANT_NAMES.length; i++) {
            if (VARIANT_NAMES[i].equals(normalized)) {
                return i;
            }
        }
        return -1;
    }

    public BlockPos getHomePosition() {
        return homePosition;
    }

    public void setHomePosition(BlockPos pos) {
        this.homePosition = pos;
    }

    public String getPersistedAction() {
        return persistedAction == null || persistedAction.isBlank() ? "@idle" : persistedAction;
    }

    public void setPersistedAction(String action) {
        this.persistedAction = action == null || action.isBlank() ? "@idle" : action;
    }

    public String getPersistedEquipmentTier() {
        return persistedEquipmentTier == null || persistedEquipmentTier.isBlank() ? "iron" : persistedEquipmentTier;
    }

    public void setPersistedEquipmentTier(String equipmentTier) {
        this.persistedEquipmentTier = equipmentTier == null || equipmentTier.isBlank() ? "iron" : equipmentTier;
    }

    public String getPersistedVirtualInventory() {
        return persistedVirtualInventory == null ? "" : persistedVirtualInventory;
    }

    public void setPersistedVirtualInventory(String virtualInventory) {
        this.persistedVirtualInventory = virtualInventory == null ? "" : virtualInventory;
    }

    public int getPersistedIronMinedCount() {
        return persistedIronMinedCount;
    }

    public void setPersistedIronMinedCount(int count) {
        this.persistedIronMinedCount = Math.max(0, count);
    }

    public int getPersistedDiamondMinedCount() {
        return persistedDiamondMinedCount;
    }

    public void setPersistedDiamondMinedCount(int count) {
        this.persistedDiamondMinedCount = Math.max(0, count);
    }

    public String getPersistedBuildSchematic() {
        return persistedBuildSchematic == null ? "" : persistedBuildSchematic;
    }

    public void setPersistedBuildSchematic(String schematic) {
        persistedBuildSchematic = schematic == null ? "" : schematic;
    }

    public BlockPos getPersistedBuildCenter() {
        return persistedBuildCenter;
    }

    public void setPersistedBuildCenter(BlockPos center) {
        persistedBuildCenter = center == null ? null : center.toImmutable();
    }

    public int getPersistedBuildTotal() {
        return persistedBuildTotal;
    }

    public void setPersistedBuildTotal(int total) {
        persistedBuildTotal = Math.max(0, total);
    }

    public String getPersistedGreetedPlayers() {
        return persistedGreetedPlayers == null ? "" : persistedGreetedPlayers;
    }

    public void setPersistedGreetedPlayers(String greetedPlayers) {
        persistedGreetedPlayers = greetedPlayers == null ? "" : greetedPlayers;
    }

    public boolean hasSpokenToPlayer(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        String normalized = playerName.trim().toLowerCase();
        for (String name : getPersistedGreetedPlayers().split(",")) {
            if (normalized.equals(name.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public void rememberSpokenPlayer(String playerName) {
        if (playerName == null || playerName.isBlank() || hasSpokenToPlayer(playerName)) return;
        String normalized = playerName.trim().toLowerCase();
        persistedGreetedPlayers = persistedGreetedPlayers.isBlank()
                ? normalized
                : persistedGreetedPlayers + "," + normalized;
    }

    public void randomizeAppearanceAndLoadout(ServerWorld world) {
        setAppearanceVariant(world.random.nextInt(VARIANT_NAMES.length));
        applyStartingLoadout();
    }

    public void applyStartingLoadout() {
        clearLoadout();

        switch (getAppearanceVariantName()) {
            case "miner" -> {
                equipStack(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                equipStack(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                equipStack(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
                equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH));
            }
            case "guardian" -> {
                equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                equipStack(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                equipStack(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
                equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            }
            case "builder" -> {
                equipStack(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                equipStack(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
                equipStack(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
                equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH));
            }
            case "scout" -> {
                equipStack(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                equipStack(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
                equipStack(EquipmentSlot.FEET, new ItemStack(Items.CHAINMAIL_BOOTS));
                equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 16));
            }
            case "farmer" -> {
                equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                equipStack(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                equipStack(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
                equipStack(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
                equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
                equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.WHEAT_SEEDS, 16));
            }
            default -> {
                equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                equipStack(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
                equipStack(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
                equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            }
        }
    }

    private void clearLoadout() {
        equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
        equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);
        equipStack(EquipmentSlot.LEGS, ItemStack.EMPTY);
        equipStack(EquipmentSlot.FEET, ItemStack.EMPTY);
        equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    @Override
    protected void initGoals() {
        // The AI brain owns movement decisions. Vanilla wandering made companions look frantic.
        this.goalSelector.add(1, new AiTickGoal(this));

        // Vanilla "hold jump in water" behavior, layered under our shoreline escape logic.
        this.goalSelector.add(0, new SwimGoal(this));

        // Creepers are not good melee targets. Back away instead of bodyguard-charging them.
        this.goalSelector.add(1, new FleeEntityGoal<>(this, CreeperEntity.class, 10.0F, 1.1D, 1.3D));

        // Bodyguard behavior: when a hostile mob is nearby, close distance and fight it.
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.15D, true));

        // Priority 6: Look at nearby players
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        
        // Priority 7: Look around randomly when idle
        this.goalSelector.add(7, new LookAroundGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(
                this,
                HostileEntity.class,
                10,
                true,
                false,
                (target, world) -> "guardian".equals(getAppearanceVariantName()) && canTargetHostile(target)
        ));
    }

    @Override
    public void tick() {
        super.tick();
        decayForgivenPlayers();
    }
    
    @Override
    public boolean isPersistent() {
        return true; // We don't want the NPC despawning when players walk away!
    }

    @Override
    public boolean canBreatheInWater() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(ServerWorld world, DamageSource source) {
        return isIgnoredEnvironmentalDamage(source) || super.isInvulnerableTo(world, source);
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        if (isIgnoredEnvironmentalDamage(source)) {
            return false;
        }

        return super.damage(world, source, amount);
    }

    @Override
    public boolean canTarget(LivingEntity target) {
        if (target instanceof PlayerEntity player && isForgivenPlayer(player)) {
            return false;
        }
        return super.canTarget(target);
    }

    public boolean isAngryAtPlayer(PlayerEntity player) {
        if (player == null) return false;
        LivingEntity target = getTarget();
        LivingEntity attacker = getAttacker();
        LivingEntity lastAttacker = getLastAttacker();
        return target == player || attacker == player || lastAttacker == player;
    }

    public void forgivePlayer(PlayerEntity player) {
        if (player == null) return;

        forgivenPlayers.put(player.getUuid(), FORGIVEN_PLAYER_TICKS);
        if (getTarget() == player) {
            setTarget(null);
        }
        if (getAttacker() == player || getLastAttacker() == player) {
            setAttacker(null);
        }
        getNavigation().stop();
    }

    public boolean isForgivenPlayer(PlayerEntity player) {
        if (player == null) return false;
        Integer ticks = forgivenPlayers.get(player.getUuid());
        return ticks != null && ticks > 0;
    }

    private void decayForgivenPlayers() {
        Iterator<Map.Entry<UUID, Integer>> iterator = forgivenPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int ticks = entry.getValue() - 1;
            if (ticks <= 0) {
                iterator.remove();
            } else {
                entry.setValue(ticks);
            }
        }
    }

    private void configureNavigation() {
        if (this.getNavigation() instanceof MobNavigation navigation) {
            navigation.setCanOpenDoors(true);
            navigation.setCanSwim(true);
            navigation.setCanWalkOverFences(false);
        }

        this.setPathfindingPenalty(PathNodeType.DOOR_OPEN, 0.0F);
        this.setPathfindingPenalty(PathNodeType.DOOR_WOOD_CLOSED, 0.0F);
        this.setPathfindingPenalty(PathNodeType.WALKABLE_DOOR, 0.0F);
        this.setPathfindingPenalty(PathNodeType.DANGER_FIRE, 16.0F);
        this.setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, -1.0F);
        this.setPathfindingPenalty(PathNodeType.LAVA, -1.0F);
        this.setPathfindingPenalty(PathNodeType.WATER, 2.0F);
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);

        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            // Completely remove the dead entity's UUID from registry since it will respawn as a new entity
            CompanionRegistry.remove(this.getUuid());
            CompanionPlayerList.unlist(serverWorld.getServer(), this.getUuid());
            serverWorld.getServer().getPlayerManager().broadcast(
                    Text.literal("§c[AI Companion] " + this.getName().getString() + " died: " + damageSource.getType().msgId()),
                    false
            );

            // Schedule a respawn at home position (or death location if no home set) after 5 seconds
            String name = this.getName().getString();
            int variant = this.getAppearanceVariant();
            BlockPos spawnPos = homePosition != null ? homePosition : this.getBlockPos();
            AiCompanionMod.scheduleRespawn(serverWorld, name, variant, spawnPos, homePosition != null, 100);
        }
    }

    private boolean isIgnoredEnvironmentalDamage(DamageSource source) {
        String damageId = source.getType().msgId();
        return "inWall".equals(damageId)
                || "cramming".equals(damageId);
    }

    public boolean canTargetHostile(LivingEntity target) {
        if (target == null || !target.isAlive() || target.isRemoved()) {
            return false;
        }
        if (target instanceof CreeperEntity || target instanceof WitchEntity) {
            return "guardian".equals(getAppearanceVariantName());
        }

        double distanceSquared = this.squaredDistanceTo(target);
        if (distanceSquared <= 16.0) {
            return true;
        }

        return !hasWaterBetween(target);
    }

    private boolean hasWaterBetween(LivingEntity target) {
        World world = this.getEntityWorld();
        Vec3d start = new Vec3d(this.getX(), this.getY(), this.getZ());
        Vec3d end = new Vec3d(target.getX(), target.getY(), target.getZ());
        Vec3d delta = end.subtract(start);
        int steps = Math.max(4, Math.min(32, (int) Math.ceil(Math.sqrt(delta.x * delta.x + delta.z * delta.z) * 2.0)));

        for (int i = 1; i < steps; i++) {
            double t = (double) i / (double) steps;
            BlockPos pos = BlockPos.ofFloored(
                    start.x + delta.x * t,
                    start.y + delta.y * t,
                    start.z + delta.z * t
            );

            if (world.getFluidState(pos).isIn(FluidTags.WATER)
                    || world.getFluidState(pos.down()).isIn(FluidTags.WATER)
                    || world.getFluidState(pos.up()).isIn(FluidTags.WATER)) {
                return true;
            }
        }

        return false;
    }
}
