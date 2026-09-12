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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.util.Arrays;
import net.runelite.api.BufferProvider;
import net.runelite.api.MainBufferProvider;

/** Clears fully hidden overlays directly; fractional opacity uses one union mask. */
final class TriangleMaskRasterizer
{
    private static final BasicStroke MASK_STROKE = new BasicStroke();
    private BufferedImage mask;
    private Graphics2D maskGraphics;
    private Graphics2D clearGraphics;
    private int[] maskPixels;
    private int[] targetPixels;
    private int[] dirtyLeft;
    private int[] dirtyRight;
    private int[] solidLeft;
    private int[] solidRight;
    private int[] overlayLeft;
    private int[] overlayRight;
    private int[] nextOverlayRow;
    private int dirtyTop;
    private int dirtyBottom;
    private int width;
    private int height;
    private int opacity;
    private boolean premultiplied;
    private boolean fastTriangles;
    private boolean active;
    private boolean directClear;
    private boolean maskDirty;
    private boolean emptyRows;
    private GroundItemOcclusion itemOcclusion;
    private double depthX, depthY, depthBase;
    private Rectangle clip;
    private final Polygon polygon = new Polygon(new int[3], new int[3], 3);

    void begin(Graphics2D graphics, BufferProvider buffer, int fallbackWidth, int fallbackHeight, int opacity)
    {
        active = false;
        directClear = false;
        disposeClearGraphics();
        this.opacity = Math.max(0, Math.min(100, opacity));
        int newWidth = buffer == null ? fallbackWidth : buffer.getWidth();
        int newHeight = buffer == null ? fallbackHeight : buffer.getHeight();
        if (newWidth <= 0 || newHeight <= 0)
        {
            targetPixels = null;
            return;
        }
        if (dirtyLeft == null || width != newWidth || height != newHeight)
        {
            if (maskGraphics != null) maskGraphics.dispose();
            mask = null;
            maskPixels = null;
            maskGraphics = null;
            width = newWidth;
            height = newHeight;
            dirtyLeft = new int[height];
            dirtyRight = new int[height];
            solidLeft = new int[height];
            solidRight = new int[height];
            overlayLeft = new int[height];
            overlayRight = new int[height];
            nextOverlayRow = new int[height];
            Arrays.fill(dirtyLeft, width);
            Arrays.fill(solidLeft, width);
        }
        else
        {
            for (int y = dirtyTop; y < dirtyBottom; y++)
            {
                if (maskDirty && dirtyLeft[y] < dirtyRight[y])
                {
                    Arrays.fill(maskPixels, y * width + dirtyLeft[y], y * width + dirtyRight[y], 0);
                }
                dirtyLeft[y] = width;
                dirtyRight[y] = 0;
                solidLeft[y] = width;
                solidRight[y] = 0;
            }
        }
        dirtyTop = height;
        dirtyBottom = 0;
        maskDirty = false;
        emptyRows = false;
        Arrays.fill(overlayLeft, -1);
        active = true;
        if (this.opacity == 0)
        {
            // Full clearing is idempotent: fallback triangles can target the
            // overlay directly, avoiding a costly second Java2D image pass.
            clearGraphics = (Graphics2D) graphics.create();
            clearGraphics.setStroke(MASK_STROKE);
            clearGraphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
            clearGraphics.setComposite(AlphaComposite.Clear);
        }

        Shape userClip = graphics.getClip();
        clip = userClip == null ? new Rectangle(0, 0, width, height)
                : userClip.getBounds().intersection(new Rectangle(0, 0, width, height));
        fastTriangles = graphics.getTransform().isIdentity()
                && (userClip == null || userClip instanceof Rectangle)
                && graphics.getRenderingHint(java.awt.RenderingHints.KEY_STROKE_CONTROL) != java.awt.RenderingHints.VALUE_STROKE_PURE;
        targetPixels = null;
        // The mask already includes the transform and clip, so the same pixel
        // compositor also handles triangles rasterized by the Java2D fallback.
        if (buffer instanceof MainBufferProvider)
        {
            java.awt.Image image = ((MainBufferProvider) buffer).getImage();
            if (image instanceof BufferedImage)
            {
                BufferedImage target = (BufferedImage) image;
                if (target.getWidth() == width && target.getHeight() == height
                        && target.getRaster().getDataBuffer() instanceof DataBufferInt
                        && target.getRaster().getSampleModel() instanceof SinglePixelPackedSampleModel
                        && ((SinglePixelPackedSampleModel) target.getRaster().getSampleModel()).getScanlineStride() == width
                        && target.getRaster().getDataBuffer().getOffset() == 0
                        && target.getRaster().getSampleModelTranslateX() == 0
                        && target.getRaster().getSampleModelTranslateY() == 0
                        && target.getColorModel() instanceof DirectColorModel)
                {
                    DirectColorModel colors = (DirectColorModel) target.getColorModel();
                    int[] pixels = ((DataBufferInt) target.getRaster().getDataBuffer()).getData();
                    if (pixels == buffer.getPixels() && (long) width * height <= pixels.length
                            && colors.getAlphaMask() == 0xff000000
                            && colors.getRedMask() == 0xff0000 && colors.getGreenMask() == 0xff00 && colors.getBlueMask() == 0xff)
                    {
                        targetPixels = pixels;
                        // RuneLite's GPU buffer remains TYPE_INT_ARGB_PRE even
                        // when its colour model reports straight alpha. The GPU
                        // uses GL_ONE blending, so RGB must fade along with alpha.
                        premultiplied = target.getType() == BufferedImage.TYPE_INT_ARGB_PRE
                                || target.isAlphaPremultiplied();
                    }
                }
            }
        }
        directClear = this.opacity == 0 && targetPixels != null;
        if (!directClear)
        {
            if (mask == null)
            {
                mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                maskPixels = ((DataBufferInt) mask.getRaster().getDataBuffer()).getData();
                maskGraphics = mask.createGraphics();
            }
            maskGraphics.setTransform(graphics.getTransform());
            maskGraphics.setClip(graphics.getClip());
            maskGraphics.setRenderingHints(graphics.getRenderingHints());
            // Keep fill rules consistent between the rasterizer and Java2D fallback.
            maskGraphics.setStroke(MASK_STROKE);
            maskGraphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
            maskGraphics.setComposite(AlphaComposite.Src);
            maskGraphics.setColor(Color.WHITE);
        }
    }

