package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaMiningProgress;
import java.util.ArrayList;import java.util.List;import java.util.function.BooleanSupplier;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/CrystalsHudWidget.java
public final class MiningCrystalHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public MiningCrystalHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}@Override public String id(){return"aquila-crystals";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition p){pos=p;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean v){enabled=v;}@Override public boolean visibleNow(){return isEnabled()&&!AquilaMiningProgress.crystals().isEmpty();}@Override public String editorLabel(){return"Crystal Hollows Crystals";}@Override protected String title(){return"Crystal Status";}
    @Override protected List<Row> rows(){var cfg=AquilaMiningProgress.config();var out=new ArrayList<Row>();for(var c:AquilaMiningProgress.crystals()){if(c.found()&&!cfg.miningCrystalShowFound||!c.found()&&!cfg.miningCrystalShowMissing)continue;String value=c.state();if(cfg.miningCrystalShowLocation)value=value+"  "+c.location();out.add(new Row("",c.name(),value,c.found()?cfg.miningGoodColor:cfg.miningWarningColor));}return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Jade Crystal","Found  Mines of Divan",0xFF55FF55),new Row("","Amber Crystal","Not Found  Goblin Queen's Den",0xFFFFFF55));}
}
