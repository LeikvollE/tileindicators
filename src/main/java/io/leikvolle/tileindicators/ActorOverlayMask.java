/*
 * Copyright (c) 2021, LeikvollE
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.leikvolle.tileindicators;

import java.awt.Graphics2D;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/** Projects selected actors into a shared overlay mask with reusable buffers. */
final class ActorOverlayMask
{
    private final Client client;
    private final ActorProjection projection = new ActorProjection();
    private final OverlayCoverage coverage = new OverlayCoverage();
    private final ActorBoundsIndex boundsIndex = new ActorBoundsIndex(projection, coverage);
    private final TriangleMaskRasterizer rasterizer = new TriangleMaskRasterizer();
    private final GroundItemOcclusion itemOcclusion;
    private final float[] clippedDepth = new float[4];
    private Graphics2D passGraphics;
    private boolean passStarted;
    private int passOpacity;
    private final float[] boundsX = new float[8], boundsY = new float[8], boundsZ = new float[8];
    private final int[] clippedX = new int[4], clippedY = new int[4], face = new int[3];

    ActorOverlayMask(Client client) { this.client = client; this.itemOcclusion = new GroundItemOcclusion(client); }

    void beginFrame(Graphics2D graphics)
    {
        beginFrame(graphics, null, null);
    }

    void beginFrame(Graphics2D graphics, RenderedActors rendered, net.runelite.client.callback.RenderCallbackManager callbacks)
    {
        coverage.beginFrame(graphics.getTransform().isIdentity() ? client.getBufferProvider() : null,
                client.getViewportXOffset(), client.getViewportYOffset(),
                client.getViewportWidth(), client.getViewportHeight());
        projection.beginFrame(client);
        boundsIndex.beginFrame();
        boolean normalCanvas = graphics.getTransform().isIdentity()
                && (graphics.getClip() == null || graphics.getClip() instanceof java.awt.Rectangle);
        itemOcclusion.beginFrame(normalCanvas ? rendered : null, callbacks, coverage);
    }

    boolean hasOverlay() { return coverage.hasOverlay(); }

    void beginPass(Graphics2D graphics)
    {
        beginPass(graphics, 0);
    }

    void beginPass(Graphics2D graphics, int opacity)
    {
        passOpacity = Math.max(0, Math.min(100, opacity));
        passGraphics = graphics;
        passStarted = false;
    }

    private void startRasterizer()
    {
        // Avoid preparing/clearing a full-size mask when every actor is rejected.
        Graphics2D clipped = (Graphics2D) passGraphics.create();
        try
        {
            clipped.clipRect(client.getViewportXOffset(), client.getViewportYOffset(),
                    client.getViewportWidth(), client.getViewportHeight());
            rasterizer.begin(clipped, client.getBufferProvider(),
                    client.getViewportXOffset() + client.getViewportWidth(),
                    client.getViewportYOffset() + client.getViewportHeight(), passOpacity);
        }
        finally { clipped.dispose(); }
        passStarted = true;
    }

    void endPass(Graphics2D graphics)
    {
        try { if (passStarted) rasterizer.apply(graphics); }
        finally
        {
            passGraphics = null;
            passStarted = false;
        }
    }

    void release()
    {
        passGraphics = null;
        passStarted = false;
        rasterizer.release();
        itemOcclusion.release();
        boundsIndex.beginFrame();
        coverage.beginFrame(null, 0, 0, 0, 0);
    }

    void addActor(Actor actor, int localZ)
    {
        addActor(actor, localZ, null);
    }

    void addActor(Actor actor, int localZ, ActorMaskAdmission admission)
    {
        if (passOpacity == 100 || !hasOverlay()) return;
        LocalPoint location = actor.getLocalLocation();
        WorldView world = actor.getWorldView();
        if (location == null || world == null) return;
        if (admission != null && !admission.canRequest(actor)) return;
        if (!idleActorBoundsOverlap(actor, world, location, localZ)) return;
        itemOcclusion.prepare();
        Model model = actor.getModel();
        if (model == null) return;
        if (admission != null) admission.modelAvailable(actor);
        if (!boundsOverlap(model, world, location, localZ)) return;
        if (!projection.project(world, location.getX(), location.getY(), localZ, actor.getCurrentOrientation(),
                model.getVerticesX(), model.getVerticesY(), model.getVerticesZ(), model.getVerticesCount())) return;
        if (!projection.crossesNearPlane && !coverage.intersectsPixels(
                projection.minX, projection.minY, projection.maxX, projection.maxY)) return;

        int[] a = model.getFaceIndices1(), b = model.getFaceIndices2(), c = model.getFaceIndices3();
        byte[] transparency = model.getFaceTransparencies();
        int faceCount = model.getFaceCount();
        for (int i = 0; i < faceCount; i++)
        {
            if (transparency != null && (transparency[i] & 255) >= 254) continue;
            if (projection.depth[a[i]] >= ActorProjection.NEAR && projection.depth[b[i]] >= ActorProjection.NEAR
                    && projection.depth[c[i]] >= ActorProjection.NEAR)
            {
                fill(projection.x[a[i]], projection.y[a[i]], projection.depth[a[i]],
                        projection.x[b[i]], projection.y[b[i]], projection.depth[b[i]],
                        projection.x[c[i]], projection.y[c[i]], projection.depth[c[i]]);
            }
            else clipFace(a[i], b[i], c[i]);
        }
    }

