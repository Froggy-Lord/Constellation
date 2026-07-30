package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AurigaStrayTimer;

import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/inventory/chocolatefactory/stray/CFStrayTimer.kt
public final class StrayTimerHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition position;private boolean enabled=true;
    public StrayTimerHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"auriga-chocolate-factory-stray-timer";}
    @Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition value){position=value;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean value){enabled=value;}
    @Override public boolean visibleNow(){return isEnabled()&&AurigaStrayTimer.visible();}
    @Override public String editorLabel(){return"Stray Timer";}
    @Override protected String title(){return"Stray Timer";}
    @Override protected List<Row> rows(){return List.of(new Row("","Remaining",AurigaStrayTimer.formatted(),AurigaStrayTimer.color()));}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Remaining","12.45s",0xFF55FFFF));}
}
