package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.ArtemisLasso;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class LassoHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public LassoHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"artemis-lasso";}
    @Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition position){this.position=position;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean enabled){this.enabled=enabled;}
    @Override public boolean visibleNow(){return isEnabled()&&ArtemisLasso.visible();}
    @Override public String editorLabel(){return"Lasso";}
    @Override protected String title(){return"Lasso";}
    @Override protected List<Row> rows(){
        var state=ArtemisLasso.state();var cfg=ArtemisLasso.config();if(state==null||cfg==null)return List.of();
        List<Row> out=new ArrayList<>();int color=state.reel()?cfg.lassoReadyColor:cfg.lassoProgressColor;
        if(cfg.lassoCompact){out.add(new Row("",state.reel()?"REEL":state.percent()+"%",state.target(),color));return out;}
        if(cfg.lassoShowProgress)out.add(new Row("","Reel",state.reel()?"NOW":bar(state.percent()),color));
        if(cfg.lassoShowPercent)out.add(new Row("","Remaining",state.reel()?"Ready":state.percent()+"%",color));
        if(cfg.lassoShowTarget&&!state.target().isBlank())out.add(new Row("","Target",state.target(),cfg.lassoTargetColor));
        if(cfg.lassoShowTool)out.add(new Row("","Tool",state.tool(),0xFFAAAAAA));
        if(cfg.lassoShowDistance)out.add(new Row("","Distance",Math.round(state.distance())+"m",0xFFAAAAAA));
        return out;
    }
    private static String bar(int percent){int remaining=Math.clamp((int)Math.ceil(percent/10.0),0,10);return"["+"-".repeat(10-remaining)+"|".repeat(remaining)+"]";}
}
