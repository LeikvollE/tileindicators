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
import java.util.Set;
import net.runelite.api.Actor;

/** Introduces new masks gradually; admitted actors still use a fresh model every frame. */
final class ActorMaskAdmission
{
    private final Set<Actor> admitted = Collections.newSetFromMap(new IdentityHashMap<>());
    private Actor pendingActor;
    private int nextCandidate;

    void beginFrame(NearestActors nearest)
    {
        admitted.retainAll(nearest.selected());
        pendingActor = null;
        int count = nearest.size();
        if (count == 0)
        {
            nextCandidate = 0;
            return;
        }
        nextCandidate %= count;
        // retainAll guarantees admitted is a subset of this selection.
        if (admitted.size() == count) return;
        // Rotate attempts so unavailable or hidden models cannot hold up the queue.
        for (int visited = 0; visited < count; visited++)
        {
            Actor actor = nearest.get(nextCandidate);
            nextCandidate = (nextCandidate + 1) % count;
            if (!admitted.contains(actor))
            {
                pendingActor = actor;
                break;
            }
        }
    }

    boolean canRequest(Actor actor)
    {
        return actor != null && (actor == pendingActor || admitted.contains(actor));
    }

    void modelAvailable(Actor actor)
    {
        admitted.add(actor);
    }

    void clear()
    {
        admitted.clear();
        pendingActor = null;
        nextCandidate = 0;
    }
}
