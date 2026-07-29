package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.ArtemisShardTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class ShardTrackerHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public ShardTrackerHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"artemis-shard-tracker";}
    @Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition position){this.position=position;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean enabled){this.enabled=enabled;}
    @Override public boolean visibleNow(){return isEnabled()&&ArtemisShardTracker.visible();}
    @Override public String editorLabel(){return"Shard Tracker";}
    @Override protected String title(){return"Shard Tracker";}
    @Override protected List<Row> rows(){
        var cfg=ArtemisShardTracker.config();var state=ArtemisShardTracker.state();
        if(cfg==null||state==null)return List.of();
        List<Row> out=new ArrayList<>();
        for(var shard:state.rows()){
            String value=shard.needed()>0?shard.obtained()+"/"+shard.needed():shard.obtained()+"x";
            if(cfg.shardTrackerShowRemaining&&shard.needed()>shard.obtained())value+=" ("+(shard.needed()-shard.obtained())+" left)";
            if(cfg.shardTrackerShowSource)value+=" ["+shard.source()+"]";
            if(cfg.shardTrackerShowValue)value+=" "+ArtemisShardTracker.coins(shard.remainingValue(),shard.priced());
            int color=shard.complete()?cfg.shardTrackerCompleteColor:shard.obtained()>0?cfg.shardTrackerIncompleteColor:cfg.shardTrackerMissingColor;
            out.add(new Row("",shard.name(),value,color));
        }
        if(cfg.shardTrackerShowTotal&&!state.rows().isEmpty())
            out.add(new Row("","Progress",state.obtained()+"/"+state.needed(),state.complete()?cfg.shardTrackerCompleteColor:cfg.shardTrackerIncompleteColor));
        if(out.isEmpty()&&!cfg.shardTrackerHideEmpty)out.add(new Row("","Tracked","None",cfg.shardTrackerMissingColor));
        return out;
    }
}
