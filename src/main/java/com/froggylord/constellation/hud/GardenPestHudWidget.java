package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HerculesPests;
import java.util.*;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/pests/PestFinder.kt
// ported from Devonian (GPL-3.0): features/garden/PestsDisplay.kt
public final class GardenPestHudWidget extends ThemedHudWidget {
    private final BooleanSupplier configEnabled;private HudPosition position;private boolean enabled=true;
    public GardenPestHudWidget(HudPosition p,BooleanSupplier e){position=p;configEnabled=e;}
    @Override public String id(){return"garden-pests";}@Override public HudPosition position(){return position;}@Override public void setPosition(HudPosition p){position=p;}@Override public boolean isEnabled(){return enabled&&configEnabled.getAsBoolean();}@Override public void setEnabled(boolean e){enabled=e;}@Override public boolean visibleNow(){return isEnabled()&&HerculesPests.state()!=null;}@Override public String editorLabel(){return"Garden Pests";}@Override protected String title(){return"Pest Finder";}
    @Override protected List<Row> rows(){var s=HerculesPests.state();if(s==null)return List.of();List<Row> out=new ArrayList<>();out.add(new Row("","Alive",s.alive()+"/"+HerculesPests.config().pestMaxCount));for(int id:s.plots()){int n=s.plotCounts().getOrDefault(id,1);out.add(new Row("","Plot "+id,n+(n==1?" pest":" pests")));}if(s.plots().isEmpty())out.add(new Row("","Plots","None"));return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Alive","3/8"),new Row("","Plot 4","2 pests"),new Row("","Plot 12","1 pest"));}
}
