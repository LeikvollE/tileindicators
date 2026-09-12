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

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

final class ActorHeight
{
    static final int UNAVAILABLE = Integer.MIN_VALUE;

    static int get(Client client, Actor actor)
    {
        WorldView world = actor.getWorldView();
        LocalPoint location = actor.getLocalLocation();
        if (world == null || location == null || client.getWorldView(location.getWorldView()) != world)
        {
            return UNAVAILABLE;
        }
        int x = location.getSceneX(), y = location.getSceneY();
        if (x < 0 || y < 0 || x >= world.getSizeX() || y >= world.getSizeY()) return UNAVAILABLE;
        byte[][][] settings = world.getTileSettings();
        if (settings == null || settings.length < 2 || settings[1] == null || x >= settings[1].length
                || settings[1][x] == null || y >= settings[1][x].length)
        {
            return UNAVAILABLE;
        }
        int footprint;
        if (actor instanceof NPC)
        {
            NPCComposition composition = ((NPC) actor).getTransformedComposition();
            if (composition == null) return UNAVAILABLE;
            footprint = composition.getFootprintSize();
        }
        else footprint = actor.getFootprintSize();
        return Perspective.getFootprintTileHeight(client, location, world.getPlane(), footprint) - actor.getAnimationHeightOffset();
    }

    private ActorHeight() { }
}
