package eu.stefanbraun612.smarthealthindicator.client.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.Requirement;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Hand-built (not annotation-generated) Cloth Config screen, so that fields can be
 * grouped into tabs and dependent fields can be hidden via Requirement - neither is
 * possible with AutoConfig's reflection-based screen generation. Mirrors the pattern
 * used by SmartAutoMineConfigScreen/SmartAutoAttackConfigScreen.
 */
public class SmartHealthIndicatorConfigScreen {

	private static final String PREFIX = "text.autoconfig.smarthealthindicator.";

	private static Component option(String field) {
		return Component.translatable(PREFIX + "option." + field);
	}

	private static Component tooltip(String field) {
		return Component.translatable(PREFIX + "option." + field + ".@Tooltip");
	}

	private static Component category(String key) {
		return Component.translatable(PREFIX + "category." + key);
	}

	public static Screen build(Screen parent) {
		ConfigHolder<SmartHealthIndicatorConfig> holder = AutoConfig.getConfigHolder(SmartHealthIndicatorConfig.class);
		SmartHealthIndicatorConfig config = holder.getConfig();
		SmartHealthIndicatorConfig defaults = new SmartHealthIndicatorConfig();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable(PREFIX + "title"))
				.setSavingRunnable(holder::save);
		ConfigEntryBuilder entryBuilder = builder.entryBuilder();

		// --- General tab ---

		ConfigCategory general = builder.getOrCreateCategory(category("general"));

		general.addEntry(entryBuilder
				.startBooleanToggle(option("displayEnabled"), config.displayEnabled)
				.setDefaultValue(defaults.displayEnabled)
				.setTooltip(tooltip("displayEnabled"))
				.setSaveConsumer(v -> config.displayEnabled = v)
				.build());

		general.addEntry(entryBuilder
				.startEnumSelector(option("passiveDisplayMode"), SmartHealthIndicatorConfig.PassiveDisplayMode.class, config.passiveDisplayMode)
				.setDefaultValue(defaults.passiveDisplayMode)
				.setTooltip(tooltip("passiveDisplayMode"))
				.setSaveConsumer(v -> config.passiveDisplayMode = v)
				.build());

		general.addEntry(entryBuilder
				.startEnumSelector(option("peekKeyBehavior"), SmartHealthIndicatorConfig.PeekKeyBehavior.class, config.peekKeyBehavior)
				.setDefaultValue(defaults.peekKeyBehavior)
				.setTooltip(tooltip("peekKeyBehavior"))
				.setSaveConsumer(v -> config.peekKeyBehavior = v)
				.build());

		general.addEntry(entryBuilder
				.startBooleanToggle(option("showCrosshairLine"), config.showCrosshairLine)
				.setDefaultValue(defaults.showCrosshairLine)
				.setTooltip(tooltip("showCrosshairLine"))
				.setSaveConsumer(v -> config.showCrosshairLine = v)
				.build());

		// --- Overlay tab ---

		ConfigCategory overlay = builder.getOrCreateCategory(category("overlay"));

		BooleanListEntry showAboveHead = entryBuilder
				.startBooleanToggle(option("showAboveHead"), config.showAboveHead)
				.setDefaultValue(defaults.showAboveHead)
				.setTooltip(tooltip("showAboveHead"))
				.setSaveConsumer(v -> config.showAboveHead = v)
				.build();
		overlay.addEntry(showAboveHead);

		BooleanListEntry showAtFeet = entryBuilder
				.startBooleanToggle(option("showAtFeet"), config.showAtFeet)
				.setDefaultValue(defaults.showAtFeet)
				.setTooltip(tooltip("showAtFeet"))
				.setSaveConsumer(v -> config.showAtFeet = v)
				.build();
		overlay.addEntry(showAtFeet);

