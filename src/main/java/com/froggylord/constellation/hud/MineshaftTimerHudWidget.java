package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaMiningProgress;
import java.util.ArrayList;import java.util.List;import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/mining/glacitemineshaft/MineshaftCaveInTimer.kt
public final class MineshaftTimerHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public MineshaftTimerHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}@Override public String id(){return"aquila-mineshaft-timer";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition p){pos=p;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean v){enabled=v;}@Override public boolean visibleNow(){return isEnabled()&&AquilaMiningProgress.timer()!=null;}@Override public String editorLabel(){return"Mineshaft Timer";}@Override protected String title(){return"Glacite Mineshaft";}
    @Override protected List<Row> rows(){var t=AquilaMiningProgress.timer();if(t==null)return List.of();var cfg=AquilaMiningProgress.config();var out=new ArrayList<Row>();if(cfg.miningTimerShowEntrance){long sec=t.entranceRemainingMillis()/1000;int color=sec<=cfg.miningTimerWarningSeconds?cfg.miningDangerColor:sec<=cfg.miningTimerCautionSeconds?cfg.miningWarningColor:cfg.miningGoodColor;out.add(new Row("","Entrance caves in",t.entranceRemainingMillis()==0?"Caved in":AquilaMiningProgress.time(t.entranceRemainingMillis()),color));}if(cfg.miningTimerShowElapsed)out.add(new Row("","Time inside",AquilaMiningProgress.time(t.elapsedMillis())));if(cfg.miningTimerShowCold)out.add(new Row("","Cold",t.cold()+" / 100",t.cold()>=90?cfg.miningDangerColor:0xFF55FFFF));if(cfg.miningTimerShowColdEstimate)out.add(new Row("","Estimated cold limit",t.coldRemainingMillis()<0?"Calculating":AquilaMiningProgress.time(t.coldRemainingMillis())));return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Entrance caves in","0:34",0xFF55FF55),new Row("","Time inside","0:26"),new Row("","Cold","31 / 100"),new Row("","Estimated cold limit","2:18"));}
}
