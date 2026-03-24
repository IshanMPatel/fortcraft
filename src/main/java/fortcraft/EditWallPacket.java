package fortcraft;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.BlockState;
import net.minecraft.world.World;

// C2S packet: player confirms a wall edit.
public record EditWallPacket(BlockPos origin, int facingOrdinal, int bitmask) implements CustomPayload {

    public static final int GRID_SIZE = 5;
    public static final int TOTAL_CELLS = GRID_SIZE * GRID_SIZE;
    public static final int MAX_BITMASK = (1 << TOTAL_CELLS) - 1;

    public static final CustomPayload.Id<EditWallPacket> ID =
            new CustomPayload.Id<>(Identifier.of(Fortcraft.MOD_ID, "edit_wall"));

    public static final PacketCodec<RegistryByteBuf, EditWallPacket> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, EditWallPacket::origin,
            PacketCodecs.INTEGER,  EditWallPacket::facingOrdinal,
            PacketCodecs.INTEGER,  EditWallPacket::bitmask,
            EditWallPacket::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void registerServer() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                World world = player.getWorld();
                BlockPos origin = payload.origin();
                Direction facing = Direction.byId(payload.facingOrdinal());
                int bitmask = Math.max(0, Math.min(MAX_BITMASK, payload.bitmask()));

                if (!player.getBlockPos().isWithinDistance(origin, 20)) return;

                int material = 0;
                outerLoop:
                for (int y = 0; y < GRID_SIZE; y++) {
                    for (int w = 0; w < GRID_SIZE; w++) {
                        BlockPos bp = getWallBlockPos(origin, facing, w, y);
                        BlockState bs = world.getBlockState(bp);
                        if (bs.isOf(Fortcraft.FORT_WALL)) {
                            material = bs.get(FortWallBlock.MATERIAL);
                            break outerLoop;
                        }
                    }
                }

                // Apply edit to each cell (1 block per cell)
                BlockState wallState = Fortcraft.FORT_WALL.getDefaultState()
                        .with(FortWallBlock.MATERIAL, material);
                
                if (facing.getAxis().isHorizontal()) {
                    wallState = wallState.with(FortWallBlock.FACING, facing);
                }

                for (int y = 0; y < GRID_SIZE; y++) {
                    for (int w = 0; w < GRID_SIZE; w++) {
                        BlockPos bp = getWallBlockPos(origin, facing, w, y);
                        int cellIndex = y * GRID_SIZE + w;
                        boolean isOpen = ((bitmask >> cellIndex) & 1) == 1;

                        if (isOpen) {
                            BlockState current = world.getBlockState(bp);
                            if (current.isOf(Fortcraft.FORT_WALL)) {
                                world.removeBlock(bp, false);
                            }
                        } else {
                            BlockState current = world.getBlockState(bp);
                            if (current.isAir() || current.isReplaceable()) {
                                world.setBlockState(bp, wallState);
                            }
                        }
                    }
                }
            });
        });
    }

    public static BlockPos getWallBlockPos(BlockPos origin, Direction facing, int w, int y) {
        return switch (facing) {
            case NORTH -> origin.add(w, y, 0);
            case SOUTH -> origin.add(w, y, 0);
            case EAST  -> origin.add(0, y, w);
            case WEST  -> origin.add(0, y, w);
            case UP, DOWN -> origin.add(w, 0, y);
            default    -> origin.add(w, y, 0);
        };
    }

    public static void send(BlockPos origin, Direction facing, int bitmask) {
        ClientPlayNetworking.send(new EditWallPacket(origin, facing.getId(), bitmask));
    }
}
