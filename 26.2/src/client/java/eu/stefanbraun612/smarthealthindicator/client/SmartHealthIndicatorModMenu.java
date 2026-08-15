package eu.stefanbraun612.smarthealthindicator.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import eu.stefanbraun612.smarthealthindicator.client.config.SmartHealthIndicatorConfig;
import me.shedaniel.autoconfig.AutoConfigClient;

public class SmartHealthIndicatorModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> AutoConfigClient.getConfigScreen(SmartHealthIndicatorConfig.class, parent).get();
	}
}
