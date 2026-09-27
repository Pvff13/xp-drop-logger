# XP Drop Logger

Logs every real xp drop to a CSV file for external analysis (Excel, Sheets, pandas,
whatever you like).

This is an overlay-free, observational plugin: it reads `StatChanged` events and writes
to disk. It never clicks, moves the mouse, or interacts with anything.

## Features

- A side panel (toolbar icon) with Start tracking / Stop tracking buttons; the current
  status text shows exactly which file and folder are being written to
- Tracking is off by default when the plugin loads - nothing gets written until you
  click Start
- Skips the login xp-sync burst so your first row is a real drop, not your entire
  account's stored xp
- One CSV row per genuine xp increase: `timestamp,skill,xp_gained,total_xp,level`
- Files are written to this plugin's sandboxed data directory
  (`.runelite/plugin-data/xp-drop-logger/`)

## Configuration

- **New file per session** (on by default) - starts a fresh timestamped CSV each time
  you click Start. Turn it off to append everything to one continuous `xp-drops.csv`
  instead.