    private boolean idleActorBoundsOverlap(Actor actor, WorldView world, LocalPoint location, int localZ)
    {
        // Reject distant idle actors before getModel(), which can rebuild their
        // equipment and animated meshes even when no overlay touches them.
        // Action animations, attached effects, and NPC-transformed players use
        // the full model path because their geometry can extend much farther.
        if (actor.getAnimation() != -1 || actor.getGraphic() != -1) return true;
        if (actor instanceof Player)
        {
            PlayerComposition appearance = ((Player) actor).getPlayerComposition();
            if (appearance == null || appearance.getTransformedNpcId() != -1) return true;
        }
        int height = Math.max(actor.getModelHeight(), actor.getLogicalHeight());
        int footprint = actor.getFootprintSize();
        if (height <= 0 || footprint <= 0 || height > 4096 || footprint > 4096) return true;
        int radius = Math.max(footprint * 2, height + 128);
        int vertical = height + 128;
        if (!boundsIndex.mayOverlap(world, location.getX(), location.getY(), localZ, radius, vertical, vertical)) return false;
        for (int i = 0; i < 8; i++)
        {
            boundsX[i] = (i & 1) == 0 ? -radius : radius;
            boundsY[i] = (i & 2) == 0 ? -vertical : vertical;
            boundsZ[i] = (i & 4) == 0 ? -radius : radius;
        }
        if (!projection.project(world, location.getX(), location.getY(), localZ, 0, boundsX, boundsY, boundsZ, 8)) return false;
        if (projection.maxX == Integer.MIN_VALUE) return false;
        return projection.crossesNearPlane || coverage.intersectsPixels(
                projection.minX, projection.minY, projection.maxX, projection.maxY);
    }

    private boolean boundsOverlap(Model model, WorldView world, LocalPoint location, int localZ)
    {
        model.calculateBoundsCylinder();
        int radius = model.getXYZMag(), height = model.getModelHeight(), bottom = model.getBottomY();
        if (radius <= 0 || height < 0 || bottom < 0) return true;
        if (!boundsIndex.mayOverlap(world, location.getX(), location.getY(), localZ, radius, height, bottom)) return false;
        for (int i = 0; i < 8; i++)
        {
            boundsX[i] = (i & 1) == 0 ? -radius : radius;
            boundsY[i] = (i & 2) == 0 ? -height : bottom;
            boundsZ[i] = (i & 4) == 0 ? -radius : radius;
        }
        if (!projection.project(world, location.getX(), location.getY(), localZ, 0, boundsX, boundsY, boundsZ, 8)) return false;
        if (projection.maxX == Integer.MIN_VALUE) return false;
        return projection.crossesNearPlane || coverage.intersectsPixels(
                projection.minX, projection.minY, projection.maxX, projection.maxY);
    }

    private void fill(int ax, int ay, float az, int bx, int by, float bz, int cx, int cy, float cz)
    {
        if (((long) bx - ax) * ((long) cy - ay) - ((long) by - ay) * ((long) cx - ax) >= 0) return;
        if (!coverage.intersects(Math.min(ax, Math.min(bx, cx)), Math.min(ay, Math.min(by, cy)),
                Math.max(ax, Math.max(bx, cx)), Math.max(ay, Math.max(by, cy)))) return;
        if (!passStarted) startRasterizer();
        rasterizer.triangle(ax, ay, az, bx, by, bz, cx, cy, cz, itemOcclusion);
    }

    private void clipFace(int a, int b, int c)
    {
        face[0] = a; face[1] = b; face[2] = c;
        int count = 0, previous = c;
        for (int current : face)
        {
            float previousZ = projection.depth[previous], currentZ = projection.depth[current];
            if (!Float.isFinite(previousZ) || !Float.isFinite(currentZ)) return;
            boolean previousInside = previousZ >= ActorProjection.NEAR, currentInside = currentZ >= ActorProjection.NEAR;
            if (previousInside != currentInside)
            {
                float t = (ActorProjection.NEAR - previousZ) / (currentZ - previousZ);
                float x = projection.cameraX[previous] + t * (projection.cameraX[current] - projection.cameraX[previous]);
                float y = projection.cameraY[previous] + t * (projection.cameraY[current] - projection.cameraY[previous]);
                clippedX[count] = projection.screenX(x, ActorProjection.NEAR);
                clippedY[count] = projection.screenY(y, ActorProjection.NEAR);
                clippedDepth[count++] = ActorProjection.NEAR;
            }
            if (currentInside)
            {
                clippedX[count] = projection.x[current];
                clippedY[count] = projection.y[current];
                clippedDepth[count++] = currentZ;
            }
            previous = current;
        }
        for (int i = 1; i + 1 < count; i++)
            fill(clippedX[0], clippedY[0], clippedDepth[0], clippedX[i], clippedY[i], clippedDepth[i],
                    clippedX[i + 1], clippedY[i + 1], clippedDepth[i + 1]);
    }
}
