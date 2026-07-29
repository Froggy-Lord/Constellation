package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.ArtemisHuntingProfit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public final class HuntingProfitHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public HuntingProfitHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"artemis-hunting-profit";}
    @Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition position){this.position=position;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean enabled){this.enabled=enabled;}
    @Override public boolean visibleNow(){return isEnabled()&&ArtemisHuntingProfit.visible();}
    @Override public String editorLabel(){return"Hunting Profit";}
    @Override protected String title(){return"Hunting Profit";}
    @Override protected List<Row> rows(){
        var cfg=ArtemisHuntingProfit.config();var stats=ArtemisHuntingProfit.stats();if(cfg==null||stats==null)return List.of();
        List<Row> out=new ArrayList<>();
        if(cfg.huntingProfitShowRecent&&stats.recent()!=null)out.add(new Row("","Recent: "+stats.recent().amount()+"x "+stats.recent().name(),ArtemisHuntingProfit.coins(stats.recent().value(),stats.recent().complete()),cfg.huntingProfitRecentColor));
        if(cfg.huntingProfitShowTable)for(var item:stats.rows())out.add(new Row("",item.amount()+"x "+item.name(),ArtemisHuntingProfit.coins(item.value(),item.complete()),item.complete()?cfg.huntingProfitValueColor:0xFFFF5555));
        out.add(new Row("","Total",ArtemisHuntingProfit.coins(stats.profit(),stats.complete()),stats.complete()?cfg.huntingProfitValueColor:0xFFFF5555));
        if(cfg.huntingProfitShowProfitPerHour)out.add(new Row("","Per hour",ArtemisHuntingProfit.coins(stats.perHour(),stats.complete()),0xFF55FFFF));
        if(cfg.huntingProfitShowMobs)out.add(new Row("","Mobs caught",Long.toString(stats.mobs()),cfg.huntingProfitNameColor));
        if(cfg.huntingProfitShowShards)out.add(new Row("","Shards",Long.toString(stats.shards()),cfg.huntingProfitNameColor));
        if(cfg.huntingProfitShowUptime)out.add(new Row("","Uptime",time(stats.uptime()),0xFFAAAAAA));
        return out;
    }
    private static String time(long millis){long seconds=millis/1000;return String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60);}
}
