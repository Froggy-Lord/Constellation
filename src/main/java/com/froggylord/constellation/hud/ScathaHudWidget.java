package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaScathaMining;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

// ported from NoFrills (GPL-3.0): features/mining/ScathaMining.java
// ported from SkyOcean (MIT): features/mining/scathas/Scathas.kt
public final class ScathaHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public ScathaHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"aquila-scatha";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition p){pos=p;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}@Override public boolean visibleNow(){return isEnabled()&&AquilaScathaMining.visible();}@Override public String editorLabel(){return"Scatha Mining";}@Override protected String title(){return"Scatha Mining";}
    @Override protected List<Row> rows(){var s=AquilaScathaMining.state();if(s==null)return List.of();var c=AquilaScathaMining.config();var out=new ArrayList<Row>();if(c.scathaHudShowCooldown){String value=s.cooldownMillis()>0?String.format(Locale.ROOT,"%.1fs",s.cooldownMillis()/1000.0):"Ready";out.add(new Row("","Cooldown",value,s.cooldownMillis()>0?c.scathaCooldownColor:c.scathaReadyColor));}if(c.scathaHudShowCurrent&&s.current()!=null)out.add(new Row("","Current",s.current()==AquilaScathaMining.WormType.SCATHA?"Scatha":"Worm",s.current()==AquilaScathaMining.WormType.SCATHA?c.scathaScathaColor:c.scathaWormColor));if(c.scathaHudShowSession)out.add(new Row("","Session",s.sessionSpawns()+" spawns",0xFFFFFFFF));if(c.scathaHudShowTotals)out.add(new Row("","Total",s.totalSpawns()+" ("+s.totalScathas()+" Scatha)",0xFFFFFFFF));if(c.scathaHudShowRatio){double ratio=s.totalSpawns()==0?0:100.0*s.totalScathas()/s.totalSpawns();out.add(new Row("","Scatha rate",String.format(Locale.ROOT,"%.1f%%",ratio),c.scathaScathaColor));}if(c.scathaHudShowDryStreak)out.add(new Row("","Dry streak",Long.toString(s.dryStreak()),0xFFFFFF55));if(c.scathaHudShowPetDrops)out.add(new Row("","Pet drops",Long.toString(s.petDrops()),c.scathaPetColor));if(c.scathaHudShowUptime&&s.sessionMillis()>0)out.add(new Row("","Session time",time(s.sessionMillis()),0xFFAAAAAA));return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Cooldown","12.4s",0xFFFF5555),new Row("","Current","Scatha",0xFFFFAA00),new Row("","Session","18 spawns",0xFFFFFFFF),new Row("","Dry streak","47",0xFFFFFF55),new Row("","Pet drops","2",0xFFFFAA00));}
    private static String time(long millis){long seconds=millis/1000;return seconds>=3600?seconds/3600+"h "+seconds/60%60+"m":seconds/60+"m "+seconds%60+"s";}
}
