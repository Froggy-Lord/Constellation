package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaHotmHelper;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public final class HotmHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public HotmHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"aquila-hotm";}@Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition position){this.position=position;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean enabled){this.enabled=enabled;}@Override public boolean visibleNow(){return isEnabled()&&AquilaHotmHelper.visible();}
    @Override public String editorLabel(){return"Heart of the Mountain";}@Override protected String title(){return"Heart of the Mountain";}
    @Override protected List<Row> rows(){
        var cfg=AquilaHotmHelper.config();var state=AquilaHotmHelper.summary();if(cfg==null)return List.of();List<Row> out=new ArrayList<>();
        if(AquilaHotmHelper.menuOpen()){
            if(cfg.hotmHudPowder){out.add(new Row("","Mithril",format(state.mithril()),0xFF55AA55));out.add(new Row("","Gemstone",format(state.gemstone()),0xFFFF55FF));out.add(new Row("","Glacite",format(state.glacite()),0xFF55FFFF));}
            if(cfg.hotmHudTokens)out.add(new Row("","Tokens",Integer.toString(state.tokens()),0xFFFFFF55));
            if(cfg.hotmHudSpent)out.add(new Row("","Allocated",format(state.mithrilSpent())+" M / "+format(state.gemstoneSpent())+" G / "+format(state.glaciteSpent())+" Gl",0xFFFFAA00));
            if(cfg.hotmHudPerks)out.add(new Row("","Perks",state.enabled()+" enabled / "+state.unlocked()+" unlocked",0xFFFFFFFF));
            if(cfg.hotmHudMaxed)out.add(new Row("","Maxed",Integer.toString(state.maxed()),0xFF55FF55));
        }
        if(cfg.hotmHudSkyMall&&!state.skyMall().isBlank())out.add(new Row("","Sky Mall",state.skyMall(),0xFF55FFFF));
        return out;
    }
    private static String format(long value){return NumberFormat.getIntegerInstance(Locale.US).format(value);}
}
