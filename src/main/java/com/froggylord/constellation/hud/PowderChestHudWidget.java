package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaMiningHighlights;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/mining/powdertracker/PowderChestTimer.kt
public final class PowderChestHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public PowderChestHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"aquila-powder-chests";}
    @Override public HudPosition position(){return pos;}
    @Override public void setPosition(HudPosition value){pos=value;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean value){enabled=value;}
    @Override public boolean visibleNow(){return isEnabled()&&AquilaMiningHighlights.powderVisible();}
    @Override public String editorLabel(){return"Powder Chests";}
    @Override protected String title(){return"Powder Chests";}
    @Override protected List<Row> rows(){var state=AquilaMiningHighlights.powderState();var cfg=AquilaMiningHighlights.config();if(state==null||cfg==null)return List.of();List<Row> rows=new ArrayList<>();int color=AquilaMiningHighlights.powderColor(state.oldestMillis());if(cfg.powderChestHudShowCount)rows.add(new Row("","Active",Integer.toString(state.count()),color));if(cfg.powderChestHudShowOldest)rows.add(new Row("","Oldest",AquilaMiningHighlights.formatRemaining(state.oldestMillis()),color));if(cfg.powderChestHudShowNearest)rows.add(new Row("","Nearest",Math.round(state.nearestDistance())+"m  "+AquilaMiningHighlights.formatRemaining(state.nearestMillis()),AquilaMiningHighlights.powderColor(state.nearestMillis())));return rows;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Active","3",0xFFFFFF55),new Row("","Oldest","18.4s",0xFFFF5555),new Row("","Nearest","7m  42.1s",0xFFFFAA00));}
}
