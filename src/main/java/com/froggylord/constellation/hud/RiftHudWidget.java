package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AndromedaRiftCore;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class RiftHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public RiftHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"andromeda-rift-core";}@Override public HudPosition position(){return position;}@Override public void setPosition(HudPosition position){this.position=position;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean enabled){this.enabled=enabled;}
    @Override public boolean visibleNow(){return isEnabled()&&AndromedaRiftCore.hudVisible();}
    @Override public String editorLabel(){return"Rift";}@Override protected String title(){return"Rift";}
    @Override protected List<Row> rows(){List<Row> rows=new ArrayList<>();add(rows,"Time",AndromedaRiftCore.hudTime(),AndromedaRiftCore.hudTimeColor());add(rows,"Motes",AndromedaRiftCore.hudMotes(),0xFF55FFFF);add(rows,"Souls",AndromedaRiftCore.hudSouls(),0xFF55FF55);add(rows,"Effigies",AndromedaRiftCore.hudEffigies(),0xFFFF5555);add(rows,"Area",AndromedaRiftCore.hudArea(),0xFFFFFFFF);return rows;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Time","12:34",0xFF55FFFF),new Row("","Motes","12,345 (+250)",0xFF55FFFF),new Row("","Souls","30/52",0xFF55FF55));}
    private static void add(List<Row> rows,String label,String value,int color){if(value!=null)rows.add(new Row("",label,value,color));}
}
