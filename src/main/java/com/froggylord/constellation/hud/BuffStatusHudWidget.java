package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AurigaBuffStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/tabhud/widget/EffectWidget.java
// ported from SkyblockAddons (LGPL-3.0-only): features/tablist/TabListParser.java, resources/regex.json
public final class BuffStatusHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;
    private HudPosition position;
    private boolean enabled = true;

    public BuffStatusHudWidget(HudPosition position, BooleanSupplier gate) {
        this.position = position;
        this.gate = gate;
    }

    @Override public String id() { return "auriga-buff-status"; }
    @Override public HudPosition position() { return position; }
    @Override public void setPosition(HudPosition value) { position = value; }
    @Override public boolean isEnabled() { return enabled && gate.getAsBoolean(); }
    @Override public void setEnabled(boolean value) { enabled = value; }
    @Override public boolean visibleNow() { return isEnabled() && AurigaBuffStatus.visible(); }
    @Override public String editorLabel() { return "Buff Status"; }
    @Override protected String title() { return "Buff Status"; }

    @Override
    protected List<Row> rows() {
        var state = AurigaBuffStatus.state();
        var cfg = AurigaBuffStatus.config();
        long now = System.currentTimeMillis();
        ArrayList<Row> rows = new ArrayList<>();
        if (cfg.buffStatusGodPotion && show(state.god(), cfg))
            rows.add(new Row("", "God Potion", value(state.god(), state.godExpiry(), now), color(state.god(), state.godExpiry(), now,
                cfg.buffStatusGodWarningMinutes * 60_000L, cfg)));
        if (cfg.buffStatusCookie && show(state.cookie(), cfg))
            rows.add(new Row("", "Cookie Buff", value(state.cookie(), state.cookieExpiry(), now), color(state.cookie(), state.cookieExpiry(), now,
                cfg.buffStatusCookieWarningHours * 3_600_000L, cfg)));
        if (cfg.buffStatusEffectCount && state.effectCount() >= 0)
            rows.add(new Row("", "Active Effects", Integer.toString(state.effectCount()), cfg.buffStatusActiveColor));
        if (cfg.buffStatusShowProfile)
            rows.add(new Row("", "Profile", AurigaBuffStatus.profile(), cfg.buffStatusUnknownColor));
        if (cfg.buffStatusShowSourceAge && state.footerSeenAt() > 0)
            rows.add(new Row("", "Updated", AurigaBuffStatus.format(now - state.footerSeenAt()) + " ago", cfg.buffStatusUnknownColor));
        return rows;
    }

    private static boolean show(AurigaBuffStatus.Status status, com.froggylord.constellation.config.AurigaConfig cfg) {
        if (status == AurigaBuffStatus.Status.UNKNOWN) return cfg.buffStatusShowUnknown;
        return status != AurigaBuffStatus.Status.EXPIRED || cfg.buffStatusShowExpired;
    }
    private static String value(AurigaBuffStatus.Status status, long expiry, long now) {
        if (status == AurigaBuffStatus.Status.ACTIVE && expiry > now) return AurigaBuffStatus.format(expiry - now);
        return switch (status) {
            case UNKNOWN -> "Unknown";
            case ACTIVE -> "Active";
            case INACTIVE -> "Inactive";
            case EXPIRED -> "Expired";
        };
    }
    private static int color(AurigaBuffStatus.Status status, long expiry, long now, long warning,
                             com.froggylord.constellation.config.AurigaConfig cfg) {
        if (status == AurigaBuffStatus.Status.UNKNOWN) return cfg.buffStatusUnknownColor;
        if (status == AurigaBuffStatus.Status.INACTIVE || status == AurigaBuffStatus.Status.EXPIRED) return cfg.buffStatusExpiredColor;
        return expiry > 0 && expiry - now <= warning ? cfg.buffStatusWarningColor : cfg.buffStatusActiveColor;
    }

    @Override
    protected List<Row> previewRows() {
        return List.of(
            new Row("", "God Potion", "16h 42m", 0xFF55FF55),
            new Row("", "Cookie Buff", "3d 8h", 0xFF55FF55),
            new Row("", "Active Effects", "17", 0xFF55FF55)
        );
    }
}
