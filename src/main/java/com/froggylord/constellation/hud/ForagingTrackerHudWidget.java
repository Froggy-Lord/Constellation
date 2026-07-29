package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.ArtemisForagingTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public final class ForagingTrackerHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public ForagingTrackerHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"artemis-foraging-tracker";}@Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition position){this.position=position;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean enabled){this.enabled=enabled;}@Override public boolean visibleNow(){return isEnabled()&&ArtemisForagingTracker.visible();}
    @Override public String editorLabel(){return"Foraging Tracker";}@Override protected String title(){return"Foraging Tracker";}
    @Override protected List<Row> rows(){
        var cfg=ArtemisForagingTracker.config();var stats=ArtemisForagingTracker.stats();if(cfg==null||stats==null)return List.of();List<Row> out=new ArrayList<>();
        if(cfg.foragingTrackerShowRecent&&stats.recent()!=null)out.add(new Row("","Recent: "+stats.recent().amount()+"x "+stats.recent().name(),ArtemisForagingTracker.coins(stats.recent().value(),stats.recent().complete()),cfg.foragingTrackerRecentColor));
        if(cfg.foragingTrackerShowTable)for(var item:stats.rows())out.add(new Row("",item.amount()+"x "+item.name(),ArtemisForagingTracker.coins(item.value(),item.complete()),item.complete()?cfg.foragingTrackerValueColor:0xFFFF5555));
        if(cfg.foragingTrackerShowProfit)out.add(new Row("","Profit",ArtemisForagingTracker.coins(stats.profit(),stats.complete()),stats.complete()?cfg.foragingTrackerValueColor:0xFFFF5555));
        if(cfg.foragingTrackerShowProfitPerHour)out.add(new Row("","Per hour",ArtemisForagingTracker.coins(stats.perHour(),stats.complete()),0xFF55FFFF));
        if(cfg.foragingTrackerShowForagingXp)out.add(new Row("","Foraging XP",Long.toString(stats.foragingXp()),cfg.foragingTrackerNameColor));
        if(cfg.foragingTrackerShowHotfXp)out.add(new Row("","HOTF XP",Long.toString(stats.hotfXp()),cfg.foragingTrackerNameColor));
        if(cfg.foragingTrackerShowWhispers)out.add(new Row("","Forest Whispers",Long.toString(stats.whispers()),0xFF55FFFF));
        if(cfg.foragingTrackerShowWholeTrees)out.add(new Row("","Whole trees",String.format(Locale.ROOT,"%.2f",stats.wholeTrees()),0xFF55FF55));
        if(cfg.foragingTrackerShowTrees)out.add(new Row("","Tree gifts",Long.toString(stats.trees()),cfg.foragingTrackerNameColor));
        if(cfg.foragingTrackerShowUptime)out.add(new Row("","Uptime",time(stats.uptime()),0xFFAAAAAA));
        return out;
    }
    private static String time(long millis){long seconds=millis/1000;return String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60);}
}
