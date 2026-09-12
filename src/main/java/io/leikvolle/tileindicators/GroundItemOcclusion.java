package io.leikvolle.tileindicators;

import java.awt.Rectangle;
import java.util.Arrays;
import net.runelite.api.*;
import net.runelite.client.callback.RenderCallbackManager;

/** Depth of opaque, scene-rendered ground-item faces that overlap overlay pixels. */
final class GroundItemOcclusion
{
    private final Client client;
    private final ActorProjection projection = new ActorProjection();
    private final float[] boundsX = new float[8], boundsY = new float[8], boundsZ = new float[8];
    private final int[] face = new int[3], clippedX = new int[4], clippedY = new int[4];
    private final float[] clippedDepth = new float[4];
    private RenderedActors rendered;
    private RenderCallbackManager callbacks;
    private OverlayCoverage coverage;
    private int[] pixels;
    private float[] depth;
    private int[] rowLeft, rowRight;
    private int width, height, top, bottom, left, right;
    private Rectangle clip;
    private boolean prepared;

    GroundItemOcclusion(Client client) { this.client = client; }

    void beginFrame(RenderedActors rendered, RenderCallbackManager callbacks, OverlayCoverage coverage)
    {
        if (depth != null)
        {
            for (int y = top; y < bottom; y++)
            {
                if (rowLeft[y] < rowRight[y]) Arrays.fill(depth, y * width + rowLeft[y], y * width + rowRight[y], 0f);
                rowLeft[y] = width; rowRight[y] = 0;
            }
        }
        top = height; bottom = 0; left = width; right = 0;
        this.rendered = rendered; this.callbacks = callbacks; this.coverage = coverage;
        pixels = null;
        prepared = false;
    }

    void prepare()
    {
        if (prepared) return;
        prepared = true;
        if (rendered == null || callbacks == null || !rendered.hasGroundItems()) return;
        BufferProvider buffer = client.getBufferProvider();
        if (buffer == null || buffer.getPixels() == null || buffer.getWidth() <= 0 || buffer.getHeight() <= 0) return;
        int w = buffer.getWidth(), h = buffer.getHeight();
        if ((long) w * h > buffer.getPixels().length) return;
        if (depth == null || width != w || height != h)
        {
            width = w; height = h;
            depth = new float[w * h]; rowLeft = new int[h]; rowRight = new int[h];
            Arrays.fill(rowLeft, width);
            top = height; bottom = 0; left = width; right = 0;
        }
        clip = new Rectangle(client.getViewportXOffset(), client.getViewportYOffset(),
                client.getViewportWidth(), client.getViewportHeight()).intersection(new Rectangle(0, 0, width, height));
        pixels = buffer.getPixels();
        projection.beginFrame(client);
        rendered.addGroundItemOccluders(this, callbacks);
    }

    void add(ItemLayer layer)
    {
        WorldView world = layer.getWorldView();
        if (world == null) return;
        int x = layer.getX(), y = layer.getY(), z = layer.getZ() - layer.getHeight();
        add(world, layer.getBottom(), x, y, z);
        add(world, layer.getMiddle(), x, y, z);
        add(world, layer.getTop(), x, y, z);
    }

    private void add(WorldView world, Renderable renderable, int x, int y, int z)
    {
        if (renderable == null) return;
        Model model = renderable instanceof Model ? (Model) renderable : renderable.getModel();
        if (model == null || model.getTransparency() != 0) return;
        model.calculateBoundsCylinder();
        int radius = model.getXYZMag(), modelHeight = model.getModelHeight(), lower = model.getBottomY();
        if (radius > 0 && modelHeight >= 0 && lower >= 0)
        {
            for (int i = 0; i < 8; i++)
            {
                boundsX[i] = (i & 1) == 0 ? -radius : radius;
                boundsY[i] = (i & 2) == 0 ? -modelHeight : lower;
                boundsZ[i] = (i & 4) == 0 ? -radius : radius;
            }
            if (!projection.project(world, x, y, z, 0, boundsX, boundsY, boundsZ, 8)) return;
            if (!projection.crossesNearPlane && !coverage.intersectsPixels(projection.minX, projection.minY, projection.maxX, projection.maxY)) return;
        }
        if (!projection.project(world, x, y, z, 0, model.getVerticesX(), model.getVerticesY(), model.getVerticesZ(), model.getVerticesCount())) return;
        if (!projection.crossesNearPlane && !coverage.intersectsPixels(projection.minX, projection.minY, projection.maxX, projection.maxY)) return;
        int[] a = model.getFaceIndices1(), b = model.getFaceIndices2(), c = model.getFaceIndices3(), colors = model.getFaceColors3();
        byte[] alpha = model.getFaceTransparencies();
        for (int i = 0; i < model.getFaceCount(); i++)
        {
            // Transparent faces do not fully hide the character behind them.
            if ((colors != null && colors[i] == -2) || (alpha != null && alpha[i] != 0)) continue;
            if (projection.depth[a[i]] >= ActorProjection.NEAR && projection.depth[b[i]] >= ActorProjection.NEAR && projection.depth[c[i]] >= ActorProjection.NEAR)
                triangle(projection.x[a[i]], projection.y[a[i]], projection.depth[a[i]],
                        projection.x[b[i]], projection.y[b[i]], projection.depth[b[i]],
                        projection.x[c[i]], projection.y[c[i]], projection.depth[c[i]]);
            else clipFace(a[i], b[i], c[i]);
        }
    }

