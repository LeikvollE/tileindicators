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

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

public class ImprovedTileIndicatorsOverlay extends Overlay
{
    private static final int CIRCLE_RESOLUTION = 64;
    private static final double[] CIRCLE_X = new double[CIRCLE_RESOLUTION], CIRCLE_Y = new double[CIRCLE_RESOLUTION];
    private static final Color SHADOW_COLOR = new Color(0x8D000000, true);
    static
    {
        for (int i = 0; i < CIRCLE_RESOLUTION; i++)
        {
            double angle = (double) i / CIRCLE_RESOLUTION * 2 * Math.PI;
            CIRCLE_X[i] = Math.cos(angle);
            CIRCLE_Y[i] = Math.sin(angle);
        }
    }

    private final Client client;
    private final ImprovedTileIndicatorsConfig config;
    private final ActorOverlayMask actorMask;
    private final BufferedImage arrowIcon;
    @Inject private ImprovedTileIndicatorsPlugin plugin;
    @Inject private RenderedActors renderedActors;
    @Inject private RenderCallbackManager renderCallbackManager;
    private LocalPoint lastDestination, lastlastDestination;
    private int spawnGameCycle, despawnGameCycle;
    private float strokeWidth = Float.NaN;
    private Stroke destinationStroke;

    @Inject
    private ImprovedTileIndicatorsOverlay(Client client, ImprovedTileIndicatorsConfig config)
    {
        this.client = client;
        this.config = config;
        actorMask = new ActorOverlayMask(client);
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        // Mask after scene overlays, including ground-item labels. Optional
        // item-overlay exceptions are scheduled afterward by LootOverlayOrder.
        setPriority(LootOverlayOrder.MASK_PRIORITY);
        arrowIcon = ImageUtil.loadImageResource(ImprovedTileIndicatorsPlugin.class, "arrow.png");
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        NearestActors nearest = plugin.getNearestActors();
        try
        {
            Player player = client.getLocalPlayer();
            if (player == null || player.getLocalLocation() == null) return null;
            if (config.customDestinationTile())
            {
                Graphics2D destination = (Graphics2D) graphics.create();
                try { renderDestination(destination); }
                finally { destination.dispose(); }
            }
            else reset();
            int opacity = Math.max(0, Math.min(100, config.overlayOpacity()));
            if (!client.isGpu() || opacity == 100) return null;
            boolean playerEnabled = config.overlaysBelowPlayer();
            boolean allNpcsEnabled = config.overlaysBelowAllNPCs() && config.maxNPCsDrawn() > 0;
            boolean namedNpcsEnabled = !allNpcsEnabled && config.overlaysBelowNPCs()
                    && config.maxNPCsDrawn() > 0 && !plugin.getOnTopNpcs().isEmpty();
            boolean npcsEnabled = allNpcsEnabled || namedNpcsEnabled;
            boolean othersEnabled = config.overlaysBelowOtherPlayers() && config.maxNPCsDrawn() > 0;
            if (!playerEnabled && !npcsEnabled && !othersEnabled) return null;
            actorMask.beginFrame(graphics, renderedActors, renderCallbackManager);
            if (!actorMask.hasOverlay()) return null;
            // Apply opacity once to the union of all character masks, so stacks
            // do not repeatedly fade the same overlay pixel.
            actorMask.beginPass(graphics, opacity);
            try
            {
                if (playerEnabled) maskActor(player);
                int limit = Math.min(nearest.size(), Math.max(0, config.maxNPCsDrawn()));
                for (int i = 0; i < limit; i++)
                {
                    Actor actor = nearest.get(i);
                    boolean enabled = actor instanceof Player ? othersEnabled
                            : allNpcsEnabled || (namedNpcsEnabled && plugin.getOnTopNpcs().contains(actor));
                    if (enabled) maskActor(actor);
                }
            }
            finally
            {
                actorMask.endPass(graphics);
            }
            return null;
        }
        finally
        {
            nearest.clear();
        }
    }

    private void maskActor(Actor actor)
    {
        // Only submitted characters can safely mask overlays. Renderers without
        // object callbacks cannot provide the visibility information required.
        if (!renderedActors.isVisible(actor, renderCallbackManager)) return;
        int height = ActorHeight.get(client, actor);
        if (height != ActorHeight.UNAVAILABLE)
            actorMask.addActor(actor, height, actor == client.getLocalPlayer() ? null : plugin.getActorMaskAdmission());
    }

    void reset()
    {
        lastDestination = lastlastDestination = null;
        spawnGameCycle = despawnGameCycle = 0;
    }

