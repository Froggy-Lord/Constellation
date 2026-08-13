package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HerculesGardenTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/farming/GardenCropSpeed.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/tracker/GardenBpsTracker.kt
public final class GardenRateHudWidget extends ThemedHudWidget {
    private final BooleanSupplier configEnabled;private HudPosition position;private boolean enabled=true;
    public GardenRateHudWidget(HudPosition position,BooleanSupplier configEnabled){this.position=position;this.configEnabled=configEnabled;}
    @Override public String id(){return "garden-rates";}@Override public HudPosition position(){return position;}@Override public void setPosition(HudPosition p){position=p;}
    @Override public boolean isEnabled(){return enabled&&configEnabled.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}
    @Override public boolean visibleNow(){return isEnabled()&&HerculesGardenTracker.rates()!=null;}@Override public String editorLabel(){return "Garden Crop Rates";}
    @Override protected String title(){return "Crop Rates";}
    @Override protected List<Row> rows(){var r=HerculesGardenTracker.rates();if(r==null)return List.of();var cfg=HerculesGardenTracker.config();var out=new ArrayList<Row>();
        out.add(new Row("","Crop",r.crop()));if(cfg.farmingShowInstantBps)out.add(new Row("","Recent BPS",decimal(r.instantBps())));
        if(cfg.farmingShowAverageBps)out.add(new Row("","Session BPS",decimal(r.averageBps())));if(cfg.farmingShowSessionBlocks)out.add(new Row("","Blocks",String.format(Locale.ROOT,"%,d",r.blocks())));
        if(cfg.farmingShowCropsPerMinute)out.add(new Row("","Blocks/min",String.format(Locale.ROOT,"%,.0f",r.instantBps()*60)));
        if(cfg.farmingShowSessionTime)out.add(new Row("","Session",time(r.elapsedMillis())));return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Crop","Sugar Cane"),new Row("","Recent BPS","19.72"),new Row("","Blocks","12,480"),new Row("","Session","10:33"));}
    private static String decimal(double v){return String.format(Locale.ROOT,"%."+Math.clamp(HerculesGardenTracker.config().farmingBpsPrecision,0,6)+"f",v);}
    private static String time(long ms){long s=ms/1000;return String.format(Locale.ROOT,"%d:%02d",s/60,s%60);}
}
