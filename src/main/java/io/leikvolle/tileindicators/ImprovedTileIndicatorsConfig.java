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

	@Alpha
	@ConfigItem(
			keyName = "highlightDestinationColor",
			name = "Destination tile",
			description = "Configures the highlight color of current destination",
			section = tileIndicatorsSection,
			position = 1
	)
	default Color highlightDestinationColor()
	{
		return new Color(0xFFB3B03F);
	}

	@ConfigItem(
			keyName = "highlightDestinationStyle",
			name = "Destination style",
			description = "The style to display the destination tile in",
			section = tileIndicatorsSection,
			position = 2
	)
	default TileStyle highlightDestinationStyle()
	{
		return TileStyle.DEFAULT;
	}

	@ConfigItem(
			keyName = "highlightDestinationTile",
			name = "Highlight destination tile",
			description = "Highlights tile player is walking to",
			section = tileIndicatorsSection,
			position = 3
	)
	default boolean highlightDestinationTile()
	{
		return true;
	}

	@ConfigItem(
			keyName = "destinationTileBorderWidth",
			name = "Destination border width",
			description = "Width of the destination tile marker border",
			section = tileIndicatorsSection,
			position = 4
	)
	default double destinationTileBorderWidth()
	{
		return 2;
	}

	@Alpha
	@ConfigItem(
			keyName = "highlightHoveredColor",
			name = "Hovered tile",
			description = "Configures the highlight color of hovered tile",
			section = tileIndicatorsSection,
			position = 5
	)
	default Color highlightHoveredColor()
	{
		return new Color(0, 0, 0, 0);
	}

	@ConfigItem(
			keyName = "highlightHoveredTile",
			name = "Highlight hovered tile",
			description = "Highlights tile player is hovering with mouse",
			section = tileIndicatorsSection,
			position = 6
	)
	default boolean highlightHoveredTile()
	{
		return false;
	}

	@ConfigItem(
			keyName = "hoveredTileBorderWidth",
			name = "Hovered tile border width",
			description = "Width of the hovered tile marker border",
			section = tileIndicatorsSection,
			position = 7
	)
	default double hoveredTileBorderWidth()
	{
		return 2;
	}

	@Alpha
	@ConfigItem(
			keyName = "highlightCurrentColor",
			name = "True tile",
			description = "Configures the highlight color of current true tile",
			section = tileIndicatorsSection,
			position = 8
	)
	default Color highlightCurrentColor()
	{
		return Color.CYAN;
	}

	@ConfigItem(
			keyName = "highlightCurrentTile",
			name = "Highlight true tile",
			description = "Highlights true tile player is on as seen by server",
			section = tileIndicatorsSection,
			position = 9
	)
	default boolean highlightCurrentTile()
	{
		return true;
	}

	@ConfigItem(
			keyName = "currentTileBorderWidth",
			name = "True tile border width",
			description = "Width of the true tile marker border",
			section = tileIndicatorsSection,
			position = 10
	)
	default double currentTileBorderWidth()
	{
		return 2;
	}

	@ConfigItem(
			keyName = "currentTileBelowPlayer",
			name = "Draw overlays below player",
			description = "Requires GPU. Draws overlays below the player",
			section = tileIndicatorsSection,
			position = 11
	)
	default boolean overlaysBelowPlayer()
	{
		return true;
	}

	@ConfigSection(
			name = "NPC Indicators",
			description = "Settings enhancing the standard NPC indicators",
			position = 1
	)
	String npcIndicatorsSection = "npcIndicatorsSection";

	@ConfigItem(
			keyName = "overlaysBelowNPCs",
			name = "Draw overlays below NPCs",
			description = "Requires GPU. Draws overlays below specified NPCs. CAUTION: Will make your game laggy if many NPCs are drawn above overlay at once. Best used for bosses, not large groups of NPCs.",
			section = npcIndicatorsSection,
			position = 12
	)
	default boolean overlaysBelowNPCs()
	{
		return true;
	}

	@ConfigItem(
			keyName = "topNPCs",
			name = "NPCs to draw on top",
			description = "List of NPCs to draw above overlays. To add NPCs, shift right-click them and click Draw-Above.",
			section = npcIndicatorsSection,
			position = 13
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