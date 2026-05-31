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
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class RedstoneScanner {
    public List<BlockPos> raw_inputs = new ArrayList<>();
    public List<String> comp_cache = new ArrayList<>();
    public List<String> valid_edges = new ArrayList<>();
    public String graph_nodes = "{\"nodes\":[], \"edges\":[]}";

    private static class RawNode {
        String id;
        BlockPos pos;
        String type;
        BlockPos supportBlockPos;
        Direction facing;
        String extraProps = "";
        
        RawNode(String id, BlockPos pos, String type) {
            this.id = id;
            this.pos = pos;
            this.type = type;
        }
    }

    private static class CollapsedNode {
        String id;
        String type;
        String label = "";
        Set<String> originalIds = new HashSet<>();
        String extraProps = "";
    }

    private List<RawNode> rawNodes = new ArrayList<>();
    private Set<BlockPos> wireBlocks = new HashSet<>();

    public void scanChunk(World world, BlockPos startPos) {
        rawNodes.clear();
        wireBlocks.clear();
        raw_inputs.clear();
        comp_cache.clear();
        valid_edges.clear();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = -64; y < 320; y++) {
                    BlockPos pos = startPos.add(x, y, z);
                    BlockState state = world.getBlockState(pos);

                    if (state.isOf(Blocks.LEVER) || state.isOf(Blocks.STONE_BUTTON) || state.isOf(Blocks.OAK_BUTTON)) {
                        RawNode node = new RawNode(pos.toShortString(), pos, "INPUT");
                        node.facing = state.contains(Properties.HORIZONTAL_FACING) ? state.get(Properties.HORIZONTAL_FACING) : Direction.NORTH;
                        rawNodes.add(node);
                        raw_inputs.add(pos);
                    } else if (state.isOf(Blocks.REDSTONE_TORCH) || state.isOf(Blocks.REDSTONE_WALL_TORCH)) {
                        RawNode node = new RawNode(pos.toShortString(), pos, "NOT_GATE");
                        Direction facing = state.contains(Properties.HORIZONTAL_FACING) ? state.get(Properties.HORIZONTAL_FACING) : Direction.UP;
                        node.facing = facing;
                        if (facing == Direction.UP) {
                            node.supportBlockPos = pos.down();
                        } else {
                            node.supportBlockPos = pos.offset(facing.getOpposite());
                        }
                        boolean is_lit = state.get(Properties.LIT);
                        node.extraProps = ":facing=" + facing.getName() + ":lit=" + is_lit;
                        rawNodes.add(node);
                        comp_cache.add("NOT_GATE:" + pos.toShortString() + node.extraProps);
                    } else if (state.isOf(Blocks.REPEATER)) {
                        RawNode node = new RawNode(pos.toShortString(), pos, "BUFFER");
                        Direction facing = state.get(Properties.HORIZONTAL_FACING);
                        node.facing = facing;
                        int delay = state.get(RepeaterBlock.DELAY);
                        node.extraProps = ":facing=" + facing.getName() + ":delay=" + delay;
                        rawNodes.add(node);
                        comp_cache.add("BUFFER:" + pos.toShortString() + node.extraProps);
                    } else if (state.isOf(Blocks.COMPARATOR)) {
                        RawNode node = new RawNode(pos.toShortString(), pos, "COMPARATOR");
                        Direction facing = state.get(Properties.HORIZONTAL_FACING);
                        node.facing = facing;
                        ComparatorMode mode = state.get(Properties.COMPARATOR_MODE);
                        node.extraProps = ":facing=" + facing.getName() + ":mode=" + mode.asString();
                        rawNodes.add(node);
                        comp_cache.add("COMPARATOR:" + pos.toShortString() + node.extraProps);
                    } else if (state.isOf(Blocks.REDSTONE_WIRE)) {
                        wireBlocks.add(pos);
                    }
                }
            }
        }

        buildGraph(world);
    }

    private void buildGraph(World world) {
        Map<String, Set<String>> rawAdj = new HashMap<>();
        for (RawNode node : rawNodes) {
            rawAdj.put(node.id, new HashSet<>());
        }

        for (RawNode src : rawNodes) {
            Set<BlockPos> powered = findPowerPropagation(world, src);
            for (RawNode dest : rawNodes) {
                if (src == dest) continue;
                if (isComponentPoweredBy(world, dest, powered)) {
                    rawAdj.get(src.id).add(dest.id);
                    valid_edges.add(src.pos.toShortString() + "->" + dest.pos.toShortString());
                }
            }
        }

        Map<String, RawNode> rawNodeMap = new HashMap<>();
        for (RawNode n : rawNodes) {
            rawNodeMap.put(n.id, n);
        }

        Map<String, Set<String>> rawInEdges = new HashMap<>();
        for (RawNode n : rawNodes) {
            rawInEdges.put(n.id, new HashSet<>());
        }
        for (Map.Entry<String, Set<String>> entry : rawAdj.entrySet()) {
            String from = entry.getKey();
            for (String to : entry.getValue()) {
                rawInEdges.get(to).add(from);
            }
        }

        Set<String> merged = new HashSet<>();
        List<CollapsedNode> collapsedNodes = new ArrayList<>();
        int inputCount = 0;
        int notCount = 0;
        int andCount = 0;
        int srCount = 0;
        int delayCount = 0;
        int compCount = 0;

        // 1. Identify SR Latches (cycles of length 2 between NOT gates)
        for (RawNode n1 : rawNodes) {
            if (merged.contains(n1.id) || !"NOT_GATE".equals(n1.type)) continue;
            for (RawNode n2 : rawNodes) {
                if (n1 == n2 || merged.contains(n2.id) || !"NOT_GATE".equals(n2.type)) continue;

                if (rawAdj.get(n1.id).contains(n2.id) && rawAdj.get(n2.id).contains(n1.id)) {
                    if (manhattanDistance(n1.pos, n2.pos) <= 4) {
                        CollapsedNode srNode = new CollapsedNode();
                        srNode.id = "SR_" + srCount++;
                        srNode.type = "SR_LATCH";
                        srNode.originalIds.add(n1.id);
                        srNode.originalIds.add(n2.id);
                        collapsedNodes.add(srNode);

                        merged.add(n1.id);
                        merged.add(n2.id);
                        break;
                    }
                }
            }
        }

        // 2. Identify AND Gates
        for (RawNode n3 : rawNodes) {
            if (merged.contains(n3.id) || !"NOT_GATE".equals(n3.type)) continue;

            Set<String> parents = rawInEdges.get(n3.id);
            List<String> notGateParents = new ArrayList<>();
            for (String parentId : parents) {
                if (merged.contains(parentId)) continue;
                RawNode pNode = rawNodeMap.get(parentId);
                if (pNode != null && "NOT_GATE".equals(pNode.type)) {
                    if (manhattanDistance(n3.pos, pNode.pos) <= 4) {
                        notGateParents.add(parentId);
                    }
                }
            }

            if (notGateParents.size() >= 2) {
                CollapsedNode andNode = new CollapsedNode();
                andNode.id = "AND_" + andCount++;
                andNode.type = "AND";
                andNode.originalIds.add(n3.id);
                for (String pId : notGateParents) {
                    andNode.originalIds.add(pId);
                    merged.add(pId);
                }
                merged.add(n3.id);
                collapsedNodes.add(andNode);
            }
        }

        // 3. Map all remaining nodes
        for (RawNode n : rawNodes) {
            if (merged.contains(n.id)) continue;
            CollapsedNode cNode = new CollapsedNode();
            if ("INPUT".equals(n.type)) {
                cNode.id = "IN_" + inputCount++;
                cNode.type = "INPUT";
                cNode.label = "Lever " + inputCount;
            } else if ("NOT_GATE".equals(n.type)) {
                cNode.id = "NOT_" + notCount++;
                cNode.type = "NOT";
            } else if ("BUFFER".equals(n.type)) {
                cNode.id = "DELAY_" + delayCount++;
                cNode.type = "DELAY";
                cNode.extraProps = n.extraProps;
            } else if ("COMPARATOR".equals(n.type)) {
                cNode.id = "COMP_" + compCount++;
                cNode.type = "COMPARATOR";
                cNode.extraProps = n.extraProps;
            }
            cNode.originalIds.add(n.id);
            collapsedNodes.add(cNode);
        }

        Map<String, String> rawToCollapsed = new HashMap<>();
        for (CollapsedNode cn : collapsedNodes) {
            for (String rId : cn.originalIds) {
                rawToCollapsed.put(rId, cn.id);
            }
        }

        Set<String> edgeSet = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : rawAdj.entrySet()) {
            String fromRaw = entry.getKey();
            String fromCollapsed = rawToCollapsed.get(fromRaw);
            if (fromCollapsed == null) continue;

            for (String toRaw : entry.getValue()) {
                String toCollapsed = rawToCollapsed.get(toRaw);
                if (toCollapsed == null || fromCollapsed.equals(toCollapsed)) continue;

                String edgeStr = "    {\"source\": \"" + fromCollapsed + "\", \"target\": \"" + toCollapsed + "\", \"from\": \"" + fromCollapsed + "\", \"to\": \"" + toCollapsed + "\"}";
                edgeSet.add(edgeStr);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"nodes\": [\n");
        for (int i = 0; i < collapsedNodes.size(); i++) {
            CollapsedNode cn = collapsedNodes.get(i);
            sb.append("    {");
            sb.append("\"id\": \"").append(cn.id).append("\", ");
            sb.append("\"type\": \"").append(cn.type).append("\"");
            if (!cn.label.isEmpty()) {
                sb.append(", \"label\": \"").append(cn.label).append("\"");
            }
            if (!cn.extraProps.isEmpty()) {
                String[] parts = cn.extraProps.split(":");
                for (String p : parts) {
                    if (p.isEmpty()) continue;
                    String[] kv = p.split("=");
                    if (kv.length == 2) {
                        sb.append(", \"").append(kv[0]).append("\": \"").append(kv[1]).append("\"");
                    }
                }
            }
            sb.append("}");
            if (i < collapsedNodes.size() - 1) sb.append(",\n");
        }
        sb.append("\n  ],\n  \"edges\": [\n");
        List<String> edgeList = new ArrayList<>(edgeSet);
        for (int i = 0; i < edgeList.size(); i++) {
            sb.append(edgeList.get(i));
            if (i < edgeList.size() - 1) sb.append(",\n");
        }
        sb.append("\n  ]\n}");
        graph_nodes = sb.toString();
    }

    private Set<BlockPos> getConnectedWireNetwork(World world, BlockPos startWire, Set<BlockPos> visitedWires) {
        Set<BlockPos> network = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(startWire);
        network.add(startWire);
        visitedWires.add(startWire);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos adj = current.offset(dir);
                // 1. Same level
                if (world.getBlockState(adj).isOf(Blocks.REDSTONE_WIRE)) {
                    if (!visitedWires.contains(adj)) {
                        visitedWires.add(adj);
                        network.add(adj);
                        queue.add(adj);
                    }
                }
                // 2. One level up (if no solid block directly above current)
                BlockPos up = adj.up();
                if (world.getBlockState(up).isOf(Blocks.REDSTONE_WIRE) && !world.getBlockState(current.up()).isSolidBlock(world, current.up())) {
                    if (!visitedWires.contains(up)) {
                        visitedWires.add(up);
                        network.add(up);
                        queue.add(up);
                    }
                }
                // 3. One level down (if no solid block directly above adj)
                BlockPos down = adj.down();
                if (world.getBlockState(down).isOf(Blocks.REDSTONE_WIRE) && !world.getBlockState(adj).isSolidBlock(world, adj)) {
                    if (!visitedWires.contains(down)) {
                        visitedWires.add(down);
                        network.add(down);
                        queue.add(down);
                    }
                }
            }
        }
        return network;
    }

    private Set<BlockPos> findPowerPropagation(World world, RawNode src) {
        Set<BlockPos> powerOrigins = new HashSet<>();
        if ("INPUT".equals(src.type)) {
            for (Direction dir : Direction.values()) {
                powerOrigins.add(src.pos.offset(dir));
            }
        } else if ("NOT_GATE".equals(src.type)) {
            powerOrigins.add(src.pos.up());
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos adj = src.pos.offset(dir);
                if (!adj.equals(src.supportBlockPos)) {
                    powerOrigins.add(adj);
                }
            }
        } else if ("BUFFER".equals(src.type) || "COMPARATOR".equals(src.type)) {
            BlockPos front = src.pos.offset(src.facing);
            powerOrigins.add(front);
            if (world.getBlockState(front).isSolidBlock(world, front)) {
                for (Direction dir : Direction.values()) {
                    powerOrigins.add(front.offset(dir));
                }
            }
        }

        Set<BlockPos> poweredBlocks = new HashSet<>(powerOrigins);
        Set<BlockPos> visitedWires = new HashSet<>();
        for (BlockPos origin : powerOrigins) {
            if (world.getBlockState(origin).isOf(Blocks.REDSTONE_WIRE)) {
                if (!visitedWires.contains(origin)) {
                    Set<BlockPos> network = getConnectedWireNetwork(world, origin, visitedWires);
                    poweredBlocks.addAll(network);
                }
            }
        }
        return poweredBlocks;
    }

    private boolean isComponentPoweredBy(World world, RawNode dest, Set<BlockPos> poweredBlocks) {
        if ("NOT_GATE".equals(dest.type)) {
            return isBlockPowered(world, dest.supportBlockPos, poweredBlocks);
        } else if ("BUFFER".equals(dest.type)) {
            BlockPos backPos = dest.pos.offset(dest.facing.getOpposite());
            return isBlockPowered(world, backPos, poweredBlocks);
        } else if ("COMPARATOR".equals(dest.type)) {
            BlockPos backPos = dest.pos.offset(dest.facing.getOpposite());
            BlockPos leftPos = dest.pos.offset(dest.facing.rotateYCounterclockwise());
            BlockPos rightPos = dest.pos.offset(dest.facing.rotateYClockwise());
            return isBlockPowered(world, backPos, poweredBlocks) ||
                   isBlockPowered(world, leftPos, poweredBlocks) ||
                   isBlockPowered(world, rightPos, poweredBlocks);
        }
        return false;
    }

    private boolean isBlockPowered(World world, BlockPos targetPos, Set<BlockPos> poweredBlocks) {
        if (poweredBlocks.contains(targetPos)) {
            return true;
        }
        if (world.getBlockState(targetPos).isSolidBlock(world, targetPos)) {
            for (BlockPos pb : poweredBlocks) {
                if (world.getBlockState(pb).isOf(Blocks.REDSTONE_WIRE)) {
                    if (pb.up().equals(targetPos) || pb.down().equals(targetPos) ||
                        pb.north().equals(targetPos) || pb.south().equals(targetPos) ||
                        pb.east().equals(targetPos) || pb.west().equals(targetPos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int manhattanDistance(BlockPos p1, BlockPos p2) {
        return Math.abs(p1.getX() - p2.getX()) + 
               Math.abs(p1.getY() - p2.getY()) + 
               Math.abs(p1.getZ() - p2.getZ());
    }

    public String buildJSON() {
        return graph_nodes;
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
}