		overlay.addEntry(entryBuilder
				.startBooleanToggle(option("prioritizeHeadOverFeet"), config.prioritizeHeadOverFeet)
				.setDefaultValue(defaults.prioritizeHeadOverFeet)
				.setTooltip(tooltip("prioritizeHeadOverFeet"))
				.setSaveConsumer(v -> config.prioritizeHeadOverFeet = v)
				.setDisplayRequirement(Requirement.all(
						Requirement.isTrue(showAboveHead),
						Requirement.isTrue(showAtFeet)))
				.build());

		overlay.addEntry(entryBuilder
				.startBooleanToggle(option("showEntityName"), config.showEntityName)
				.setDefaultValue(defaults.showEntityName)
				.setTooltip(tooltip("showEntityName"))
				.setSaveConsumer(v -> config.showEntityName = v)
				.build());

		overlay.addEntry(entryBuilder
				.startFloatField(option("horizontalRadius"), config.horizontalRadius)
				.setDefaultValue(defaults.horizontalRadius)
				.setTooltip(tooltip("horizontalRadius"))
				.setSaveConsumer(v -> config.horizontalRadius = v)
				.build());

		overlay.addEntry(entryBuilder
				.startFloatField(option("verticalRadius"), config.verticalRadius)
				.setDefaultValue(defaults.verticalRadius)
				.setTooltip(tooltip("verticalRadius"))
				.setSaveConsumer(v -> config.verticalRadius = v)
				.build());

		overlay.addEntry(entryBuilder
				.startIntField(option("healthDecimalPlaces"), config.healthDecimalPlaces)
				.setDefaultValue(defaults.healthDecimalPlaces)
				.setMin(0)
				.setMax(2)
				.setTooltip(tooltip("healthDecimalPlaces"))
				.setSaveConsumer(v -> config.healthDecimalPlaces = v)
				.build());

		BooleanListEntry useHeartsDisplay = entryBuilder
				.startBooleanToggle(option("useHeartsDisplay"), config.useHeartsDisplay)
				.setDefaultValue(defaults.useHeartsDisplay)
				.setTooltip(tooltip("useHeartsDisplay"))
				.setSaveConsumer(v -> config.useHeartsDisplay = v)
				.build();
		overlay.addEntry(useHeartsDisplay);

		overlay.addEntry(entryBuilder
				.startBooleanToggle(option("showFullHealthCheckmark"), config.showFullHealthCheckmark)
				.setDefaultValue(defaults.showFullHealthCheckmark)
				.setTooltip(tooltip("showFullHealthCheckmark"))
				.setSaveConsumer(v -> config.showFullHealthCheckmark = v)
				.setDisplayRequirement(Requirement.isTrue(useHeartsDisplay))
				.build());

		overlay.addEntry(entryBuilder
				.startFloatField(option("textScale"), config.textScale)
				.setDefaultValue(defaults.textScale)
				.setTooltip(tooltip("textScale"))
				.setSaveConsumer(v -> config.textScale = v)
				.build());

		overlay.addEntry(entryBuilder
				.startBooleanToggle(option("showThroughTransparentBlocks"), config.showThroughTransparentBlocks)
				.setDefaultValue(defaults.showThroughTransparentBlocks)
				.setTooltip(tooltip("showThroughTransparentBlocks"))
				.setSaveConsumer(v -> config.showThroughTransparentBlocks = v)
				.build());

		BooleanListEntry cacheHealthText = entryBuilder
				.startBooleanToggle(option("cacheHealthText"), config.cacheHealthText)
				.setDefaultValue(defaults.cacheHealthText)
				.setTooltip(tooltip("cacheHealthText"))
				.setSaveConsumer(v -> config.cacheHealthText = v)
				.build();
		overlay.addEntry(cacheHealthText);

		overlay.addEntry(entryBuilder
				.startIntField(option("cacheDurationMs"), config.cacheDurationMs)
				.setDefaultValue(defaults.cacheDurationMs)
				.setMin(0)
				.setMax(1000)
				.setTooltip(tooltip("cacheDurationMs"))
				.setSaveConsumer(v -> config.cacheDurationMs = v)
				.setDisplayRequirement(Requirement.isTrue(cacheHealthText))
				.build());

