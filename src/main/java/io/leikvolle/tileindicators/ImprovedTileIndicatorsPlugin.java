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

import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;

import static net.runelite.api.MenuAction.MENU_ACTION_DEPRIORITIZE_OFFSET;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import net.runelite.client.util.WildcardMatcher;

import java.util.*;

@PluginDescriptor(
		name = "Improved Tile Indicators",
		description = "An improved version of the tile indicators plugin",
		tags = {"rs3", "overlay", "tile", "indicators"}
)
@Slf4j
public class ImprovedTileIndicatorsPlugin extends Plugin
{
	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ImprovedTileIndicatorsOverlay overlay;

	@Inject ImprovedTileIndicatorsConfig config;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Getter(AccessLevel.PACKAGE)
	private final Set<NPC> onTopNpcs = new HashSet<>();
	private List<String> onTopNPCNames = new ArrayList<>();

	private static final String DRAW_ABOVE = "Draw-Above";
	private static final String DRAW_BELOW = "Draw-Below";
	private static final String UNTAG_ALL = "Un-tag-All";

	@Provides
	ImprovedTileIndicatorsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ImprovedTileIndicatorsConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		clientThread.invoke(this::rebuild);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN ||
				event.getGameState() == GameState.HOPPING)
		{
			onTopNpcs.clear();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if (!configChanged.getGroup().equals("improvedtileindicators"))
		{
			return;
		}

		clientThread.invoke(this::rebuild);
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned npcSpawned)
	{
		final NPC npc = npcSpawned.getNpc();
		final String npcName = npc.getName();

		if (npcName == null)
		{
			return;
		}

		if (onTopMatchesNPCName(npcName))
		{
			onTopNpcs.add(npc);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		final NPC npc = npcDespawned.getNpc();
		onTopNpcs.remove(npc);
	}


	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		int type = event.getType();

		if (type >= MENU_ACTION_DEPRIORITIZE_OFFSET)
		{
			type -= MENU_ACTION_DEPRIORITIZE_OFFSET;
		}

		final MenuAction menuAction = MenuAction.of(type);

		if (menuAction == MenuAction.EXAMINE_NPC && client.isKeyPressed(KeyCode.KC_SHIFT) && config.overlaysBelowNPCs())
		{
			final String npcName = getNameForCachedNPC(event.getIdentifier());
			if (npcName == null) return;
			boolean matchesList = onTopNPCNames.stream()
					.filter(highlight -> !highlight.equalsIgnoreCase(npcName))
					.anyMatch(highlight -> WildcardMatcher.matches(highlight, npcName));

			// Only show draw options to npcs not affected by a wildcard entry, as wildcards will not be removed by menu options
			if (!matchesList)
			{
				client.createMenuEntry(-1)
					.setOption(onTopNPCNames.stream().anyMatch(npcName::equalsIgnoreCase) ? DRAW_BELOW : DRAW_ABOVE)
					.setTarget(event.getTarget())
					.setIdentifier(event.getIdentifier())
					.setType(MenuAction.RUNELITE)
					.onClick(this::toggleDraw);
			}
		}
	}

	public void toggleDraw(MenuEntry click)
	{
		final String name = getNameForCachedNPC(click.getIdentifier());
		if (name == null) return;
		// this trips a config change which triggers the overlay rebuild
		updateNpcsToDrawAbove(name);
	}

	private void updateNpcsToDrawAbove(String npc)
	{
		final List<String> highlightedNpcs = new ArrayList<>(onTopNPCNames);

		if (!highlightedNpcs.removeIf(npc::equalsIgnoreCase))
		{
			highlightedNpcs.add(npc);
		}

		// this triggers the config change event and rebuilds npcs
		config.setTopNPCs(Text.toCSV(highlightedNpcs));
	}

	List<String> getTopNPCs()
	{
		final String configNpcs = config.getTopNPCs();

		if (configNpcs.isEmpty())
		{
			return Collections.emptyList();
		}

		return Text.fromCSV(configNpcs);
	}

	void rebuild()
	{
		onTopNPCNames = getTopNPCs();
		onTopNpcs.clear();

		if (client.getGameState() != GameState.LOGGED_IN &&
				client.getGameState() != GameState.LOADING)
		{
			return;
		}

		for (NPC npc : client.getNpcs())
		{
			final String npcName = npc.getName();

			if (npcName == null)
			{
				continue;
			}

			if (onTopMatchesNPCName(npcName))
			{
				onTopNpcs.add(npc);
			}
		}
	}

	private boolean onTopMatchesNPCName(String npcName)
	{
		for (String matching : onTopNPCNames)
		{
			if (WildcardMatcher.matches(matching, npcName))
			{
				return true;
			}
		}

		return false;
	}

	private String getNameForCachedNPC(int id)
	{
		final NPC[] cachedNPCs = client.getCachedNPCs();
		final NPC npc = cachedNPCs[id];

		if (npc == null)
		{
			return null;
		}

		return npc.getName();
	}

}