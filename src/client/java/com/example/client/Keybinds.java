package com.example.client;

import com.example.TemplateMod;
import com.example.config.ModConfig;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class Keybinds {
    public static KeyMapping toggleKeybind;

    public static void register() {
        ModConfig config = ModConfig.getInstance();
        int keyCode = config.keybind != 0 ? config.keybind : GLFW.GLFW_KEY_C;

        toggleKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.crystal-auto.toggle",
                keyCode,
                "key.categories.crystal-auto"
        ));
    }

    public static void onKeyPress() {
        if (toggleKeybind != null && toggleKeybind.consumeClick()) {
            ModConfig config = ModConfig.getInstance();
            config.enabled = !config.enabled;
            config.save();

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String status = config.enabled ? "§aAktiviert" : "§cDeaktiviert";
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§6[Crystal Auto] §fModus: " + status),
                        true
                );
            }
        }
    }

    public static void updateKeybind(int keyCode) {
        if (toggleKeybind != null) {
            KeyBindingHelper.unregisterKeyBinding(toggleKeybind);
        }
        ModConfig config = ModConfig.getInstance();
        config.keybind = keyCode;
        config.save();
        register();
    }

    public static String getKeybindName() {
        if (toggleKeybind == null) return "Nicht gesetzt";
        return toggleKeybind.getTranslatedKeyMessage().getString();
    }
}