package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AndromedaMountaintop;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/rift/area/mountaintop/SunGeckoHelper.kt, TimiteTracker.kt, UbikReminder.kt
public final class MountaintopHudWidget extends ThemedHudWidget {
    private final String mode;private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public MountaintopHudWidget(String mode,HudPosition position,BooleanSupplier gate){this.mode=mode;this.position=position;this.gate=gate;}
    @Override public String id(){return"andromeda-mountaintop-"+mode;}@Override public HudPosition position(){return position;}@Override public void setPosition(HudPosition value){position=value;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}
    @Override public boolean visibleNow(){return isEnabled()&&!rows().isEmpty();}
    @Override public String editorLabel(){return switch(mode){case"sun"->"Sun Gecko";case"timite"->"Timite Tracker";default->"Ubik's Cube";};}
    @Override protected String title(){return editorLabel();}
    @Override protected List<Row> rows(){return AndromedaMountaintop.hudRows(mode).stream().map(row->new Row("",row.label(),row.value(),row.color())).toList();}
    @Override protected List<Row> previewRows(){return switch(mode){case"sun"->List.of(new Row("","Health","250/250",0xFF55FF55),new Row("","Combo","4/10 x2",0xFFFFFF55));case"timite"->List.of(new Row("","Timite","32",0xFF55FFFF),new Row("","Profit","12,800 Motes",0xFFFF55FF));default->List.of(new Row("","Ready in","1:24:15",0xFFFFFF55));};}
}
