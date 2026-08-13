package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HerculesVisitorHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/visitor/GardenVisitorShoppingList.kt
public final class GardenVisitorHudWidget extends ThemedHudWidget {
    private final BooleanSupplier configEnabled;
    private HudPosition position;
    private boolean enabled = true;

    public GardenVisitorHudWidget(HudPosition position, BooleanSupplier configEnabled) { this.position=position; this.configEnabled=configEnabled; }
    @Override public String id(){return "garden-visitors";}
    @Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition position){this.position=position;}
    @Override public boolean isEnabled(){return enabled&&configEnabled.getAsBoolean();}
    @Override public void setEnabled(boolean enabled){this.enabled=enabled;}
    @Override public boolean visibleNow(){return isEnabled()&&!HerculesVisitorHelper.displayRows().isEmpty();}
    @Override public String editorLabel(){return "Visitor Shopping List";}
    @Override protected String title(){return "Visitor Shopping List";}
    @Override protected List<Row> rows(){List<Row> rows=new ArrayList<>();for(var row:HerculesVisitorHelper.displayRows())rows.add(new Row("",row.label(),row.value(),row.color()));return rows;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Enchanted Sugar Cane","x9,639  15.2M  §eI:0 S:?"),new Row("","Total","15.2M",0xFFFFAA00),new Row("","Spaceman","§dSpace Helmet",0xFFFF55FF));}
}
