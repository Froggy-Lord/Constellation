package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AurigaHitmanCosts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/inventory/chocolatefactory/hitman/HitmanSlots.kt
public final class HitmanCostsHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate; private HudPosition position; private boolean enabled=true;
    public HitmanCostsHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"auriga-hitman-costs";}
    @Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition value){position=value;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean value){enabled=value;}
    @Override public boolean visibleNow(){return isEnabled()&&AurigaHitmanCosts.visible();}
    @Override public String editorLabel(){return"Hitman Slot Costs";}
    @Override protected String title(){return"Hitman Slot Progress";}
    @Override protected List<Row> rows(){
        var state=AurigaHitmanCosts.state();var cfg=AurigaHitmanCosts.config();ArrayList<Row> out=new ArrayList<>();
        if(cfg.chocolateFactoryHitmanShowPurchased)out.add(new Row("","Purchased",state.owned()+"/"+state.total(),0xFF55FF55));
        if(cfg.chocolateFactoryHitmanShowPaid)out.add(new Row("","Total paid",AurigaHitmanCosts.coins(state.paid()),0xFFFFAA00));
        if(cfg.chocolateFactoryHitmanShowRemaining)out.add(new Row("","Remaining",String.valueOf(state.total()-state.owned()),0xFFFF5555));
        if(cfg.chocolateFactoryHitmanShowRemainingCost)out.add(new Row("","Cost left",AurigaHitmanCosts.coins(state.remaining()),0xFFFFAA00));
        if(cfg.chocolateFactoryHitmanShowNext)for(int i=0;i<state.next().size();i++)
            out.add(new Row("","Slot "+(state.owned()+i+1),AurigaHitmanCosts.coins(state.next().get(i)),0xFFFFFFFF));
        return out;
    }
    @Override protected List<Row> previewRows(){return List.of(new Row("","Purchased","12/28",0xFF55FF55),new Row("","Total paid","18,611,000 Coins",0xFFFFAA00),new Row("","Remaining","16",0xFFFF5555),new Row("","Slot 13","5,000,000 Coins",0xFFFFFFFF));}
}
