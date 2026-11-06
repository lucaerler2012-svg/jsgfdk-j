package com.example.client;

import com.example.TemplateMod;
import com.example.config.ModConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class TemplateModClient implements ClientModInitializer {

    // State machine states
    public enum State {
        IDLE,
        AIM_BLOCK,           // Aiming at block to place crystal/obsidian
        PLACE_OBSIDIAN,      // Placing obsidian (obsidian mode)
        PLACE_CRYSTAL,       // Placing end crystal
        AIM_CRYSTAL,         // Aiming at placed crystal
        ATTACK_CRYSTAL,      // Attacking crystal
        WAIT_TICK            // Waiting for server sync
    }

    private State currentState = State.IDLE;
    private int tickCounter = 0;
    private BlockPos targetBlockPos = null;
    private Entity targetCrystal = null;
    private boolean isObsidianMode = false;
    private Direction targetFace = Direction.UP;

    @Override
    public void onInitializeClient() {
        // Register keybinds
        Keybinds.register();

        // Register tick event
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        TemplateMod.LOGGER.info("Crystal Auto Client initialized!");
    }

    private void onClientTick(Minecraft mc) {
        // Handle keybind press
        Keybinds.onKeyPress();

        ModConfig config = ModConfig.getInstance();
        if (!config.enabled) {
            resetState();
            return;
        }

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        if (player == null || level == null) {
            resetState();
            return;
        }

        // Check creative mode requirement
        if (config.onlyInCreative && !player.getAbilities().instabuild) {
            return;
        }

        // Get held item
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        // Determine mode based on held item
        boolean holdingCrystal = mainHand.is(Items.END_CRYSTAL) || offHand.is(Items.END_CRYSTAL);
        boolean holdingObsidian = mainHand.is(Items.OBSIDIAN) || offHand.is(Items.OBSIDIAN);

        // State machine
        switch (currentState) {
            case IDLE -> handleIdleState(mc, player, level, holdingCrystal, holdingObsidian, config);
            case AIM_BLOCK -> handleAimBlockState(mc, player, level, config);
            case PLACE_OBSIDIAN -> handlePlaceObsidianState(mc, player, level, config);
            case PLACE_CRYSTAL -> handlePlaceCrystalState(mc, player, level, config);
            case AIM_CRYSTAL -> handleAimCrystalState(mc, player, level, config);
            case ATTACK_CRYSTAL -> handleAttackCrystalState(mc, player, level, config);
            case WAIT_TICK -> handleWaitTickState(mc, player, level, config);
        }

        // Update HUD
        if (config.showHudStatus && mc.gui != null && mc.gui.getChat() != null) {
            // HUD rendering would go here if needed
        }
    }

    private void handleIdleState(Minecraft mc, LocalPlayer player, ClientLevel level,
                                 boolean holdingCrystal, boolean holdingObsidian, ModConfig config) {
        // Check for crystal mode
        if (config.crystalModeEnabled && holdingCrystal && mc.crosshairPickEntity != null) {
            Entity entity = mc.crosshairPickEntity;
            if (entity instanceof EndCrystal crystal) {
                // Crystal in crosshair - try to attack it
                if (canReachEntity(player, crystal, config.crystalReach)) {
                    currentState = State.AIM_CRYSTAL;
                    targetCrystal = crystal;
                    tickCounter = 0;
                }
            }
        }

        // Check for block placement (right-click on block)
        if (mc.hitResult instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            Direction face = blockHit.getDirection();

            // Crystal mode: right-click with crystal on block
            if (config.crystalModeEnabled && holdingCrystal && !holdingObsidian) {
                if (canPlaceCrystalOnBlock(level, pos, face)) {
                    isObsidianMode = false;
                    targetBlockPos = pos.relative(face);
                    targetFace = face;
                    currentState = State.AIM_BLOCK;
                    tickCounter = 0;
                    return;
                }
            }

            // Obsidian mode: right-click with obsidian
            if (config.obsidianModeEnabled && holdingObsidian) {
                if (canPlaceObsidianOnBlock(level, pos, face)) {
                    isObsidianMode = true;
                    targetBlockPos = pos.relative(face);
                    targetFace = face;
                    currentState = State.AIM_BLOCK;
                    tickCounter = 0;
                    return;
                }
            }
        }
    }

    private void handleAimBlockState(Minecraft mc, LocalPlayer player, ClientLevel level, ModConfig config) {
        if (targetBlockPos == null) {
            resetState();
            return;
        }

        // Calculate look angles to target block
        Vec3 targetPos = Vec3.atCenterOf(targetBlockPos).add(0.5, 0.5, 0.5);
        rotateToTarget(player, targetPos, config.maxTurnSpeed);

        // Check if we're looking at the target
        if (isLookingAt(player, targetPos, 2.0f)) {
            tickCounter++;
            int delay = isObsidianMode ? config.obsidianDelayTicks : config.crystalDelayTicks;

            if (tickCounter >= delay) {
                if (isObsidianMode) {
                    currentState = State.PLACE_OBSIDIAN;
                } else {
                    currentState = State.PLACE_CRYSTAL;
                }
                tickCounter = 0;
            }
        } else {
            tickCounter = 0;
        }
    }

    private void handlePlaceObsidianState(Minecraft mc, LocalPlayer player, ClientLevel level, ModConfig config) {
        if (targetBlockPos == null) {
            resetState();
            return;
        }

        // Place obsidian
        InteractionResult result = placeBlock(player, mc, targetBlockPos, Items.OBSIDIAN.getDefaultInstance(), targetFace);

        if (result.consumesAction()) {
            // Wait for server to process
            currentState = State.WAIT_TICK;
            tickCounter = 0;
        } else {
            resetState();
        }
    }

    private void handlePlaceCrystalState(Minecraft mc, LocalPlayer player, ClientLevel level, ModConfig config) {
        if (targetBlockPos == null) {
            resetState();
            return;
        }

        // Place end crystal on the block
        InteractionResult result = placeBlock(player, mc, targetBlockPos, Items.END_CRYSTAL.getDefaultInstance(), Direction.UP);

        if (result.consumesAction()) {
            // Wait for server to process and crystal to spawn
            currentState = State.WAIT_TICK;
            tickCounter = 0;
        } else {
            resetState();
        }
    }

    private void handleWaitTickState(Minecraft mc, LocalPlayer player, ClientLevel level, ModConfig config) {
        tickCounter++;

        if (isObsidianMode) {
            // After placing obsidian, wait a bit then place crystal
            if (tickCounter >= config.obsidianDelayTicks) {
                // Now we need to place crystal on the obsidian
                currentState = State.PLACE_CRYSTAL;
                tickCounter = 0;
            }
        } else {
            // After placing crystal, wait for it to spawn on server
            if (tickCounter >= config.crystalDelayTicks) {
                // Find the crystal we just placed
                targetCrystal = findCrystalAt(level, targetBlockPos);
                if (targetCrystal != null) {
                    currentState = State.AIM_CRYSTAL;
                    tickCounter = 0;
                } else {
                    // Crystal not found yet, wait a bit more
                    if (tickCounter > config.crystalDelayTicks * 3) {
                        resetState();
                    }
                }
            }
        }
    }

    private void handleAimCrystalState(Minecraft mc, LocalPlayer player, ClientLevel level, ModConfig config) {
        if (targetCrystal == null || !targetCrystal.isAlive()) {
            resetState();
            return;
        }

        // Check if crystal still exists on server side
        if (!level.getEntitiesOfClass(EndCrystal.class, targetCrystal.getBoundingBox().inflate(1.0)).contains(targetCrystal)) {
            // Crystal might have been removed/exploded
            resetState();
            return;
        }

        // Aim at crystal
        Vec3 crystalPos = targetCrystal.position().add(0, targetCrystal.getBbHeight() * 0.5, 0);
        rotateToTarget(player, crystalPos, config.maxTurnSpeed);

        // Check if we're looking at crystal and in reach
        double reach = isObsidianMode ? config.obsidianReach : config.crystalReach;
        if (isLookingAt(player, crystalPos, 1.5f) && canReachEntity(player, targetCrystal, reach)) {
            tickCounter++;
            int delay = isObsidianMode ? config.obsidianDelayTicks : config.crystalDelayTicks;

            if (tickCounter >= delay) {
                currentState = State.ATTACK_CRYSTAL;
                tickCounter = 0;
            }
        } else {
            tickCounter = 0;
        }
    }

    private void handleAttackCrystalState(Minecraft mc, LocalPlayer player, ClientLevel level, ModConfig config) {
        if (targetCrystal == null || !targetCrystal.isAlive()) {
            resetState();
            return;
        }

        // Attack the crystal
        boolean shouldAttack = isObsidianMode ? config.obsidianAutoAttack : config.crystalAutoAttack;
        if (shouldAttack) {
            mc.gameMode.attack(player, targetCrystal);
            player.swing(InteractionHand.MAIN_HAND);
        }

        resetState();
    }

    // Helper methods

    private boolean canReachEntity(LocalPlayer player, Entity entity, double reach) {
        return player.distanceToSqr(entity) <= reach * reach;
    }

    private boolean canPlaceCrystalOnBlock(ClientLevel level, BlockPos pos, Direction face) {
        BlockPos placePos = pos.relative(face);
        BlockState state = level.getBlockState(placePos);

        // Can only place on bedrock or obsidian
        return (state.is(Blocks.BEDROCK) || state.is(Blocks.OBSIDIAN))
                && level.getBlockState(placePos.above()).isAir()
                && level.getBlockState(placePos.above(2)).isAir();
    }

    private boolean canPlaceObsidianOnBlock(ClientLevel level, BlockPos pos, Direction face) {
        BlockPos placePos = pos.relative(face);
        BlockState state = level.getBlockState(placePos);

        // Can place on any solid block
        return state.isAir() || state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK) || state.isSolid();
    }

    private Entity findCrystalAt(ClientLevel level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(1.0);
        return level.getEntitiesOfClass(EndCrystal.class, box).stream().findFirst().orElse(null);
    }

    private InteractionResult placeBlock(LocalPlayer player, Minecraft mc, BlockPos pos, ItemStack stack, Direction face) {
        // Create block hit result for the placement
        Vec3 hitVec = Vec3.atCenterOf(pos).add(face.getNormal().scale(0.5));
        BlockHitResult hitResult = new BlockHitResult(hitVec, face, pos, false);

        // Use interaction manager to place block (sends proper packets)
        BlockPlaceContext context = new BlockPlaceContext(
                player,
                InteractionHand.MAIN_HAND,
                stack,
                hitResult
        );

        // Check if we can place
        if (!stack.canPlace(context)) {
            return InteractionResult.FAIL;
        }

        // Actually place the block
        return mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);
    }

    private void rotateToTarget(LocalPlayer player, Vec3 target, double maxTurnSpeed) {
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 delta = target.subtract(eyePos);

        double horizontalDist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0f;
        float targetPitch = (float) Math.toDegrees(-Math.atan2(delta.y, horizontalDist));

        // Normalize yaw
        targetYaw = Math.floorMod((int) targetYaw + 360, 360);

        // Calculate smooth rotation
        float currentYaw = player.getYRot();
        float currentPitch = player.getXRot();

        float yawDiff = Math.abs(targetYaw - currentYaw);
        if (yawDiff > 180) yawDiff = 360 - yawDiff;

        float pitchDiff = Math.abs(targetPitch - currentPitch);

        if (yawDiff <= maxTurnSpeed && pitchDiff <= maxTurnSpeed) {
            // Close enough, snap to target
            player.setYRot(targetYaw);
            player.setXRot(targetPitch);
        } else {
            // Smooth rotation
            float newYaw = rotateTowards(currentYaw, targetYaw, (float) maxTurnSpeed);
            float newPitch = rotateTowards(currentPitch, targetPitch, (float) maxTurnSpeed);

            player.setYRot(newYaw);
            player.setXRot(newPitch);
        }
    }

    private float rotateTowards(float current, float target, float maxStep) {
        float diff = target - current;

        // Normalize to -180 to 180
        while (diff <= -180) diff += 360;
        while (diff > 180) diff -= 360;

        if (Math.abs(diff) <= maxStep) {
            return target;
        }

        return current + Math.signum(diff) * maxStep;
    }

    private boolean isLookingAt(LocalPlayer player, Vec3 target, float toleranceDegrees) {
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getViewVector(1.0f);
        Vec3 toTarget = target.subtract(eyePos).normalize();

        double dot = lookVec.dot(toTarget);
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));

        return angle <= toleranceDegrees;
    }

    private void resetState() {
        currentState = State.IDLE;
        tickCounter = 0;
        targetBlockPos = null;
        targetCrystal = null;
        isObsidianMode = false;
        targetFace = Direction.UP;
    }

    public State getCurrentState() {
        return currentState;
    }

    public boolean isActive() {
        return currentState != State.IDLE;
    }
}