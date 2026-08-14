package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.LyraCurrencyTracker;

import java.util.List;
import java.util.function.BooleanSupplier;

// HUD lifecycle ported from Athen (BSD-3-Clause): hud/HUDElement.kt
public final class LyraCurrencyHudWidget extends ThemedHudWidget {
    private final HudPosition initial;
    private final BooleanSupplier enabled;
    public LyraCurrencyHudWidget(HudPosition initial, BooleanSupplier enabled){this.initial=initial;this.enabled=enabled;}
    @Override public String id(){return "lyra-currency";}
    @Override public HudPosition position(){return initial;}
    @Override public void setPosition(HudPosition position){}
    @Override public boolean isEnabled(){return enabled.getAsBoolean();}
    @Override public void setEnabled(boolean enabled){}
    @Override public boolean visibleNow(){return isEnabled()&&!LyraCurrencyTracker.hudLines().isEmpty();}
    @Override protected String title(){return "Currency";}
    @Override protected List<Row> rows(){return LyraCurrencyTracker.hudLines().stream().map(line->new Row("","",line)).toList();}
    @Override protected List<Row> previewRows(){return List.of(new Row("","","Purse: §612.4M"),new Row("","","Session: §a+240.1k"),new Row("","","Rate: §a1.2M/h"));}
    @Override public String editorLabel(){return "Currency HUD";}
}
