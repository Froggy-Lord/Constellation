package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.PhoenixCollectionTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyblockCollectionTracker (LGPL-2.1): gui/overlays/CollectionOverlay.java
public final class CollectionTrackerHudWidget extends ThemedHudWidget {
    private HudPosition pos;private boolean enabled=true;private final BooleanSupplier gate;
    public CollectionTrackerHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"phoenix-collection-tracker";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition value){pos=value;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}@Override public boolean visibleNow(){return isEnabled()&&PhoenixCollectionTracker.visible();}@Override public String editorLabel(){return"Collection Tracker";}@Override protected String title(){var data=PhoenixCollectionTracker.state();return data==null?"Collection Tracker":data.name;}
    @Override protected List<Row> rows(){var data=PhoenixCollectionTracker.state();var cfg=PhoenixCollectionTracker.config();if(data==null)return List.of();List<Row> rows=new ArrayList<>();if(cfg.collectionTrackerShowTotal)rows.add(new Row("","Total",PhoenixCollectionTracker.format(data.amount),cfg.collectionTrackerValueColor));if(cfg.collectionTrackerShowSession)rows.add(new Row("","Session",PhoenixCollectionTracker.format(PhoenixCollectionTracker.gained()),cfg.collectionTrackerProgressColor));if(cfg.collectionTrackerShowRate)rows.add(new Row("","Rate",PhoenixCollectionTracker.perHour()>0?PhoenixCollectionTracker.format(PhoenixCollectionTracker.perHour())+"/h":"Calculating",cfg.collectionTrackerValueColor));long goal=PhoenixCollectionTracker.goal();if(cfg.collectionTrackerShowGoal&&goal>0)rows.add(new Row("","Goal",PhoenixCollectionTracker.format(Math.max(0,goal-data.amount))+" left",cfg.collectionTrackerProgressColor));if(cfg.collectionTrackerShowEta&&goal>data.amount&&!PhoenixCollectionTracker.eta().isBlank())rows.add(new Row("","ETA",PhoenixCollectionTracker.eta(),cfg.collectionTrackerValueColor));if(cfg.collectionTrackerShowLastGain&&PhoenixCollectionTracker.lastGain()>0)rows.add(new Row("","Last sync","+"+PhoenixCollectionTracker.format(PhoenixCollectionTracker.lastGain()),cfg.collectionTrackerProgressColor));if(cfg.collectionTrackerShowFreshness)rows.add(new Row("","Updated",freshness(),PhoenixCollectionTracker.age()>300?0xFFFF5555:0xFFAAAAAA));return rows;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Total","12.45M"),new Row("","Session","184.2k",0xFF55FF55),new Row("","Rate","632.1k/h"),new Row("","Goal","2.55M left"));}
    private static String freshness(){long age=PhoenixCollectionTracker.age();return age<5?"just now":PhoenixCollectionTracker.duration(age)+" ago";}
}
