package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HydraFishingState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/fishing/FishingBaitDisplay.kt
public final class FishingBaitHudWidget extends ThemedHudWidget {
    private HudPosition pos;private final BooleanSupplier gate;private boolean enabled=true;
    public FishingBaitHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"hydra-fishing-bait";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition p){pos=p;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}@Override public boolean visibleNow(){return isEnabled()&&HydraFishingState.baitVisible();}@Override public String editorLabel(){return"Fishing Bait";}@Override protected String title(){return"Fishing Bait";}
    @Override protected List<Row> rows(){var cfg=HydraFishingState.config();var bait=HydraFishingState.bait();if(cfg==null||bait==null)return List.of();var out=new ArrayList<Row>();out.add(new Row("","Bait",bait.name(),bait.amount()==0?cfg.fishingBaitEmptyColor:bait.amount()<=cfg.fishingBaitLowThreshold?cfg.fishingBaitLowColor:cfg.fishingBaitNameColor));if(cfg.fishingBaitShowAmount&&bait.amount()>0)out.add(new Row("","Remaining",Integer.toString(bait.amount()),bait.amount()<=cfg.fishingBaitLowThreshold?cfg.fishingBaitLowColor:cfg.fishingBaitAmountColor));if(cfg.fishingBaitShowRodState)out.add(new Row("","Rod",HydraFishingState.hookVisible()?"Cast":"Ready",cfg.fishingBaitAmountColor));return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Bait","Fish Bait",0xFFFFFFFF),new Row("","Remaining","64",0xFF55FFFF));}
}
