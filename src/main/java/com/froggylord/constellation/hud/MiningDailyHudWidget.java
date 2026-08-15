package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaMiningGuidance;
import java.util.ArrayList;import java.util.List;import java.util.function.BooleanSupplier;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/Fetchur.java
// ported from SkyOcean (MIT): features/mining/PuzzlerSolver.kt
public final class MiningDailyHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public MiningDailyHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"aquila-mining-daily";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition p){pos=p;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean v){enabled=v;}@Override public boolean visibleNow(){var s=AquilaMiningGuidance.state();return isEnabled()&&s!=null&&(!s.fetchurAnswer().isEmpty()||s.puzzler()!=null);}@Override public String editorLabel(){return"Mining Daily Helpers";}@Override protected String title(){return"Mining Guidance";}
    @Override protected List<Row> rows(){var cfg=AquilaMiningGuidance.config();var s=AquilaMiningGuidance.state();if(s==null)return List.of();var out=new ArrayList<Row>();if(cfg.miningDailyShowFetchur&&!s.fetchurAnswer().isEmpty())out.add(new Row("","Fetchur",s.fetchurAnswer(),cfg.miningDwarvenColor));if(cfg.miningDailyShowPuzzler&&s.puzzler()!=null)out.add(new Row("","Puzzler",s.puzzler().getX()+", "+s.puzzler().getY()+", "+s.puzzler().getZ(),cfg.miningPuzzlerColor));return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Fetchur","Compass",0xFF45BDE0),new Row("","Puzzler","181, 195, 135",0xFF55FFFF));}
}
