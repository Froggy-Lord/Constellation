package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaMiningProgress;
import java.util.ArrayList;import java.util.List;import java.util.Locale;import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/mining/MineshaftPityDisplay.kt
public final class MineshaftPityHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public MineshaftPityHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}@Override public String id(){return"aquila-mineshaft-pity";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition p){pos=p;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean v){enabled=v;}@Override public boolean visibleNow(){return isEnabled()&&AquilaMiningProgress.pity()!=null;}@Override public String editorLabel(){return"Mineshaft Pity";}@Override protected String title(){return"Mineshaft Pity";}
    @Override protected List<Row> rows(){var p=AquilaMiningProgress.pity();if(p==null)return List.of();var cfg=AquilaMiningProgress.config();var out=new ArrayList<Row>();if(cfg.miningPityShowCounter)out.add(new Row("","Counter",String.format(Locale.ROOT,"%,d / %,d",p.current(),p.maximum()),cfg.miningPityColor));if(cfg.miningPityShowRemaining)out.add(new Row("","Until pity",String.format(Locale.ROOT,"%,d",Math.max(0,p.maximum()-p.current()))));if(cfg.miningPityShowPercent)out.add(new Row("","Progress",String.format(Locale.ROOT,"%.1f%%",p.current()*100.0/Math.max(1,p.maximum()))));if(cfg.miningPityShowLastFound&&p.lastFoundMillis()>0)out.add(new Row("","Last found",AquilaMiningProgress.time(System.currentTimeMillis()-p.lastFoundMillis())));return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Counter","1,284 / 2,000",0xFFFFAA00),new Row("","Until pity","716"),new Row("","Progress","64.2%"));}
}
