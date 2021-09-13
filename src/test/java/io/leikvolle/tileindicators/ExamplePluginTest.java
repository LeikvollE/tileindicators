package io.leikvolle.tileindicators;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ExamplePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ImprovedTileIndicatorsPlugin.class);
		RuneLite.main(args);
	}
}