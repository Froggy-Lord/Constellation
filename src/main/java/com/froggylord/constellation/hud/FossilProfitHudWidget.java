package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaFossilProfit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/mining/fossilexcavator/ExcavatorProfitTracker.kt
public final class FossilProfitHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;
    private HudPosition pos;
    private boolean enabled=true;
    public FossilProfitHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"aquila-fossil-profit";}
    @Override public HudPosition position(){return pos;}
    @Override public void setPosition(HudPosition value){pos=value;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean value){enabled=value;}
    @Override public boolean visibleNow(){return isEnabled()&&AquilaFossilProfit.visible();}
    @Override public String editorLabel(){return"Fossil Profit";}
    @Override protected String title(){return"Fossil Excavation Profit";}
    @Override protected List<Row> rows(){
        var stats=AquilaFossilProfit.stats();var cfg=AquilaFossilProfit.config();if(stats==null||cfg==null)return List.of();List<Row> rows=new ArrayList<>();
        if(cfg.fossilProfitShowItems)for(var item:stats.items()){String value="";if(cfg.fossilProfitShowAmounts)value+=item.amount()+"x";if(cfg.fossilProfitShowAmounts&&cfg.fossilProfitShowValues)value+="  ";if(cfg.fossilProfitShowValues)value+=AquilaFossilProfit.coins(item.value(),item.complete());rows.add(new Row("",item.name(),value,item.complete()?cfg.fossilProfitPositiveColor:cfg.fossilProfitPartialColor));}
        if(cfg.fossilProfitShowExcavations)rows.add(new Row("","Excavations",Long.toString(stats.excavations()),0xFFFFFF55));
        if(cfg.fossilProfitShowScrapCost)rows.add(new Row("","Scrap cost","-"+AquilaFossilProfit.coins(stats.scrapCost(),stats.scrapCost()>0||stats.excavations()==0),cfg.fossilProfitNegativeColor));
        if(cfg.fossilProfitShowDust&&stats.dust()>0)rows.add(new Row("","Fossil Dust",stats.dust()+"  "+AquilaFossilProfit.coins(stats.dustValue(),stats.scrapCost()>0),cfg.fossilProfitPositiveColor));
        if(cfg.fossilProfitShowPowder&&stats.powder()>0)rows.add(new Row("","Glacite Powder",String.format(java.util.Locale.ROOT,"%,d",stats.powder()),0xFF55FFFF));
        int profitColor=!stats.complete()?cfg.fossilProfitPartialColor:stats.profit()<0?cfg.fossilProfitNegativeColor:cfg.fossilProfitPositiveColor;
        if(cfg.fossilProfitShowTotal)rows.add(new Row("","Total profit",AquilaFossilProfit.coins(stats.profit(),stats.complete()),profitColor));
        if(cfg.fossilProfitShowPerExcavation)rows.add(new Row("","Per excavation",AquilaFossilProfit.coins(stats.perExcavation(),stats.complete()),profitColor));
        if(cfg.fossilProfitShowPerHour)rows.add(new Row("","Profit per hour",AquilaFossilProfit.coins(stats.perHour(),stats.complete()),profitColor));
        if(cfg.fossilProfitShowUptime)rows.add(new Row("","Uptime",AquilaFossilProfit.time(stats.activeMillis()),0xFFFFFFFF));
        if(stats.recent()!=null)rows.add(new Row("","Recent",stats.recent().name()+"  "+AquilaFossilProfit.coins(stats.recent().value(),stats.recent().complete()),stats.recent().complete()?cfg.fossilProfitPositiveColor:cfg.fossilProfitPartialColor));
        return rows;
    }
    @Override protected List<Row> previewRows(){return List.of(new Row("","Tusk Fossil","2x  1.20m",0xFF55FF55),new Row("","Excavations","43",0xFFFFFF55),new Row("","Glacite Powder","71,216",0xFF55FFFF),new Row("","Total profit","18.42m",0xFF55FF55),new Row("","Profit per hour","6.71m",0xFF55FF55));}
}