    void triangle(int ax, int ay, float az, int bx, int by, float bz, int cx, int cy, float cz,
            GroundItemOcclusion occlusion)
    {
        if (fastTriangles && occlusion != null && occlusion.intersects(Math.min(ax, Math.min(bx, cx)),
                Math.min(ay, Math.min(by, cy)), Math.max(ax, Math.max(bx, cx)), Math.max(ay, Math.max(by, cy))))
        {
            double area = (double) (bx - ax) * (cy - ay) - (double) (by - ay) * (cx - ax);
            if (area == 0) return;
            double qa = 1.0 / az, qb = 1.0 / bz, qc = 1.0 / cz;
            depthX = ((qb - qa) * (cy - ay) - (qc - qa) * (by - ay)) / area;
            depthY = ((bx - ax) * (qc - qa) - (cx - ax) * (qb - qa)) / area;
            depthBase = qa - ax * depthX - ay * depthY;
            itemOcclusion = occlusion;
        }
        try { triangle(ax, ay, bx, by, cx, cy); }
        finally { itemOcclusion = null; }
    }

    void triangle(int ax, int ay, int bx, int by, int cx, int cy)
    {
        if (opacity == 100 || !active) return;
        // Keep Java2D's exact clipping and stroke normalization for edge cases.
        if (!fastTriangles || (itemOcclusion == null && (!clip.contains(ax, ay) || !clip.contains(bx, by) || !clip.contains(cx, cy))))
        {
            polygon.xpoints[0] = ax;
            polygon.xpoints[1] = bx;
            polygon.xpoints[2] = cx;
            polygon.ypoints[0] = ay;
            polygon.ypoints[1] = by;
            polygon.ypoints[2] = cy;
            polygon.invalidate();
            if (clearGraphics != null)
            {
                clearGraphics.fill(polygon);
                return;
            }
            maskGraphics.fill(polygon);
            maskDirty = true;
            // Transforms and nonrectangular clips may spread the drawn bounds.
            Rectangle bounds = maskGraphics.getTransform().createTransformedShape(polygon).getBounds();
            markDirty(bounds.x, bounds.y, (long) bounds.x + bounds.width + 1, (long) bounds.y + bounds.height + 1);
            return;
        }
        if (ay > by) { int t = ax; ax = bx; bx = t; t = ay; ay = by; by = t; }
        if (by > cy) { int t = bx; bx = cx; cx = t; t = by; by = cy; cy = t; }
        if (ay > by) { int t = ax; ax = bx; bx = t; t = ay; ay = by; by = t; }
        if (ay == cy) return;
        if (itemOcclusion != null && (!clip.contains(ax, ay) || !clip.contains(bx, by) || !clip.contains(cx, cy)))
        {
            clippedDepthTriangle(ax, ay, bx, by, cx, cy);
            return;
        }

        // Integer polygon fills sample integer scanlines. Ten fractional bits
        // preserve Java2D's edge stepping, including its rounding at thin edges.
        long longStep = ((long) (cx - ax) << 10) / (cy - ay);
        long longX = (long) ax << 10;
        if (ay < by)
        {
            long shortStep = ((long) (bx - ax) << 10) / (by - ay);
            long shortX = (long) ax << 10;
            for (int y = ay; y < by; y++, longX += longStep, shortX += shortStep)
            {
                if (emptyRows)
                {
                    int next = nextOverlayRow(y, by);
                    longX += (next - y) * longStep;
                    shortX += (next - y) * shortStep;
                    y = next;
                    if (y == by) break;
                }
                span(y, longX, shortX);
            }
        }
        if (by < cy)
        {
            long shortStep = ((long) (cx - bx) << 10) / (cy - by);
            long shortX = (long) bx << 10;
            for (int y = by; y < cy; y++, longX += longStep, shortX += shortStep)
            {
                if (emptyRows)
                {
                    int next = nextOverlayRow(y, cy);
                    longX += (next - y) * longStep;
                    shortX += (next - y) * shortStep;
                    y = next;
                    if (y == cy) break;
                }
                span(y, longX, shortX);
            }
        }
    }

