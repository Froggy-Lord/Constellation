package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HerculesPests;
import java.util.*;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/pests/PestSpawnTimer.kt
public final class GardenPestTimerHudWidget extends ThemedHudWidget {
    private final BooleanSupplier configEnabled;private HudPosition position;private boolean enabled=true;
    public GardenPestTimerHudWidget(HudPosition p,BooleanSupplier e){position=p;configEnabled=e;}@Override public String id(){return"garden-pest-timer";}@Override public HudPosition position(){return position;}@Override public void setPosition(HudPosition p){position=p;}@Override public boolean isEnabled(){return enabled&&configEnabled.getAsBoolean();}@Override public void setEnabled(boolean e){enabled=e;}@Override public boolean visibleNow(){return isEnabled()&&HerculesPests.showTimer();}@Override public String editorLabel(){return"Pest Spawn Timer";}@Override protected String title(){return"Pest Timer";}
    @Override protected List<Row> rows(){var s=HerculesPests.state();if(s==null)return List.of();var c=HerculesPests.config();List<Row> out=new ArrayList<>();if(c.pestTimerShowLastSpawn)out.add(new Row("","Last spawn",s.lastSpawnAt()==0?"None":time(System.currentTimeMillis()-s.lastSpawnAt())+" ago"));if(c.pestTimerShowCooldown)out.add(new Row("","Cooldown",s.cooldown()));if(c.pestTimerShowAverage&&s.averageSpawnMillis()>0)out.add(new Row("","Average",time(s.averageSpawnMillis())));return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Last spawn","1:02 ago"),new Row("","Cooldown","33s"),new Row("","Average","2:11"));}private static String time(long ms){long s=Math.max(0,ms/1000);return String.format(Locale.ROOT,"%d:%02d",s/60,s%60);}
}
