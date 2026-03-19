package fortcraft;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

public class FortWallBlock extends Block {
    // 0 = Solid, 1 = Window, 2 = Door
    public static final IntProperty EDIT_STATE = IntProperty.of("edit", 0, 2);
    // 0 = Wood, 1 = Brick, 2 = Metal
    public static final IntProperty MATERIAL = IntProperty.of("material", 0, 2);
    
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;

    public FortWallBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
            .with(EDIT_STATE, 0)
            .with(FACING, Direction.NORTH)
            .with(MATERIAL, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(EDIT_STATE, FACING, MATERIAL);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }
}