    private void clippedDepthTriangle(int ax, int ay, int bx, int by, int cx, int cy)
    {
        long step = ((long) (cx - ax) << 10) / (cy - ay);
        if (ay < by)
        {
            long shortStep = ((long) (bx - ax) << 10) / (by - ay);
            int start = Math.max(ay, clip.y), end = Math.min(by, clip.y + clip.height);
            long lx = ((long) ax << 10) + (start - ay) * step, sx = ((long) ax << 10) + (start - ay) * shortStep;
            for (int y = start; y < end; y++, lx += step, sx += shortStep) span(y, lx, sx);
        }
        if (by < cy)
        {
            long shortStep = ((long) (cx - bx) << 10) / (cy - by);
            int start = Math.max(by, clip.y), end = Math.min(cy, clip.y + clip.height);
            long lx = ((long) ax << 10) + (start - ay) * step, sx = ((long) bx << 10) + (start - by) * shortStep;
            for (int y = start; y < end; y++, lx += step, sx += shortStep) span(y, lx, sx);
        }
    }

    private void span(int y, long x1, long x2)
    {
        int left = (int) ((Math.min(x1, x2) + 1023) >> 10);
        int right = (int) ((Math.max(x1, x2) + 1023) >> 10);
        if (itemOcclusion != null)
        {
            left = Math.max(left, clip.x);
            right = Math.min(right, clip.x + clip.width);
        }
        if (left >= right) return;
        if (left >= solidLeft[y] && right <= solidRight[y]) return;
        if (targetPixels != null)
        {
            if (overlayLeft[y] < 0) findOverlayBounds(y);
            left = Math.max(left, overlayLeft[y]);
            right = Math.min(right, overlayRight[y]);
            if (left >= right) return;
        }
        if (left >= solidLeft[y] && right <= solidRight[y]) return;
        if (itemOcclusion != null && itemOcclusion.intersectsRow(y, left, right))
        {
            double q = depthBase + (left + 0.5) * depthX + (y + 0.5) * depthY;
            int start = left;
            for (int x = left; x < right; x++, q += depthX)
            {
                if (!itemOcclusion.occludes(x, y, q)) continue;
                if (start < x) fillSpan(y, start, x);
                start = x + 1;
            }
            if (start < right) fillSpan(y, start, right);
            return;
        }
        fillSpan(y, left, right);
    }

    private void fillSpan(int y, int left, int right)
    {
        if (left >= solidLeft[y] && right <= solidRight[y]) return;
        int row = y * width;
        int[] output = directClear ? targetPixels : maskPixels;
        int value = directClear ? 0 : -1;
        if (!directClear) maskDirty = true;
        if (left <= solidRight[y] && right >= solidLeft[y])
        {
            if (left < solidLeft[y]) Arrays.fill(output, row + left, row + solidLeft[y], value);
            if (right > solidRight[y]) Arrays.fill(output, row + solidRight[y], row + right, value);
            solidLeft[y] = Math.min(solidLeft[y], left);
            solidRight[y] = Math.max(solidRight[y], right);
        }
        else
        {
            Arrays.fill(output, row + left, row + right, value);
            if (right - left > solidRight[y] - solidLeft[y])
            {
                solidLeft[y] = left;
                solidRight[y] = right;
            }
        }
        dirtyLeft[y] = Math.min(dirtyLeft[y], left);
        dirtyRight[y] = Math.max(dirtyRight[y], right);
        dirtyTop = Math.min(dirtyTop, y);
        dirtyBottom = Math.max(dirtyBottom, y + 1);
    }

