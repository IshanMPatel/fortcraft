package fortcraft;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;

public class FortRampBlock extends StairsBlock {
    // 0 = Wood, 1 = Brick, 2 = Metal
    public static final IntProperty MATERIAL = IntProperty.of("material", 0, 2);

    public FortRampBlock(BlockState baseBlockState, Settings settings) {
        super(baseBlockState, settings);
        this.setDefaultState(this.getDefaultState().with(MATERIAL, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(MATERIAL);
    }
}