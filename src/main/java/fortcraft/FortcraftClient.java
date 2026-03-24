package fortcraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class FortcraftClient implements ClientModInitializer {

    private BlockPos lastBuiltGrid = null;

    private static boolean editing = false;
    private BlockPos editOrigin = null;
    private Direction editFacing = null;
    private boolean[] editGrid = new boolean[25];

    private static KeyBinding editKey;

    private boolean wasLeftDown = false;
    private boolean wasRightDown = false;
    private boolean dragTargetState = false;
    private int lastAimedCell = -1;
    private int lastHotbarSlot = -1;

    public static boolean isEditing() {
        return editing;
    }

    @Override
    public void onInitializeClient() {
        editKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.fortcraft.edit",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.fortcraft"
        ));

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (editing && client.options != null) {
                while (client.options.attackKey.wasPressed()) {}
                client.options.attackKey.setPressed(false);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            while (editKey.wasPressed()) {
                if (!editing) {
                    tryEnterEditMode(client);
                } else {
                    confirmEdit();
                }
            }

            if (editing) {
                boolean cancel = false;
                if (client.player.getInventory().selectedSlot != lastHotbarSlot) {
                    cancel = true;
                } else if (editOrigin != null && client.player.getPos().squaredDistanceTo(editOrigin.toCenterPos()) > 64.0) {
                    cancel = true;
                }
                
                if (cancel || !handleEditClicks(client)) {
                    cancelEdit();
                } else {
                    return;
                }
            }

            if (client.player != null) {
                lastHotbarSlot = client.player.getInventory().selectedSlot;
            }

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

        // Render: preview boxes + edit overlay
        WorldRenderEvents.LAST.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;

            Vec3d cameraPos = context.camera().getPos();
            MatrixStack matrices = context.matrixStack();
            VertexConsumer buffer = context.consumers().getBuffer(RenderLayer.getLines());

            if (editing && editOrigin != null && editFacing != null) {
                renderEditOverlay(matrices, buffer, cameraPos);
                return;
            }

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

    //  Edit-mode methods
    private void tryEnterEditMode(MinecraftClient client) {
        HitResult hit = client.player.raycast(6.0, 0, false);
        if (!(hit instanceof BlockHitResult blockHit)) return;

        BlockPos hitPos = blockHit.getBlockPos();
        BlockState hitState = client.world.getBlockState(hitPos);
        if (!hitState.isOf(Fortcraft.FORT_WALL)) return;

        Direction hitSide = blockHit.getSide();
        boolean hasX = client.world.getBlockState(hitPos.east()).isOf(Fortcraft.FORT_WALL) || client.world.getBlockState(hitPos.west()).isOf(Fortcraft.FORT_WALL);
        boolean hasZ = client.world.getBlockState(hitPos.north()).isOf(Fortcraft.FORT_WALL) || client.world.getBlockState(hitPos.south()).isOf(Fortcraft.FORT_WALL);
        boolean hasY = client.world.getBlockState(hitPos.up()).isOf(Fortcraft.FORT_WALL) || client.world.getBlockState(hitPos.down()).isOf(Fortcraft.FORT_WALL);

        Direction facing;
        if ((hitSide == Direction.UP || hitSide == Direction.DOWN) || (hasX && hasZ && !hasY)) {
            facing = Direction.UP;
        } else {
            facing = hitState.get(FortWallBlock.FACING);
        }

        BlockPos origin = findWallOrigin(client, hitPos, facing);
        if (origin == null) return;

        editOrigin = origin;
        editFacing = facing;
        editGrid = new boolean[25];
        lastHotbarSlot = client.player.getInventory().selectedSlot;

        for (int y = 0; y < 5; y++) {
            for (int w = 0; w < 5; w++) {
                BlockPos bp = EditWallPacket.getWallBlockPos(origin, facing, w, y);
                BlockState bs = client.world.getBlockState(bp);
                if (!bs.isOf(Fortcraft.FORT_WALL)) {
                    editGrid[y * 5 + w] = true; // already open
                }
            }
        }

        editing = true;
    }

    private BlockPos findWallOrigin(MinecraftClient client, BlockPos hitPos, Direction facing) {
        net.minecraft.util.hit.HitResult hit = client.player.raycast(6.0, 0, false);
        if (hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) return hitPos;

        Vec3d targetPos = hit.getPos();

        int originX = (int) Math.round((targetPos.x - 2.5) / 4.0) * 4;
        int originY = (int) Math.round((targetPos.y - 2.5) / 4.0) * 4;
        int originZ = (int) Math.round((targetPos.z - 2.5) / 4.0) * 4;

        if (facing == Direction.UP || facing == Direction.DOWN) {
            return new BlockPos(originX, hitPos.getY(), originZ);
        } else if (facing == Direction.NORTH || facing == Direction.SOUTH) {
            return new BlockPos(originX, originY, hitPos.getZ());
        } else {
            return new BlockPos(hitPos.getX(), originY, originZ);
        }
    }

    private void confirmEdit() {
        if (editOrigin != null && editFacing != null) {
            int bitmask = 0;
            for (int i = 0; i < 25; i++) {
                if (editGrid[i]) bitmask |= (1 << i);
            }
            EditWallPacket.send(editOrigin, editFacing, bitmask);
        }
        editing = false;
        editOrigin = null;
        editFacing = null;
        editGrid = new boolean[25];
    }

    private void cancelEdit() {
        editing = false;
        editOrigin = null;
        editFacing = null;
        editGrid = new boolean[25];
    }

    private boolean handleEditClicks(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        int cell = getAimedCell(client);

        if ((leftDown && !wasLeftDown && cell < 0) || (rightDown && !wasRightDown && cell < 0)) {
            wasLeftDown = leftDown;
            wasRightDown = rightDown;
            return false;
        }

        if (leftDown) {
            if (!wasLeftDown) {
                if (cell >= 0 && cell < 25) {
                    editGrid[cell] = !editGrid[cell];
                    dragTargetState = editGrid[cell];
                    lastAimedCell = cell;
                }
            } else {
                if (cell >= 0 && cell < 25 && cell != lastAimedCell) {
                    editGrid[cell] = dragTargetState;
                    lastAimedCell = cell;
                }
            }
        } else {
            lastAimedCell = -1;
        }

        if (rightDown && !wasRightDown) {
            editGrid = new boolean[25];
        }

        wasLeftDown = leftDown;
        wasRightDown = rightDown;
        return true;
    }

    private int getAimedCell(MinecraftClient client) {
        Vec3d eye = client.player.getEyePos();
        Vec3d look = client.player.getRotationVector();

        double planeCoord;
        double dirComponent;
        double eyeComponent;

        boolean isFloor = (editFacing == Direction.UP || editFacing == Direction.DOWN);
        boolean isNS = (editFacing == Direction.NORTH || editFacing == Direction.SOUTH);
        if (isFloor) {
            planeCoord = (eye.y < editOrigin.getY() + 0.5) ? editOrigin.getY() : editOrigin.getY() + 1.0;
            dirComponent = look.y;
            eyeComponent = eye.y;
        } else if (isNS) {
            planeCoord = (eye.z < editOrigin.getZ() + 0.5) ? editOrigin.getZ() : editOrigin.getZ() + 1.0;
            dirComponent = look.z;
            eyeComponent = eye.z;
        } else {
            planeCoord = (eye.x < editOrigin.getX() + 0.5) ? editOrigin.getX() : editOrigin.getX() + 1.0;
            dirComponent = look.x;
            eyeComponent = eye.x;
        }

        if (Math.abs(dirComponent) < 1e-6) return -1;

        double t = (planeCoord - eyeComponent) / dirComponent;
        if (t < 0 || t > 10) return -1;

        Vec3d hitPoint = eye.add(look.multiply(t));

        double localH, localV;
        if (isFloor) {
            localH = hitPoint.x - editOrigin.getX();
            localV = hitPoint.z - editOrigin.getZ();
        } else if (isNS) {
            localH = hitPoint.x - editOrigin.getX();
            localV = hitPoint.y - editOrigin.getY();
        } else {
            localH = hitPoint.z - editOrigin.getZ();
            localV = hitPoint.y - editOrigin.getY();
        }

        int w = (int) Math.floor(localH);
        int y = (int) Math.floor(localV);

        if (w < 0 || w >= 5 || y < 0 || y >= 5) return -1;

        return y * 5 + w;
    }

    private void renderEditOverlay(MatrixStack matrices, VertexConsumer buffer, Vec3d cameraPos) {
        boolean isFloor = (editFacing == Direction.UP || editFacing == Direction.DOWN);
        boolean isNS = (editFacing == Direction.NORTH || editFacing == Direction.SOUTH);

        for (int y = 0; y < 5; y++) {
            for (int w = 0; w < 5; w++) {
                int idx = y * 5 + w;
                boolean selected = editGrid[idx];

                float r, g, b;
                if (selected) {
                    r = 0.2F; g = 0.5F; b = 1.0F;
                } else {
                    r = 0.8F; g = 0.8F; b = 0.8F;
                }

                Box cellBox;
                if (isFloor) {
                    double floorY = editOrigin.getY();
                    cellBox = new Box(
                            editOrigin.getX() + w, floorY - 0.01, editOrigin.getZ() + y,
                            editOrigin.getX() + w + 1, floorY + 1.01, editOrigin.getZ() + y + 1
                    );
                } else if (isNS) {
                    double z = editOrigin.getZ();
                    cellBox = new Box(
                            editOrigin.getX() + w, editOrigin.getY() + y, z - 0.01,
                            editOrigin.getX() + w + 1, editOrigin.getY() + y + 1, z + 1.01
                    );
                } else {
                    double x = editOrigin.getX();
                    cellBox = new Box(
                            x - 0.01, editOrigin.getY() + y, editOrigin.getZ() + w,
                            x + 1.01, editOrigin.getY() + y + 1, editOrigin.getZ() + w + 1
                    );
                }

                drawCustomBox(matrices, buffer,
                        cellBox.offset(-cameraPos.x, -cameraPos.y, -cameraPos.z),
                        r, g, b, 0.9F);
            }
        }
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