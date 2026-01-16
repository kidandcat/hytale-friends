package com.friends.features.tracker;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.protocol.DrawType;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;

import java.util.*;

/**
 * A* Pathfinding implementation for finding paths between players.
 *
 * This finds a walkable path avoiding solid blocks, with support for
 * 3D movement (going up/down terrain).
 */
public class AStarPathfinder {

    // Maximum search iterations to prevent infinite loops
    private static final int MAX_ITERATIONS = 5000;

    // Maximum path length in blocks
    private static final int MAX_PATH_LENGTH = 200;

    // Height player can step up (1 block)
    private static final int STEP_HEIGHT = 1;

    // Height clearance needed for player (2 blocks)
    private static final int PLAYER_HEIGHT = 2;

    private final World world;

    public AStarPathfinder(World world) {
        this.world = world;
    }

    /**
     * Find a path from start to end position.
     * Returns list of waypoints, or empty list if no path found.
     */
    public List<Vector3d> findPath(Vector3d start, Vector3d end) {
        // Convert to integer coordinates
        int startX = (int) Math.floor(start.x);
        int startY = (int) Math.floor(start.y);
        int startZ = (int) Math.floor(start.z);

        int endX = (int) Math.floor(end.x);
        int endY = (int) Math.floor(end.y);
        int endZ = (int) Math.floor(end.z);

        // Check if start and end are too far apart
        double directDist = distance(startX, startY, startZ, endX, endY, endZ);
        if (directDist > MAX_PATH_LENGTH) {
            System.out.println("[Pathfinder] Distance too far: " + directDist);
            return Collections.emptyList();
        }

        // A* algorithm
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fScore));
        Map<Long, Node> allNodes = new HashMap<>();
        Set<Long> closedSet = new HashSet<>();

        Node startNode = new Node(startX, startY, startZ);
        startNode.gScore = 0;
        startNode.fScore = heuristic(startX, startY, startZ, endX, endY, endZ);

        openSet.add(startNode);
        allNodes.put(posToLong(startX, startY, startZ), startNode);

        int iterations = 0;

        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;

            Node current = openSet.poll();

            // Check if we reached the goal (within 2 blocks is close enough)
            if (distance(current.x, current.y, current.z, endX, endY, endZ) < 2) {
                return reconstructPath(current, start, end);
            }

            long currentKey = posToLong(current.x, current.y, current.z);
            closedSet.add(currentKey);

            // Check all neighbors (including diagonals and vertical movement)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -STEP_HEIGHT; dy <= STEP_HEIGHT; dy++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        int nx = current.x + dx;
                        int ny = current.y + dy;
                        int nz = current.z + dz;

                        long neighborKey = posToLong(nx, ny, nz);

                        if (closedSet.contains(neighborKey)) continue;

                        // Check if this position is walkable
                        if (!isWalkable(nx, ny, nz)) continue;

                        // Calculate movement cost (diagonal moves cost more)
                        double moveCost = (dx != 0 && dz != 0) ? 1.414 : 1.0;
                        if (dy != 0) moveCost += 0.5; // Vertical movement costs extra

                        double tentativeG = current.gScore + moveCost;

                        Node neighbor = allNodes.get(neighborKey);
                        if (neighbor == null) {
                            neighbor = new Node(nx, ny, nz);
                            allNodes.put(neighborKey, neighbor);
                        }

                        if (tentativeG < neighbor.gScore) {
                            neighbor.parent = current;
                            neighbor.gScore = tentativeG;
                            neighbor.fScore = tentativeG + heuristic(nx, ny, nz, endX, endY, endZ);

                            if (!openSet.contains(neighbor)) {
                                openSet.add(neighbor);
                            }
                        }
                    }
                }
            }
        }

        System.out.println("[Pathfinder] No path found after " + iterations + " iterations");
        return Collections.emptyList();
    }

    /**
     * Check if a position is walkable (has floor, no blocking blocks at feet/head level)
     */
    private boolean isWalkable(int x, int y, int z) {
        try {
            // Need solid ground below
            if (!isSolid(x, y - 1, z)) {
                return false;
            }

            // Need clear space at feet and head level
            if (isSolid(x, y, z) || isSolid(x, y + 1, z)) {
                return false;
            }

            return true;
        } catch (Exception e) {
            // If we can't check the block, assume not walkable
            return false;
        }
    }

    /**
     * Check if a block position is solid (blocks movement)
     */
    private boolean isSolid(int x, int y, int z) {
        try {
            // Get chunk coordinates
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            long chunkKey = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);

            WorldChunk chunk = world.getChunkIfLoaded(chunkKey);
            if (chunk == null) {
                // Chunk not loaded - treat as solid to avoid going there
                return true;
            }

            // Get block type at this position
            BlockType blockType = chunk.getBlockType(x & 15, y, z & 15);

            if (blockType == null) {
                return false; // Air
            }

            // Check if the block is empty/air
            DrawType drawType = blockType.getDrawType();
            if (drawType == DrawType.Empty) {
                return false;
            }

            // Most other blocks are solid
            return true;

        } catch (Exception e) {
            // Error accessing block - treat as solid
            return true;
        }
    }

    /**
     * Reconstruct the path from goal node back to start
     */
    private List<Vector3d> reconstructPath(Node goalNode, Vector3d start, Vector3d end) {
        List<Vector3d> path = new ArrayList<>();

        Node current = goalNode;
        while (current != null) {
            // Add 0.5 to center the waypoint in the block, and 1.0 for player height
            path.add(new Vector3d(current.x + 0.5, current.y + 1.0, current.z + 0.5));
            current = current.parent;
        }

        // Reverse to get path from start to end
        Collections.reverse(path);

        // Simplify path by removing unnecessary intermediate nodes
        return simplifyPath(path);
    }

    /**
     * Remove unnecessary waypoints that are in a straight line
     */
    private List<Vector3d> simplifyPath(List<Vector3d> path) {
        if (path.size() <= 2) return path;

        List<Vector3d> simplified = new ArrayList<>();
        simplified.add(path.get(0));

        for (int i = 1; i < path.size() - 1; i++) {
            Vector3d prev = path.get(i - 1);
            Vector3d curr = path.get(i);
            Vector3d next = path.get(i + 1);

            // Check if current point is on the line between prev and next
            double dx1 = curr.x - prev.x;
            double dy1 = curr.y - prev.y;
            double dz1 = curr.z - prev.z;

            double dx2 = next.x - curr.x;
            double dy2 = next.y - curr.y;
            double dz2 = next.z - curr.z;

            // If direction changes significantly, keep this waypoint
            if (Math.abs(dx1 - dx2) > 0.1 || Math.abs(dy1 - dy2) > 0.1 || Math.abs(dz1 - dz2) > 0.1) {
                simplified.add(curr);
            }
        }

        simplified.add(path.get(path.size() - 1));
        return simplified;
    }

    private double heuristic(int x1, int y1, int z1, int x2, int y2, int z2) {
        // Euclidean distance as heuristic
        return distance(x1, y1, z1, x2, y2, z2);
    }

    private double distance(int x1, int y1, int z1, int x2, int y2, int z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private long posToLong(int x, int y, int z) {
        // Pack position into a long for efficient storage
        return ((long)(x & 0xFFFFF) << 40) | ((long)(y & 0xFFF) << 28) | (z & 0xFFFFFFF);
    }

    /**
     * Node class for A* algorithm
     */
    private static class Node {
        final int x, y, z;
        Node parent;
        double gScore = Double.MAX_VALUE;
        double fScore = Double.MAX_VALUE;

        Node(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Node)) return false;
            Node node = (Node) o;
            return x == node.x && y == node.y && z == node.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }
}
