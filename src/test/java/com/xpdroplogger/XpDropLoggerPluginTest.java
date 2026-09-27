package com.xpdroplogger;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Developer entry point: boots a real RuneLite client with this plugin loaded, via {@code ./gradlew run}. */
public class XpDropLoggerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(XpDropLoggerPlugin.class);
		RuneLite.main(args);
	}
}
