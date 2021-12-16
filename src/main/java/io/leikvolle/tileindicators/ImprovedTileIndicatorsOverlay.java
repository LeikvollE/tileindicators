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
import java.util.Comparator;
import java.util.stream.Collectors;
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
public class ImprovedTileIndicatorsOverlay extends Overlay {
    private final Client client;
    private final ImprovedTileIndicatorsConfig config;
    private final BufferedImage ARROW_ICON;

    @Inject
    private ImprovedTileIndicatorsPlugin plugin;

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
                renderTile(graphics, client.getSelectedSceneTile().getLocalLocation(), config.highlightHoveredColor(), config.hoveredTileBorderWidth());
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
                    renderRS3Tile(graphics, client.getLocalDestinationLocation(), config.highlightDestinationColor(), true);
                    break;
                case RS3_NO_ARROW:
                    renderRS3Tile(graphics, client.getLocalDestinationLocation(), config.highlightDestinationColor(), false);
                    break;
                case DEFAULT:
                    renderTile(graphics, client.getLocalDestinationLocation(), config.highlightDestinationColor(), config.destinationTileBorderWidth());
                    break;
            }
        }

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

        if (config.highlightCurrentTile())
        {
            renderTile(graphics, playerPosLocal, config.highlightCurrentColor(), config.currentTileBorderWidth());
        }

        if (config.overlaysBelowPlayer() && client.isGpu())
        {
            removeActor(graphics, client.getLocalPlayer());
        }
        if (config.overlaysBelowNPCs())
        {
            // Limits the number of npcs drawn below overlays, ranks the NPCs by distance to player.
            for (NPC npc : plugin.getOnTopNpcs().stream().sorted(Comparator.comparingInt(npc -> npc.getLocalLocation().distanceTo(playerPosLocal))).limit(config.maxNPCsDrawn()).collect(Collectors.toSet())) {
                removeActor(graphics, npc);
            }
        }
        return null;
    }

    private void renderTile(final Graphics2D graphics, final LocalPoint dest, final Color color, final double borderWidth)
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

        OverlayUtil.renderPolygon(graphics, poly, color, new BasicStroke((float) borderWidth));

    }

    private void renderRS3Tile(final Graphics2D graphics, final LocalPoint dest, final Color color, boolean drawArrow)
    {
        if (dest == null)
        {
            return;
        }
        double size = 0.65 * (Math.min(5.0, client.getGameCycle() - gameCycle) / 5.0);

        final Polygon poly = getCanvasTargetTileAreaPoly(client, dest, size, client.getPlane(), 10);
        final Polygon shadow = getCanvasTargetTileAreaPoly(client, dest, size, client.getPlane(), 0);
        Point canvasLoc = Perspective.getCanvasImageLocation(client, dest, ARROW_ICON, 150 + (int) (20 * Math.sin(client.getGameCycle() / 10.0)));

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

        if (canvasLoc != null && drawArrow && shadow != null)
        {
            // TODO: improve scale as you zoom out
            double imageScale = 0.8 * Math.min(client.get3dZoom() / 500.0, 1);
            graphics.drawImage(ARROW_ICON, (int) (shadow.getBounds().width / 2 + shadow.getBounds().x - ARROW_ICON.getWidth() * imageScale / 2), canvasLoc.getY(), (int) (ARROW_ICON.getWidth() * imageScale), (int) (ARROW_ICON.getHeight() * imageScale), null);
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
            double angle = ((float) i / resolution) * 2 * Math.PI;
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

    private void removeActor(final Graphics2D graphics, final Actor actor) {
        final int clipX1 = client.getViewportXOffset();
        final int clipY1 = client.getViewportYOffset();
        final int clipX2 = client.getViewportWidth() + clipX1;
        final int clipY2 = client.getViewportHeight() + clipY1;
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

        int size = 1;
        if (actor instanceof NPC)
        {
            NPCComposition composition = ((NPC) actor).getTransformedComposition();
            if (composition != null)
            {
                size = composition.getSize();
            }
        }

        final LocalPoint lp = actor.getLocalLocation();

        final int localX = lp.getX();
        final int localY = lp.getY();
        final int northEastX = lp.getX() + Perspective.LOCAL_TILE_SIZE * (size - 1) / 2;
        final int northEastY = lp.getY() + Perspective.LOCAL_TILE_SIZE * (size - 1) / 2;
        final LocalPoint northEastLp = new LocalPoint(northEastX, northEastY);
        int localZ = Perspective.getTileHeight(client, northEastLp, client.getPlane());
        int rotation = actor.getCurrentOrientation();

        Perspective.modelToCanvas(client, vCount, localX, localY, localZ, rotation, x3d, z3d, y3d, x2d, y2d);

        boolean anyVisible = false;

        for (int i = 0; i < vCount; i++) {
            int x = x2d[i];
            int y = y2d[i];

            boolean visibleX = x >= clipX1 && x < clipX2;
            boolean visibleY = y >= clipY1 && y < clipY2;
            anyVisible |= visibleX && visibleY;
        }

        if (!anyVisible) return;

        int tCount = model.getFaceCount();
        int[] tx = model.getFaceIndices1();
        int[] ty = model.getFaceIndices2();
        int[] tz = model.getFaceIndices3();

        Composite orig = graphics.getComposite();
        graphics.setComposite(AlphaComposite.Clear);
        graphics.setColor(Color.WHITE);
        for (int i = 0; i < tCount; i++) {
            // Cull tris facing away from the camera
            if (getTriDirection(x2d[tx[i]], y2d[tx[i]], x2d[ty[i]], y2d[ty[i]], x2d[tz[i]], y2d[tz[i]]) >= 0)
            {
                continue;
            }
            Polygon p = new Polygon(
                    new int[]{x2d[tx[i]], x2d[ty[i]], x2d[tz[i]]},
                    new int[]{y2d[tx[i]], y2d[ty[i]], y2d[tz[i]]},
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