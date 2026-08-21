package eu.stefanbraun612.smarthealthindicator.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import eu.stefanbraun612.smarthealthindicator.client.config.SmartHealthIndicatorConfigScreen;

public class SmartHealthIndicatorModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SmartHealthIndicatorConfigScreen::build;
	}
}
