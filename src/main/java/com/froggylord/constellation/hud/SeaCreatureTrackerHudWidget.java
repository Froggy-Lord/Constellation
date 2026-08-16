package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HydraSeaCreatureTracker;
import java.util.ArrayList;import java.util.List;import java.util.Locale;import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/fishing/tracker/SeaCreatureTracker.kt
public final class SeaCreatureTrackerHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public SeaCreatureTrackerHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"hydra-sea-creatures";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition p){pos=p;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean v){enabled=v;}@Override public boolean visibleNow(){return isEnabled()&&HydraSeaCreatureTracker.visible();}@Override public String editorLabel(){return"Sea Creature Tracker";}@Override protected String title(){return"Sea Creatures";}
    @Override protected List<Row> rows(){var cfg=HydraSeaCreatureTracker.config();var stats=HydraSeaCreatureTracker.stats();if(stats==null)return List.of();var out=new ArrayList<Row>();for(var entry:stats.entries()){String value=Integer.toString(entry.amount());if(cfg.seaCreatureTrackerShowPercentage)value+="  "+String.format(Locale.ROOT,"%.1f%%",entry.percentage());out.add(new Row("",entry.creature().name(),value,HydraSeaCreatureTracker.entryColor(entry)));}if(cfg.seaCreatureTrackerShowTotal)out.add(new Row("","Total",Integer.toString(stats.total()),0xFFFFFFFF));if(cfg.seaCreatureTrackerShowRate)out.add(new Row("","Per hour",String.format(Locale.ROOT,"%.1f",stats.perHour()),0xFF55FFFF));if(cfg.seaCreatureTrackerShowDoubleHookStats)out.add(new Row("","Double hooks",Integer.toString(stats.doubleHooks()),0xFFFF55FF));if(cfg.seaCreatureTrackerShowUptime)out.add(new Row("","Uptime",time(stats.activeMillis()),0xFFAAAAAA));return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Lord Jawbus","3  2.1%",0xFFFF55FF),new Row("","Total","142",0xFFFFFFFF),new Row("","Per hour","38.7",0xFF55FFFF));}
    private static String time(long ms){long s=Math.max(0,ms/1000);return String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60);}
}
