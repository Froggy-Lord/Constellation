package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HydraFishingProfitTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public final class FishingProfitHudWidget extends ThemedHudWidget {
    private HudPosition pos; private final BooleanSupplier gate; private boolean enabled=true;
    public FishingProfitHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"hydra-fishing-profit";} @Override public HudPosition position(){return pos;} @Override public void setPosition(HudPosition p){pos=p;} @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();} @Override public void setEnabled(boolean v){enabled=v;} @Override public boolean visibleNow(){return isEnabled()&&HydraFishingProfitTracker.visible();} @Override public String editorLabel(){return"Fishing Profit";} @Override protected String title(){return"Fishing Profit";}
    @Override protected List<Row> rows(){var cfg=HydraFishingProfitTracker.config();var stats=HydraFishingProfitTracker.stats();if(cfg==null||stats==null)return List.of();var out=new ArrayList<Row>();if(cfg.fishingProfitShowRecentDrops&&stats.recent()!=null)out.add(new Row("","Recent: "+stats.recent().name(),HydraFishingProfitTracker.coins(stats.recent().value(),stats.recent().complete()),cfg.fishingProfitRecentColor));if(cfg.fishingProfitShowTable)for(var item:stats.items())out.add(new Row("",item.amount()+"x "+item.name(),HydraFishingProfitTracker.coins(item.value(),item.complete()),item.complete()?cfg.fishingProfitValueColor:0xFFFF5555));out.add(new Row("","Total",HydraFishingProfitTracker.coins(stats.profit(),stats.complete()),stats.complete()?cfg.fishingProfitValueColor:0xFFFF5555));if(cfg.fishingProfitShowProfitPerHour)out.add(new Row("","Per hour",HydraFishingProfitTracker.coins(stats.perHour(),stats.complete()),0xFF55FFFF));if(cfg.fishingProfitShowCatches)out.add(new Row("","Catches",Integer.toString(stats.catches()),cfg.fishingProfitNameColor));if(cfg.fishingProfitShowUptime)out.add(new Row("","Uptime",time(stats.activeMillis()),0xFFAAAAAA));return out;}
    private static String time(long millis){long seconds=millis/1000;return String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60);}
}
