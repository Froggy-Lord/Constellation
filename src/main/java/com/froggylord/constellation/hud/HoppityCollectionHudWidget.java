package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AurigaHoppityCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/event/hoppity/HoppityCollectionStats.kt
public final class HoppityCollectionHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate; private HudPosition position; private boolean enabled=true;
    public HoppityCollectionHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"auriga-hoppity-collection";}
    @Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition value){position=value;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean value){enabled=value;}
    @Override public boolean visibleNow(){return isEnabled()&&AurigaHoppityCollection.visible();}
    @Override public String editorLabel(){return"Hoppity Collection";}
    @Override protected String title(){return"Hoppity Collection";}
    @Override protected List<Row> rows(){
        var state=AurigaHoppityCollection.state();var cfg=AurigaHoppityCollection.config();ArrayList<Row> out=new ArrayList<>();
        if(cfg.hoppityCollectionShowRarities)for(var entry:state.rarities().entrySet()){if(entry.getKey()==AurigaHoppityCollection.Rarity.UNKNOWN)continue;var value=entry.getValue();String text=value.unique()+"/"+value.total();if(cfg.hoppityCollectionShowDuplicates)text+=" +"+value.duplicates();out.add(new Row("",entry.getKey().label,text,entry.getKey().color));}
        if(cfg.hoppityCollectionShowTotal)out.add(new Row("","Total",state.unique()+"/"+state.total()+(cfg.hoppityCollectionShowDuplicates?" +"+state.duplicates():""),state.complete()?0xFF55FF55:0xFFFFAA00));
        if(cfg.hoppityCollectionShowProgress)out.add(new Row("","Hypixel",state.progress()+"/"+state.progressTotal(),state.progress()>=state.progressTotal()&&state.progressTotal()>0?0xFF55FF55:0xFFFFFFFF));
        if(cfg.hoppityCollectionShowPages)out.add(new Row("","Pages",state.pagesSeen()+"/"+state.maxPages(),state.complete()?0xFF55FF55:0xFFFF5555));
        return out;
    }
    @Override protected List<Row> previewRows(){return List.of(new Row("","Common","210/240 +18",0xFFFFFFFF),new Row("","Legendary","31/42 +4",0xFFFFAA00),new Row("","Total","395/508 +76",0xFFFFAA00),new Row("","Pages","12/17",0xFFFF5555));}
}
