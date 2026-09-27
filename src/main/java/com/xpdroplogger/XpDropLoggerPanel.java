package com.xpdroplogger;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;

/** Side panel: start/stop tracking. */
class XpDropLoggerPanel extends PluginPanel
{
	private final XpDropLoggerPlugin plugin;

	private final JButton startButton = new JButton("Start tracking");
	private final JButton stopButton = new JButton("Stop tracking");

	private final PluginErrorPanel statusPanel = new PluginErrorPanel();

	XpDropLoggerPanel(XpDropLoggerPlugin plugin)
	{
		this.plugin = plugin;

		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());

		JPanel layoutPanel = new JPanel();
		layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
		add(layoutPanel, BorderLayout.NORTH);

		JPanel buttonPanel = new JPanel();
		buttonPanel.setBorder(new EmptyBorder(0, 0, 4, 0));
		buttonPanel.setLayout(new GridBagLayout());

		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(0, 2, 4, 2);

		c.gridx = 0;
		c.gridy = 0;
		buttonPanel.add(startButton, c);

		c.gridx = 1;
		c.gridy = 0;
		buttonPanel.add(stopButton, c);

		layoutPanel.add(buttonPanel);
		layoutPanel.add(statusPanel);

		startButton.setFocusable(false);
		stopButton.setFocusable(false);

		startButton.addActionListener(e -> plugin.startTracking());
		stopButton.addActionListener(e -> plugin.stopTracking());

		updateStatus();
	}

	void updateStatus()
	{
		boolean tracking = plugin.isTracking();

		startButton.setEnabled(!tracking);
		stopButton.setEnabled(tracking);

		if (tracking)
		{
			statusPanel.setContent("Tracking", "Xp drops are being logged to " + plugin.getCurrentFileName()
				+ " in " + plugin.getDataDirPath() + ".");
		}
		else
		{
			statusPanel.setContent("Not tracking", "Click Start tracking to begin. Files are written to "
				+ plugin.getDataDirPath() + ".");
		}
	}
}
