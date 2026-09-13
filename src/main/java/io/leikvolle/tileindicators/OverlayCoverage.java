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

import java.util.Arrays;
import net.runelite.api.BufferProvider;

/**
 * Indexes overlay occupancy in small blocks. A summed-area table makes each
 * bounds/triangle query constant time, independent of the area it covers.
 */
final class OverlayCoverage
{
    private static final int BLOCK_SHIFT = 3;
    private static final int BLOCK_SIZE = 1 << BLOCK_SHIFT;
    private static final byte EMPTY = 1;
    private static final byte OCCUPIED = 2;

    private int[] pixels;
    private byte[] blocks = new byte[0];
    private int[] sums = new int[0];
    private int width;
    private int height;
    private int columns;
    private int rows;
    private int stride;
    private int left;
    private int top;
    private int right;
    private int bottom;
    private boolean overlayChecked;
    private boolean overlayPresent;
    private boolean indexed;
    private int queries;
    private int blockVisits;
    private final int[] queryLeft = new int[128], queryTop = new int[128];
    private final int[] queryRight = new int[128], queryBottom = new int[128];
    private final byte[] queryResults = new byte[128];

    void beginFrame(BufferProvider buffer, int x, int y, int viewportWidth, int viewportHeight)
    {
        overlayChecked = false;
        indexed = false;
        queries = blockVisits = 0;
        Arrays.fill(queryResults, (byte) 0);
        pixels = null;
        if (buffer == null || buffer.getWidth() <= 0 || buffer.getHeight() <= 0)
        {
            return;
        }
        int[] bufferPixels = buffer.getPixels();
        width = buffer.getWidth();
        height = buffer.getHeight();
        if (bufferPixels == null || (long) width * height > bufferPixels.length)
        {
            return;
        }
        pixels = bufferPixels;
        left = Math.max(0, x);
        top = Math.max(0, y);
        right = Math.min(width, x + viewportWidth);
        bottom = Math.min(height, y + viewportHeight);
        columns = (width + BLOCK_SIZE - 1) >> BLOCK_SHIFT;
        rows = (height + BLOCK_SIZE - 1) >> BLOCK_SHIFT;
        stride = columns + 1;
        int count = columns * rows;
        if (blocks.length < count)
        {
            blocks = new byte[count];
        }
        else Arrays.fill(blocks, 0, count, (byte) 0);
        if (sums.length < stride * (rows + 1))
        {
            sums = new int[stride * (rows + 1)];
        }
    }

    boolean hasOverlay()
    {
        if (pixels == null) return true;
        if (!overlayChecked)
        {
            overlayPresent = false;
            for (int y = top; y < bottom; y++)
            {
                if (rowHasAlpha(y * width + left, y * width + right))
                {
                    overlayPresent = true;
                    break;
                }
            }
            if (!overlayPresent) Arrays.fill(blocks, EMPTY);
            overlayChecked = true;
        }
        return overlayPresent;
    }

    // Bounds are inclusive, matching projected vertices and triangle bounds.
    boolean intersects(int minX, int minY, int maxX, int maxY)
    {
        if (pixels == null)
        {
            return true;
        }
        minX = Math.max(left, minX);
        minY = Math.max(top, minY);
        maxX = Math.min(right - 1, maxX);
        maxY = Math.min(bottom - 1, maxY);
        if (minX > maxX || minY > maxY)
        {
            return false;
        }
        if (!hasOverlay()) return false;
        int x1 = minX >> BLOCK_SHIFT, x2 = (maxX >> BLOCK_SHIFT) + 1;
        int y1 = minY >> BLOCK_SHIFT, y2 = (maxY >> BLOCK_SHIFT) + 1;
        // Small/empty scenes and clusters rejected by one shared bounds check
        // should not pay to index the whole viewport. Build it only after
        // repeated searches, reusing all blocks already checked this frame.
        if (!indexed && ++queries >= 64 && blockVisits >= 4096) buildIndex();
        if (indexed)
            return sums[y2 * stride + x2] - sums[y1 * stride + x2]
                    - sums[y2 * stride + x1] + sums[y1 * stride + x1] != 0;
        for (int by = y1; by < y2; by++)
        {
            for (int bx = x1; bx < x2; bx++)
            {
                blockVisits++;
                int index = by * columns + bx;
                if (blocks[index] == 0) blocks[index] = scanBlock(bx, by) ? OCCUPIED : EMPTY;
                if (blocks[index] == OCCUPIED) return true;
            }
        }
        return false;
    }

