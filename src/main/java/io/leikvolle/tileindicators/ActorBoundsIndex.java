package io.leikvolle.tileindicators;

import java.util.Arrays;
import net.runelite.api.WorldView;

/** Shares conservative bounds checks between nearby actors for one frame. */
final class ActorBoundsIndex
{
    private static final int SIZE = 1024;
    private static final int CELL_SHIFT = 7;
    private static final int CELL_SIZE = 1 << CELL_SHIFT;
    private final WorldView[] worlds = new WorldView[SIZE];
    private final int[] cellX = new int[SIZE], cellY = new int[SIZE];
    private final int[] radii = new int[SIZE], minHeight = new int[SIZE], maxHeight = new int[SIZE];
    private final boolean[] overlaps = new boolean[SIZE];
    private final float[] x = new float[8], y = new float[8], z = new float[8];
    private final ActorProjection projection;
    private final OverlayCoverage coverage;

    ActorBoundsIndex(ActorProjection projection, OverlayCoverage coverage)
    {
        this.projection = projection;
        this.coverage = coverage;
    }

    void beginFrame()
    {
        // Camera, overlays, heights and child-world transforms can all change
        // every frame. Never carry a rejection over to the next frame.
        Arrays.fill(worlds, null);
    }

    boolean mayOverlap(WorldView world, int localX, int localY, int localZ,
                       int radius, int height, int bottom)
    {
        // Keep unexpected model dimensions on the exact path, avoiding
        // overflow when quantizing an invalid or exceptionally large bound.
        if (radius < 0 || radius > 65536 || height < 0 || height > 65536 || bottom < 0 || bottom > 65536
                || localZ < -1_000_000 || localZ > 1_000_000) return true;
        int cx = localX >> CELL_SHIFT, cy = localY >> CELL_SHIFT;
        int r = (radius + CELL_SIZE - 1) >> CELL_SHIFT;
        int low = (localZ - height) >> CELL_SHIFT;
        int high = (localZ + bottom) >> CELL_SHIFT;
        int hash = System.identityHashCode(world) ^ cx * 73856093 ^ cy * 19349663
                ^ r * 83492791 ^ low * 961748941 ^ high * 982451653;
        int slot = (hash ^ (hash >>> 16)) & (SIZE - 1);
        if (worlds[slot] == world && cellX[slot] == cx && cellY[slot] == cy
                && radii[slot] == r && minHeight[slot] == low && maxHeight[slot] == high)
        {
            return overlaps[slot];
        }
        // Enclose every location and radius in the bucket, including actors
        // moving between tile centres and actors at negative terrain heights.
        for (int i = 0; i < 8; i++)
        {
            x[i] = ((i & 1) == 0 ? cx - r : cx + r + 1) * CELL_SIZE;
            y[i] = ((i & 2) == 0 ? low : high + 1) * CELL_SIZE;
            z[i] = ((i & 4) == 0 ? cy - r : cy + r + 1) * CELL_SIZE;
        }
        boolean overlap = projection.project(world, 0, 0, 0, 0, x, y, z, 8)
                && projection.maxX != Integer.MIN_VALUE
                && (projection.crossesNearPlane || coverage.intersects(
                    projection.minX, projection.minY, projection.maxX, projection.maxY));
        worlds[slot] = world;
        cellX[slot] = cx; cellY[slot] = cy; radii[slot] = r;
        minHeight[slot] = low; maxHeight[slot] = high;
        overlaps[slot] = overlap;
        return overlap;
    }
}
