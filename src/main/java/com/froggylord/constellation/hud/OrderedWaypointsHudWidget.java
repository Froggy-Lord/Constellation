package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaOrderedWaypoints;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/mining/OrderedWaypoints.kt
public final class OrderedWaypointsHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public OrderedWaypointsHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"aquila-ordered-waypoints";}
    @Override public HudPosition position(){return pos;}
    @Override public void setPosition(HudPosition value){pos=value;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean value){enabled=value;}
    @Override public boolean visibleNow(){return isEnabled()&&AquilaOrderedWaypoints.state()!=null;}
    @Override public String editorLabel(){return"Ordered Mining Route";}
    @Override protected String title(){return"Ordered Mining Route";}
    @Override protected List<Row> rows(){var state=AquilaOrderedWaypoints.state();var cfg=AquilaOrderedWaypoints.config();if(state==null||cfg==null)return List.of();List<Row> rows=new ArrayList<>();if(cfg.orderedWaypointsHudShowRoute)rows.add(new Row("","Route",state.route()+(state.dirty()?" (unsaved)":""),state.dirty()?0xFFFFFF55:0xFF55FF55));if(cfg.orderedWaypointsHudShowProgress)rows.add(new Row("","Progress",state.current()+"/"+state.total(),cfg.orderedWaypointsCurrentColor));if(cfg.orderedWaypointsHudShowCurrent)rows.add(new Row("","Current",label(state.current(),state.waypoint()),cfg.orderedWaypointsCurrentColor));if(cfg.orderedWaypointsHudShowNext)rows.add(new Row("","Next",label(state.current()%state.total()+1,state.next()),cfg.orderedWaypointsNextColor));if(cfg.orderedWaypointsHudShowDistance)rows.add(new Row("","Distance",String.format(java.util.Locale.ROOT,"%.1fm",state.distance()),cfg.orderedWaypointsCurrentColor));if(state.setup())rows.add(new Row("","Mode","Setup",cfg.orderedWaypointsSetupColor));return rows;}
    private static String label(int number,AquilaOrderedWaypoints.Waypoint waypoint){return waypoint.label().isBlank()?Integer.toString(number):number+" "+waypoint.label();}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Route","JASP_1",0xFF55FF55),new Row("","Progress","4/18",0xFF55FF55),new Row("","Current","4 Ruby vein",0xFF55FF55),new Row("","Distance","7.2m",0xFF55FF55));}
}