    private void buildIndex()
    {
        Arrays.fill(sums, 0, stride, 0);
        for (int by = 0; by < rows; by++)
        {
            int total = 0, previous = by * stride, row = previous + stride;
            sums[row] = 0;
            for (int bx = 0; bx < columns; bx++)
            {
                int index = by * columns + bx;
                if (blocks[index] == 0) blocks[index] = scanBlock(bx, by) ? OCCUPIED : EMPTY;
                if (blocks[index] == OCCUPIED) total++;
                sums[row + bx + 1] = sums[previous + bx + 1] + total;
            }
        }
        indexed = true;
    }

    private boolean scanBlock(int bx, int by)
    {
        int startX = Math.max(left, bx << BLOCK_SHIFT);
        int endX = Math.min(right, (bx + 1) << BLOCK_SHIFT);
        int startY = Math.max(top, by << BLOCK_SHIFT);
        int endY = Math.min(bottom, (by + 1) << BLOCK_SHIFT);
        if (startX >= endX) return false;
        for (int y = startY; y < endY; y++)
        {
            if (rowHasAlpha(y * width + startX, y * width + endX)) return true;
        }
        return false;
    }

    boolean intersectsPixels(int minX, int minY, int maxX, int maxY)
    {
        if (pixels == null) return true;
        minX = Math.max(left, minX);
        minY = Math.max(top, minY);
        maxX = Math.min(right - 1, maxX);
        maxY = Math.min(bottom - 1, maxY);
        if (minX > maxX || minY > maxY) return false;
        // Stacked actors commonly ask about the same rectangles. The overlay
        // cannot gain pixels during a mask pass, so reuse those pixel searches.
        int hash = minX * 73856093 ^ minY * 19349663 ^ maxX * 83492791 ^ maxY * 961748941;
        int slot = (hash ^ (hash >>> 16)) & (queryResults.length - 1);
        if (queryResults[slot] != 0 && queryLeft[slot] == minX && queryTop[slot] == minY
                && queryRight[slot] == maxX && queryBottom[slot] == maxY)
            return queryResults[slot] == OCCUPIED;
        boolean result = intersects(minX, minY, maxX, maxY) && scanPixels(minX, minY, maxX, maxY);
        queryLeft[slot] = minX; queryTop[slot] = minY;
        queryRight[slot] = maxX; queryBottom[slot] = maxY;
        queryResults[slot] = result ? OCCUPIED : EMPTY;
        return result;
    }

    private boolean scanPixels(int minX, int minY, int maxX, int maxY)
    {
        for (int by = minY >> BLOCK_SHIFT; by <= maxY >> BLOCK_SHIFT; by++)
        {
            for (int bx = minX >> BLOCK_SHIFT; bx <= maxX >> BLOCK_SHIFT; bx++)
            {
                int index = by * columns + bx;
                if (blocks[index] == EMPTY) continue;
                int startX = Math.max(minX, bx << BLOCK_SHIFT);
                int endX = Math.min(maxX + 1, (bx + 1) << BLOCK_SHIFT);
                int startY = Math.max(minY, by << BLOCK_SHIFT);
                int endY = Math.min(maxY + 1, (by + 1) << BLOCK_SHIFT);
                for (int y = startY; y < endY; y++)
                    if (rowHasAlpha(y * width + startX, y * width + endX)) return true;
            }
        }
        return false;
    }

    private boolean rowHasAlpha(int start, int end)
    {
        int[] data = pixels;
        int i = start;
        for (; i + 7 < end; i += 8)
        {
            if (((data[i] | data[i + 1] | data[i + 2] | data[i + 3]
                    | data[i + 4] | data[i + 5] | data[i + 6] | data[i + 7]) & 0xff000000) != 0) return true;
        }
        for (; i < end; i++)
            if ((data[i] & 0xff000000) != 0) return true;
        return false;
    }
}
