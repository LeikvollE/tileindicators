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

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Projection;
import net.runelite.api.WorldView;

/** GPU projection retaining camera-space coordinates for near-plane clipping. */
final class ActorProjection
{
    static final float NEAR = 50f;
    int[] x = new int[0];
    int[] y = new int[0];
    float[] cameraX = new float[0];
    float[] cameraY = new float[0];
    float[] depth = new float[0];
    int minX, minY, maxX, maxY;
    boolean crossesNearPlane;
    private final float[] projected = new float[3];
    private float cameraOriginX, cameraOriginY, cameraOriginZ;
    private float pitchSin, pitchCos, yawSin, yawCos;
    private float centerX, centerY, scale;
    private int viewportX, viewportY;

    void beginFrame(Client client)
    {
        cameraOriginX = client.getCameraFpX();
        cameraOriginY = client.getCameraFpY();
        cameraOriginZ = client.getCameraFpZ();
        pitchSin = (float) Math.sin(client.getCameraFpPitch());
        pitchCos = (float) Math.cos(client.getCameraFpPitch());
        yawSin = (float) Math.sin(client.getCameraFpYaw());
        yawCos = (float) Math.cos(client.getCameraFpYaw());
        centerX = client.getViewportWidth() / 2f;
        centerY = client.getViewportHeight() / 2f;
        viewportX = client.getViewportXOffset();
        viewportY = client.getViewportYOffset();
        scale = client.getScale();
    }

    boolean project(WorldView world, int localX, int localY, int localZ, int rotation,
                    float[] vx, float[] vy, float[] vz, int count)
    {
        if (world == null) return false;
        Projection worldProjection = world.isTopLevel() ? null : world.getCanvasProjection();
        if (!world.isTopLevel() && worldProjection == null) return false;
        if (x.length < count)
        {
            int capacity = Math.max(count, x.length * 2);
            x = new int[capacity]; y = new int[capacity];
            cameraX = new float[capacity]; cameraY = new float[capacity]; depth = new float[capacity];
        }
        float sin = Perspective.SINE[rotation] / 65536f;
        float cos = Perspective.COSINE[rotation] / 65536f;
        float offsetX = localX - cameraOriginX;
        float offsetY = localY - cameraOriginY;
        float offsetZ = localZ - cameraOriginZ;
        minX = minY = Integer.MAX_VALUE;
        maxX = maxY = Integer.MIN_VALUE;
        crossesNearPlane = false;
        for (int i = 0; i < count; i++)
        {
            float px = vx[i], py = vy[i], pz = vz[i];
            if (rotation != 0)
            {
                float originalX = px;
                px = originalX * cos + pz * sin;
                pz = pz * cos - originalX * sin;
            }
            float cx, cy, cz;
            if (worldProjection != null)
            {
                float[] point = worldProjection.project(px + localX, py + localZ, pz + localY, projected);
                cx = point[0]; cy = point[1]; cz = point[2];
            }
            else
            {
                px += offsetX; pz += offsetY; py += offsetZ;
                cx = px * yawCos + pz * yawSin;
                float forward = pz * yawCos - px * yawSin;
                cy = py * pitchCos - forward * pitchSin;
                cz = forward * pitchCos + py * pitchSin;
            }
            cameraX[i] = cx; cameraY[i] = cy; depth[i] = cz;
            if (cz >= NEAR)
            {
                int sx = screenX(cx, cz), sy = screenY(cy, cz);
                x[i] = sx; y[i] = sy;
                minX = Math.min(minX, sx); minY = Math.min(minY, sy);
                maxX = Math.max(maxX, sx); maxY = Math.max(maxY, sy);
            }
            else
            {
                x[i] = y[i] = Integer.MIN_VALUE;
                crossesNearPlane = true;
            }
        }
        return true;
    }

    int screenX(float value, float z) { return Math.round(centerX + value * scale / z + viewportX); }
    int screenY(float value, float z) { return Math.round(centerY + value * scale / z + viewportY); }
}
