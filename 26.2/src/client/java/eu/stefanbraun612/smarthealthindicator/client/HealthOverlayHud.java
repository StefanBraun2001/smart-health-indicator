package eu.stefanbraun612.smarthealthindicator.client;

import eu.stefanbraun612.smarthealthindicator.client.config.SmartHealthIndicatorConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Draws everything in the 2D HUD pass: nearby entities' health is projected from world
 * space to screen space with {@code GameRenderer.projectPointToScreen}, so no part of
 * 26.2's deferred entity-text submission pipeline is involved.
 */
public class HealthOverlayHud implements HudElement {

	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int CROSSHAIR_OFFSET_Y = 10;
	// Extra height above the entity's bounding box for the "above head" anchor, and how
	// far below the entity's origin the "at feet" anchor sits.
	private static final double HEAD_ANCHOR_OFFSET = 0.6;
	// Positive offset raises the anchor above the entity's actual feet position (which
	// sits exactly on the ground surface) - a negative/subtracted offset would put the
	// point inside the solid ground block below, which the occlusion raycast then sees
	// as blocked and never draws.
	private static final double FEET_ANCHOR_OFFSET = 0.1;
	// Bounds how many transparent blocks (glass panes stacked in a row, etc.) a single
	// line-of-sight check will hop through before giving up and treating it as blocked.
	private static final int MAX_TRANSPARENT_HOPS = 8;
	// Safety valve against unbounded growth on long sessions (mob farms etc.) - the cache
	// is a pure perf optimisation, never a correctness dependency, so simply dropping it
	// once it gets large is fine; it just refills live on the next few frames.
	private static final int MAX_CACHE_ENTRIES = 4096;

	// Formatted "x / y" health text only - never the entity-presence/range check, which
	// always runs live every frame regardless of caching (see renderWorldOverlay). Keyed
	// by entity ID, expired by wall-clock time (not ticks) so a stalled server tick can't
	// leave stale text stuck on screen. Cleared entirely on disconnect/rejoin.
	private final Map<Integer, CachedText> healthTextCache = new HashMap<>();

	private record CachedText(String text, long computedAtNanos) {}

	public void clearCache() {
		healthTextCache.clear();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null) {
			return;
		}

