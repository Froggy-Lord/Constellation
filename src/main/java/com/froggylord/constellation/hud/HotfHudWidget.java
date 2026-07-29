package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.ArtemisHotf;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public final class HotfHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public HotfHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"artemis-hotf";}@Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition position){this.position=position;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean enabled){this.enabled=enabled;}@Override public boolean visibleNow(){return isEnabled()&&ArtemisHotf.visible();}
    @Override public String editorLabel(){return"Heart of the Forest";}@Override protected String title(){return"Heart of the Forest";}
    @Override protected List<Row> rows(){
        var cfg=ArtemisHotf.config();var state=ArtemisHotf.summary();if(cfg==null)return List.of();List<Row> out=new ArrayList<>();
        if(cfg.hotfHudWhispers)out.add(new Row("","Whispers",format(state.whispers()),0xFF55FFFF));
        if(cfg.hotfHudTokens)out.add(new Row("","Tokens",Integer.toString(state.tokens()),0xFFFFFF55));
        if(cfg.hotfHudSpent)out.add(new Row("","Allocated",format(state.spent()),0xFFFFAA00));
        if(cfg.hotfHudPerks)out.add(new Row("","Perks",state.enabled()+" enabled / "+state.unlocked()+" unlocked",0xFFFFFFFF));
        if(cfg.hotfHudMaxed)out.add(new Row("","Maxed",Integer.toString(state.maxed()),0xFF55FF55));
        return out;
    }
    private static String format(long value){return NumberFormat.getIntegerInstance(Locale.US).format(value);}
}
