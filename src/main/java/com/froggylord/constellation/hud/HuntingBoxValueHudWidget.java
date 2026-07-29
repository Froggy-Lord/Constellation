package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.ArtemisHuntingBoxValue;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class HuntingBoxValueHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public HuntingBoxValueHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"artemis-hunting-box-value";}
    @Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition position){this.position=position;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean enabled){this.enabled=enabled;}
    @Override public boolean visibleNow(){return isEnabled()&&ArtemisHuntingBoxValue.visible();}
    @Override public String editorLabel(){return"Hunting Box Value";}
    @Override protected String title(){return"Hunting Box Value";}
    @Override protected List<Row> rows(){
        var cfg=ArtemisHuntingBoxValue.config();var state=ArtemisHuntingBoxValue.state();
        if(cfg==null||state==null)return List.of();
        List<Row> out=new ArrayList<>();
        int shown=0;
        if(cfg.huntingBoxValueShowRows)for(var shard:state.rows()){
            if(shown++>=Math.clamp(cfg.huntingBoxValueRows,1,100))break;
            String label=(cfg.huntingBoxValueShowAmount?shard.amount()+"x ":"")+shard.name();
            List<String> values=new ArrayList<>();
            if(cfg.huntingBoxValueShowSell)values.add("sell "+ArtemisHuntingBoxValue.coins(shard.sellTotal(),shard.sellPriced()));
            if(cfg.huntingBoxValueShowBuy)values.add("buy "+ArtemisHuntingBoxValue.coins(shard.buyTotal(),shard.buyPriced()));
            if(cfg.huntingBoxValueShowUnit)values.add("each "+ArtemisHuntingBoxValue.coins(shard.buyUnit(),shard.buyPriced()));
            boolean complete=(!cfg.huntingBoxValueShowSell||shard.sellPriced())
                    &&(!(cfg.huntingBoxValueShowBuy||cfg.huntingBoxValueShowUnit)||shard.buyPriced());
            out.add(new Row("",label,String.join("  ",values),complete?0xFF55FF55:0xFFFF5555));
        }
        if(state.error())out.add(new Row("","Warning","No shard lore parsed",0xFFFF5555));
        if(cfg.huntingBoxValueShowTotalShards)out.add(new Row("","Total shards",Long.toString(state.totalShards()),0xFF55FFFF));
        if(cfg.huntingBoxValueShowTotalSell)out.add(new Row("","Instant sell",ArtemisHuntingBoxValue.coins(state.totalSell(),state.sellComplete()),state.sellComplete()?0xFFFFAA00:0xFFFF5555));
        if(cfg.huntingBoxValueShowTotalBuy)out.add(new Row("","Instant buy",ArtemisHuntingBoxValue.coins(state.totalBuy(),state.buyComplete()),state.buyComplete()?0xFFFFAA00:0xFFFF5555));
        return out;
    }
}
