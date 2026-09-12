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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.*;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;

/** Observes scene-selected actors, then honors the combined draw-filter result. */
@Singleton
final class RenderedActors implements RenderCallback
{
    private final Client client;
    private final Map<Actor, GameObject> actors = new IdentityHashMap<>();
    private final Map<Actor, Scene> scenes = new IdentityHashMap<>();
    private final Map<ItemLayer, Scene> groundItems = new IdentityHashMap<>();
    private volatile boolean recordGroundItems;
    private Set<? extends Actor> selectedActors = Collections.emptySet();
    private Player localPlayer;
    private GameObject localObject;
    private Scene localScene;
    private boolean checkingVisibility;

    @Inject
    RenderedActors(Client client)
    {
        this.client = client;
    }

    void beginFrame(Player local, Set<? extends Actor> selected)
    {
        synchronized (groundItems)
        {
            groundItems.clear();
            recordGroundItems = local != null || !selected.isEmpty();
        }
        actors.clear();
        scenes.clear();
        localPlayer = local;
        localObject = null;
        localScene = null;
        selectedActors = selected;
    }

    @Override
    public boolean drawObject(Scene scene, TileObject object)
    {
        // Item piles can be submitted by GPU render workers. Record references
        // only; model access and depth rasterization happen on the client thread.
        if (object instanceof ItemLayer)
        {
            if (recordGroundItems)
            {
                synchronized (groundItems)
                {
                    if (recordGroundItems && !checkingVisibility) groundItems.put((ItemLayer) object, scene);
                }
            }
            return true;
        }
        // Scene uploads can query these filters on the maploader thread. Only
        // client-thread draws belong to the current frame's actor selection.
        if (!client.isClientThread() || checkingVisibility || (localPlayer == null && selectedActors.isEmpty())) return true;
        if (object instanceof GameObject)
        {
            GameObject gameObject = (GameObject) object;
            Renderable renderable = gameObject.getRenderable();
            if (renderable == localPlayer && localPlayer != null)
            {
                localObject = gameObject;
                localScene = scene;
            }
            else if (renderable instanceof Actor && selectedActors.contains(renderable))
            {
                Actor actor = (Actor) renderable;
                actors.put(actor, gameObject);
                scenes.put(actor, scene);
            }
        }
        return true;
    }

    boolean hasGroundItems()
    {
        synchronized (groundItems) { return !groundItems.isEmpty(); }
    }

    void addGroundItemOccluders(GroundItemOcclusion occlusion, RenderCallbackManager callbacks)
    {
        synchronized (groundItems)
        {
            checkingVisibility = true;
            try
            {
                for (Map.Entry<ItemLayer, Scene> entry : groundItems.entrySet())
                    if (callbacks.drawObject(entry.getValue(), entry.getKey())) occlusion.add(entry.getKey());
            }
            finally { checkingVisibility = false; }
        }
    }


    boolean isVisible(Actor actor, RenderCallbackManager callbacks)
    {
        GameObject object = actor == localPlayer ? localObject : actors.get(actor);
        if (object == null) return false;
        Scene scene = actor == localPlayer ? localScene : scenes.get(actor);
        // An earlier callback is not proof that a later callback accepted the
        // actor. Recheck the public filter chain without recording another draw.
        checkingVisibility = true;
        try
        {
            return callbacks.drawObject(scene, object);
        }
        finally
        {
            checkingVisibility = false;
        }
    }
}
