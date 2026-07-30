package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AurigaUnclaimedEggs;

import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/event/hoppity/HoppityEggDisplayManager.kt
public final class UnclaimedEggsHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition position;private boolean enabled=true;
    public UnclaimedEggsHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"auriga-hoppity-unclaimed-eggs";}
    @Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition value){position=value;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean value){enabled=value;}
    @Override public boolean visibleNow(){return isEnabled()&&AurigaUnclaimedEggs.visible();}
    @Override public String editorLabel(){return"Unclaimed Hoppity Eggs";}
    @Override protected String title(){return"Unclaimed Eggs";}
    @Override protected List<Row> rows(){return AurigaUnclaimedEggs.rows().stream().map(row->new Row("",row.label(),row.value(),row.color())).toList();}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Breakfast","Ready",0xFF55FF55),new Row("","Lunch","Claimed · 8h 12m",0xFFAAAAAA),new Row("","Dinner","2h 47m",0xFF55FF55),new Row("","Event ends","1d 3h",0xFFFFFFFF));}
}
