package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.PhoenixSpeedPresets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class SpeedPresetHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public SpeedPresetHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"phoenix-speed-presets";}@Override public HudPosition position(){return position;}@Override public void setPosition(HudPosition position){this.position=position;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean enabled){this.enabled=enabled;}
    @Override public boolean visibleNow(){if(!isEnabled()||PhoenixSpeedPresets.lastSpeed()<0)return false;var cfg=PhoenixSpeedPresets.config();return!cfg.speedPresetsHudRecentOnly||PhoenixSpeedPresets.recentlyUsed();}
    @Override public String editorLabel(){return"Speed Preset";}@Override protected String title(){return"Speed Preset";}
    @Override protected List<Row> rows(){var cfg=PhoenixSpeedPresets.config();List<Row> rows=new ArrayList<>();if(cfg.speedPresetsHudName)rows.add(new Row("","Preset",PhoenixSpeedPresets.lastPreset(),0xFF55FFFF));if(cfg.speedPresetsHudSpeed)rows.add(new Row("","Speed",Integer.toString(PhoenixSpeedPresets.lastSpeed()),0xFFFFFF55));if(cfg.speedPresetsHudProfile)rows.add(new Row("","Profile",PhoenixSpeedPresets.profile(),0xFFFFFFFF));return rows;}
}
