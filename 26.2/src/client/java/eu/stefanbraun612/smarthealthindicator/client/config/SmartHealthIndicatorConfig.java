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

	// Only matters when both showAboveHead and showAtFeet are on. Off (default): both may
	// render at once, so an entity with clear space on both ends shows the text twice.
	// On: try the head anchor first; only fall back to the feet anchor if the head anchor
	// itself has no line of sight (e.g. a 2-block-high space, where the head position is
	// blocked by the ceiling but the feet position isn't).
	@ConfigEntry.Gui.Tooltip
	public boolean prioritizeHeadOverFeet = false;

	// Single toggle for both anchor points - when on, the entity's name is drawn on its
	// own line above the health text, whether that's above the head, at the feet, or both.
	@ConfigEntry.Gui.Tooltip
	public boolean showEntityName = false;

	// Off by default. When on, a jockey (a living entity riding another living entity -
	// boats/minecarts don't count, since they aren't living entities) gets exactly one
	// indicator for the whole stack instead of one per member, cycling through each
	// member's health/name every 2 seconds with a distinct color per stack position
	// (bottom = blue-ish) so you can tell who's currently shown.
	@ConfigEntry.Gui.Tooltip
	public boolean jockeyCombinedIndicator = false;

	// Only has an effect while jockeyCombinedIndicator is also on. B(ottom)/T(op) for a
	// 2-member stack; for 3+, the bottom is still B, the top is still T, and everything in
	// between is numbered M1, M2, ... going up.
	@ConfigEntry.Gui.Tooltip
	public boolean jockeyShowPositionPrefix = true;

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

	// Hearts can't show fractional health, so a mob resting on a partial point (e.g. 19.7)
	// rounds to look full even though it isn't - see showFullHealthCheckmark below for how
	// that ambiguity is resolved. Applies to both the world overlay and the crosshair line.
	@ConfigEntry.Gui.Tooltip
	public boolean useHeartsDisplay = false;

	@ConfigEntry.Gui.Tooltip
	public boolean showFullHealthCheckmark = true; // only used when useHeartsDisplay is on

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

	public enum ListMode {
		// Show everything except what's in the list below.
		BLACKLIST,
		// Show only what's in the list below - an empty list then shows nothing.
		WHITELIST
	}

	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
	public ListMode entityTypeListMode = ListMode.BLACKLIST;

	@ConfigEntry.Gui.Tooltip
	public List<String> entityTypeBlacklist = new ArrayList<>(); // exact registry IDs, e.g. "minecraft:zombie"

	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
	public ListMode playerNameListMode = ListMode.BLACKLIST;

	@ConfigEntry.Gui.Tooltip
	public List<String> playerNameBlacklist = new ArrayList<>(); // exact player usernames, case-insensitive

	// --- Crosshair line ---

	@ConfigEntry.Gui.Tooltip
	public boolean showCrosshairLine = true;
}
