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

import java.awt.Color;

import net.runelite.client.config.*;

@ConfigGroup("improvedtileindicators")
public interface ImprovedTileIndicatorsConfig extends Config
{

	@ConfigSection(
			name = "Player Tile indicators",
			description = "Settings replacing the normal tile indicators plugin",
			position = 0
	)
	String tileIndicatorsSection = "tileIndicatorsSection";

	@ConfigItem(
			keyName = "currentTileBelowPlayer",
			name = "Draw overlays below player",
			description = "Requires GPU. Draws overlays below the player",
			section = tileIndicatorsSection,
			position = 1
	)
	default boolean overlaysBelowPlayer()
	{
		return true;
	}

	@ConfigSection(
			name = "Destination Tile",
			description = "Settings for modifying the destination tile",
			position = 1
	)
	String destinationTileSection = "destinationTileSection";

	@ConfigItem(
			keyName = "customDestinationTile",
			name = "Custom destination tile",
			description = "Enables the use of custom tile indicators on destination",
			section = destinationTileSection,
			position = 2
	)
	default boolean customDestinationTile() { return false;}

	@ConfigItem(
			keyName = "highlightDestinationStyle",
			name = "Destination Tile Style",
			description = "The style of the destination tile",
			section = destinationTileSection,
			position = 3
	)
	default TileStyle highlightDestinationStyle()  {return TileStyle.RS3;}

	@ConfigItem(
			keyName = "destinationTileBorderWitdh",
			name = "Destination tile border width",
			description = "The width of the custom destination indicator",
			section = destinationTileSection,
			position = 4
	)
	default double destinationTileBorderWidth() { return 2; }

	@Alpha
	@ConfigItem(
			keyName = "highlightDestinationColor",
			name = "Destination tile",
			description = "Configures the highlight color of current destination",
			section = destinationTileSection,
			position = 5
	)
	default Color highlightDestinationColor()
	{
		return new Color(0xFFB3B03F);
	}

	@ConfigSection(
			name = "NPC Indicators",
			description = "Settings enhancing the standard NPC indicators",
			position = 2
	)
	String npcIndicatorsSection = "npcIndicatorsSection";

	@ConfigItem(
			keyName = "overlaysBelowNPCs",
			name = "Draw overlays below NPCs",
			description = "Requires GPU. Draws overlays below specified NPCs. CAUTION: Will make your game laggy if many NPCs are drawn above overlay at once. Best used for bosses, not large groups of NPCs.",
			section = npcIndicatorsSection,
			position = 6
	)
	default boolean overlaysBelowNPCs()
	{
		return true;
	}

	@ConfigItem(
			keyName = "maxNPCsDrawn",
			name = "NPC limit",
			description = "The number of NPCs in the scene at a time to be affected by this plugin. Will affect FPS.",
			section = npcIndicatorsSection,
			position = 7
	)
	@Range(
			max = 20
	)
	default int maxNPCsDrawn() {return 10;}

	@ConfigItem(
			keyName = "topNPCs",
			name = "NPCs to draw on top",
			description = "List of NPCs to draw above overlays. To add NPCs, shift right-click them and click Draw-Above.",
			section = npcIndicatorsSection,
			position = 8
	)
	default String getTopNPCs()
	{
		return "";
	}

	@ConfigItem(
			keyName = "topNPCs",
			name = "",
			description = ""
	)
	void setTopNPCs(String npcsToDrawAbove);
}