    private void findOverlayBounds(int y)
    {
        // Scan a row only when a triangle reaches it. Empty rows and margins
        // need no mask writes. Keep nonzero RGB even at zero alpha so clearing
        // preserves the existing pixel result for straight-alpha buffers too.
        int start = Math.max(0, clip.x);
        int end = (int) Math.min(width, (long) clip.x + clip.width);
        int row = y * width;
        int[] pixels = targetPixels;
        while (start + 7 < end && (pixels[row + start] | pixels[row + start + 1]
                | pixels[row + start + 2] | pixels[row + start + 3] | pixels[row + start + 4]
                | pixels[row + start + 5] | pixels[row + start + 6] | pixels[row + start + 7]) == 0) start += 8;
        while (start < end && pixels[row + start] == 0) start++;
        while (end - 8 >= start && (pixels[row + end - 1] | pixels[row + end - 2]
                | pixels[row + end - 3] | pixels[row + end - 4] | pixels[row + end - 5]
                | pixels[row + end - 6] | pixels[row + end - 7] | pixels[row + end - 8]) == 0) end -= 8;
        while (end > start && pixels[row + end - 1] == 0) end--;
        overlayLeft[y] = start;
        overlayRight[y] = end;
        nextOverlayRow[y] = y + 1;
        if (start == end) emptyRows = true;
        // Within this pass overlays can only lose pixels. Cached bounds may
        // overestimate after a direct clear, but can never omit a later mask.
    }

    private int nextOverlayRow(int y, int end)
    {
        int first = y;
        while (y < end)
        {
            if (overlayLeft[y] < 0) findOverlayBounds(y);
            if (overlayLeft[y] < overlayRight[y]) break;
            y = nextOverlayRow[y];
        }
        y = Math.min(y, end);
        // Remember known-empty runs so subsequent triangles jump over them.
        if (first < y) nextOverlayRow[first] = y;
        return y;
    }

    private void markDirty(int x, int y, long right, long bottom)
    {
        int left = Math.max(0, x);
        int top = Math.max(0, y);
        int endX = (int) Math.min(width, right);
        int endY = (int) Math.min(height, bottom);
        if (left >= endX || top >= endY) return;
        for (int row = top; row < endY; row++)
        {
            dirtyLeft[row] = Math.min(dirtyLeft[row], left);
            dirtyRight[row] = Math.max(dirtyRight[row], endX);
        }
        dirtyTop = Math.min(dirtyTop, top);
        dirtyBottom = Math.max(dirtyBottom, endY);
    }

    void release()
    {
        itemOcclusion = null;
        disposeClearGraphics();
        if (maskGraphics != null) maskGraphics.dispose();
        maskGraphics = null;
        mask = null;
        maskPixels = targetPixels = null;
        dirtyLeft = dirtyRight = solidLeft = solidRight = null;
        overlayLeft = overlayRight = null;
        nextOverlayRow = null;
        clip = null;
        width = height = dirtyTop = dirtyBottom = 0;
        active = false;
        directClear = maskDirty = false;
        emptyRows = false;
    }

    private void disposeClearGraphics()
    {
        if (clearGraphics != null) clearGraphics.dispose();
        clearGraphics = null;
    }

    void apply(Graphics2D graphics)
    {
        try { applyMask(graphics); }
        finally
        {
            disposeClearGraphics();
            active = false;
        }
    }

    private void applyMask(Graphics2D graphics)
    {
        if (opacity == 100 || directClear || dirtyTop >= dirtyBottom || !active) return;
        if (targetPixels != null)
        {
            for (int y = dirtyTop; y < dirtyBottom; y++)
            {
                int end = y * width + dirtyRight[y];
                for (int i = y * width + dirtyLeft[y]; i < end; i++)
                {
                    if (maskPixels[i] == 0) continue;
                    int pixel = targetPixels[i];
                    int alpha = ((pixel >>> 24) * opacity + 50) / 100;
                    if (alpha == 0) targetPixels[i] = 0;
                    else if (!premultiplied) targetPixels[i] = (pixel & 0xffffff) | (alpha << 24);
                    else
                    {
                        int red = (((pixel >>> 16) & 255) * opacity + 50) / 100;
                        int green = (((pixel >>> 8) & 255) * opacity + 50) / 100;
                        int blue = ((pixel & 255) * opacity + 50) / 100;
                        targetPixels[i] = (alpha << 24) | (red << 16) | (green << 8) | blue;
                    }
                }
            }
        }
        else
        {
            Graphics2D output = (Graphics2D) graphics.create();
            try
            {
                // The mask is already in device coordinates and already clipped.
                output.setTransform(new AffineTransform());
                output.setClip(null);
                output.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT, 1f - opacity / 100f));
                output.drawImage(mask, 0, 0, null);
            }
            finally { output.dispose(); }
        }
    }
}
