package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.ArtemisSweep;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class SweepDetailsHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public SweepDetailsHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"artemis-sweep-details";}@Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition position){this.position=position;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean enabled){this.enabled=enabled;}@Override public boolean visibleNow(){return isEnabled()&&ArtemisSweep.visible();}
    @Override public String editorLabel(){return"Sweep Details";}@Override protected String title(){return"Sweep Details";}
    @Override protected List<Row> rows(){
        var cfg=ArtemisSweep.config();var state=ArtemisSweep.state();if(cfg==null||state==null)return List.of();List<Row> out=new ArrayList<>();
        if(cfg.sweepDetailsShowTree)out.add(new Row("","Tree",state.tree().isBlank()?"Unknown":state.tree()));
        if(cfg.sweepDetailsShowToughness)out.add(new Row("","Toughness",number(state.toughness())));
        if(cfg.sweepDetailsShowSweep)out.add(new Row("","Sweep",number(state.finalSweep())+(state.finalSweep()<state.maxSweep()?" / "+number(state.maxSweep()):""),state.finalSweep()<state.maxSweep()?cfg.sweepPenaltyColor:cfg.sweepGoodColor));
        if(cfg.sweepDetailsShowLogs)out.add(new Row("","Logs",number(state.logs()),cfg.sweepGoodColor));
        if(cfg.sweepDetailsShowPenalty&&state.thrown())out.add(new Row("","Axe throw","-"+number(state.throwPenalty())+"%",cfg.sweepPenaltyColor));
        if(cfg.sweepDetailsShowPenalty&&state.stylePenalty())out.add(new Row("","Wrong style","-"+number(state.stylePenaltyAmount())+"%",cfg.sweepPenaltyColor));
        if(cfg.sweepDetailsShowCorrectStyle&&!state.correctStyle().isBlank())out.add(new Row("","Correct style",state.correctStyle(),cfg.sweepGoodColor));
        return out;
    }
    @Override protected List<Row> previewRows(){return List.of(new Row("","Tree","Fig"),new Row("","Toughness","7"),new Row("","Sweep","221 / 442",0xFFFF5555),new Row("","Logs","9.02",0xFF55FF55));}
    private static String number(double value){if(value<0)return"Unknown";return value==Math.rint(value)?Long.toString((long)value):String.format(java.util.Locale.ROOT,"%.2f",value).replaceAll("0+$","").replaceAll("\\.$","");}
}
