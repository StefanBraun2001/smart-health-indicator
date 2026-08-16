package eu.stefanbraun612.smarthealthindicator.client;

import com.mojang.blaze3d.platform.InputConstants;
import eu.stefanbraun612.smarthealthindicator.client.config.SmartHealthIndicatorConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class SmartHealthIndicatorClient implements ClientModInitializer {
	public static final String MOD_ID = "smarthealthindicator";

	private static KeyMapping peekKey;
	private static KeyMapping displayToggleKey;
	private static boolean displayEnabled = true;

	@Override
	public void onInitializeClient() {
		AutoConfig.register(SmartHealthIndicatorConfig.class, GsonConfigSerializer::new);

		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(MOD_ID, "main"));

		peekKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.smarthealthindicator.peek",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_H,
				category
		));

		displayToggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.smarthealthindicator.toggle_display",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_G,
				category
		));

		HealthOverlayHud overlay = new HealthOverlayHud();
		HudElementRegistry.addLast(
				Identifier.fromNamespaceAndPath(MOD_ID, "health_overlay"),
				overlay);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (displayToggleKey.consumeClick()) {
				displayEnabled = !displayEnabled;
			}
		});

		// Cached health text must never survive a disconnect/rejoin or a switch to a
		// different server - otherwise it's exactly the "stale/misleading on multiplayer"
		// failure this mod was built to avoid.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> overlay.clearCache());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> overlay.clearCache());
	}

	/** True while the hold-to-peek key is held, forcing the effective display mode to ALL. */
	public static boolean isPeeking() {
		return peekKey != null && peekKey.isDown();
	}

	/** Master on/off switch for the whole mod's output, flipped by the toggle-display key. */
	public static boolean isDisplayEnabled() {
		return displayEnabled;
	}

	public static SmartHealthIndicatorConfig config() {
		return AutoConfig.getConfigHolder(SmartHealthIndicatorConfig.class).getConfig();
	}
}
