package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HerculesGardenTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/GardenOptimalSpeed.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/GardenYawAndPitch.kt
public final class GardenControlHudWidget extends ThemedHudWidget {
    private final BooleanSupplier configEnabled;
    private HudPosition position;
    private boolean enabled = true;
    public GardenControlHudWidget(HudPosition position, BooleanSupplier configEnabled){this.position=position;this.configEnabled=configEnabled;}
    @Override public String id(){return "garden-control";}
    @Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition position){this.position=position;}
    @Override public boolean isEnabled(){return enabled&&configEnabled.getAsBoolean();}
    @Override public void setEnabled(boolean enabled){this.enabled=enabled;}
    @Override public boolean visibleNow(){return isEnabled()&&HerculesGardenTracker.control()!=null;}
    @Override public String editorLabel(){return "Garden Farming Control";}
    @Override protected String title(){return "Farming Control";}
    @Override protected List<Row> rows(){
        var c=HerculesGardenTracker.control();if(c==null)return List.of();var cfg=HerculesGardenTracker.config();var out=new ArrayList<Row>();
        if(cfg.farmingShowCrop)out.add(new Row("","Crop",c.crop()));
        int speedColor=HerculesGardenTracker.speedGood(c)?0xFF55FF55:0xFFFF5555;
        if(cfg.farmingShowSpeed)out.add(new Row("","Speed",Integer.toString(c.speed()),speedColor));
        if(cfg.farmingShowTargetSpeed)out.add(new Row("","Target speed",Integer.toString(c.targetSpeed())));
        if(cfg.farmingShowSpeedDifference)out.add(new Row("","Speed difference",signed(c.speed()-c.targetSpeed()),speedColor));
        int angleColor=HerculesGardenTracker.anglesGood(c)?0xFF55FF55:0xFFFF5555;
        if(c.anglesVisible()&&cfg.farmingShowYawPitch){out.add(new Row("","Yaw",decimal(c.yaw(),cfg.farmingYawPrecision),angleColor));out.add(new Row("","Pitch",decimal(c.pitch(),cfg.farmingPitchPrecision),angleColor));}
        if(c.anglesVisible()&&cfg.farmingShowTargetAngles)out.add(new Row("","Target yaw/pitch",decimal(c.targetYaw(),cfg.farmingYawPrecision)+" / "+decimal(c.targetPitch(),cfg.farmingPitchPrecision)));
        if(c.anglesVisible()&&cfg.farmingShowAngleDifference)out.add(new Row("","Angle difference",decimal(c.yawDifference(),2)+" / "+decimal(c.pitchDifference(),2),angleColor));
        return out;
    }
    @Override protected List<Row> previewRows(){return List.of(new Row("","Crop","Sugar Cane"),new Row("","Speed","328",0xFF55FF55),new Row("","Target speed","328"),new Row("","Yaw/Pitch","-135.00 / 0.00",0xFF55FF55));}
    private static String signed(int value){return value>0?"+"+value:Integer.toString(value);}
    private static String decimal(double value,int precision){return String.format(Locale.ROOT,"%."+Math.clamp(precision,0,6)+"f",value);}
}
