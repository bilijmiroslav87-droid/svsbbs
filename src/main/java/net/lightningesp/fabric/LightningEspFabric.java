package net.lightningesp.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.lightningesp.gui.PulseVisualsScreen;
import org.lwjgl.glfw.GLFW;

public class LightningEspFabric implements ClientModInitializer {
    private static KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {
        // Создаем клавишу без обращения к константам категорий!
        openMenuKey = new KeyBinding(
            "key.lightningesp.menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            "category.lightningesp"
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Регистрируем бинд динамически при первом тике
            if (openMenuKey != null) {
                while (openMenuKey.wasPressed()) {
                    if (client.player != null) {
                        client.setScreen(new PulseVisualsScreen());
                    }
                }
            }
        });
    }
}
