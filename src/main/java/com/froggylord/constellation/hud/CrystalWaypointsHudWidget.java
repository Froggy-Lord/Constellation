package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaCrystalWaypoints;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/CrystalsHudWidget.java
public final class CrystalWaypointsHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public CrystalWaypointsHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"aquila-crystal-waypoints";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition p){pos=p;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}@Override public boolean visibleNow(){return isEnabled()&&AquilaCrystalWaypoints.visible();}@Override public String editorLabel(){return"Crystal Waypoints";}@Override protected String title(){return"Crystal Waypoints";}
    @Override protected List<Row> rows(){var s=AquilaCrystalWaypoints.state();if(s==null)return List.of();var c=AquilaCrystalWaypoints.config();var out=new ArrayList<Row>();if(c.crystalWaypointsHudShowSolver){String value=switch(s.solver()){case READY->"Ready";case FIRST_TRAIL->"First "+s.firstParticles()+"/"+s.required();case MOVE_FOR_SECOND->"Move and use again";case SECOND_TRAIL->"Second "+s.secondParticles()+"/"+s.required();case SOLVED->"Solved";};out.add(new Row("","Compass",value,c.crystalWaypointUnknownColor));}if(c.crystalWaypointsHudShowCount)out.add(new Row("","Waypoints",Integer.toString(s.waypoints().size()),0xFF55FFFF));if(c.crystalWaypointsHudShowNearest&&s.nearest()!=null)out.add(new Row("",s.nearest().type().display(),Math.round(s.nearestDistance())+"m",color(s.nearest().type())));return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Compass","Move and use again",0xFFFFFFFF),new Row("","Waypoints","4",0xFF55FFFF),new Row("","Mines of Divan","142m",0xFF55FF55));}
    private static int color(AquilaCrystalWaypoints.Type type){var c=AquilaCrystalWaypoints.config();return switch(type){case UNKNOWN->c.crystalWaypointUnknownColor;case JUNGLE_TEMPLE->c.crystalWaypointJungleColor;case MINES_OF_DIVAN->c.crystalWaypointDivanColor;case GOBLIN_QUEENS_DEN->c.crystalWaypointGoblinColor;case LOST_PRECURSOR_CITY->c.crystalWaypointCityColor;case KHAZAD_DUM->c.crystalWaypointKhazadColor;case FAIRY_GROTTO->c.crystalWaypointGrottoColor;case DRAGONS_LAIR->c.crystalWaypointDragonColor;case CORLEONE->c.crystalWaypointCorleoneColor;case KING_YOLKAR->c.crystalWaypointYolkarColor;case ODAWA->c.crystalWaypointOdawaColor;case KEY_GUARDIAN->c.crystalWaypointKeyGuardianColor;case XALX->c.crystalWaypointXalxColor;};}
}
