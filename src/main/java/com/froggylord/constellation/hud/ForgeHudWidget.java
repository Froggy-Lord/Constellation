package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaForgeHelper;
import java.util.ArrayList;import java.util.List;import java.util.function.BooleanSupplier;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/tabhud/widget/ForgeWidget.java
public final class ForgeHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public ForgeHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"aquila-forges";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition p){pos=p;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean v){enabled=v;}@Override public boolean visibleNow(){return isEnabled()&&!rows().isEmpty();}@Override public String editorLabel(){return"Forge Slots";}@Override protected String title(){return"Forges";}
    @Override protected List<Row> rows(){var cfg=AquilaForgeHelper.config();var out=new ArrayList<Row>();for(var slot:AquilaForgeHelper.slots()){if(slot.state()==AquilaForgeHelper.State.EMPTY&&!cfg.forgeShowEmpty||slot.state()==AquilaForgeHelper.State.LOCKED&&!cfg.forgeShowLocked||slot.state()==AquilaForgeHelper.State.READY&&!cfg.forgeShowReady)continue;String name=(cfg.forgeShowSlotNumber?slot.slot()+") ":"")+slot.item();String value=switch(slot.state()){case ACTIVE->cfg.forgeShowRemaining?AquilaForgeHelper.format(slot.remaining()):"Active";case READY->"Ready";case EMPTY->"Empty";case LOCKED->"Locked";};int color=switch(slot.state()){case ACTIVE->cfg.forgeActiveColor;case READY->cfg.forgeReadyColor;case EMPTY->cfg.forgeEmptyColor;case LOCKED->cfg.forgeLockedColor;};out.add(new Row("",name,value,color));}return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","1) Refined Mithril","2h 14m",0xFFFFFF55),new Row("","2) Mithril Plate","Ready",0xFF55FF55));}
}
