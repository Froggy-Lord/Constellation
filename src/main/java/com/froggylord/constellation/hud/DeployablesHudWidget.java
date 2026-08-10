package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HydraDeployables;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Feesh (Apache-2.0): features/overlays/DeployablesTimer.kt
public final class DeployablesHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public DeployablesHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"hydra-deployables";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition p){pos=p;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}@Override public boolean visibleNow(){return isEnabled()&&HydraDeployables.visible();}@Override public String editorLabel(){return"Deployables";}@Override protected String title(){return"Deployables";}
    @Override protected List<Row> rows(){var out=new ArrayList<Row>();var cfg=HydraDeployables.config();for(var state:HydraDeployables.hudStates()){String label=cfg.deployableHudShowType?state.name():"Remaining";String value=cfg.deployableHudShowSeconds?state.seconds()+"s":HydraDeployables.time(state.seconds());out.add(new Row("",label,value,state.warning()?cfg.deployableWarningColor:state.color()));}return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Totem of Corruption","1m 02s",0xFFAA00AA),new Row("","SOS Flare","2m 58s",0xFFFFAA00),new Row("","Umberella","30s",0xFF5555FF));}
}
