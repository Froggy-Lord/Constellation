package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HydraFishingFestival;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Feesh (Apache-2.0): features/overlays/FishingFestivalTracker.kt
public final class FishingFestivalHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public FishingFestivalHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"hydra-fishing-festival";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition p){pos=p;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}@Override public boolean visibleNow(){return isEnabled()&&HydraFishingFestival.visible();}@Override public String editorLabel(){return"Fishing Festival";}@Override protected String title(){return"Fishing Festival";}
    @Override protected List<Row> rows(){var state=HydraFishingFestival.state();if(state==null)return List.of();var cfg=HydraFishingFestival.config();var out=new ArrayList<Row>();out.add(new Row("","Sharks",Integer.toString(state.total()),cfg.fishingFestivalTotalColor));if(cfg.fishingFestivalHudShowBreakdown){out.add(new Row("","Great White",Integer.toString(state.greatWhite()),cfg.fishingFestivalGreatWhiteColor));out.add(new Row("","Tiger",Integer.toString(state.tiger()),cfg.fishingFestivalTigerColor));out.add(new Row("","Blue",Integer.toString(state.blue()),cfg.fishingFestivalBlueColor));out.add(new Row("","Nurse",Integer.toString(state.nurse()),cfg.fishingFestivalNurseColor));}if(cfg.fishingFestivalHudShowPersonalBests){out.add(new Row("","Best Sharks",Integer.toString(state.bestTotal()),cfg.fishingFestivalPbColor));out.add(new Row("","Best Great Whites",Integer.toString(state.bestGreatWhite()),cfg.fishingFestivalPbColor));}return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Sharks","500",0xFF55FFFF),new Row("","Great White","50",0xFFFFAA00),new Row("","Tiger","100",0xFFAA00AA),new Row("","Blue","150",0xFF5555FF),new Row("","Nurse","200",0xFF55FF55));}
}