    void release()
    {
        reset();
        actorMask.release();
    }

    void resetWorld(WorldView world)
    {
        if ((lastDestination != null && lastDestination.getWorldView() == world.getId())
                || (lastlastDestination != null && lastlastDestination.getWorldView() == world.getId())) reset();
    }

    private void renderDestination(Graphics2D graphics)
    {
        LocalPoint destination = client.getLocalDestinationLocation();
        if (lastDestination != null && !lastDestination.equals(destination))
        {
            lastlastDestination = lastDestination;
            despawnGameCycle = client.getGameCycle();
        }
        if (lastDestination == null || !lastDestination.equals(destination))
        {
            if (destination != null) spawnGameCycle = client.getGameCycle();
            lastDestination = destination;
        }
        if (client.getGameCycle() - despawnGameCycle > 7) lastlastDestination = null;
        switch (config.highlightDestinationStyle())
        {
            case RS3:
                renderRS3Tile(graphics, lastDestination, config.highlightDestinationColor(), true, true);
                renderRS3Tile(graphics, lastlastDestination, config.highlightDestinationColor(), false, false);
                break;
            case RS3_NO_ARROW:
                renderRS3Tile(graphics, lastDestination, config.highlightDestinationColor(), false, true);
                renderRS3Tile(graphics, lastlastDestination, config.highlightDestinationColor(), false, false);
                break;
        }
    }

    private void renderRS3Tile(Graphics2D graphics, LocalPoint dest, Color color, boolean drawArrow, boolean appearing)
    {
        if (dest == null) return;
        WorldView world = client.getWorldView(dest.getWorldView());
        if (world == null) return;
        double size = appearing ? 0.65 * (Math.min(7.0, client.getGameCycle() - spawnGameCycle) / 7.0)
                : 0.65 * ((7 - (client.getGameCycle() - despawnGameCycle)) / 7.0);
        if (size < 0) return;
        Polygon poly = getCanvasTargetTileCirclePoly(client, dest, size, world.getPlane(), 10);
        Polygon shadow = getCanvasTargetTileCirclePoly(client, dest, size, world.getPlane(), 0);
        if (poly == null || shadow == null) return;
        float width = (float) config.destinationTileBorderWidth();
        if (!Float.isFinite(width) || width < 0) width = 2;
        if (width != strokeWidth)
        {
            destinationStroke = new BasicStroke(width);
            strokeWidth = width;
        }
        graphics.setStroke(destinationStroke);
        graphics.setColor(SHADOW_COLOR);
        graphics.draw(shadow);
        graphics.setColor(color);
        graphics.draw(poly);
        if (drawArrow && arrowIcon != null)
        {
            Point canvasLoc = Perspective.getCanvasImageLocation(client, dest, arrowIcon,
                    150 + (int) (20 * Math.sin(client.getGameCycle() / 10.0)));
            if (canvasLoc == null) return;
            double imageScale = 0.8 * Math.min(client.get3dZoom() / 500.0, 1);
            Rectangle bounds = shadow.getBounds();
            graphics.drawImage(arrowIcon, (int) (bounds.width / 2 + bounds.x - arrowIcon.getWidth() * imageScale / 2),
                    canvasLoc.getY(), (int) (arrowIcon.getWidth() * imageScale), (int) (arrowIcon.getHeight() * imageScale), null);
        }
    }

    public static Polygon getCanvasTargetTileCirclePoly(@Nonnull Client client, @Nonnull LocalPoint localLocation,
                                                        double size, int plane, int zOffset)
    {
        WorldView world = client.getWorldView(localLocation.getWorldView());
        if (world == null) return null;
        int x = localLocation.getSceneX(), y = localLocation.getSceneY();
        if (x < 0 || y < 0 || x >= world.getSizeX() || y >= world.getSizeY()) return null;
        Polygon poly = new Polygon();
        int height = Perspective.getTileHeight(client, localLocation, plane) - zOffset;
        for (int i = 0; i < CIRCLE_RESOLUTION; i++)
        {
            int localX = (int) (localLocation.getX() + CIRCLE_X[i] * Perspective.LOCAL_TILE_SIZE * size);
            int localY = (int) (localLocation.getY() + CIRCLE_Y[i] * Perspective.LOCAL_TILE_SIZE * size);
            Point point = Perspective.localToCanvas(client, localLocation.getWorldView(), localX, localY, height);
            if (point != null) poly.addPoint(point.getX(), point.getY());
        }
        return poly.npoints >= 3 ? poly : null;
    }
}
