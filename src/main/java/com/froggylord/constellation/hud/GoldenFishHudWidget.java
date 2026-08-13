package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HydraGoldenFish;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/fishing/trophy/GoldenFishTimer.kt
public final class GoldenFishHudWidget extends ThemedHudWidget {
    private HudPosition pos;private final BooleanSupplier gate;private boolean enabled=true;
    public GoldenFishHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"hydra-golden-fish";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition p){pos=p;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}@Override public boolean visibleNow(){return isEnabled()&&HydraGoldenFish.visible();}@Override public String editorLabel(){return"Golden Fish";}@Override protected String title(){return"Golden Fish";}
    @Override protected List<Row> rows(){var cfg=HydraGoldenFish.config();var state=HydraGoldenFish.state();if(cfg==null||state==null)return List.of();var out=new ArrayList<Row>();if(state.active()){if(cfg.goldenFishShowInteractions)out.add(new Row("","Interactions",state.interactions()+"/3",state.ready()?cfg.goldenFishReadyColor:cfg.goldenFishNormalColor));if(cfg.goldenFishShowDespawn)out.add(new Row("","Despawns",HydraGoldenFish.time(state.despawnMillis()),state.despawnMillis()<10_000?cfg.goldenFishWarningColor:cfg.goldenFishTimerColor));}else{if(cfg.goldenFishShowLastSpawn)out.add(new Row("","Last fish",state.lastSpawnAgo()<0?"None":HydraGoldenFish.time(state.lastSpawnAgo())+" ago",cfg.goldenFishNormalColor));if(cfg.goldenFishShowLastRod)out.add(new Row("","Last rod",state.lastRodAgo()<0?"None":HydraGoldenFish.time(state.lastRodAgo())+" ago",state.rodValid()?cfg.goldenFishTimerColor:cfg.goldenFishWarningColor));out.add(new Row("",state.available()?"Available":"Can spawn",state.spawnMillis()<0?"Cast rod":state.available()?HydraGoldenFish.time(state.availableFor())+" ago":HydraGoldenFish.time(state.spawnMillis()),state.available()?cfg.goldenFishReadyColor:cfg.goldenFishTimerColor));if(cfg.goldenFishShowChance&&state.available())out.add(new Row("","Chance",String.format(Locale.ROOT,"%.1f%%",state.chance()*100),cfg.goldenFishReadyColor));}return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Last fish","7:21 ago",0xFFFFAA00),new Row("","Available","0:42 ago",0xFF55FF55),new Row("","Chance","17.5%",0xFF55FF55));}
}
