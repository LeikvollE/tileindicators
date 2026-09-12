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

import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.Projection;
import net.runelite.api.Scene;
import net.runelite.api.hooks.DrawCallbacks;

/** Detects the public drawTemp entry point without depending on renderer plugins. */
final class RendererSupport
{
    private DrawCallbacks previous;
    private boolean objectCallbacks;

    boolean hasObjectCallbacks(DrawCallbacks renderer)
    {
        if (renderer == previous) return objectCallbacks;
        previous = renderer;
        objectCallbacks = false;
        if (renderer != null)
        {
            try
            {
                // Legacy renderers inherit this API method's no-op default.
                // Inspect public method metadata only, once per renderer change;
                // never access renderer internals or replace its callbacks.
                objectCallbacks = renderer.getClass().getMethod("drawTemp", Projection.class, Scene.class,
                        GameObject.class, Model.class, int.class, int.class, int.class, int.class)
                        .getDeclaringClass() != DrawCallbacks.class;
            }
            catch (NoSuchMethodException ignored)
            {
                // Retain legacy behavior if this entry point is unavailable.
            }
        }
        return objectCallbacks;
    }
}
