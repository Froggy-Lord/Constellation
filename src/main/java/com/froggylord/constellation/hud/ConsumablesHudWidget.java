package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HydraConsumables;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Feesh (Apache-2.0): features/overlays/ConsumablesTimer.kt
public final class ConsumablesHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public ConsumablesHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"hydra-consumables";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition p){pos=p;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}@Override public boolean visibleNow(){return isEnabled()&&HydraConsumables.visible();}@Override public String editorLabel(){return"Fishing Consumables";}@Override protected String title(){return"Consumables";}
    @Override protected List<Row> rows(){var out=new ArrayList<Row>();var cfg=HydraConsumables.config();for(var state:HydraConsumables.states()){String value=!state.active()?"Inactive":state.soon()?"Soon":cfg.consumableHudShowSeconds?state.seconds()+"s":HydraConsumables.time(state.seconds());out.add(new Row("",state.name(),value,state.warning()?cfg.consumableWarningColor:state.color()));}return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Moby-Duck","1h 00m 00s",0xFFAA00AA),new Row("","Blizzard","9m 42s",0xFF55FFFF),new Row("","Prime Lushlilac Bonbon","17h 58m",0xFFFF55FF));}
}
