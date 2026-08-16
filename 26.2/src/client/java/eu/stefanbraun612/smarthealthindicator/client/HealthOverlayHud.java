package eu.stefanbraun612.smarthealthindicator.client;

import eu.stefanbraun612.smarthealthindicator.client.config.SmartHealthIndicatorConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

	// Same sprites/sizing vanilla's own HUD health bar uses (net.minecraft.client.gui.Hud).
	private static final Identifier HEART_CONTAINER_SPRITE = Identifier.withDefaultNamespace("hud/heart/container");
	private static final Identifier HEART_FULL_SPRITE = Identifier.withDefaultNamespace("hud/heart/full");
	private static final Identifier HEART_HALF_SPRITE = Identifier.withDefaultNamespace("hud/heart/half");
	private static final int HEART_SIZE = 9;
	private static final int HEART_STEP = 8;
	private static final int CHECKMARK_COLOR = 0xFF55FF55;
	// Tiny pixel-art checkmark (dx, dy pairs) drawn over the last heart's bottom-right
	// corner - avoids depending on font glyph support for a check-mark character.
	private static final int[][] CHECKMARK_PIXELS = {{0, 2}, {1, 3}, {2, 4}, {3, 2}, {4, 0}};

	private static final long JOCKEY_CYCLE_MS = 2000;
	// Bottom of the stack (the mount) first, then a distinct color per rider going up.
	private static final int[] JOCKEY_COLORS = {
			0xFF6699FF, // blue-ish - mount
			0xFF55FF55, // green - 1st rider
			0xFFFFCC55, // orange - 2nd rider
			0xFFCC66FF, // purple - 3rd rider
			0xFFFF6699, // pink - 4th+ rider
	};

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

			if (config.jockeyCombinedIndicator && isJockeyMember(living)) {
				if (isJockeyRider(living)) {
					continue; // handled once below, when we reach the bottom of the stack
				}
				renderJockeyStack(graphics, font, client, level, player, config, mode, living, entityPos, eyePos,
						crosshairTarget, partialTick);
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

			boolean headVisible = false;
			if (config.showAboveHead) {
				Vec3 headPoint = entityPos.add(0.0, living.getBbHeight() + HEAD_ANCHOR_OFFSET, 0.0);
				headVisible = hasLineOfSight(level, player, eyePos, headPoint, allowTransparent);
				if (headVisible) {
					drawAtWorldPoint(graphics, font, client, config, health, maxHealth, text, nameText, null, headPoint, scale, TEXT_COLOR);
				}
			}
			if (config.showAtFeet) {
				// With both anchors on and priority enabled, the head anchor already "won"
				// if it was visible - skip feet entirely so the text isn't doubled. If the
				// head anchor was blocked (or above-head is off), feet renders as normal.
				boolean skipFeet = config.prioritizeHeadOverFeet && config.showAboveHead && headVisible;
				if (!skipFeet) {
					Vec3 feetPoint = entityPos.add(0.0, FEET_ANCHOR_OFFSET, 0.0);
					if (hasLineOfSight(level, player, eyePos, feetPoint, allowTransparent)) {
						drawAtWorldPoint(graphics, font, client, config, health, maxHealth, text, nameText, null, feetPoint, scale, TEXT_COLOR);
					}
				}
			}
		}
	}

	/**
	 * Renders exactly one indicator for an entire jockey stack (a living entity riding
	 * another living entity, possibly several deep), cycling through each member's
	 * health/name every {@link #JOCKEY_CYCLE_MS} with a color per stack position instead
	 * of drawing one line per member.
	 */
	private void renderJockeyStack(GuiGraphicsExtractor graphics, Font font, Minecraft client, ClientLevel level,
			LocalPlayer player, SmartHealthIndicatorConfig config, SmartHealthIndicatorConfig.PassiveDisplayMode mode,
			LivingEntity root, Vec3 rootPos, Vec3 eyePos, LivingEntity crosshairTarget, float partialTick) {
		List<LivingEntity> stack = new ArrayList<>();
		collectJockeyStack(root, stack);
		stack.removeIf(member -> !isCategoryAllowed(member, player, config));
		if (stack.isEmpty()) {
			return;
		}

		boolean anyDamaged = stack.stream().anyMatch(member -> member.getHealth() < member.getMaxHealth());
		if (mode == SmartHealthIndicatorConfig.PassiveDisplayMode.DAMAGED_ONLY && !anyDamaged) {
			return;
		}

		int activeIndex = (int) ((System.currentTimeMillis() / JOCKEY_CYCLE_MS) % stack.size());
		LivingEntity active = stack.get(activeIndex);
		int color = JOCKEY_COLORS[Math.min(activeIndex, JOCKEY_COLORS.length - 1)];
		String prefix = config.jockeyShowPositionPrefix ? jockeyPositionPrefix(activeIndex, stack.size()) : null;

		float health = active.getHealth();
		float maxHealth = active.getMaxHealth();
		String text = getHealthText(active, health, maxHealth, config);
		String nameText = config.showEntityName ? active.getName().getString() : null;
		boolean allowTransparent = config.showThroughTransparentBlocks && active == crosshairTarget;
		float scale = Math.max(0.05f, config.textScale);

		LivingEntity top = stack.get(stack.size() - 1);
		Vec3 topPos = top.getPosition(partialTick);

		boolean headVisible = false;
		if (config.showAboveHead) {
			Vec3 headPoint = topPos.add(0.0, top.getBbHeight() + HEAD_ANCHOR_OFFSET, 0.0);
			headVisible = hasLineOfSight(level, player, eyePos, headPoint, allowTransparent);
			if (headVisible) {
				drawAtWorldPoint(graphics, font, client, config, health, maxHealth, text, nameText, prefix, headPoint, scale, color);
			}
		}
		if (config.showAtFeet) {
			boolean skipFeet = config.prioritizeHeadOverFeet && config.showAboveHead && headVisible;
			if (!skipFeet) {
				Vec3 feetPoint = rootPos.add(0.0, FEET_ANCHOR_OFFSET, 0.0);
				if (hasLineOfSight(level, player, eyePos, feetPoint, allowTransparent)) {
					drawAtWorldPoint(graphics, font, client, config, health, maxHealth, text, nameText, prefix, feetPoint, scale, color);
				}
			}
		}
	}

	/** B(ottom)/T(op) for 2 members; 3+, bottom/top stay B/T, everything between is M1, M2, ... */
	private static String jockeyPositionPrefix(int index, int stackSize) {
		if (stackSize <= 1) {
			return null;
		}
		if (index == 0) {
			return "B";
		}
		if (index == stackSize - 1) {
			return "T";
		}
		return "M" + index;
	}

	private static boolean hasLivingPassenger(Entity entity) {
		for (Entity passenger : entity.getPassengers()) {
			if (passenger instanceof LivingEntity) {
				return true;
			}
		}
		return false;
	}

	// A boat/minecart passenger's vehicle is never a LivingEntity, so this naturally
	// excludes them from jockey handling without any explicit Boat/AbstractMinecart check.
	private static boolean isJockeyRider(LivingEntity living) {
		return living.getVehicle() instanceof LivingEntity;
	}

	private static boolean isJockeyMember(LivingEntity living) {
		return isJockeyRider(living) || hasLivingPassenger(living);
	}

	/** Depth-first, bottom (root) to top, flattening any branching passenger chains. */
	private static void collectJockeyStack(Entity entity, List<LivingEntity> out) {
		if (entity instanceof LivingEntity living) {
			out.add(living);
		}
		for (Entity passenger : entity.getPassengers()) {
			collectJockeyStack(passenger, out);
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
		return switch (config.peekKeyBehavior) {
			case SHOW_ALL -> SmartHealthIndicatorConfig.PassiveDisplayMode.ALL;
			case DAMAGED_ONLY -> SmartHealthIndicatorConfig.PassiveDisplayMode.DAMAGED_ONLY;
			// USE_CONFIGURED_MODE: respect the configured filter while peeking, but OFF
			// alone would make the peek key a no-op, so fall back to ALL only in that case.
			case USE_CONFIGURED_MODE -> config.passiveDisplayMode == SmartHealthIndicatorConfig.PassiveDisplayMode.OFF
					? SmartHealthIndicatorConfig.PassiveDisplayMode.ALL
					: config.passiveDisplayMode;
		};
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
			return matchesList(name, config.playerNameBlacklist, config.playerNameListMode);
		}

		boolean friendly = living.getType().getCategory().isFriendly();
		if (friendly && !config.showPassive) {
			return false;
		}
		if (!friendly && !config.showHostile) {
			return false;
		}
		String typeId = EntityType.getKey(living.getType()).toString();
		return matchesList(typeId, config.entityTypeBlacklist, config.entityTypeListMode);
	}

	/** BLACKLIST: allowed unless listed. WHITELIST: allowed only if listed (empty = none). */
	private boolean matchesList(String value, List<String> list, SmartHealthIndicatorConfig.ListMode mode) {
		boolean listed = list.stream().anyMatch(entry -> entry.equalsIgnoreCase(value));
		return switch (mode) {
			case BLACKLIST -> !listed;
			case WHITELIST -> listed;
		};
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

	private static final int PREFIX_GAP = 2;

	private void drawAtWorldPoint(GuiGraphicsExtractor graphics, Font font, Minecraft client,
			SmartHealthIndicatorConfig config, float health, float maxHealth, String text, String nameText,
			String prefix, Vec3 worldPoint, float scale, int color) {
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

		int totalHearts = config.useHeartsDisplay ? heartCount(maxHealth) : 0;
		float contentWidth = config.useHeartsDisplay ? heartsRowWidth(totalHearts) : font.width(text);
		float lineHeight = config.useHeartsDisplay ? HEART_SIZE : font.lineHeight;
		float prefixWidth = prefix != null ? font.width(prefix) + PREFIX_GAP : 0;
		float lineWidth = contentWidth + prefixWidth;
		float widestLine = Math.max(lineWidth, nameText != null ? font.width(nameText) : 0);
		float halfWidth = widestLine * scale / 2.0f;
		float extraLineHeight = nameText != null ? font.lineHeight * scale : 0;
		if (screenX + halfWidth < 0 || screenX - halfWidth > guiWidth
				|| screenY + lineHeight * scale < 0 || screenY - extraLineHeight > guiHeight) {
			return;
		}

		// Scale in GUI pose space rather than pre-dividing the coordinates, so text stays
		// crisp at any scale instead of being drawn at fractional pixel positions.
		graphics.pose().pushMatrix();
		graphics.pose().translate(screenX, screenY - lineHeight * scale / 2.0f);
		graphics.pose().scale(scale, scale);
		if (nameText != null) {
			graphics.centeredText(font, nameText, 0, -font.lineHeight, TEXT_COLOR);
		}

		float leftEdge = -lineWidth / 2.0f;
		if (prefix != null) {
			int prefixCenter = Math.round(leftEdge + (prefixWidth - PREFIX_GAP) / 2.0f);
			graphics.centeredText(font, prefix, prefixCenter, 0, color);
		}
		int contentCenter = Math.round(leftEdge + prefixWidth + contentWidth / 2.0f);
		if (config.useHeartsDisplay) {
			drawHeartsRow(graphics, health, maxHealth, config.showFullHealthCheckmark, contentCenter, 0, color);
		} else {
			graphics.centeredText(font, text, contentCenter, 0, color);
		}
		graphics.pose().popMatrix();
	}

	private static int heartCount(float maxHealth) {
		int maxPoints = Math.max(1, Math.round(maxHealth));
		return (maxPoints + 1) / 2;
	}

	private static int heartsRowWidth(int totalHearts) {
		return totalHearts <= 0 ? 0 : (totalHearts - 1) * HEART_STEP + HEART_SIZE;
	}

	/** Draws a row of vanilla heart sprites, centered at local x=centerX, top edge at local y. */
	private void drawHeartsRow(GuiGraphicsExtractor graphics, float health, float maxHealth,
			boolean showCheckmark, int centerX, int y, int color) {
		int healthPoints = Math.max(0, Math.round(health));
		int totalHearts = heartCount(maxHealth);
		int fullHearts = Math.min(healthPoints / 2, totalHearts);
		boolean halfHeart = (healthPoints % 2) == 1 && fullHearts < totalHearts;
		int startX = centerX - heartsRowWidth(totalHearts) / 2;

		for (int i = 0; i < totalHearts; i++) {
			int x = startX + i * HEART_STEP;
			Identifier sprite;
			if (i < fullHearts) {
				sprite = HEART_FULL_SPRITE;
			} else if (i == fullHearts && halfHeart) {
				sprite = HEART_HALF_SPRITE;
			} else {
				sprite = HEART_CONTAINER_SPRITE;
			}
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, HEART_SIZE, HEART_SIZE, color);
		}

		// Hearts can't show the fractional health a mob might actually be resting on (e.g.
		// 19.7/20.0 rounds to look identical to a genuine 20.0/20.0) - the checkmark marks
		// the difference instead of pretending hearts have more precision than they do.
		if (showCheckmark && totalHearts > 0 && Math.abs(health - maxHealth) < 0.001f) {
			int lastHeartX = startX + (totalHearts - 1) * HEART_STEP;
			for (int[] pixel : CHECKMARK_PIXELS) {
				int px = lastHeartX + 4 + pixel[0];
				int py = y + 4 + pixel[1];
				graphics.fill(px, py, px + 1, py + 1, CHECKMARK_COLOR);
			}
		}
	}

	private void renderCrosshairLine(GuiGraphicsExtractor graphics, Font font, Minecraft client,
			LocalPlayer player, SmartHealthIndicatorConfig config) {
		LivingEntity living = getCrosshairTarget(client, player);
		if (living == null || !isCategoryAllowed(living, player, config)) {
			return;
		}

		String nameText = living.getName().getString();
		float health = living.getHealth();
		float maxHealth = living.getMaxHealth();
		String healthText = getHealthText(living, health, maxHealth, config);
		float scale = Math.max(0.05f, config.textScale);

		graphics.pose().pushMatrix();
		graphics.pose().translate(graphics.guiWidth() / 2.0f, graphics.guiHeight() / 2.0f + CROSSHAIR_OFFSET_Y);
		graphics.pose().scale(scale, scale);
		graphics.centeredText(font, nameText, 0, -font.lineHeight, TEXT_COLOR);
		if (config.useHeartsDisplay) {
			drawHeartsRow(graphics, health, maxHealth, config.showFullHealthCheckmark, 0, 0, TEXT_COLOR);
		} else {
			graphics.centeredText(font, healthText, 0, 0, TEXT_COLOR);
		}
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
