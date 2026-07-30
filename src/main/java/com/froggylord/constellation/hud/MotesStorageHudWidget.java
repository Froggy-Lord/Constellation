package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AndromedaMotes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/rift/everywhere/motes/ShowMotesNpcSellPrice.kt
public final class MotesStorageHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public MotesStorageHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"andromeda-motes-storage";}@Override public HudPosition position(){return position;}@Override public void setPosition(HudPosition position){this.position=position;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean enabled){this.enabled=enabled;}
    @Override public boolean visibleNow(){return isEnabled()&&AndromedaMotes.storageVisible();}@Override public String editorLabel(){return"Rift Storage Value";}@Override protected String title(){return"Rift Storage";}
    @Override protected List<Row> rows(){List<Row> out=new ArrayList<>();String value=AndromedaMotes.hudStorage(),items=AndromedaMotes.hudStorageItems();if(value!=null)out.add(new Row("","Motes",value,0xFFFF55FF));if(items!=null)out.add(new Row("","Contents",items,0xFFFFFFFF));return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Motes","128,450",0xFFFF55FF),new Row("","Contents","64 items · 7 stacks",0xFFFFFFFF));}
}
