package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaCorpseHelper;
import java.util.ArrayList;import java.util.List;import java.util.function.BooleanSupplier;

// ported from SkyOcean (MIT): features/mining/mineshaft/CorpseKeyAnnouncement.kt
public final class CorpseKeyHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public CorpseKeyHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}@Override public String id(){return"aquila-corpse-keys";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition p){pos=p;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean v){enabled=v;}@Override public boolean visibleNow(){return isEnabled()&&!AquilaCorpseHelper.keyRows().isEmpty();}@Override public String editorLabel(){return"Corpse Keys";}@Override protected String title(){return"Corpse Keys";}
    @Override protected List<Row> rows(){var cfg=AquilaCorpseHelper.config();var out=new ArrayList<Row>();for(var row:AquilaCorpseHelper.keyRows()){String value="";if(cfg.corpseKeyShowRequired)value+="need "+row.required();if(cfg.corpseKeyShowAvailable)value+=(value.isEmpty()?"":"  ")+"known "+row.known()+(row.complete()?"":"+");int color=cfg.corpseKeyWarnMissing&&row.complete()&&row.known()<row.required()?0xFFFF5555:row.known()>=row.required()?0xFF55FF55:0xFFFFFF55;out.add(new Row("",row.type().display()+" Keys",value,color));}return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Umber Keys","need 2  have 3",0xFF55FF55),new Row("","Vanguard Keys","need 1  have 0",0xFFFF5555));}
}
