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

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.ImageUtil;

@Slf4j
public class ImprovedTileIndicatorsOverlay extends Overlay
{
    private final Client client;
    private final ImprovedTileIndicatorsConfig config;
    private final BufferedImage ARROW_ICON;

    private LocalPoint lastDestination;
    private int gameCycle;

    @Inject
    private ImprovedTileIndicatorsOverlay(Client client, ImprovedTileIndicatorsConfig config)
    {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(OverlayPriority.MED);

        ARROW_ICON = ImageUtil.loadImageResource(ImprovedTileIndicatorsPlugin.class, "arrow.png");
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (config.highlightHoveredTile())
        {
            // If we have tile "selected" render it
            if (client.getSelectedSceneTile() != null)
            {
                renderTile(graphics, client.getSelectedSceneTile().getLocalLocation(), config.highlightHoveredColor(), config.hoveredTileBorderWidth(), false);
            }
        }

        if (config.highlightDestinationTile())
        {
            if (lastDestination == null || !lastDestination.equals(client.getLocalDestinationLocation()))
            {
                gameCycle = client.getGameCycle();
                lastDestination = client.getLocalDestinationLocation();
            }
            switch (config.highlightDestinationStyle())
            {
                case RS3:
                    renderRS3Tile(graphics, client.getLocalDestinationLocation(), config.highlightDestinationColor());
                    break;
                case DEFAULT:
                    renderTile(graphics, client.getLocalDestinationLocation(), config.highlightDestinationColor(), config.destinationTileBorderWidth(), false);
                    break;
            }
        }

        if (config.highlightCurrentTile())
        {
            final WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
            if (playerPos == null)
            {
                return null;
            }

            final LocalPoint playerPosLocal = LocalPoint.fromWorld(client, playerPos);
            if (playerPosLocal == null)
            {
                return null;
            }

            renderTile(graphics, playerPosLocal, config.highlightCurrentColor(), config.currentTileBorderWidth(), config.currentTileBelowPlayer());
        }
        return null;
    }

    private void renderTile(final Graphics2D graphics, final LocalPoint dest, final Color color, final double borderWidth, final boolean removePlayer)
    {
        if (dest == null)
        {
            return;
        }

        final Polygon poly = Perspective.getCanvasTilePoly(client, dest);


        if (poly == null)
        {
            return;
        }

        if (removePlayer)
        {
            //Rectangle bounds = poly.getBounds();
            //BufferedImage tileImage = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_4BYTE_ABGR);
            //poly.translate(-bounds.x, -bounds.y);
            //Graphics2D g = tileImage.createGraphics();
            //g.setRenderingHint(
            //        RenderingHints.KEY_ANTIALIASING,
            //        RenderingHints.VALUE_ANTIALIAS_ON);
            OverlayUtil.renderPolygon(graphics, poly, color, new BasicStroke((float) borderWidth));
            removeActor(graphics, client.getLocalPlayer(), null);
            //graphics.drawImage(tileImage, bounds.x, bounds.y, null);
        }
        else
        {
            OverlayUtil.renderPolygon(graphics, poly, color, new BasicStroke((float) borderWidth));
        }
    }

    private void renderRS3Tile(final Graphics2D graphics, final LocalPoint dest, final Color color)
    {
        if (dest == null)
        {
            return;
        }
        double size = 0.65*(Math.min(5.0, client.getGameCycle()-gameCycle)/5.0);

        final Polygon poly = getCanvasTargetTileAreaPoly(client, dest, size, client.getPlane(), 10);
        final Polygon shadow = getCanvasTargetTileAreaPoly(client, dest, size, client.getPlane(), 0);
        Point canvasLoc = Perspective.getCanvasImageLocation(client, dest, ARROW_ICON, 150 + (int)(20 * Math.sin(client.getGameCycle()/10.0)));

        if (poly != null)
        {

            final Stroke originalStroke = graphics.getStroke();
            graphics.setStroke(new BasicStroke((float) config.destinationTileBorderWidth()));
            graphics.setColor(new Color(0x8D000000, true));
            graphics.draw(shadow);
            graphics.setColor(color);
            graphics.draw(poly);
            graphics.setStroke(originalStroke);
        }

        if (canvasLoc != null)
        {
            // TODO: improve scale as you zoom out
            double imageScale = 0.8*Math.min(client.get3dZoom()/500.0, 1);
            graphics.drawImage(ARROW_ICON, canvasLoc.getX(), canvasLoc.getY(), (int)(ARROW_ICON.getWidth()*imageScale),(int)(ARROW_ICON.getHeight()*imageScale), null);
        }

    }

