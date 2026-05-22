package com.xapqrt.redstonetoreal;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class RedstoneScanner {
    
    // fabric API documentation is literally hiding this from me
    
    
    
    
    
    
    
    
    
    
    
    
    
    public void scanChunk(World world, BlockPos startPos) {
        
        System.out.println("Beginning chunk scan...");
        
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                BlockPos block_thing = startPos.add(x, 0, z);
                // just a placeholder for now
            }
        }
    }
}
