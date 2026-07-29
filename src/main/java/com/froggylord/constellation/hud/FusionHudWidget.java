package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.ArtemisFusion;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class FusionHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public FusionHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"artemis-fusion";}
    @Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition position){this.position=position;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean enabled){this.enabled=enabled;}
    @Override public boolean visibleNow(){return isEnabled()&&ArtemisFusion.visible();}
    @Override public String editorLabel(){return"Fusion";}
    @Override protected String title(){return"Fusion";}
    @Override protected List<Row> rows(){
        var cfg=ArtemisFusion.config();var state=ArtemisFusion.state();if(cfg==null||state==null)return List.of();
        List<Row> out=new ArrayList<>();
        if(cfg.fusionShowInputs){addInput(out,"First",state.first(),cfg);addInput(out,"Second",state.second(),cfg);}
        if(cfg.fusionShowOutput&&state.output()!=null)out.add(new Row("","Output",amount(state.output())+" "+state.output().name(),cfg.fusionOutputColor));
        if(cfg.fusionShowInputCost&&state.first()!=null)out.add(new Row("","Input cost",ArtemisFusion.coins(state.inputCost(),state.first().priced()&&state.second().priced()),cfg.fusionInputColor));
        if(cfg.fusionShowOutputValue&&state.output()!=null)out.add(new Row("","Output value",ArtemisFusion.coins(state.outputValue(),state.output().priced()),cfg.fusionOutputColor));
        if(cfg.fusionShowNetValue&&state.first()!=null&&state.output()!=null)out.add(new Row("","Net",ArtemisFusion.coins(state.outputValue()-state.inputCost(),state.complete()),state.outputValue()>=state.inputCost()?cfg.fusionReadyColor:cfg.fusionMissingColor));
        if(cfg.fusionWarnMissing&&state.first()!=null)out.add(new Row("","Materials",state.ready()?"Ready":"Missing",state.ready()?cfg.fusionReadyColor:cfg.fusionMissingColor));
        if(cfg.fusionShowPureReptiles&&state.pureReptiles()>0)out.add(new Row("","Pure Reptiles",Long.toString(state.pureReptiles()),cfg.fusionOutputColor));
        if(out.isEmpty()&&cfg.fusionShowLastResult&&!state.lastResult().isBlank())out.add(new Row("","Last",state.lastAmount()+"x "+state.lastResult(),cfg.fusionOutputColor));
        return out;
    }
    private static void addInput(List<Row> out,String label,ArtemisFusion.Shard shard,com.froggylord.constellation.config.ArtemisConfig cfg){
        if(shard==null)return;String value=shard.name();
        if(cfg.fusionShowOwned)value+=" "+shard.owned();
        if(cfg.fusionShowRequired)value+=(cfg.fusionShowOwned?"/":"x")+shard.required();
        out.add(new Row("",label,value,shard.owned()>=shard.required()?cfg.fusionInputColor:cfg.fusionMissingColor));
    }
    private static String amount(ArtemisFusion.Shard shard){return shard.amount()+"x";}
}