    public static Polygon getCanvasTargetTileAreaPoly(
            @Nonnull Client client,
            @Nonnull LocalPoint localLocation,
            double size,
            int plane,
            int zOffset)
    {
        final int sceneX = localLocation.getSceneX();
        final int sceneY = localLocation.getSceneY();

        if (sceneX < 0 || sceneY < 0 || sceneX >= Perspective.SCENE_SIZE || sceneY >= Perspective.SCENE_SIZE)
        {
            return null;
        }

        Polygon poly = new Polygon();
        int resolution = 64;
        final int height = Perspective.getTileHeight(client, localLocation, plane) - zOffset;

        for (int i = 0; i < resolution; i++) {
            double angle = ((float)i/resolution)*2*Math.PI;
            double offsetX = Math.cos(angle);
            double offsetY = Math.sin(angle);
            int x = (int) (localLocation.getX() + (offsetX * Perspective.LOCAL_TILE_SIZE * size));
            int y = (int) (localLocation.getY() + (offsetY * Perspective.LOCAL_TILE_SIZE * size));
            Point p = Perspective.localToCanvas(client, x, y, height);
            if (p == null) {
                continue;
            }
            poly.addPoint(p.getX(), p.getY());

        }

        return poly;
    }

    private void removeActor(final Graphics2D graphics, final Actor actor, Rectangle bounds)
    {
        Object origAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
        Model model = actor.getModel();
        int vCount = model.getVerticesCount();
        int[] x3d = model.getVerticesX();
        int[] y3d = model.getVerticesY();
        int[] z3d = model.getVerticesZ();

        int[] x2d = new int[vCount];
        int[] y2d = new int[vCount];

        int localX = actor.getLocalLocation().getX();
        int localY = actor.getLocalLocation().getY();
        int localZ = Perspective.getTileHeight(client, client.getLocalPlayer().getLocalLocation(), client.getPlane());
        int rotation = actor.getCurrentOrientation();

        Perspective.modelToCanvas(client, vCount, localX, localY, localZ, rotation, x3d, z3d, y3d, x2d, y2d);

        int tCount = model.getTrianglesCount();
        int[] tx = model.getTrianglesX();
        int[] ty = model.getTrianglesY();
        int[] tz = model.getTrianglesZ();

        Composite orig = graphics.getComposite();
        graphics.setComposite(AlphaComposite.Clear);
        graphics.setColor(Color.WHITE);
        for (int i = 0; i < tCount; i++) {
            // Cull tris facing away from the camera and tris outside of the tile.
            if (getTriDirection(x2d[tx[i]], y2d[tx[i]], x2d[ty[i]], y2d[ty[i]], x2d[tz[i]], y2d[tz[i]]) >= 0){// || (!bounds.contains(x2d[tx[i]], y2d[tx[i]]) && !bounds.contains(x2d[ty[i]], y2d[ty[i]]) && !bounds.contains(x2d[tz[i]], y2d[tz[i]]))) {
                continue;
            }
            int xShift = 0;//-bounds.x;
            int yShift = 0;//-bounds.y;
            Polygon p = new Polygon(
                    new int[]{x2d[tx[i]]+xShift,x2d[ty[i]]+xShift,x2d[tz[i]]+xShift},
                    new int[]{y2d[tx[i]]+yShift,y2d[ty[i]]+yShift,y2d[tz[i]]+yShift},
                    3);
            graphics.fill(p);

        }
        graphics.setComposite(orig);
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                origAA);
    }

    private int getTriDirection(int x1, int y1, int x2, int y2, int x3, int y3) {
        int x4 = x2 - x1;
        int y4 = y2 - y1;
        int x5 = x3 - x1;
        int y5 = y3 - y1;
        return x4 * y5 - y4 * x5;
    }

}