package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HerculesPests;
import java.util.*;
import java.util.function.BooleanSupplier;

// ported from Devonian (GPL-3.0): features/garden/PestKillsTracker.kt
// ported from Devonian (GPL-3.0): features/garden/PestDropTracker.kt
public final class GardenPestStatsHudWidget extends ThemedHudWidget {
    private final BooleanSupplier configEnabled;private HudPosition position;private boolean enabled=true;
    public GardenPestStatsHudWidget(HudPosition p,BooleanSupplier e){position=p;configEnabled=e;}@Override public String id(){return"garden-pest-stats";}@Override public HudPosition position(){return position;}@Override public void setPosition(HudPosition p){position=p;}@Override public boolean isEnabled(){return enabled&&configEnabled.getAsBoolean();}@Override public void setEnabled(boolean e){enabled=e;}@Override public boolean visibleNow(){return isEnabled()&&HerculesPests.stats()!=null;}@Override public String editorLabel(){return"Pest Statistics";}@Override protected String title(){return"Pest Statistics";}
    @Override protected List<Row> rows(){var s=HerculesPests.stats();if(s==null)return List.of();var c=HerculesPests.config();List<Row> out=new ArrayList<>();if(c.pestStatsShowKills)out.add(new Row("","Kills",c.pestStatsShowSession?s.sessionKills()+" session / "+s.lifetimeKills()+" total":Long.toString(s.lifetimeKills())));if(c.pestStatsShowDrops)out.add(new Row("","Drops",c.pestStatsShowSession?s.sessionDrops()+" session / "+s.lifetimeDrops()+" total":Long.toString(s.lifetimeDrops())));if(c.pestStatsShowProfit)out.add(new Row("","Profit",c.pestStatsShowSession?coins(s.sessionProfit())+" session / "+coins(s.lifetimeProfit())+" total":coins(s.lifetimeProfit())));if(c.pestStatsShowProfitPerHour&&s.elapsedMillis()>0)out.add(new Row("","Profit/hour",coins(s.sessionProfit()*3_600_000d/s.elapsedMillis())));if(!s.lastPest().isBlank())out.add(new Row("","Last",s.lastPest()+" / "+s.lastDrop()));return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Kills","12 session / 94 total"),new Row("","Drops","642 session / 4,821 total"),new Row("","Profit","128.4k"));}private static String coins(double v){if(v>=1_000_000)return String.format(Locale.ROOT,"%.2fm",v/1_000_000);if(v>=1000)return String.format(Locale.ROOT,"%.1fk",v/1000);return String.format(Locale.ROOT,"%.0f",v);}
}
