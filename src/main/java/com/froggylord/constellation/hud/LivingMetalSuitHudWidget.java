package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AndromedaLivingCave;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/rift/area/livingcave/LivingMetalSuitProgress.kt
public final class LivingMetalSuitHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public LivingMetalSuitHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"andromeda-living-metal-suit";}@Override public HudPosition position(){return position;}@Override public void setPosition(HudPosition value){position=value;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}
    @Override public boolean visibleNow(){return isEnabled()&&AndromedaLivingCave.suitVisible();}
    @Override public String editorLabel(){return"Living Metal Suit";}@Override protected String title(){return"Living Metal Suit";}
    @Override protected List<Row> rows(){return AndromedaLivingCave.suitRows().stream().map(row->new Row("",row.label(),row.value(),row.color())).toList();}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Total","62.5%",0xFF55FF55),new Row("","Helmet","||||||||||---------- 50%",0xFF55FFFF),new Row("","Chestplate","|||||||||||||||----- 75%",0xFF55FFFF));}
}
