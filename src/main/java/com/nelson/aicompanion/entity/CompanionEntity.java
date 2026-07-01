package com.nelson.aicompanion.entity;

import com.nelson.aicompanion.AiCompanionMod;
import com.nelson.aicompanion.entity.ai.AiTickGoal;
import com.nelson.aicompanion.players.CompanionPlayerList;
import com.nelson.aicompanion.players.CompanionRegistry;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

public class CompanionEntity extends PathfinderMob {
    private static final int FORGIVEN_PLAYER_TICKS = 20 * 30;
    private static final EntityDataAccessor<Integer> APPEARANCE_VARIANT = SynchedEntityData.defineId(
            CompanionEntity.class,
            EntityDataSerializers.INT
    );
    private static final String[] VARIANT_NAMES = {
            "wanderer",
            "miner",
            "guardian",
            "builder",
            "scout",
            "farmer",
            "rancher"
    };

    // Look-at-speaker: AiTickGoal reads these each tick to hold gaze on the player who spoke
    public Player speakerLookTarget = null;
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

    public CompanionEntity(EntityType<? extends PathfinderMob> entityType, Level world) {
        super(entityType, world);
        configureNavigation();
    }

    public static AttributeSupplier.Builder createCompanionAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3F)
                .add(Attributes.FOLLOW_RANGE, 128.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(APPEARANCE_VARIANT, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("AppearanceVariant", getAppearanceVariant());
        if (homePosition != null) {
            nbt.putInt("HomeX", homePosition.getX());
            nbt.putInt("HomeY", homePosition.getY());
            nbt.putInt("HomeZ", homePosition.getZ());
        }
        nbt.putString("AiAction", persistedAction);
        nbt.putString("AiEquipmentTier", persistedEquipmentTier);
        nbt.putString("AiVirtualInventory", persistedVirtualInventory);
        nbt.putInt("AiIronMined", persistedIronMinedCount);
        nbt.putInt("AiDiamondMined", persistedDiamondMinedCount);
        nbt.putString("AiBuildSchematic", persistedBuildSchematic);
        if (persistedBuildCenter != null) {
            nbt.putInt("AiBuildX", persistedBuildCenter.getX());
            nbt.putInt("AiBuildY", persistedBuildCenter.getY());
            nbt.putInt("AiBuildZ", persistedBuildCenter.getZ());
        }
        nbt.putInt("AiBuildTotal", persistedBuildTotal);
        nbt.putString("AiGreetedPlayers", persistedGreetedPlayers);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        setAppearanceVariant(nbt.getInt("AppearanceVariant"));
        if (nbt.contains("HomeX")) {
            homePosition = new BlockPos(nbt.getInt("HomeX"), nbt.getInt("HomeY"), nbt.getInt("HomeZ"));
        }
        persistedAction = nbt.contains("AiAction") ? nbt.getString("AiAction") : "@idle";
        persistedEquipmentTier = nbt.contains("AiEquipmentTier") ? nbt.getString("AiEquipmentTier") : "iron";
        persistedVirtualInventory = nbt.contains("AiVirtualInventory") ? nbt.getString("AiVirtualInventory") : "";
        persistedIronMinedCount = nbt.getInt("AiIronMined");
        persistedDiamondMinedCount = nbt.getInt("AiDiamondMined");
        persistedBuildSchematic = nbt.contains("AiBuildSchematic") ? nbt.getString("AiBuildSchematic") : "";
        persistedBuildCenter = nbt.contains("AiBuildX")
                ? new BlockPos(nbt.getInt("AiBuildX"), nbt.getInt("AiBuildY"), nbt.getInt("AiBuildZ"))
                : null;
        persistedBuildTotal = nbt.getInt("AiBuildTotal");
        persistedGreetedPlayers = nbt.contains("AiGreetedPlayers") ? nbt.getString("AiGreetedPlayers") : "";
    }

    public int getAppearanceVariant() {
        return Math.floorMod(this.entityData.get(APPEARANCE_VARIANT), VARIANT_NAMES.length);
    }

    public void setAppearanceVariant(int variant) {
        this.entityData.set(APPEARANCE_VARIANT, Math.floorMod(variant, VARIANT_NAMES.length));
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
        persistedBuildCenter = center == null ? null : center.immutable();
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

    public void randomizeAppearanceAndLoadout(ServerLevel world) {
        setAppearanceVariant(world.random.nextInt(VARIANT_NAMES.length));
        applyStartingLoadout();
    }

    public void applyStartingLoadout() {
        clearLoadout();

        switch (getAppearanceVariantName()) {
            case "miner" -> {
                setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH));
            }
            case "guardian" -> {
                setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            }
            case "builder" -> {
                setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
                setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH));
            }
            case "scout" -> {
                setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
                setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.CHAINMAIL_BOOTS));
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 16));
            }
            case "farmer" -> {
                setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
                setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.WHEAT_SEEDS, 16));
            }
            case "rancher" -> {
                setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
                setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WHEAT, 16));
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.OAK_FENCE, 16));
            }
            default -> {
                setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
                setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            }
        }
    }

    private void clearLoadout() {
        setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    @Override
    protected void registerGoals() {
        // The AI brain owns movement decisions. Vanilla wandering made companions look frantic.
        this.goalSelector.addGoal(1, new AiTickGoal(this));

        // Vanilla "hold jump in water" behavior, layered under our shoreline escape logic.
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // Creepers are not good melee targets. Back away instead of bodyguard-charging them.
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Creeper.class, 10.0F, 1.1D, 1.3D));

        // Bodyguard behavior: when a hostile mob is nearby, close distance and fight it.
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, true));

        // Priority 6: Look at nearby players
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        
        // Priority 7: Look around randomly when idle
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this,
                Monster.class,
                10,
                true,
                false,
                target -> "guardian".equals(getAppearanceVariantName()) && canTargetHostile(target)
        ));
    }

    @Override
    public void tick() {
        super.tick();
        decayForgivenPlayers();
    }
    
    @Override
    public boolean isPersistenceRequired() {
        return true; // We don't want the NPC despawning when players walk away!
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return isIgnoredEnvironmentalDamage(source) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isIgnoredEnvironmentalDamage(source)) {
            return false;
        }

        return super.hurt(source, amount);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof Player player && isForgivenPlayer(player)) {
            return false;
        }
        return super.canAttack(target);
    }

    public boolean isAngryAtPlayer(Player player) {
        if (player == null) return false;
        LivingEntity target = getTarget();
        LivingEntity attacker = getLastHurtByMob();
        LivingEntity lastAttacker = getLastAttacker();
        return target == player || attacker == player || lastAttacker == player;
    }

    public void forgivePlayer(Player player) {
        if (player == null) return;

        forgivenPlayers.put(player.getUUID(), FORGIVEN_PLAYER_TICKS);
        if (getTarget() == player) {
            setTarget(null);
        }
        if (getLastHurtByMob() == player || getLastAttacker() == player) {
            setLastHurtByMob(null);
        }
        getNavigation().stop();
    }

    public boolean isForgivenPlayer(Player player) {
        if (player == null) return false;
        Integer ticks = forgivenPlayers.get(player.getUUID());
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
        if (this.getNavigation() instanceof GroundPathNavigation navigation) {
            navigation.setCanOpenDoors(true);
            navigation.setCanFloat(true);
            navigation.setCanWalkOverFences(false);
        }

        this.setPathfindingMalus(PathType.DOOR_OPEN, 0.0F);
        this.setPathfindingMalus(PathType.DOOR_WOOD_CLOSED, 0.0F);
        this.setPathfindingMalus(PathType.WALKABLE_DOOR, 0.0F);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.LAVA, -1.0F);
        this.setPathfindingMalus(PathType.WATER, 2.0F);
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);

        if (this.getCommandSenderWorld() instanceof ServerLevel serverWorld) {
            // Completely remove the dead entity's UUID from registry since it will respawn as a new entity
            CompanionRegistry.remove(this.getUUID());
            CompanionPlayerList.unlist(serverWorld.getServer(), this.getUUID());
            serverWorld.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("§c[AI Companion] " + this.getName().getString() + " died: " + damageSource.type().msgId()),
                    false
            );

            // Schedule a respawn at home position (or death location if no home set) after 5 seconds
            String name = this.getName().getString();
            int variant = this.getAppearanceVariant();
            BlockPos spawnPos = homePosition != null ? homePosition : this.blockPosition();
            AiCompanionMod.scheduleRespawn(serverWorld, name, variant, spawnPos, homePosition != null, 100);
        }
    }

    private boolean isIgnoredEnvironmentalDamage(DamageSource source) {
        String damageId = source.type().msgId();
        return "inWall".equals(damageId)
                || "cramming".equals(damageId);
    }

    public boolean canTargetHostile(LivingEntity target) {
        if (target == null || !target.isAlive() || target.isRemoved()) {
            return false;
        }
        if (target instanceof Creeper || target instanceof Witch) {
            return "guardian".equals(getAppearanceVariantName());
        }

        double distanceSquared = this.distanceToSqr(target);
        if (distanceSquared <= 16.0) {
            return true;
        }

        return !hasWaterBetween(target);
    }

    private boolean hasWaterBetween(LivingEntity target) {
        Level world = this.getCommandSenderWorld();
        Vec3 start = new Vec3(this.getX(), this.getY(), this.getZ());
        Vec3 end = new Vec3(target.getX(), target.getY(), target.getZ());
        Vec3 delta = end.subtract(start);
        int steps = Math.max(4, Math.min(32, (int) Math.ceil(Math.sqrt(delta.x * delta.x + delta.z * delta.z) * 2.0)));

        for (int i = 1; i < steps; i++) {
            double t = (double) i / (double) steps;
            BlockPos pos = BlockPos.containing(
                    start.x + delta.x * t,
                    start.y + delta.y * t,
                    start.z + delta.z * t
            );

            if (world.getFluidState(pos).is(FluidTags.WATER)
                    || world.getFluidState(pos.below()).is(FluidTags.WATER)
                    || world.getFluidState(pos.above()).is(FluidTags.WATER)) {
                return true;
            }
        }

        return false;
    }
}
