package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaMiningProgress;
import java.util.ArrayList;import java.util.List;import java.util.function.BooleanSupplier;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/tabhud/widget/CommsWidget.java
public final class MiningCommissionHudWidget extends ThemedHudWidget {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public MiningCommissionHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"aquila-commissions";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition p){pos=p;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean v){enabled=v;}@Override public boolean visibleNow(){return isEnabled()&&!AquilaMiningProgress.commissions().isEmpty();}@Override public String editorLabel(){return"Mining Commissions";}@Override protected String title(){return"Commissions";}
    @Override protected List<Row> rows(){var cfg=AquilaMiningProgress.config();var out=new ArrayList<Row>();for(var c:AquilaMiningProgress.commissions()){if(c.done()&&!cfg.miningCommissionShowDone)continue;if(!c.done()&&!cfg.miningCommissionShowPercent)continue;out.add(new Row("",c.name(),c.progress(),c.done()?cfg.miningGoodColor:0xFF55FFFF));if(!cfg.miningCommissionShowAll)break;}return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Mithril Miner","72%",0xFF55FFFF),new Row("","Goblin Slayer","DONE",0xFF55FF55));}
}
