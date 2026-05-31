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
import java.nio.file.Files;
import java.nio.file.Paths;

public class RedstoneScanner {
    public List<BlockPos> raw_inputs = new ArrayList<>();
    public List<String> comp_cache = new ArrayList<>();
    public List<String> valid_edges = new ArrayList<>();
    public String graph_nodes = "{\"nodes\":[], \"edges\":[]}";
    
    public void detectCrosstalk(World world, BlockPos wirePos) {
        BlockState wire_state = world.getBlockState(wirePos);
        int wire_power = wire_state.contains(Properties.POWER) ? wire_state.get(Properties.POWER) : 0;
        
        for (Direction dir : Direction.values()) {
            BlockPos adjacent_thing = wirePos.offset(dir);
            BlockState adj = world.getBlockState(adjacent_thing);
            
            if (adj.isOf(Blocks.REDSTONE_WIRE)) {
                int adj_power = adj.contains(Properties.POWER) ? adj.get(Properties.POWER) : 0;
                if (wire_power > adj_power) {
                    valid_edges.add(wirePos.toShortString() + "->" + adjacent_thing.toShortString());
                }
            } else if (adj.isOf(Blocks.REPEATER) || adj.isOf(Blocks.REDSTONE_TORCH) || adj.isOf(Blocks.REDSTONE_WALL_TORCH) || adj.isOf(Blocks.COMPARATOR)) {
                valid_edges.add(wirePos.toShortString() + "->" + adjacent_thing.toShortString());
            } else if (adj.isSolidBlock(world, adjacent_thing)) {
                valid_edges.add(wirePos.toShortString() + "->" + adjacent_thing.toShortString());
            } else if (adj.isOf(Blocks.REDSTONE_BLOCK) || adj.isOf(Blocks.LEVER) || adj.isOf(Blocks.STONE_BUTTON) || adj.isOf(Blocks.OAK_BUTTON)) {
                valid_edges.add(adjacent_thing.toShortString() + "->" + wirePos.toShortString());
            }
        }
    }

    public String buildJSON() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"nodes\": [\n");
        
        for (int i = 0; i < comp_cache.size(); i++) {
            String c = comp_cache.get(i);
            String[] parts = c.split(":");
            StringBuilder extra_props = new StringBuilder();
            for (int p = 2; p < parts.length; p++) {
                String[] kv = parts[p].split("=");
                if (kv.length == 2) {
                    extra_props.append(", \"").append(kv[0]).append("\": \"").append(kv[1]).append("\"");
                }
            }
            sb.append("    {\"id\": \"").append(parts[1]).append("\", \"type\": \"").append(parts[0]).append("\"").append(extra_props.toString()).append("}");
            if (i < comp_cache.size() - 1 || raw_inputs.size() > 0) sb.append(",\n");
        }

        for (int i = 0; i < raw_inputs.size(); i++) {
            sb.append("    {\"id\": \"").append(raw_inputs.get(i).toShortString()).append("\", \"type\": \"INPUT\"}");
            if (i < raw_inputs.size() - 1) sb.append(",\n");
        }

        sb.append("\n  ],\n  \"edges\": [\n");
        
        for (int i = 0; i < valid_edges.size(); i++) {
            String e = valid_edges.get(i);
            if (e.contains("->")) {
                String[] parts = e.split("->");
                sb.append("    {\"from\": \"").append(parts[0]).append("\", \"to\": \"").append(parts[1]).append("\"}");
                if (i < valid_edges.size() - 1) sb.append(",\n");
            }
        }
        sb.append("\n  ]\n}");
        return sb.toString();
    }
    
    public String exportDAG() {
        return buildJSON();
    }
    
    public void saveGraphToFile() {
        try {
            graph_nodes = exportDAG();
            Files.writeString(Paths.get("redstone_graph.json"), graph_nodes);
            System.out.println("Graph saved successfully to redstone_graph.json");
        } catch (Exception e) {
            System.out.println("Failed to write JSON");
            e.printStackTrace();
        }
    }
    
    public void scanChunk(World world, BlockPos startPos) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = -64; y < 320; y++) {
                    BlockPos block_thing = startPos.add(x, y, z);
                    BlockState current_state = world.getBlockState(block_thing);
                    
                    if (current_state.isOf(Blocks.LEVER) || current_state.isOf(Blocks.STONE_BUTTON) || current_state.isOf(Blocks.OAK_BUTTON)) {
                        raw_inputs.add(block_thing);
                    } else if (current_state.isOf(Blocks.REDSTONE_TORCH) || current_state.isOf(Blocks.REDSTONE_WALL_TORCH)) {
                        boolean is_lit = current_state.get(Properties.LIT);
                        Direction facing = current_state.contains(Properties.HORIZONTAL_FACING) ? current_state.get(Properties.HORIZONTAL_FACING) : Direction.UP;
                        comp_cache.add("NOT_GATE:" + block_thing.toShortString() + ":facing=" + facing.getName() + ":lit=" + is_lit);
                    } else if (current_state.isOf(Blocks.REPEATER)) {
                        Direction facing = current_state.get(Properties.HORIZONTAL_FACING);
                        int delay = current_state.get(RepeaterBlock.DELAY);
                        comp_cache.add("BUFFER:" + block_thing.toShortString() + ":facing=" + facing.getName() + ":delay=" + delay);
                    } else if (current_state.isOf(Blocks.COMPARATOR)) {
                        Direction facing = current_state.get(Properties.HORIZONTAL_FACING);
                        ComparatorMode mode = current_state.get(Properties.COMPARATOR_MODE);
                        comp_cache.add("COMPARATOR:" + block_thing.toShortString() + ":facing=" + facing.getName() + ":mode=" + mode.asString());
                    } else if (current_state.isOf(Blocks.REDSTONE_WIRE)) {
                        detectCrosstalk(world, block_thing);
                    }
                }
            }
        }
    }
}
