package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class PartyGuardScreen extends Screen {
    private final Screen parent;
    private final List<EditBox> fields = new ArrayList<>();
    private EditBox floor, cata, secrets, average, mp, pb, message;
    private OrionConfig cfg;
    private String validationError = "";

    public PartyGuardScreen(Screen parent) {
        super(Component.literal("Party Guard"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        cfg = ConstellationClient.cfg().orion;
        int x = width / 2 - 120;
        floor = field(x, 50, 52, "AUTO", cfg.partyGuardFloor);
        cata = field(x + 60, 50, 52, "Cata", Integer.toString(cfg.partyGuardMinCata));
        secrets = field(x + 120, 50, 110, "Secrets", Integer.toString(cfg.partyGuardMinSecrets));
        average = field(x, 84, 70, "Average", Double.toString(cfg.partyGuardMinAverageSecrets));
        mp = field(x + 78, 84, 70, "MP", Integer.toString(cfg.partyGuardMinMagicalPower));
        pb = field(x + 156, 84, 74, "PB sec", Integer.toString(cfg.partyGuardMaxPbSeconds));
        message = field(x, 144, 230, "Kick message", cfg.partyGuardKickMessage);
        message.setMaxLength(120);
    }

    private EditBox field(int x, int y, int w, String hint, String value) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setValue(value == null ? "" : value);
        box.setMaxLength(32);
        fields.add(box);
        addRenderableWidget(box);
        return box;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        ConstellationUi.background(g, width, height, delta);
        ConstellationUi.header(g, font, "Party Guard",
            cfg.partyGuard ? cfg.partyGuardDryRun ? "Dry run" : "Active" : "Disabled", width);
        int x = width / 2 - 120;
        ConstellationUi.panel(g, x - 10, 31, 250, Math.min(211, height - 68));
        g.text(font, "Floor", x, 38, ConstellationTheme.TEXT_MUTED, false);
        g.text(font, "Minimum Cata", x + 60, 38, ConstellationTheme.TEXT_MUTED, false);
        g.text(font, "Minimum secrets", x + 120, 38, ConstellationTheme.TEXT_MUTED, false);
        g.text(font, "Minimum average", x, 72, ConstellationTheme.TEXT_MUTED, false);
        g.text(font, "Minimum MP", x + 78, 72, ConstellationTheme.TEXT_MUTED, false);
        g.text(font, "Maximum PB", x + 156, 72, ConstellationTheme.TEXT_MUTED, false);
        toggle(g, x, 110, 72, "Enabled", cfg.partyGuard, mx, my);
        toggle(g, x + 78, 110, 72, "Dry run", cfg.partyGuardDryRun, mx, my);
        toggle(g, x + 156, 110, 74, "No PB", cfg.partyGuardKickMissingPb, mx, my);
        g.text(font, "Message variables: {player} {reasons} {cata} {secrets} {average} {mp} {pb} {floor}",
            x, 132, ConstellationTheme.TEXT_MUTED, false);
        toggle(g, x, 170, 106, "Send reason", cfg.partyGuardSendReason, mx, my);
        toggle(g, x + 112, 170, 118, "Private reason", cfg.partyGuardPrivateReason, mx, my);
        g.text(font, "Whitelist overrides every rule. Blacklist bypasses API requirements.", x, 198, ConstellationTheme.TEXT, false);
        g.text(font, "Manage lists: /partyguard whitelist|blacklist|remove <player>", x, 212, ConstellationTheme.TEXT_MUTED, false);
        g.text(font, "API failures always fail open. Auto-kick requires confirmed party leadership.", x, 226, 0xFFFFFF55, false);
        if (!validationError.isEmpty())
            g.text(font, ConstellationUi.fit(font, validationError, 240), x, height - 42, 0xFFFF7777, false);
        button(g, width / 2 - 50, height - 28, 100, 18, "Save and close", mx, my);
        for (EditBox field : fields)
            ConstellationTheme.search(g, field.getX() - 2, field.getY() - 2,
                field.getWidth() + 4, field.getHeight() + 4, field.isFocused());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int mx = (int) event.x(), my = (int) event.y(), x = width / 2 - 120;
        if (inside(mx, my, x, 110, 72, 18)) { cfg.partyGuard = !cfg.partyGuard; return true; }
        if (inside(mx, my, x + 78, 110, 72, 18)) { cfg.partyGuardDryRun = !cfg.partyGuardDryRun; return true; }
        if (inside(mx, my, x + 156, 110, 74, 18)) { cfg.partyGuardKickMissingPb = !cfg.partyGuardKickMissingPb; return true; }
        if (inside(mx, my, x, 170, 106, 18)) { cfg.partyGuardSendReason = !cfg.partyGuardSendReason; return true; }
        if (inside(mx, my, x + 112, 170, 118, 18)) { cfg.partyGuardPrivateReason = !cfg.partyGuardPrivateReason; return true; }
        if (inside(mx, my, width / 2 - 50, height - 28, 100, 18)) {
            if (save()) Minecraft.getInstance().setScreenAndShow(parent);
            return true;
        }
        return super.mouseClicked(event, dbl);
    }

    private boolean save() {
        validationError = "";
        String selectedFloor = floor.getValue().trim().toUpperCase();
        if (selectedFloor.equals("AUTO") || selectedFloor.matches("[FM][1-7]")) cfg.partyGuardFloor = selectedFloor;
        else validationError = "Floor must be AUTO, F1-F7 or M1-M7.";
        cfg.partyGuardMinCata = integer(cata, cfg.partyGuardMinCata, 0, 100, "Cata");
        cfg.partyGuardMinSecrets = integer(secrets, cfg.partyGuardMinSecrets, 0, 100_000_000, "Secrets");
        cfg.partyGuardMinAverageSecrets = decimal(average, cfg.partyGuardMinAverageSecrets, 0, 100, "Average");
        cfg.partyGuardMinMagicalPower = integer(mp, cfg.partyGuardMinMagicalPower, 0, 10_000, "MP");
        cfg.partyGuardMaxPbSeconds = integer(pb, cfg.partyGuardMaxPbSeconds, 0, 3600, "PB");
        cfg.partyGuardKickMessage = message.getValue();
        ConstellationClient.saveConfig();
        return validationError.isEmpty();
    }

    private int integer(EditBox box, int fallback, int min, int max, String name) {
        try { return Math.clamp(Integer.parseInt(box.getValue().trim()), min, max); }
        catch (Exception ignored) { invalid(name); return fallback; }
    }

    private double decimal(EditBox box, double fallback, double min, double max, String name) {
        try { return Math.clamp(Double.parseDouble(box.getValue().trim()), min, max); }
        catch (Exception ignored) { invalid(name); return fallback; }
    }

    private void invalid(String name) {
        if (validationError.isEmpty()) validationError = name + " must be a number.";
    }

    private void toggle(GuiGraphicsExtractor g, int x, int y, int w, String text, boolean value, int mx, int my) {
        ConstellationUi.button(g, font, x, y, w, 18, text + ": " + (value ? "on" : "off"),
            inside(mx, my, x, y, w, 18), value);
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, int mx, int my) {
        ConstellationUi.button(g, font, x, y, w, h, text, inside(mx, my, x, y, w, h), false);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    @Override public void onClose() { save(); Minecraft.getInstance().setScreenAndShow(parent); }
}
