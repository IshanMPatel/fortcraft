package fortcraft;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Fortcraft implements ModInitializer {
	public static final String MOD_ID = "fortcraft";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Fortcraft INSTANCE;

    public static final Identifier FORT_WALL_ID = Identifier.of(MOD_ID, "fort_wall");
    public static final RegistryKey<Block> FORT_WALL_KEY = RegistryKey.of(RegistryKeys.BLOCK, FORT_WALL_ID);
    public static final Block FORT_WALL = new FortWallBlock(AbstractBlock.Settings.copy(Blocks.COBBLESTONE).registryKey(FORT_WALL_KEY));

    public static final Identifier FORT_RAMP_ID = Identifier.of(MOD_ID, "fort_ramp");
    public static final RegistryKey<Block> FORT_RAMP_KEY = RegistryKey.of(RegistryKeys.BLOCK, FORT_RAMP_ID);
    public static final Block FORT_RAMP = new FortRampBlock(FORT_WALL.getDefaultState(), AbstractBlock.Settings.copy(Blocks.COBBLESTONE).registryKey(FORT_RAMP_KEY));

    // Material tracker (0=Wood, 1=Brick, 2=Metal)
    public static final Map<UUID, Integer> PLAYER_MATERIALS = new HashMap<>();

    public Fortcraft() {
        INSTANCE = this;
    }

    private ActionResult handleInteraction(PlayerEntity player, World world, Hand hand) {
        boolean isBlueprint = player.getStackInHand(hand).isOf(Items.PAPER) ||
                              player.getStackInHand(hand).isOf(Items.FEATHER) ||
                              player.getStackInHand(hand).isOf(Items.BOWL) ||
                              player.getStackInHand(hand).isOf(Items.WHEAT);

        if (!isBlueprint) return ActionResult.PASS;

        // Material swapping (Sneak + Right Click)
        if (player.isSneaking()) {
            if (!world.isClient && hand == Hand.MAIN_HAND) {
                var blueprintStack = player.getStackInHand(hand);
                if (!player.getItemCooldownManager().isCoolingDown(blueprintStack)) {
                    int currentMat = PLAYER_MATERIALS.getOrDefault(player.getUuid(), 0);
                    int nextMat = (currentMat + 1) % 3; // Cycles 0 -> 1 -> 2 -> 0
                    PLAYER_MATERIALS.put(player.getUuid(), nextMat);
                    String matName = nextMat == 0 ? "Wood" : (nextMat == 1 ? "Brick" : "Metal");
                    Formatting color = nextMat == 0 ? Formatting.YELLOW : (nextMat == 1 ? Formatting.RED : Formatting.GRAY);
                    player.sendMessage(Text.literal("Material: " + matName).formatted(color), true);
                    player.getItemCooldownManager().set(blueprintStack, 5);
                }
            }
            return ActionResult.SUCCESS;
        }

        int material = PLAYER_MATERIALS.getOrDefault(player.getUuid(), 0);

        if (player.getStackInHand(hand).isOf(Items.PAPER)) return buildWall(player, world, material);
        if (player.getStackInHand(hand).isOf(Items.FEATHER)) return buildRamp(player, world, material);
        if (player.getStackInHand(hand).isOf(Items.BOWL)) return buildFloor(player, world, material);
        if (player.getStackInHand(hand).isOf(Items.WHEAT)) return buildCone(player, world, material);

        return ActionResult.PASS;
    }

    private boolean checkSupport(World world, Set<BlockPos> blueprint) {
        for (BlockPos pos : blueprint) {
            if (!world.getBlockState(pos).isReplaceable()) {
                return true;
            }
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.offset(dir);
                if (!blueprint.contains(neighbor) && !world.getBlockState(neighbor).isReplaceable()) {
                    return true;
                }
            }
        }
        return false;
    }

    private ActionResult placeBlueprint(World world, Map<BlockPos, BlockState> blueprint) {
        if (!checkSupport(world, blueprint.keySet())) return ActionResult.PASS;

        for (Map.Entry<BlockPos, BlockState> entry : blueprint.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState newState = entry.getValue();
            BlockState oldState = world.getBlockState(pos);

            if (newState.getBlock() instanceof StairsBlock) {
                world.setBlockState(pos, newState);
                if (world.getBlockState(pos.down()).isOf(Blocks.COBBLESTONE)) {
                    world.removeBlock(pos.down(), false);
                }
            } else if (newState.getBlock() == FORT_WALL) {
                if (oldState.getBlock() instanceof StairsBlock) continue;
                if (world.getBlockState(pos.up()).getBlock() instanceof StairsBlock) continue;
                world.setBlockState(pos, newState);
            } else if (oldState.isReplaceable()) {
                world.setBlockState(pos, newState);
            }
        }
        return ActionResult.SUCCESS;
    }

    private ActionResult buildWall(PlayerEntity player, World world, int material) {
        Vec3d targetPos = getTargetPos(player);
        int gridSize = 4;
        int cellStartX = Math.floorDiv((int)Math.floor(targetPos.x), gridSize) * gridSize;
        int cellStartY = Math.floorDiv((int)Math.floor(targetPos.y), gridSize) * gridSize;
        int cellStartZ = Math.floorDiv((int)Math.floor(targetPos.z), gridSize) * gridSize;

        Direction facing = player.getHorizontalFacing();

        double distToWest = targetPos.x - cellStartX;
        double distToEast = (cellStartX + gridSize) - targetPos.x;
        double distToNorth = targetPos.z - cellStartZ;
        double distToSouth = (cellStartZ + gridSize) - targetPos.z;

        Map<BlockPos, BlockState> blueprint = new HashMap<>();
        BlockState wallState = FORT_WALL.getDefaultState().with(FortWallBlock.MATERIAL, material);

        for (int y = 0; y <= gridSize; y++) {
            for (int w = 0; w <= gridSize; w++) {
                BlockPos pos;
                if (facing == Direction.NORTH || facing == Direction.SOUTH) {
                    int lockedZ = (distToNorth < distToSouth) ? cellStartZ : cellStartZ + gridSize;
                    pos = new BlockPos(cellStartX + w, cellStartY + y, lockedZ);
                    wallState = wallState.with(FortWallBlock.FACING, distToNorth < distToSouth ? Direction.NORTH : Direction.SOUTH);
                } else {
                    int lockedX = (distToWest < distToEast) ? cellStartX : cellStartX + gridSize;
                    pos = new BlockPos(lockedX, cellStartY + y, cellStartZ + w);
                    wallState = wallState.with(FortWallBlock.FACING, distToWest < distToEast ? Direction.WEST : Direction.EAST);
                }
                blueprint.put(pos, wallState);
            }
        }
        return placeBlueprint(world, blueprint);
    }

    private ActionResult buildFloor(PlayerEntity player, World world, int material) {
        Vec3d targetPos = getTargetPos(player);
        int gridSize = 4;
        int cellX = Math.floorDiv((int)Math.floor(targetPos.x), gridSize) * gridSize;
        int cellY = Math.floorDiv((int)Math.floor(targetPos.y), gridSize) * gridSize;
        int cellZ = Math.floorDiv((int)Math.floor(targetPos.z), gridSize) * gridSize;

        Map<BlockPos, BlockState> blueprint = new HashMap<>();
        BlockState floorState = FORT_WALL.getDefaultState().with(FortWallBlock.MATERIAL, material);

        for (int x = 0; x <= gridSize; x++) {
            for (int z = 0; z <= gridSize; z++) {
                blueprint.put(new BlockPos(cellX + x, cellY, cellZ + z), floorState);
            }
        }
        return placeBlueprint(world, blueprint);
    }

    private ActionResult buildRamp(PlayerEntity player, World world, int material) {
        Vec3d targetPos = getTargetPos(player);
        int gridSize = 4;
        int cellX = Math.floorDiv((int)Math.floor(targetPos.x), gridSize) * gridSize;
        int cellY = Math.floorDiv((int)Math.floor(targetPos.y), gridSize) * gridSize;
        int cellZ = Math.floorDiv((int)Math.floor(targetPos.z), gridSize) * gridSize;

        Direction facing = player.getHorizontalFacing();
        BlockState state = FORT_RAMP.getDefaultState().with(StairsBlock.FACING, facing).with(FortRampBlock.MATERIAL, material);

        Map<BlockPos, BlockState> blueprint = new HashMap<>();
        for (int step = 0; step <= gridSize; step++) {
            for (int w = 0; w <= gridSize; w++) {
                int px = cellX; int pz = cellZ;
                if (facing == Direction.NORTH) { px = cellX + w; pz = cellZ + (gridSize - step); }
                else if (facing == Direction.SOUTH) { px = cellX + w; pz = cellZ + step; }
                else if (facing == Direction.WEST) { pz = cellZ + w; px = cellX + (gridSize - step); }
                else { pz = cellZ + w; px = cellX + step; }
                blueprint.put(new BlockPos(px, cellY + step, pz), state);
            }
        }
        return placeBlueprint(world, blueprint);
    }

    private ActionResult buildCone(PlayerEntity player, World world, int material) {
        Vec3d targetPos = getTargetPos(player);
        int gridSize = 4;
        int cellX = Math.floorDiv((int)Math.floor(targetPos.x), gridSize) * gridSize;
        int cellY = Math.floorDiv((int)Math.floor(targetPos.y), gridSize) * gridSize;
        int cellZ = Math.floorDiv((int)Math.floor(targetPos.z), gridSize) * gridSize;

        Map<BlockPos, BlockState> blueprint = new HashMap<>();
        BlockState wallState = FORT_WALL.getDefaultState().with(FortWallBlock.MATERIAL, material);

        for (int step = 0; step <= 2; step++) {
            int min = step;
            int max = gridSize - step;
            for (int x = min; x <= max; x++) {
                for (int z = min; z <= max; z++) {
                    if (x == min || x == max || z == min || z == max) {
                        BlockPos pos = new BlockPos(cellX + x, cellY + step, cellZ + z);
                        if (step == 2) {
                            blueprint.put(pos, wallState);
                        } else {
                            Direction facing = Direction.NORTH;
                            if (z == min) facing = Direction.SOUTH;
                            else if (z == max) facing = Direction.NORTH;
                            else if (x == min) facing = Direction.EAST;
                            else if (x == max) facing = Direction.WEST;
                            blueprint.put(pos, FORT_RAMP.getDefaultState().with(StairsBlock.FACING, facing).with(FortRampBlock.MATERIAL, material));
                        }
                    }
                }
            }
        }
        return placeBlueprint(world, blueprint);
    }

    public void spawnPortaFortAt(World world, BlockPos targetPos) {
        int gridSize = 4;
        int cx = Math.floorDiv(targetPos.getX(), gridSize) * gridSize;
        int cy = targetPos.getY();
        int cz = Math.floorDiv(targetPos.getZ(), gridSize) * gridSize;

        Map<BlockPos, BlockState> blueprint = new HashMap<>();
        BlockState wall = Blocks.STONE_BRICKS.getDefaultState();
        BlockState floor = Blocks.STONE_BRICKS.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();

        int towerHeight = 8;

        for (int y = 0; y <= towerHeight; y++) {
            for (int x = 0; x <= gridSize; x++) {
                for (int z = 0; z <= gridSize; z++) {
                    boolean isOuter = (x == 0 || x == gridSize || z == 0 || z == gridSize);

                    if (isOuter) {
                        if (z == 0 && (x == 1 || x == 2 || x == 3) && y < 3) {
                            blueprint.put(new BlockPos(cx + x, cy + y, cz + z), air);
                        } else {
                            blueprint.put(new BlockPos(cx + x, cy + y, cz + z), wall);
                        }
                    } else {
                        if (y == 0) {
                            blueprint.put(new BlockPos(cx + x, cy + y, cz + z), floor);
                        } else {
                            blueprint.put(new BlockPos(cx + x, cy + y, cz + z), air);
                        }
                    }
                }
            }
        }
        for (int y = 1; y <= towerHeight; y++) {
            blueprint.put(new BlockPos(cx + 2, cy + y, cz + 3), Blocks.LADDER.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
        }
        int topY = cy + towerHeight;
        for (int x = -1; x <= gridSize + 1; x++) {
            for (int z = -1; z <= gridSize + 1; z++) {
                if (x >= 1 && x <= 3 && z >= 1 && z <= 3) continue;
                blueprint.put(new BlockPos(cx + x, topY, cz + z), floor);
            }
        }
        int rampHeight = 3;
        for (int r = 1; r <= rampHeight; r++) {
            int currentY = topY + r;
            int minEdge = -1 - r;
            int maxEdge = gridSize + 1 + r;

            for (int i = minEdge; i <= maxEdge; i++) {
                blueprint.put(new BlockPos(cx + i, currentY - 1, cz + minEdge), wall);
                blueprint.put(new BlockPos(cx + i, currentY - 1, cz + maxEdge), wall);
                blueprint.put(new BlockPos(cx + minEdge, currentY - 1, cz + i), wall);
                blueprint.put(new BlockPos(cx + maxEdge, currentY - 1, cz + i), wall);

                blueprint.put(new BlockPos(cx + i, currentY, cz + minEdge), Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH));
                blueprint.put(new BlockPos(cx + i, currentY, cz + maxEdge), Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
                blueprint.put(new BlockPos(cx + minEdge, currentY, cz + i), Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.WEST));
                blueprint.put(new BlockPos(cx + maxEdge, currentY, cz + i), Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST));
            }

            blueprint.put(new BlockPos(cx + minEdge, currentY, cz + minEdge), wall);
            blueprint.put(new BlockPos(cx + maxEdge, currentY, cz + minEdge), wall);
            blueprint.put(new BlockPos(cx + minEdge, currentY, cz + maxEdge), wall);
            blueprint.put(new BlockPos(cx + maxEdge, currentY, cz + maxEdge), wall);
        }

        placeBlueprint(world, blueprint);
    }

    private Vec3d getTargetPos(PlayerEntity player) {
        Vec3d target = player.getEyePos().add(player.getRotationVector().multiply(4.0));
        if (player.getPitch() > 60.0f) {
            target = player.getPos();
        }
        return target;
    }

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");

        Registry.register(Registries.BLOCK, FORT_WALL_ID, FORT_WALL);
        Registry.register(Registries.BLOCK, FORT_RAMP_ID, FORT_RAMP);

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> handleInteraction(player, world, hand));
        UseItemCallback.EVENT.register((player, world, hand) -> handleInteraction(player, world, hand));
	}
}