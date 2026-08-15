package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HydraPetLeveling;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Feesh (Apache-2.0): features/alerts/PetLevelUpAlert.kt
public final class PetLevelHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public PetLevelHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"hydra-pet-level";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition p){pos=p;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}@Override public boolean visibleNow(){return isEnabled()&&HydraPetLeveling.visible();}@Override public String editorLabel(){return"Maxed Pet";}@Override protected String title(){return"Maxed Pet";}
    @Override protected List<Row> rows(){var state=HydraPetLeveling.state();if(state==null)return List.of();var cfg=HydraPetLeveling.config();var out=new ArrayList<Row>();out.add(new Row("",state.pet(),"Level "+state.level(),cfg.petLevelAlertColor));if(state.maxKnown()&&cfg.petLevelHudShowPrice)out.add(new Row("","Value",HydraPetLeveling.coins(state.maxPrice()),cfg.petLevelPriceColor));if(state.profitKnown()&&cfg.petLevelHudShowProfit)out.add(new Row("","Leveling Profit",HydraPetLeveling.coins(state.profit()),state.profit()>=0?cfg.petLevelProfitColor:cfg.petLevelLossColor));return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Flying Fish","Level 100",0xFFFF55FF),new Row("","Value","42.5m",0xFFFFAA00),new Row("","Leveling Profit","+12.3m",0xFF55FF55));}
}
