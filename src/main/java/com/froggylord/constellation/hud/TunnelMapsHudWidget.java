package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AquilaTunnelMaps;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class TunnelMapsHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public TunnelMapsHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"aquila-tunnel-maps";}@Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition position){this.position=position;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean enabled){this.enabled=enabled;}@Override public boolean visibleNow(){return isEnabled()&&AquilaTunnelMaps.usable()&&!AquilaTunnelMaps.activeDestination().isBlank();}
    @Override public String editorLabel(){return"Tunnel Maps";}@Override protected String title(){return"Tunnel Maps";}
    @Override protected List<Row> rows(){var cfg=AquilaTunnelMaps.config();List<Row> rows=new ArrayList<>();String status=AquilaTunnelMaps.routeStatus();
        if(cfg.tunnelMapsHudActive)rows.add(new Row("","Active",AquilaTunnelMaps.activeDestination(),0xFF55FFFF));
        if(cfg.tunnelMapsHudDistance){String distance=status.contains(", ")?status.substring(status.indexOf(", ")+2):status;rows.add(new Row("","Distance",distance,0xFFFFFF55));}
        if(cfg.tunnelMapsHudNodes){String nodes=status.contains(" nodes")?status.substring(0,status.indexOf(" nodes")):"-";rows.add(new Row("","Nodes",nodes,0xFFFFFFFF));}
        return rows;}
}
