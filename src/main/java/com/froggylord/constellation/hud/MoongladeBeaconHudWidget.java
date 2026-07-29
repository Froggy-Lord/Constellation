package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.ArtemisMoongladeBeacon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class MoongladeBeaconHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public MoongladeBeaconHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"artemis-moonglade-beacon";}@Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition position){this.position=position;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean enabled){this.enabled=enabled;}@Override public boolean visibleNow(){return isEnabled()&&ArtemisMoongladeBeacon.visible();}
    @Override public String editorLabel(){return"Moonglade Beacon";}@Override protected String title(){return"Moonglade Beacon";}
    @Override protected List<Row> rows(){
        var cfg=ArtemisMoongladeBeacon.config();if(cfg==null)return List.of();List<Row> out=new ArrayList<>();
        for(var tune:ArtemisMoongladeBeacon.views()){
            if(cfg.moongladeBeaconShowSolved&&tune.solved()){out.add(new Row("",tune.name(),"Solved",cfg.moongladeBeaconReadyColor));continue;}
            if(ArtemisMoongladeBeacon.views().size()>1)out.add(new Row("",tune.name(),"",0xFF55FFFF));
            add(out,cfg,"Color",tune.referenceColor(),tune.currentColor(),tune.colorOffset());
            add(out,cfg,"Speed",tune.referenceSpeed(),tune.currentSpeed(),tune.speedOffset());
            add(out,cfg,"Pitch",tune.referencePitch(),tune.currentPitch(),tune.pitchOffset());
        }
        return out;
    }
    private static void add(List<Row> out,com.froggylord.constellation.config.ArtemisConfig cfg,String name,String reference,String current,Integer offset){
        String value="";if(cfg.moongladeBeaconShowReference)value+="Target "+reference;
        if(cfg.moongladeBeaconShowCurrent)value+=(value.isEmpty()?"":" | ")+"Now "+current;
        if(cfg.moongladeBeaconShowOffsets&&offset!=null)value+=(value.isEmpty()?"":" | ")+(offset>0?"+":"")+offset;
        out.add(new Row("",name,value,offset==null?cfg.moongladeBeaconUnknownColor:offset==0?cfg.moongladeBeaconCorrectColor|0xFF000000:0xFFFFFFFF));
    }
}
