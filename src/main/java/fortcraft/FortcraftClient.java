package fortcraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public class FortcraftClient implements ClientModInitializer {

    private BlockPos lastBuiltGrid = null;

    @Override
    public void onInitializeClient() {
        // Turbo build
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            if (!client.options.useKey.isPressed()) {
                lastBuiltGrid = null;
                return;
            }

            boolean isWall = client.player.getMainHandStack().isOf(Items.PAPER);
            boolean isRamp = client.player.getMainHandStack().isOf(Items.FEATHER);
            boolean isFloor = client.player.getMainHandStack().isOf(Items.BOWL);
            boolean isCone = client.player.getMainHandStack().isOf(Items.WHEAT);

            if (isWall || isRamp || isFloor || isCone) {
                Vec3d targetPos = client.player.getEyePos().add(client.player.getRotationVector().multiply(4.0));
                if (client.player.getPitch() > 60.0f) targetPos = client.player.getPos();

                int gridSize = 4;
                int cx = Math.floorDiv((int)Math.floor(targetPos.x), gridSize) * gridSize;
                int cy = Math.floorDiv((int)Math.floor(targetPos.y), gridSize) * gridSize;
                int cz = Math.floorDiv((int)Math.floor(targetPos.z), gridSize) * gridSize;

                BlockPos currentGrid = new BlockPos(cx, cy, cz);

                if (!currentGrid.equals(lastBuiltGrid)) {
                    client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                    lastBuiltGrid = currentGrid;
                }
            }
        });

        WorldRenderEvents.LAST.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;

            boolean isWall = client.player.getMainHandStack().isOf(Items.PAPER);
            boolean isRamp = client.player.getMainHandStack().isOf(Items.FEATHER);
            boolean isFloor = client.player.getMainHandStack().isOf(Items.BOWL);
            boolean isCone = client.player.getMainHandStack().isOf(Items.WHEAT);
            boolean isFort = client.player.getMainHandStack().isOf(Items.EGG);

            if (!isWall && !isRamp && !isFloor && !isCone && !isFort) return;

            Vec3d targetPos = client.player.getEyePos().add(client.player.getRotationVector().multiply(4.0));
            if (client.player.getPitch() > 60.0f) targetPos = client.player.getPos();

            int gridSize = 4;
            int cellStartX = Math.floorDiv((int)Math.floor(targetPos.x), gridSize) * gridSize;
            int cellStartY = Math.floorDiv((int)Math.floor(targetPos.y), gridSize) * gridSize;
            int cellStartZ = Math.floorDiv((int)Math.floor(targetPos.z), gridSize) * gridSize;

            Direction facing = client.player.getHorizontalFacing();
            Vec3d cameraPos = context.camera().getPos();
            MatrixStack matrices = context.matrixStack();
            VertexConsumer buffer = context.consumers().getBuffer(RenderLayer.getLines());

            if (isWall) {
                double distToWest = targetPos.x - cellStartX;
                double distToEast = (cellStartX + gridSize) - targetPos.x;
                double distToNorth = targetPos.z - cellStartZ;
                double distToSouth = (cellStartZ + gridSize) - targetPos.z;

                Box wallBox;
                if (facing == Direction.NORTH || facing == Direction.SOUTH) {
                    int lockedZ = (distToNorth < distToSouth) ? cellStartZ : cellStartZ + gridSize;
                    wallBox = new Box(cellStartX, cellStartY, lockedZ, cellStartX + 5, cellStartY + 5, lockedZ + 1);
                } else {
                    int lockedX = (distToWest < distToEast) ? cellStartX : cellStartX + gridSize;
                    wallBox = new Box(lockedX, cellStartY, cellStartZ, lockedX + 1, cellStartY + 5, cellStartZ + 5);
                }
                drawCustomBox(matrices, buffer, wallBox.offset(-cameraPos.x, -cameraPos.y, -cameraPos.z), 0.0F, 0.5F, 1.0F, 0.8F);

            } else if (isFloor) {
                Box floorBox = new Box(cellStartX, cellStartY, cellStartZ, cellStartX + 5, cellStartY + 0.2, cellStartZ + 5);
                drawCustomBox(matrices, buffer, floorBox.offset(-cameraPos.x, -cameraPos.y, -cameraPos.z), 0.0F, 1.0F, 0.0F, 0.8F);

            } else if (isRamp) {
                for (int step = 0; step <= gridSize; step++) {
                    int yOff = step;
                    Box stepBox;
                    if (facing == Direction.NORTH) stepBox = new Box(cellStartX, cellStartY + yOff, cellStartZ + (gridSize - step), cellStartX + 5, cellStartY + yOff + 1, cellStartZ + (gridSize - step) + 1);
                    else if (facing == Direction.SOUTH) stepBox = new Box(cellStartX, cellStartY + yOff, cellStartZ + step, cellStartX + 5, cellStartY + yOff + 1, cellStartZ + step + 1);
                    else if (facing == Direction.WEST) stepBox = new Box(cellStartX + (gridSize - step), cellStartY + yOff, cellStartZ, cellStartX + (gridSize - step) + 1, cellStartY + yOff + 1, cellStartZ + 5);
                    else stepBox = new Box(cellStartX + step, cellStartY + yOff, cellStartZ, cellStartX + step + 1, cellStartY + yOff + 1, cellStartZ + 5);
                    drawCustomBox(matrices, buffer, stepBox.offset(-cameraPos.x, -cameraPos.y, -cameraPos.z), 1.0F, 1.0F, 0.0F, 0.8F);
                }
            } else if (isCone) {
                for (int step = 0; step <= 2; step++) {
                    int min = step;
                    int max = gridSize - step;
                    for (int x = min; x <= max; x++) {
                        for (int z = min; z <= max; z++) {
                            if (x == min || x == max || z == min || z == max) {
                                Box coneBox = new Box(cellStartX + x, cellStartY + step, cellStartZ + z, cellStartX + x + 1, cellStartY + step + 1, cellStartZ + z + 1);
                                drawCustomBox(matrices, buffer, coneBox.offset(-cameraPos.x, -cameraPos.y, -cameraPos.z), 1.0F, 0.0F, 0.0F, 0.8F);
                            }
                        }
                    }
                }
            } else if (isFort) {
                Vec3d pos = client.player.getEyePos();
                float pitch = client.player.getPitch();
                float yaw = client.player.getYaw();

                float vx = -MathHelper.sin(yaw * 0.017453292F) * MathHelper.cos(pitch * 0.017453292F);
                float vy = -MathHelper.sin(pitch * 0.017453292F);
                float vz = MathHelper.cos(yaw * 0.017453292F) * MathHelper.cos(pitch * 0.017453292F);
                Vec3d vel = new Vec3d(vx, vy, vz).normalize().multiply(1.5);

                Vec3d lastPos = pos;
                BlockPos impactPos = null;

                for (int i = 0; i < 100; i++) {
                    pos = pos.add(vel);
                    vel = vel.multiply(0.99).subtract(0, 0.03, 0);

                    drawLine(matrices.peek(), buffer,
                        (float)(lastPos.x - cameraPos.x), (float)(lastPos.y - cameraPos.y), (float)(lastPos.z - cameraPos.z), 
                        (float)(pos.x - cameraPos.x), (float)(pos.y - cameraPos.y), (float)(pos.z - cameraPos.z), 
                        1.0F, 1.0F, 1.0F, 0.8F);

                    BlockPos checkPos = BlockPos.ofFloored(pos);
                    if (!client.world.getBlockState(checkPos).isReplaceable()) {
                        impactPos = checkPos;
                        break;
                    }
                    lastPos = pos;
                }

                if (impactPos != null) {
                    int cX = Math.floorDiv(impactPos.getX(), gridSize) * gridSize;
                    int cY = impactPos.getY();
                    int cZ = Math.floorDiv(impactPos.getZ(), gridSize) * gridSize;

                    Box fortBox = new Box(cX - 6, cY, cZ - 6, cX + 11, cY + 11, cZ + 11);
                    drawCustomBox(matrices, buffer, fortBox.offset(-cameraPos.x, -cameraPos.y, -cameraPos.z), 0.0F, 1.0F, 1.0F, 0.8F);
                }
            }

        });
    }

    private void drawCustomBox(MatrixStack matrices, VertexConsumer vertexConsumer, Box box, float r, float g, float b, float a) {
        MatrixStack.Entry entry = matrices.peek();
        float minX = (float) box.minX; float minY = (float) box.minY; float minZ = (float) box.minZ;
        float maxX = (float) box.maxX; float maxY = (float) box.maxY; float maxZ = (float) box.maxZ;
        drawLine(entry, vertexConsumer, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        drawLine(entry, vertexConsumer, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        drawLine(entry, vertexConsumer, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        drawLine(entry, vertexConsumer, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);
        drawLine(entry, vertexConsumer, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(entry, vertexConsumer, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(entry, vertexConsumer, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        drawLine(entry, vertexConsumer, minX, maxY, maxZ, minX, minY, maxZ, r, g, b, a);
        drawLine(entry, vertexConsumer, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        drawLine(entry, vertexConsumer, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(entry, vertexConsumer, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(entry, vertexConsumer, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private void drawLine(MatrixStack.Entry entry, VertexConsumer vertexConsumer, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        vertexConsumer.vertex(entry.getPositionMatrix(), x1, y1, z1).color(r, g, b, a).normal(entry, x2 - x1, y2 - y1, z2 - z1);
        vertexConsumer.vertex(entry.getPositionMatrix(), x2, y2, z2).color(r, g, b, a).normal(entry, x2 - x1, y2 - y1, z2 - z1);
    }
}