		SmartHealthIndicatorConfig config = SmartHealthIndicatorClient.config();
		Font font = client.font;
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);

		renderWorldOverlay(graphics, font, client, player, level, config, partialTick);

		if (config.showCrosshairLine) {
			renderCrosshairLine(graphics, font, client, player, config);
		}
	}

	private void renderWorldOverlay(GuiGraphicsExtractor graphics, Font font, Minecraft client,
			LocalPlayer player, ClientLevel level, SmartHealthIndicatorConfig config, float partialTick) {
		SmartHealthIndicatorConfig.PassiveDisplayMode mode = effectiveMode(config);
		if (mode == SmartHealthIndicatorConfig.PassiveDisplayMode.OFF) {
			return;
		}
		if (!config.showAboveHead && !config.showAtFeet) {
			return;
		}

		Vec3 playerPos = player.getPosition(partialTick);
		Vec3 eyePos = player.getEyePosition(partialTick);
		double horizontalLimitSq = (double) config.horizontalRadius * config.horizontalRadius;
		// Seeing through transparent blocks (see hasLineOfSight) is scoped to only the
		// entity currently under your crosshair, matching Health Indicators' "see through
		// walls" behavior - it's a targeted peek at what you're aiming at, not a general
		// see-everyone-through-glass radar for the whole radius.
		LivingEntity crosshairTarget = getCrosshairTarget(client, player);

		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
				continue;
			}
			if (!isCategoryAllowed(living, player, config)) {
				continue;
			}

			Vec3 entityPos = living.getPosition(partialTick);
			double dx = entityPos.x - playerPos.x;
			double dy = entityPos.y - playerPos.y;
			double dz = entityPos.z - playerPos.z;
			if (dx * dx + dz * dz > horizontalLimitSq || Math.abs(dy) > config.verticalRadius) {
				continue;
			}

			float health = living.getHealth();
			float maxHealth = living.getMaxHealth();
			if (mode == SmartHealthIndicatorConfig.PassiveDisplayMode.DAMAGED_ONLY && health >= maxHealth) {
				continue;
			}

			String text = getHealthText(living, health, maxHealth, config);
			String nameText = config.showEntityName ? living.getName().getString() : null;
			boolean allowTransparent = config.showThroughTransparentBlocks && living == crosshairTarget;
			float scale = Math.max(0.05f, config.textScale);

			if (config.showAboveHead) {
				Vec3 headPoint = entityPos.add(0.0, living.getBbHeight() + HEAD_ANCHOR_OFFSET, 0.0);
				if (hasLineOfSight(level, player, eyePos, headPoint, allowTransparent)) {
					drawAtWorldPoint(graphics, font, client, text, nameText, headPoint, scale);
				}
			}
			if (config.showAtFeet) {
				Vec3 feetPoint = entityPos.add(0.0, FEET_ANCHOR_OFFSET, 0.0);
				if (hasLineOfSight(level, player, eyePos, feetPoint, allowTransparent)) {
					drawAtWorldPoint(graphics, font, client, text, nameText, feetPoint, scale);
				}
			}
		}
	}

	/** The living entity currently under the crosshair, within interaction reach, or null. */
	private LivingEntity getCrosshairTarget(Minecraft client, LocalPlayer player) {
		HitResult hitResult = client.hitResult;
		if (!(hitResult instanceof EntityHitResult entityHit)
				|| !(entityHit.getEntity() instanceof LivingEntity living)
				|| !living.isAlive()) {
			return null;
		}
		double reach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		if (player.getEyePosition().distanceToSqr(hitResult.getLocation()) > reach * reach) {
			return null;
		}
		return living;
	}

	private SmartHealthIndicatorConfig.PassiveDisplayMode effectiveMode(SmartHealthIndicatorConfig config) {
		if (!SmartHealthIndicatorClient.isPeeking()) {
			return config.passiveDisplayMode;
		}
		if (config.peekKeyBehavior == SmartHealthIndicatorConfig.PeekKeyBehavior.SHOW_ALL) {
			return SmartHealthIndicatorConfig.PassiveDisplayMode.ALL;
		}
		// USE_CONFIGURED_MODE: respect the configured filter while peeking, but OFF alone
		// would make the peek key a no-op, so fall back to ALL only in that case.
		return config.passiveDisplayMode == SmartHealthIndicatorConfig.PassiveDisplayMode.OFF
				? SmartHealthIndicatorConfig.PassiveDisplayMode.ALL
				: config.passiveDisplayMode;
	}

	/** Category (hostile/passive/players/self) and per-type/per-name blacklist checks. */
	private boolean isCategoryAllowed(LivingEntity living, LocalPlayer player, SmartHealthIndicatorConfig config) {
		if (living == player) {
			return config.showSelf;
		}
		if (living instanceof Player) {
			if (!config.showPlayers) {
				return false;
			}
			String name = living.getName().getString();
			for (String blocked : config.playerNameBlacklist) {
				if (blocked.equalsIgnoreCase(name)) {
					return false;
				}
			}
			return true;
		}

		boolean friendly = living.getType().getCategory().isFriendly();
		if (friendly && !config.showPassive) {
			return false;
		}
		if (!friendly && !config.showHostile) {
			return false;
		}
		String typeId = EntityType.getKey(living.getType()).toString();
		for (String blocked : config.entityTypeBlacklist) {
			if (blocked.equalsIgnoreCase(typeId)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Walks the sightline from the player's eyes to a world point, hopping past blocks
	 * that don't fully occlude (glass, leaves, ...) when {@code allowThroughTransparent}
	 * is set, but always stopping at a genuinely opaque block regardless of that flag.
	 */
	private boolean hasLineOfSight(ClientLevel level, LocalPlayer player, Vec3 from, Vec3 to,
			boolean allowThroughTransparent) {
		Vec3 direction = to.subtract(from);
		if (direction.lengthSqr() < 1.0e-4) {
			return true;
		}
		Vec3 nudge = direction.normalize().scale(0.05);

		Vec3 rayFrom = from;
		for (int hop = 0; hop < MAX_TRANSPARENT_HOPS; hop++) {
			BlockHitResult hit = level.clip(new ClipContext(
					rayFrom, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
			if (hit.getType() == HitResult.Type.MISS) {
				return true;
			}
			if (!allowThroughTransparent || level.getBlockState(hit.getBlockPos()).canOcclude()) {
				return false;
			}
			rayFrom = hit.getLocation().add(nudge);
		}
		return false;
	}

	private void drawAtWorldPoint(GuiGraphicsExtractor graphics, Font font, Minecraft client,
			String text, String nameText, Vec3 worldPoint, float scale) {
		// Normalised device coordinates: x/y in [-1, 1], z > 1 means the point is behind
		// the camera (same test vanilla's waypoint tracker uses).
		Vec3 ndc = client.gameRenderer.projectPointToScreen(worldPoint);
		if (ndc.z > 1.0) {
			return;
		}

		int guiWidth = graphics.guiWidth();
		int guiHeight = graphics.guiHeight();
		float screenX = (float) ((ndc.x * 0.5 + 0.5) * guiWidth);
		float screenY = (float) ((1.0 - (ndc.y * 0.5 + 0.5)) * guiHeight);

		float widestLine = Math.max(font.width(text), nameText != null ? font.width(nameText) : 0);
		float halfWidth = widestLine * scale / 2.0f;
		float extraLineHeight = nameText != null ? font.lineHeight * scale : 0;
		if (screenX + halfWidth < 0 || screenX - halfWidth > guiWidth
				|| screenY + font.lineHeight * scale < 0 || screenY - extraLineHeight > guiHeight) {
			return;
		}

		// Scale in GUI pose space rather than pre-dividing the coordinates, so text stays
		// crisp at any scale instead of being drawn at fractional pixel positions.
		graphics.pose().pushMatrix();
		graphics.pose().translate(screenX, screenY - font.lineHeight * scale / 2.0f);
		graphics.pose().scale(scale, scale);
		if (nameText != null) {
			graphics.centeredText(font, nameText, 0, -font.lineHeight, TEXT_COLOR);
		}
		graphics.centeredText(font, text, 0, 0, TEXT_COLOR);
		graphics.pose().popMatrix();
	}

	private void renderCrosshairLine(GuiGraphicsExtractor graphics, Font font, Minecraft client,
			LocalPlayer player, SmartHealthIndicatorConfig config) {
		LivingEntity living = getCrosshairTarget(client, player);
		if (living == null || !isCategoryAllowed(living, player, config)) {
			return;
		}

		String nameText = living.getName().getString();
		String healthText = getHealthText(living, living.getHealth(), living.getMaxHealth(), config);
		float scale = Math.max(0.05f, config.textScale);

		graphics.pose().pushMatrix();
		graphics.pose().translate(graphics.guiWidth() / 2.0f, graphics.guiHeight() / 2.0f + CROSSHAIR_OFFSET_Y);
		graphics.pose().scale(scale, scale);
		graphics.centeredText(font, nameText, 0, -font.lineHeight, TEXT_COLOR);
		graphics.centeredText(font, healthText, 0, 0, TEXT_COLOR);
		graphics.pose().popMatrix();
	}

	private String getHealthText(Entity entity, float health, float maxHealth, SmartHealthIndicatorConfig config) {
		if (!config.cacheHealthText || config.cacheDurationMs <= 0) {
			return formatHealth(health, maxHealth, config.healthDecimalPlaces);
		}

		long now = System.nanoTime();
		CachedText cached = healthTextCache.get(entity.getId());
		if (cached != null && (now - cached.computedAtNanos()) < config.cacheDurationMs * 1_000_000L) {
			return cached.text();
		}

		String text = formatHealth(health, maxHealth, config.healthDecimalPlaces);
		if (healthTextCache.size() >= MAX_CACHE_ENTRIES) {
			healthTextCache.clear();
		}
		healthTextCache.put(entity.getId(), new CachedText(text, now));
		return text;
	}

	private static String formatHealth(float health, float maxHealth, int decimalPlaces) {
		String format = "%." + decimalPlaces + "f / %." + decimalPlaces + "f";
		return String.format(Locale.ROOT, format, health, maxHealth);
	}
}
