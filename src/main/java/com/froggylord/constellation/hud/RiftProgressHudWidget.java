package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AndromedaEverywhere;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/rift/everywhere/CruxTalismanDisplay.kt, PunchcardHighlight.kt
public final class RiftProgressHudWidget extends ThemedHudWidget {
    private final String mode;private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public RiftProgressHudWidget(String mode,HudPosition position,BooleanSupplier gate){this.mode=mode;this.position=position;this.gate=gate;}
    @Override public String id(){return"andromeda-rift-"+mode;}@Override public HudPosition position(){return position;}@Override public void setPosition(HudPosition value){position=value;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}
    @Override public boolean visibleNow(){return isEnabled()&&!rows().isEmpty();}@Override public String editorLabel(){return mode.equals("crux")?"Crux Talisman":"Punchcard";}
    @Override protected String title(){return editorLabel();}@Override protected List<Row> rows(){return AndromedaEverywhere.hudRows(mode).stream().map(row->new Row("",row.label(),row.value(),row.color())).toList();}
    @Override protected List<Row> previewRows(){return mode.equals("crux")?List.of(new Row("","Progress","62.5%",0xFF55FF55),new Row("","Shy III","88/100",0xFFFFFF55)):List.of(new Row("","Punched","12/20",0xFFFF55FF));}
}
