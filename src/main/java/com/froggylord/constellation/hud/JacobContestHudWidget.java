package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HerculesGardenTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/contest/FarmingContestApi.kt
public final class JacobContestHudWidget extends ThemedHudWidget {
    private final BooleanSupplier configEnabled;private HudPosition position;private boolean enabled=true;
    public JacobContestHudWidget(HudPosition position,BooleanSupplier configEnabled){this.position=position;this.configEnabled=configEnabled;}
    @Override public String id(){return "garden-jacob";}@Override public HudPosition position(){return position;}@Override public void setPosition(HudPosition p){position=p;}
    @Override public boolean isEnabled(){return enabled&&configEnabled.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}
    @Override public boolean visibleNow(){return isEnabled()&&HerculesGardenTracker.contest()!=null;}@Override public String editorLabel(){return "Jacob Contest";}
    @Override protected String title(){return "Jacob's Contest";}
    @Override protected List<Row> rows(){var c=HerculesGardenTracker.contest();if(c==null)return List.of();var cfg=HerculesGardenTracker.config();var out=new ArrayList<Row>();
        if(cfg.jacobShowCrop)out.add(new Row("","Crop",c.crop()));if(cfg.jacobShowCollected)out.add(new Row("","Collected",String.format(Locale.ROOT,"%,d",c.collected())));
        if(cfg.jacobShowRate)out.add(new Row("","Rate",String.format(Locale.ROOT,"%.1f/s",c.cropsPerSecond())));if(cfg.jacobShowElapsed)out.add(new Row("","Elapsed",time(c.elapsedMillis())));
        if(cfg.jacobShowProjectedTotal)out.add(new Row("","Projected",String.format(Locale.ROOT,"%,d",c.projectedTotal()),0xFFFFAA00));return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Crop","Wheat"),new Row("","Collected","182,440"),new Row("","Rate","165.2/s"),new Row("","Projected","198,240",0xFFFFAA00));}
    private static String time(long ms){long s=ms/1000;return String.format(Locale.ROOT,"%d:%02d",s/60,s%60);}
}
