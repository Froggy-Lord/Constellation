package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AurigaHoppityEventSummary;

import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/event/hoppity/summary/HoppityLiveDisplay.kt
public final class HoppityEventHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition position;private boolean enabled=true;
    public HoppityEventHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"auriga-hoppity-event-summary";}
    @Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition value){position=value;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean value){enabled=value;}
    @Override public boolean visibleNow(){return isEnabled()&&AurigaHoppityEventSummary.visible();}
    @Override public String editorLabel(){return"Hoppity Event Summary";}
    @Override protected String title(){return"Hoppity's Hunt #"+(AurigaHoppityEventSummary.selectedYear()-345);}
    @Override protected List<Row> rows(){return AurigaHoppityEventSummary.rows().stream().map(row->new Row("",row.label(),row.value(),row.color())).toList();}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Meal eggs","86",0xFF55FFFF),new Row("","Unique rabbits","24",0xFF55FF55),new Row("","Duplicates","62",0xFFAAAAAA),new Row("","Dupe chocolate","1.42B",0xFFFFAA00));}
}