    private void clipFace(int a, int b, int c)
    {
        face[0] = a; face[1] = b; face[2] = c;
        int count = 0, previous = c;
        for (int current : face)
        {
            float pz = projection.depth[previous], cz = projection.depth[current];
            if (!Float.isFinite(pz) || !Float.isFinite(cz)) return;
            boolean pIn = pz >= ActorProjection.NEAR, cIn = cz >= ActorProjection.NEAR;
            if (pIn != cIn)
            {
                float t = (ActorProjection.NEAR - pz) / (cz - pz);
                clippedX[count] = projection.screenX(projection.cameraX[previous] + t * (projection.cameraX[current] - projection.cameraX[previous]), ActorProjection.NEAR);
                clippedY[count] = projection.screenY(projection.cameraY[previous] + t * (projection.cameraY[current] - projection.cameraY[previous]), ActorProjection.NEAR);
                clippedDepth[count++] = ActorProjection.NEAR;
            }
            if (cIn)
            {
                clippedX[count] = projection.x[current]; clippedY[count] = projection.y[current];
                clippedDepth[count++] = cz;
            }
            previous = current;
        }
        for (int i = 1; i + 1 < count; i++)
            triangle(clippedX[0], clippedY[0], clippedDepth[0], clippedX[i], clippedY[i], clippedDepth[i],
                    clippedX[i + 1], clippedY[i + 1], clippedDepth[i + 1]);
    }

    private void triangle(int ax, int ay, float az, int bx, int by, float bz, int cx, int cy, float cz)
    {
        double area = (double) (bx - ax) * (cy - ay) - (double) (by - ay) * (cx - ax);
        if (area >= 0 || !coverage.intersects(Math.min(ax, Math.min(bx, cx)), Math.min(ay, Math.min(by, cy)),
                Math.max(ax, Math.max(bx, cx)), Math.max(ay, Math.max(by, cy)))) return;
        double qa = 1.0 / az, qb = 1.0 / bz, qc = 1.0 / cz;
        double dx = ((qb - qa) * (cy - ay) - (qc - qa) * (by - ay)) / area;
        double dy = ((bx - ax) * (qc - qa) - (cx - ax) * (qb - qa)) / area;
        int y1 = Math.max(clip.y, Math.min(ay, Math.min(by, cy))), y2 = Math.min(clip.y + clip.height, Math.max(ay, Math.max(by, cy)));
        for (int y = y1; y < y2; y++)
        {
            double sample = y + 0.5, lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            if (sample >= Math.min(ay, by) && sample < Math.max(ay, by)) { double v = ax + (sample - ay) * (bx - ax) / (by - ay); lo = Math.min(lo, v); hi = Math.max(hi, v); }
            if (sample >= Math.min(by, cy) && sample < Math.max(by, cy)) { double v = bx + (sample - by) * (cx - bx) / (cy - by); lo = Math.min(lo, v); hi = Math.max(hi, v); }
            if (sample >= Math.min(cy, ay) && sample < Math.max(cy, ay)) { double v = cx + (sample - cy) * (ax - cx) / (ay - cy); lo = Math.min(lo, v); hi = Math.max(hi, v); }
            int start = Math.max(clip.x, (int) Math.ceil(lo - 0.5)), end = Math.min(clip.x + clip.width, (int) Math.ceil(hi - 0.5));
            double q = qa + (start + 0.5 - ax) * dx + (sample - ay) * dy;
            int first = end, last = start;
            for (int x = start; x < end; x++, q += dx)
            {
                int index = y * width + x;
                if ((pixels[index] & 0xff000000) == 0 || q <= depth[index]) continue;
                depth[index] = (float) q;
                first = Math.min(first, x); last = x + 1;
            }
            if (first < last)
            {
                rowLeft[y] = Math.min(rowLeft[y], first); rowRight[y] = Math.max(rowRight[y], last);
                top = Math.min(top, y); bottom = Math.max(bottom, y + 1);
                left = Math.min(left, first); right = Math.max(right, last);
            }
        }
    }

    boolean intersects(int x1, int y1, int x2, int y2)
    {
        return bottom > top && x1 < right && x2 >= left && y1 < bottom && y2 >= top;
    }

    boolean intersectsRow(int y, int x1, int x2)
    {
        return y >= top && y < bottom && x1 < rowRight[y] && x2 > rowLeft[y];
    }

    boolean occludes(int x, int y, double inverseDepth)
    {
        return depth[y * width + x] > inverseDepth * 1.0001;
    }

    void release()
    {
        depth = null; pixels = null; rowLeft = rowRight = null;
        rendered = null; callbacks = null; coverage = null; clip = null;
        width = height = top = bottom = left = right = 0;
        prepared = false;
    }
}
