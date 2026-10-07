package net.lightningesp.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.lightningesp.core.LightningEspState;

public class PulseVisualsScreen extends Screen {

    public PulseVisualsScreen() {
        super(Text.literal("Pulse Visuals"));
    }

    @Override
    protected void init() {
        int startX = this.width / 2 - 140;
        int startY = this.height / 2 - 100;

        // 1. Lightning ESP
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Lightning ESP: " + getStatus(LightningEspState.enabled)),
            b -> { LightningEspState.enabled = !LightningEspState.enabled; b.setMessage(Text.literal("Lightning ESP: " + getStatus(LightningEspState.enabled))); }
        ).dimensions(startX + 15, startY + 40, 120, 20).build());

        // 2. Tracers
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Tracers: " + getStatus(LightningEspState.tracers)),
            b -> { LightningEspState.tracers = !LightningEspState.tracers; b.setMessage(Text.literal("Tracers: " + getStatus(LightningEspState.tracers))); }
        ).dimensions(startX + 145, startY + 40, 120, 20).build());

        // 3. Ambience
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Ambience (Night): " + getStatus(LightningEspState.ambience)),
            b -> { LightningEspState.ambience = !LightningEspState.ambience; b.setMessage(Text.literal("Ambience (Night): " + getStatus(LightningEspState.ambience))); }
        ).dimensions(startX + 15, startY + 70, 120, 20).build());

        // 4. China Hat
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("China Hat: " + getStatus(LightningEspState.chinaHat)),
            b -> { LightningEspState.chinaHat = !LightningEspState.chinaHat; b.setMessage(Text.literal("China Hat: " + getStatus(LightningEspState.chinaHat))); }
        ).dimensions(startX + 145, startY + 70, 120, 20).build());

        // 5. Custom Fog
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Custom Fog: " + getStatus(LightningEspState.customFog)),
            b -> { LightningEspState.customFog = !LightningEspState.customFog; b.setMessage(Text.literal("Custom Fog: " + getStatus(LightningEspState.customFog))); }
        ).dimensions(startX + 15, startY + 100, 120, 20).build());

        // 6. Rainbow Colors
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Rainbow Theme: " + getStatus(LightningEspState.rainbow)),
            b -> { LightningEspState.rainbow = !LightningEspState.rainbow; b.setMessage(Text.literal("Rainbow Theme: " + getStatus(LightningEspState.rainbow))); }
        ).dimensions(startX + 145, startY + 100, 120, 20).build());
    }

    private String getStatus(boolean active) {
        return active ? "§a[ON]" : "§c[OFF]";
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Исправленный вызов метода для Fabric 1.20.1
        this.renderBackground(context, mouseX, mouseY, delta);
        
        int startX = this.width / 2 - 150;
        int startY = this.height / 2 - 110;
        
        // Корпус GUI
        context.fill(startX, startY, startX + 300, startY + 210, 0xF00D0D12); 
        context.fill(startX, startY, startX + 300, startY + 2, 0xFF9D00FF);

        // Шапка
        context.drawText(this.textRenderer, "§b§lPULSE §f§lVISUALS §7| CLIENT 1.20.1", startX + 15, startY + 15, 0xFFFFFF, true);
        context.drawText(
            
