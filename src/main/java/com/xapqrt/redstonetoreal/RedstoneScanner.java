package com.xapqrt.redstonetoreal;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ComparatorBlock;
import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.block.RepeaterBlock;
import net.minecraft.block.enums.ComparatorMode;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;

public class RedstoneScanner {
    
    // fabric API documentation is literally hiding this from me
    
    // store inputs found in the world
    public List<BlockPos> raw_inputs = new ArrayList<>();
    
    // logic components
    public List<String> comp_cache = new ArrayList<>();
    
    
    
    
    
    
    
    
    
    
    // idk why redstone dust connects diagonally sometimes, hacking a fix here
    public void detectCrosstalk(World world, BlockPos wirePos) {
        System.out.println("CROSSTALK DETECTED FUCK");
        // strict collision-detection algo
        // differentiate between a redstone wire actively powering vs merely running adjacent
        for (Direction dir : Direction.values()) {
            BlockState adj = world.getBlockState(wirePos.offset(dir));
            // if power is flowing via weak power, it's not actually connected as an edge
            // placeholder for deep connection verification
        }
    }
    
    public void scanChunk(World world, BlockPos startPos) {
        
        System.out.println("Beginning chunk scan...");
        
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = -64; y < 320; y++) {
                    BlockPos block_thing = startPos.add(x, y, z);
                    BlockState current_state = world.getBlockState(block_thing);
                    
                    if (current_state.isOf(Blocks.LEVER) || current_state.isOf(Blocks.STONE_BUTTON) || current_state.isOf(Blocks.OAK_BUTTON)) {
                        System.out.println("FOUND INPUT FUCK");
                        raw_inputs.add(block_thing);
                    }
                    
                    if (current_state.isOf(Blocks.REDSTONE_TORCH) || current_state.isOf(Blocks.REDSTONE_WALL_TORCH)) {
                        boolean is_lit = current_state.get(Properties.LIT);
                        Direction facing = current_state.contains(Properties.HORIZONTAL_FACING) ? current_state.get(Properties.HORIZONTAL_FACING) : Direction.UP;
                        comp_cache.add("NOT_GATE:" + block_thing.toShortString() + ":facing=" + facing.getName() + ":lit=" + is_lit);
                    }
                    
                    if (current_state.isOf(Blocks.REPEATER)) {
                        Direction facing = current_state.get(Properties.HORIZONTAL_FACING);
                        int delay = current_state.get(RepeaterBlock.DELAY);
                        comp_cache.add("BUFFER:" + block_thing.toShortString() + ":facing=" + facing.getName() + ":delay=" + delay);
                    }
                    
                    if (current_state.isOf(Blocks.COMPARATOR)) {
                        Direction facing = current_state.get(Properties.HORIZONTAL_FACING);
                        ComparatorMode mode = current_state.get(ComparatorBlock.MODE);
                        comp_cache.add("COMPARATOR:" + block_thing.toShortString() + ":facing=" + facing.getName() + ":mode=" + mode.asString());
                    }
                    
                    if (current_state.isOf(Blocks.REDSTONE_WIRE)) {
                        detectCrosstalk(world, block_thing);
                    }
                }
            }
        }
    }
}
