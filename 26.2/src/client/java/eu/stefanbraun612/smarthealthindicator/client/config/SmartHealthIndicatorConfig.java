package eu.stefanbraun612.smarthealthindicator.client.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import java.util.ArrayList;
import java.util.List;

@Config(name = "smarthealthindicator")
public class SmartHealthIndicatorConfig implements ConfigData {

	// --- World overlay ---

	public enum PassiveDisplayMode {
		// Never show floating health text on its own (the peek key still works).
		OFF,
		// Only entities that are not at full health.
		DAMAGED_ONLY,
		// Every living entity in range.
		ALL
	}

	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
	public PassiveDisplayMode passiveDisplayMode = PassiveDisplayMode.OFF;

	public enum PeekKeyBehavior {
		// Ignore the configured filter entirely while peeking - show every living entity.
		SHOW_ALL,
		// Use passiveDisplayMode as configured while peeking (so DAMAGED_ONLY stays
		// DAMAGED_ONLY instead of being upgraded to ALL); if it's OFF, falls back to ALL
		// so the peek key always shows something.
		USE_CONFIGURED_MODE
	}

	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
	public PeekKeyBehavior peekKeyBehavior = PeekKeyBehavior.SHOW_ALL;

	@ConfigEntry.Gui.Tooltip
	public boolean showAboveHead = true;

	@ConfigEntry.Gui.Tooltip
	public boolean showAtFeet = false;

	// Single toggle for both anchor points - when on, the entity's name is drawn on its
	// own line above the health text, whether that's above the head, at the feet, or both.
	@ConfigEntry.Gui.Tooltip
	public boolean showEntityName = false;

	// The overlay always respects real occlusion - opaque blocks (stone, dirt, doors, ...)
	// block it no matter what, for every entity. This only controls the borderline case of
	// non-occluding blocks (glass, leaves, water, ...), and only for whatever entity is
	// currently under your crosshair (like Health Indicators' "show through walls"): on,
	// you can see through them at your current target; off, any block hides it there too.
	@ConfigEntry.Gui.Tooltip
	public boolean showThroughTransparentBlocks = true;

	@ConfigEntry.Gui.Tooltip
	public float horizontalRadius = 16.0f; // blocks, combined X/Z distance from the player

	@ConfigEntry.Gui.Tooltip
	public float verticalRadius = 8.0f; // blocks, Y distance from the player

	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.BoundedDiscrete(min = 0, max = 2)
	public int healthDecimalPlaces = 1;

	// 1.0 = unchanged default size. Applies to both the world overlay text and the
	// crosshair line. Clamped to a small positive minimum at render time so a stray 0 or
	// negative value can't be typed in and break the text matrix.
	@ConfigEntry.Gui.Tooltip
	public float textScale = 1.0f;

	// Off by default - recomputing health text every frame is already cheap for normal
	// entity counts. When on, the formatted "x / y" text (not the entity-presence check,
	// which always stays live) is reused for up to cacheDurationMs before being refreshed,
	// keyed by entity ID and expired by wall-clock time so a stalled server tick can't
	// leave it stuck - see minecraft-modding-toolchain memory for the reasoning.
	@ConfigEntry.Gui.Tooltip
	public boolean cacheHealthText = false;

	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.BoundedDiscrete(min = 0, max = 1000)
	public int cacheDurationMs = 200;

	// --- Entity filtering ---
	// Applies to both the world overlay and the crosshair line - anything filtered out
	// here never gets an indicator either way.

	@ConfigEntry.Gui.Tooltip
	public boolean showHostile = true; // MobCategory.isFriendly() == false, e.g. zombies, skeletons

	@ConfigEntry.Gui.Tooltip
	public boolean showPassive = true; // MobCategory.isFriendly() == true, e.g. cows, villagers

	@ConfigEntry.Gui.Tooltip
	public boolean showPlayers = true; // other players, not yourself

	@ConfigEntry.Gui.Tooltip
	public boolean showSelf = false; // your own client player, e.g. when viewed in third person

	@ConfigEntry.Gui.Tooltip
	public List<String> entityTypeBlacklist = new ArrayList<>(); // exact registry IDs, e.g. "minecraft:zombie"

	@ConfigEntry.Gui.Tooltip
	public List<String> playerNameBlacklist = new ArrayList<>(); // exact player usernames, case-insensitive

	// --- Crosshair line ---

	@ConfigEntry.Gui.Tooltip
	public boolean showCrosshairLine = true;
}
