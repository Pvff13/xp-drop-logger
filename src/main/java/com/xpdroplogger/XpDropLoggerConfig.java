package com.xpdroplogger;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("xpdroplogger")
public interface XpDropLoggerConfig extends Config
{
	@ConfigItem(
		position = 0,
		keyName = "newFilePerSession",
		name = "New file per session",
		description = "Start a fresh timestamped CSV file each time you log in, instead of appending to one continuous file"
	)
	default boolean newFilePerSession()
	{
		return true;
	}
}
