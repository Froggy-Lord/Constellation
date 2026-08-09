package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HydraBarnFishing;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Feesh (Apache-2.0): features/overlays/BarnFishingTimer.kt
public final class BarnFishingHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate; private HudPosition pos; private boolean enabled=true;
    public BarnFishingHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"hydra-barn-fishing";} @Override public HudPosition position(){return pos;} @Override public void setPosition(HudPosition p){pos=p;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();} @Override public void setEnabled(boolean value){enabled=value;}
    @Override public boolean visibleNow(){return isEnabled()&&HydraBarnFishing.visible();} @Override public String editorLabel(){return"Barn Fishing";} @Override protected String title(){return"Barn Fishing";}
    @Override protected List<Row> rows(){var s=HydraBarnFishing.state();if(s==null)return List.of();var cfg=HydraBarnFishing.config();var out=new java.util.ArrayList<Row>();out.add(new Row("","Sea creatures",Integer.toString(s.count()),s.countWarning()?cfg.barnTimerWarningColor:cfg.barnTimerNormalColor));out.add(new Row("","Stack time",HydraBarnFishing.time(s.elapsedMillis()),s.timeWarning()?cfg.barnTimerWarningColor:cfg.barnTimerNormalColor));if(cfg.barnTimerShowThreshold)out.add(new Row("","Mob threshold",Integer.toString(s.threshold()),0xFFAAAAAA));if(cfg.barnTimerShowArea)out.add(new Row("","Area",s.area(),0xFFAAAAAA));return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Sea creatures","25",0xFFFFFFFF),new Row("","Stack time","2:30",0xFFFFFFFF),new Row("","Mob threshold","50",0xFFAAAAAA));}
}
