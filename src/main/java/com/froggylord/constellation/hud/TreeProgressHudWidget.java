package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.ArtemisTreeProgress;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class TreeProgressHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public TreeProgressHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"artemis-tree-progress";}@Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition position){this.position=position;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean enabled){this.enabled=enabled;}@Override public boolean visibleNow(){return isEnabled()&&ArtemisTreeProgress.visible();}
    @Override public String editorLabel(){return"Tree Progress";}@Override protected String title(){return"Tree Progress";}
    @Override protected List<Row> rows(){
        var cfg=ArtemisTreeProgress.config();var state=ArtemisTreeProgress.state();if(cfg==null||state==null)return List.of();
        int color=state.percent()>=100?cfg.treeProgressCompleteColor:state.percent()>=80?cfg.treeProgressNearlyDoneColor:cfg.treeProgressNormalColor;
        if(cfg.treeProgressCompact)return List.of(new Row("","",state.type()+" "+state.percent()+"%",color));
        List<Row> out=new ArrayList<>();
        if(cfg.treeProgressShowType)out.add(new Row("","Tree",state.type(),color));
        if(cfg.treeProgressShowPercent)out.add(new Row("","Progress",state.percent()+"%",color));
        if(cfg.treeProgressShowBar)out.add(new Row("","",bar(state.percent()),color));
        if(cfg.treeProgressShowDistance)out.add(new Row("","Distance",Math.round(state.distance())+"m",0xFFAAAAAA));
        if(cfg.treeProgressShowContributors&&state.contributors()>0)out.add(new Row("","Contributors",Integer.toString(state.contributors()),0xFF55FFFF));
        return out;
    }
    private static String bar(int percent){int filled=Math.clamp((int)Math.round(percent/10.0),0,10);return"["+"|".repeat(filled)+"-".repeat(10-filled)+"]";}
}
