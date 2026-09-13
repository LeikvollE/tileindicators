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
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;

/** Cheap selection priorities; no model requests or renderer changes. */
final class NpcPriority
{
    static final int NAMED = 0;
    static final int LOCAL_COMBAT = 1;
    static final int BOSS = 2;
    static final int OTHER_COMBAT = 3;
    static final int NEARBY = 4;

    // The public NPC API has no universal boss flag. Exact in-game names cover
    // transformed phases without enumerating every form's ID. Keep this list
    // current when new bosses arrive; followers are excluded below.
    private static final Set<String> BOSSES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "Abyssal Sire", "Alchemical Hydra", "Amoxliatl", "Araxxor", "Artio",
            "Barrelchest", "Basilisk Sentinel", "Bryophyta", "Brutus", "Callisto",
            "Calvar'ion", "Cerberus", "Chaos Elemental", "Chaos Fanatic",
            "Commander Zilyana", "Corporeal Beast", "Crazy archaeologist",
            "Dagannoth Prime", "Dagannoth Rex", "Dagannoth Supreme", "Dawn", "Dusk",
            "Deranged archaeologist", "Duke Sucellus", "Galvek", "Gemstone Crab",
            "General Graardor", "Giant Mole", "Hespori", "K'ril Tsutsaroth",
            "Kalphite Queen", "King Black Dragon", "Kraken", "Kree'arra", "Mad Angel",
            "The Mimic", "Nex", "Obor", "Phantom Muspah", "Phosani's Nightmare",
            "Sarachnis", "Scorpia", "Scurrius", "Shellbane gryphon", "Skotizo",
            "Spindel", "The Hueycoatl", "Hueycoatl body", "Hueycoatl tail",
            "The Leviathan", "The Nightmare", "The Whisperer", "Thermonuclear smoke devil",
            "Vardorvis", "Vet'ion", "Vorkath", "Yama", "Zalcano", "Zulrah",
            "Crystalline Hunllef", "Corrupted Hunllef", "TzTok-Jad", "JalTok-Jad",
            "TzKal-Zuk", "Sol Heredit", "Doom of Mokhaiotl",
            "Branda the Fire Queen", "Eldric the Ice King", "Blood Moon", "Blue Moon", "Eclipse Moon",
            "Great Olm", "Great Olm (Left claw)", "Great Olm (Right claw)",
            "Tekton", "Tekton (enraged)", "Vasa Nistirio", "Vespula", "Vanguard", "Muttadile",
            "The Maiden of Sugadinti", "Pestilent Bloat", "Nylocas Vasilias",
            "Sotetseg", "Xarpus", "Verzik Vitur", "Akkha", "Ba-Ba", "Kephri", "Zebak",
            "Tumeken's Warden", "Elidinis' Warden", "Tempoross")));

    static int of(NPC npc, Player local, Actor localTarget)
    {
        if (npc == null || npc.isDead()) return NEARBY;
        NPCComposition composition = npc.getTransformedComposition();
        if (composition == null || composition.isFollower()) return NEARBY;
        boolean combatant = npc.getCombatLevel() > 0;
        Actor target = combatant ? npc.getInteracting() : null;
        if (combatant && (npc == localTarget || target == local)) return LOCAL_COMBAT;
        if (BOSSES.contains(npc.getName())) return BOSS;
        // A live health bar also catches combat when the opponent is outside
        // visibility range. Non-combat NPCs and following pets do not qualify.
        if (combatant && (target != null || npc.getHealthRatio() >= 0)) return OTHER_COMBAT;
        return NEARBY;
    }

    private NpcPriority() { }
}
