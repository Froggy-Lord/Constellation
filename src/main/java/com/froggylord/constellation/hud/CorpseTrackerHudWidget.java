package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaCorpseHelper;
import java.util.List;import java.util.function.BooleanSupplier;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/profittrackers/corpse/CorpseProfitTracker.java
public final class CorpseTrackerHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public CorpseTrackerHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}@Override public String id(){return"aquila-corpse-tracker";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition p){pos=p;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean v){enabled=v;}@Override public boolean visibleNow(){return isEnabled()&&AquilaCorpseHelper.stats()!=null;}@Override public String editorLabel(){return"Corpse Tracker";}@Override protected String title(){return"Corpse Tracker";}
    @Override protected List<Row> rows(){var s=AquilaCorpseHelper.stats();if(s==null)return List.of();var cfg=AquilaCorpseHelper.config();return List.of(new Row("","Detected",Integer.toString(s.found())),new Row("","Opened",Integer.toString(s.opened())),new Row("","Last profit",cfg.corpseProfitHud?money(s.lastProfit())+(s.lastComplete()?"":" partial"):"Hidden",s.lastProfit()>=0?0xFF55FF55:0xFFFF5555),new Row("","Session profit",cfg.corpseProfitHud?money(s.totalProfit())+(s.sessionComplete()?"":" partial"):"Hidden",s.totalProfit()>=0?0xFF55FF55:0xFFFF5555));}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Detected","4"),new Row("","Opened","2"),new Row("","Last profit","1.24M",0xFF55FF55),new Row("","Session profit","1.81M",0xFF55FF55));}
    private static String money(double value){double a=Math.abs(value);String s=a>=1_000_000?String.format(java.util.Locale.ROOT,"%.2fM",a/1_000_000):a>=1_000?String.format(java.util.Locale.ROOT,"%.1fk",a/1_000):String.format(java.util.Locale.ROOT,"%.0f",a);return value<0?"-"+s:s;}
}