		// --- Jockeys tab ---

		ConfigCategory jockeys = builder.getOrCreateCategory(category("jockeys"));

		BooleanListEntry jockeyCombinedIndicator = entryBuilder
				.startBooleanToggle(option("jockeyCombinedIndicator"), config.jockeyCombinedIndicator)
				.setDefaultValue(defaults.jockeyCombinedIndicator)
				.setTooltip(tooltip("jockeyCombinedIndicator"))
				.setSaveConsumer(v -> config.jockeyCombinedIndicator = v)
				.build();
		jockeys.addEntry(jockeyCombinedIndicator);

		jockeys.addEntry(entryBuilder
				.startBooleanToggle(option("jockeyShowPositionPrefix"), config.jockeyShowPositionPrefix)
				.setDefaultValue(defaults.jockeyShowPositionPrefix)
				.setTooltip(tooltip("jockeyShowPositionPrefix"))
				.setSaveConsumer(v -> config.jockeyShowPositionPrefix = v)
				.setDisplayRequirement(Requirement.isTrue(jockeyCombinedIndicator))
				.build());

		// --- Filtering tab ---

		ConfigCategory filtering = builder.getOrCreateCategory(category("filtering"));

		filtering.addEntry(entryBuilder
				.startBooleanToggle(option("showHostile"), config.showHostile)
				.setDefaultValue(defaults.showHostile)
				.setTooltip(tooltip("showHostile"))
				.setSaveConsumer(v -> config.showHostile = v)
				.build());

		filtering.addEntry(entryBuilder
				.startBooleanToggle(option("showPassive"), config.showPassive)
				.setDefaultValue(defaults.showPassive)
				.setTooltip(tooltip("showPassive"))
				.setSaveConsumer(v -> config.showPassive = v)
				.build());

		filtering.addEntry(entryBuilder
				.startBooleanToggle(option("showPlayers"), config.showPlayers)
				.setDefaultValue(defaults.showPlayers)
				.setTooltip(tooltip("showPlayers"))
				.setSaveConsumer(v -> config.showPlayers = v)
				.build());

		filtering.addEntry(entryBuilder
				.startBooleanToggle(option("showSelf"), config.showSelf)
				.setDefaultValue(defaults.showSelf)
				.setTooltip(tooltip("showSelf"))
				.setSaveConsumer(v -> config.showSelf = v)
				.build());

		filtering.addEntry(entryBuilder
				.startEnumSelector(option("entityTypeListMode"), SmartHealthIndicatorConfig.ListMode.class, config.entityTypeListMode)
				.setDefaultValue(defaults.entityTypeListMode)
				.setTooltip(tooltip("entityTypeListMode"))
				.setSaveConsumer(v -> config.entityTypeListMode = v)
				.build());

		filtering.addEntry(entryBuilder
				.startStrList(option("entityTypeBlacklist"), config.entityTypeBlacklist)
				.setDefaultValue(defaults.entityTypeBlacklist)
				.setTooltip(tooltip("entityTypeBlacklist"))
				.setSaveConsumer(v -> config.entityTypeBlacklist = v)
				.build());

		filtering.addEntry(entryBuilder
				.startEnumSelector(option("playerNameListMode"), SmartHealthIndicatorConfig.ListMode.class, config.playerNameListMode)
				.setDefaultValue(defaults.playerNameListMode)
				.setTooltip(tooltip("playerNameListMode"))
				.setSaveConsumer(v -> config.playerNameListMode = v)
				.build());

		filtering.addEntry(entryBuilder
				.startStrList(option("playerNameBlacklist"), config.playerNameBlacklist)
				.setDefaultValue(defaults.playerNameBlacklist)
				.setTooltip(tooltip("playerNameBlacklist"))
				.setSaveConsumer(v -> config.playerNameBlacklist = v)
				.build());

		return builder.build();
	}
}
