package com.xpdroplogger;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;

/**
 * Logs every real xp drop (per-skill xp increase) to a CSV file under this plugin's
 * sandboxed data directory (.runelite/plugin-data/xp-drop-logger/). Purely observational
 * - reads client state and writes to disk, never clicks or interacts with anything.
 * Tracking is off by default; start/stop it from the side panel.
 */
@Slf4j
@PluginDescriptor(
	name = "XP Drop Logger",
	description = "Logs every xp drop to a CSV file for external analysis",
	tags = {"xp", "experience", "tracker", "log", "export", "csv"}
)
public class XpDropLoggerPlugin extends Plugin
{
	private static final DateTimeFormatter ROW_TIMESTAMP = DateTimeFormatter.ISO_INSTANT;
	private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

	@Inject
	private Client client;

	@Inject
	private XpDropLoggerConfig config;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ClientToolbar clientToolbar;

	private XpDropLoggerPanel panel;
	private NavigationButton navButton;

	private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);

	/**
	 * Ticks left to ignore incoming StatChanged events for. On login, the client fires a
	 * burst of StatChanged events syncing every skill's full stored xp - those aren't real
	 * drops and must not be logged as one. Mirrors the same initializeTracker pattern
	 * RuneLite's own built-in XP Tracker plugin uses for exactly this reason. This runs
	 * regardless of whether tracking is currently started, so the baseline is always
	 * accurate and ready the moment the user does click Start.
	 */
	private int initializeTicks;

	private String lastUsername;

	private boolean tracking;

	private Filepath dataDir;
	private Filepath sessionFile;

	@Provides
	XpDropLoggerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(XpDropLoggerConfig.class);
	}

	@Override
	protected void startUp() throws IOException
	{
		lastXp.clear();
		initializeTicks = 2;
		lastUsername = null;
		tracking = false;
		sessionFile = null;

		// Plugin.getPluginDirectory() is the sandboxed replacement for manually building
		// a path under RuneLite.RUNELITE_DIR - resolves to .runelite/plugin-data/xp-drop-logger.
		dataDir = getPluginDirectory();
		dataDir.createDirectories();

		panel = new XpDropLoggerPanel(this);
		BufferedImage icon = ImageUtil.loadImageResource(XpDropLoggerPlugin.class, "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("XP Drop Logger")
			.icon(icon)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;
		lastXp.clear();
	}

	void startTracking()
	{
		if (config.newFilePerSession())
		{
			// A pattern-based formatter like yyyy-MM-dd_HH-mm-ss needs actual calendar
			// fields, which a bare Instant doesn't carry - it must be attached to a zone
			// first (system default here, since this is only used for a local filename).
			String timestamp = FILE_TIMESTAMP.format(Instant.now().atZone(ZoneId.systemDefault()));
			sessionFile = dataDir.join("xp-drops_" + timestamp + ".csv");
		}

		tracking = true;

		if (panel != null)
		{
			panel.updateStatus();
		}
	}

	void stopTracking()
	{
		tracking = false;

		if (panel != null)
		{
			panel.updateStatus();
		}
	}

	boolean isTracking()
	{
		return tracking;
	}

	String getCurrentFileName()
	{
		return resolveTargetFile().getFileName();
	}

	String getDataDirPath()
	{
		return dataDir.toString();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();

		if (state == GameState.LOGGING_IN || state == GameState.HOPPING)
		{
			initializeTicks = 2;
		}
		else if (state == GameState.LOGGED_IN)
		{
			// LOGGED_IN also fires on region/instance changes, not just real logins -
			// only reset tracking if the character actually changed. Client.getUsername()
			// is deprecated (it's RuneLite's own OAuth account concept now, not the game
			// character), so the local player's display name is used instead.
			String username = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
			if (username != null && !username.equals(lastUsername))
			{
				lastUsername = username;
				lastXp.clear();
				initializeTicks = Math.max(initializeTicks, 2);
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (initializeTicks > 0)
		{
			initializeTicks--;

			if (initializeTicks == 0)
			{
				// Sync period over - seed the baseline from current totals so the very
				// next real gain computes a correct (small) delta instead of the
				// account's entire stored xp for each skill.
				for (Skill skill : Skill.values())
				{
					lastXp.put(skill, client.getSkillExperience(skill));
				}
			}
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (initializeTicks > 0)
		{
			return;
		}

		Skill skill = event.getSkill();
		if (skill == null)
		{
			// Skill.OVERALL was deprecated to a null constant (no longer a real Skill
			// value) - a null skill means nothing meaningful to log. EnumMap also
			// rejects null keys outright, so this guard is required, not just tidy.
			return;
		}

		int currentXp = event.getXp();
		Integer previousXp = lastXp.get(skill);

		if (previousXp == null)
		{
			// Shouldn't normally happen (baseline is seeded once the sync period ends),
			// but if it does, just establish the baseline rather than guess a delta.
			lastXp.put(skill, currentXp);
			return;
		}

		if (currentXp <= previousXp)
		{
			// Not a gain (xp only ever increases; boosts/drains change boostedLevel, not xp).
			return;
		}

		int xpGained = currentXp - previousXp;
		lastXp.put(skill, currentXp);

		if (!tracking)
		{
			return;
		}

		int level = event.getLevel();
		Instant now = Instant.now();
		Filepath targetFile = resolveTargetFile();

		executor.submit(() -> writeRow(targetFile, now, skill, xpGained, currentXp, level));
	}

	private Filepath resolveTargetFile()
	{
		if (config.newFilePerSession() && sessionFile != null)
		{
			return sessionFile;
		}

		return dataDir.join("xp-drops.csv");
	}

	/**
	 * Runs on the executor thread, never the client thread. Opens the file in append
	 * mode, writes one row, and closes it - simpler and more crash-safe than keeping a
	 * writer open across the session, at the cost of a bit of per-row overhead that's
	 * irrelevant off the client thread.
	 */
	private void writeRow(Filepath file, Instant timestamp, Skill skill, int xpGained, int totalXp, int level)
	{
		try
		{
			boolean isNewFile = !file.exists();

			try (Writer writer = file.openWriter(StandardOpenOption.CREATE, StandardOpenOption.APPEND))
			{
				if (isNewFile)
				{
					writer.write("timestamp,skill,xp_gained,total_xp,level\n");
				}

				writer.write(ROW_TIMESTAMP.format(timestamp));
				writer.write(',');
				writer.write(skill.getName());
				writer.write(',');
				writer.write(Integer.toString(xpGained));
				writer.write(',');
				writer.write(Integer.toString(totalXp));
				writer.write(',');
				writer.write(Integer.toString(level));
				writer.write('\n');
			}
		}
		catch (IOException e)
		{
			log.warn("Failed to write xp drop row", e);
		}
	}
}
