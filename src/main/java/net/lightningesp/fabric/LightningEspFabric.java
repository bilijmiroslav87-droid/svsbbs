package net.lightningesp.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.lightningesp.gui.PulseVisualsScreen;
import org.lwjgl.glfw.GLFW;

public class LightningEspFabric implements ClientModInitializer {
    private static KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {
        // Конструктор всего из 3 параметров (без всяких категорий!):
        // 1. Название, 2. Код клавиши, 3. Имя группы
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.lightningesp.menu",
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            "Pulse Visuals"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new PulseVisualsScreen());
                }
            }
        });
    }
}
