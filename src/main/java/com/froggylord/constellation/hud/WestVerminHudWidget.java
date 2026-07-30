package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AndromedaWestVillage;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/rift/area/westvillage/VerminTracker.kt
public final class WestVerminHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public WestVerminHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"andromeda-west-vermin";}@Override public HudPosition position(){return position;}@Override public void setPosition(HudPosition value){position=value;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}
    @Override public boolean visibleNow(){return isEnabled()&&AndromedaWestVillage.verminHudVisible();}
    @Override public String editorLabel(){return"Vermin Tracker";}@Override protected String title(){return"Vermin";}
    @Override protected List<Row> rows(){return List.of(new Row("","Flies",AndromedaWestVillage.flies(),0xFF55FF55),new Row("","Spiders",AndromedaWestVillage.spiders(),0xFF55FF55),new Row("","Silverfish",AndromedaWestVillage.silverfish(),0xFF55FF55));}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Flies","12",0xFF55FF55),new Row("","Spiders","8",0xFF55FF55),new Row("","Silverfish","5",0xFF55FF55));}
}
