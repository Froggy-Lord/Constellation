package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.ArtemisAttributeOverlay;
import com.froggylord.constellation.constellation.ArtemisHuntingProfit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class AttributeOverlayHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public AttributeOverlayHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"artemis-attribute-overlay";}@Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition position){this.position=position;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean enabled){this.enabled=enabled;}@Override public boolean visibleNow(){return isEnabled()&&ArtemisAttributeOverlay.visible();}
    @Override public String editorLabel(){return"Attribute Shards";}@Override protected String title(){return"Attribute Shards";}
    @Override protected List<Row> rows(){
        var cfg=ArtemisAttributeOverlay.config();var state=ArtemisAttributeOverlay.state();if(cfg==null||state==null)return List.of();
        List<Row> out=new ArrayList<>();int shown=0;
        for(var shard:state.rows()){
            if(cfg.attributeOverlayHideUnknownPrice&&!shard.priced()&&shard.needed()>0)continue;
            if(shown++>=Math.clamp(cfg.attributeOverlayRows,1,100))break;
            String label=shard.name();List<String> values=new ArrayList<>();
            if(cfg.attributeOverlayShowTier)values.add("T"+shard.tier());
            if(cfg.attributeOverlayShowNeeded)values.add(shard.needed()+" needed");
            if(cfg.attributeOverlayShowBox&&cfg.attributeOverlayIncludeHuntingBox)values.add(shard.box()+" box");
            if(cfg.attributeOverlayShowPrice)values.add(ArtemisHuntingProfit.coins(shard.price(),shard.priced()||shard.needed()==0));
            int color=shard.needed()==0?cfg.attributeOverlayCompleteColor:shard.priced()?cfg.attributeOverlayNormalColor:cfg.attributeOverlayUnknownColor;
            out.add(new Row("",label,String.join("  ",values),color));
        }
        if(cfg.attributeOverlayShowSummary){
            out.add(new Row("","Unlocked",state.unlocked()+"/"+state.known(),0xFF55FFFF));
            out.add(new Row("","Maxed",state.maxed()+"/"+state.known(),0xFF55FF55));
            out.add(new Row("","Levels",Integer.toString(state.levels()),0xFFFFFF55));
            out.add(new Row("","Price to goal",ArtemisHuntingProfit.coins(state.total(),state.complete()),state.complete()?0xFFFFAA00:0xFFFF5555));
        }
        return out;
    }
}
