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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projection;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/** Selects a bounded set by NPC priority, then distance, without loading models. */
final class NearestActors
{
    static final int MAX_LIMIT = 1000;
    private final Actor[] actors = new Actor[MAX_LIMIT];
    private final double[] distances = new double[MAX_LIMIT];
    private final int[] priorities = new int[MAX_LIMIT];
    private final int[] orders = new int[MAX_LIMIT];
    private final Set<Actor> selected = Collections.newSetFromMap(new IdentityHashMap<>());
    private final float[] playerPoint = new float[3], actorPoint = new float[3];
    private final NpcExclusions npcExclusions = new NpcExclusions();
    private Player localPlayer;
    private Actor localTarget;
    private LocalPoint origin;
    private WorldView playerWorld;
    private boolean mainOrigin;
    private int limit, count, sequence;

    void setExcludedNpcs(String names)
    {
        npcExclusions.setNames(names);
    }

    void select(Player local, Iterable<NPC> namedNpcs, WorldView world,
                boolean includePlayers, boolean includeAllNpcs, int requestedLimit)
    {
        clear();
        limit = Math.max(0, Math.min(MAX_LIMIT, requestedLimit));
        if (limit == 0 || local == null) return;
        localPlayer = local;
        localTarget = local.getInteracting();
        origin = local.getLocalLocation();
        playerWorld = local.getWorldView();
        try
        {
            if (origin == null || playerWorld == null) return;
            mainOrigin = toMainWorld(playerWorld, origin, playerPoint);
            if (namedNpcs != null) for (NPC npc : namedNpcs) add(npc, NpcPriority.NAMED);
            collectWorld(world, includePlayers, includeAllNpcs);
            sortSelected();
        }
        finally
        {
            localPlayer = null;
            localTarget = null;
            origin = null;
            playerWorld = null;
        }
    }

    private void collectWorld(WorldView world, boolean includePlayers, boolean includeAllNpcs)
    {
        if (world == null || (!includePlayers && !includeAllNpcs)) return;
        if (includeAllNpcs)
        {
            Iterable<? extends NPC> npcs = world.npcs();
            if (npcs != null) for (NPC npc : npcs) add(npc, NpcPriority.of(npc, localPlayer, localTarget));
        }
        if (includePlayers)
        {
            Iterable<? extends Player> players = world.players();
            if (players != null) for (Player player : players) add(player, NpcPriority.NEARBY);
        }
        Iterable<? extends WorldView> children = world.worldViews();
        if (children != null) for (WorldView child : children) collectWorld(child, includePlayers, includeAllNpcs);
    }

    private void add(Actor actor, int priority)
    {
        if (actor == null || actor == localPlayer || selected.contains(actor)) return;
        // Exclusions override named/combat/boss priority before using a slot or model.
        if (actor instanceof NPC && npcExclusions.matches(((NPC) actor).getName())) return;
        LocalPoint location = actor.getLocalLocation();
        WorldView world = actor.getWorldView();
        if (location == null || world == null) return;
        double dx, dy;
        if (world == playerWorld)
        {
            dx = (double) location.getX() - origin.getX();
            dy = (double) location.getY() - origin.getY();
        }
        else
        {
            if (!mainOrigin || !toMainWorld(world, location, actorPoint)) return;
            dx = actorPoint[0] - playerPoint[0];
            dy = actorPoint[2] - playerPoint[2];
        }
        double distance = dx * dx + dy * dy;
        if (count < limit)
        {
            actors[count] = actor;
            distances[count] = distance;
            priorities[count] = priority;
            orders[count] = sequence++;
            count++;
            if (count == limit) heapify();
        }
        else
        {
            // The worst selected actor is at the root. Replacing it takes
            // logarithmic work instead of shifting the entire selected array.
            if (priority > priorities[0] || (priority == priorities[0] && distance >= distances[0])) return;
            selected.remove(actors[0]);
            actors[0] = actor;
            distances[0] = distance;
            priorities[0] = priority;
            orders[0] = sequence++;
            siftDown(0, count);
        }
        selected.add(actor);
    }

    private boolean worse(int a, int b)
    {
        return priorities[a] > priorities[b] || (priorities[a] == priorities[b]
                && (distances[a] > distances[b] || (distances[a] == distances[b] && orders[a] > orders[b])));
    }

    private void swap(int a, int b)
    {
        Actor actor = actors[a]; actors[a] = actors[b]; actors[b] = actor;
        double distance = distances[a]; distances[a] = distances[b]; distances[b] = distance;
        int priority = priorities[a]; priorities[a] = priorities[b]; priorities[b] = priority;
        int order = orders[a]; orders[a] = orders[b]; orders[b] = order;
    }

    private void siftDown(int index, int size)
    {
        for (int child = index * 2 + 1; child < size; child = index * 2 + 1)
        {
            if (child + 1 < size && worse(child + 1, child)) child++;
            if (!worse(child, index)) return;
            swap(index, child);
            index = child;
        }
    }

    private void heapify()
    {
        for (int i = count / 2 - 1; i >= 0; i--) siftDown(i, count);
    }

    private void sortSelected()
    {
        if (count < limit) heapify();
        // Restore priority/distance order for drawing and admission. Encounter
        // order breaks exact ties, matching the previous stable insertion sort.
        for (int end = count - 1; end > 0; end--)
        {
            swap(0, end);
            siftDown(0, end);
        }
    }

    private boolean toMainWorld(WorldView world, LocalPoint point, float[] result)
    {
        if (world.isTopLevel())
        {
            result[0] = point.getX();
            result[2] = point.getY();
            return true;
        }
        Projection projection = world.getMainWorldProjection();
        if (projection == null) return false;
        float[] projected = projection.project(point.getX(), 0, point.getY(), result);
        result[0] = projected[0];
        result[2] = projected[2];
        return Float.isFinite(result[0]) && Float.isFinite(result[2]);
    }

    int size() { return count; }
    Actor get(int index) { return actors[index]; }
    Set<Actor> selected() { return selected; }

    void clear()
    {
        Arrays.fill(actors, 0, count, null);
        selected.clear();
        count = sequence = 0;
    }
}
