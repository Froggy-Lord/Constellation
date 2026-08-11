package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HydraFishingState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/fishing/FishingHookDisplayHelper.java
public final class FishingHookHudWidget extends ThemedHudWidget {
    private HudPosition pos;private final BooleanSupplier gate;private boolean enabled=true;
    public FishingHookHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"hydra-fishing-hook";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition p){pos=p;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}@Override public boolean visibleNow(){return isEnabled()&&HydraFishingState.hookVisible();}@Override public String editorLabel(){return"Fishing Hook";}@Override protected String title(){return"Fishing Hook";}
    @Override protected List<Row> rows(){var cfg=HydraFishingState.config();var state=HydraFishingState.state();if(cfg==null||state==null)return List.of();var out=new ArrayList<Row>();String hook=state.ready()&&cfg.fishingHookCustomReadyText?cfg.fishingHookReadyText:state.hookText();out.add(new Row("","Catch",hook,state.ready()?cfg.fishingHookReadyColor:cfg.fishingHookTimerColor));if(cfg.fishingHookShowBobberAge)out.add(new Row("","Bobber",String.format(Locale.ROOT,"%.2fs",state.ageMillis()/1000.0),cfg.fishingHookInfoColor));if(cfg.fishingHookShowLiquid)out.add(new Row("","Liquid",state.liquid(),cfg.fishingHookInfoColor));if(cfg.fishingHookShowDistance)out.add(new Row("","Distance",String.format(Locale.ROOT,"%.1fm",state.distance()),cfg.fishingHookInfoColor));return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Catch","!!!",0xFFFF5555),new Row("","Liquid","Water",0xFF55FFFF));}
}
