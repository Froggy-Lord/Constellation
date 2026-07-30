package com.froggylord.constellation.ui;

import com.froggylord.constellation.constellation.AquilaTunnelMaps;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

// ported from SkyHanni (LGPL-3.0-or-later): features/mining/TunnelsMaps.kt
public final class TunnelMapScreen extends Screen {
    private final Screen parent;
    private EditBox search;
    private double scroll;

    public TunnelMapScreen(Screen parent){super(Component.literal("Tunnel Maps"));this.parent=parent;}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init(){
        search=new EditBox(font,14,27,Math.min(240,width-28),18,Component.literal("Search destinations"));
        search.setHint(Component.literal("search destinations"));
        search.setResponder(value->scroll=0);
        addRenderableWidget(search);
    }

    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xD8080810);
        g.text(font,"Glacite Tunnel Maps",14,12,ConstellationTheme.ACCENT_BRIGHT,false);
        g.text(font,AquilaTunnelMaps.routeStatus(),Math.min(270,width/2),15,ConstellationTheme.TEXT_MUTED,false);
        int top=52,rowHeight=20,y=top-(int)scroll;List<String> rows=rows();
        for(String name:rows){
            if(y+17>=top&&y<height-31){
                boolean active=name.equals(AquilaTunnelMaps.activeDestination());
                int color=active?0xFF285B55:inside(mx,my,12,y,width-24,17)?0xFF303044:0xC020202C;
                g.fill(12,y,width-12,y+17,color);
                g.text(font,name,18,y+5,active?0xFF55FFFF:ConstellationTheme.TEXT,false);
                if(active)g.text(font,"active",width-18-font.width("active"),y+5,0xFF55FFFF,false);
            }
            y+=rowHeight;
        }
        button(g,12,height-24,62,"Clear",mx,my);
        button(g,80,height-24,72,"Next spot",mx,my);
        button(g,158,height-24,78,"Campfire",mx,my);
        g.text(font,"click a destination to route",width-14-font.width("click a destination to route"),height-18,ConstellationTheme.TEXT_MUTED,false);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event,boolean dbl){
        int mx=(int)event.x(),my=(int)event.y();
        if(inside(mx,my,12,height-24,62,18)){AquilaTunnelMaps.clear();return true;}
        if(inside(mx,my,80,height-24,72,18)){AquilaTunnelMaps.nextFromUi();return true;}
        if(inside(mx,my,158,height-24,78,18)){AquilaTunnelMaps.campfireFromUi();return true;}
        int y=52-(int)scroll;for(String name:rows()){if(inside(mx,my,12,y,width-24,17)){AquilaTunnelMaps.choose(name);return true;}y+=20;}
        return super.mouseClicked(event,dbl);
    }
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){int max=Math.max(0,rows().size()*20-(height-84));scroll=Math.clamp(scroll-sy*24,0,max);return true;}
    private List<String> rows(){String value=search==null?"":search.getValue().trim().toLowerCase(Locale.ROOT);return AquilaTunnelMaps.destinations().stream().filter(name->value.isBlank()||name.toLowerCase(Locale.ROOT).contains(value)).toList();}
    private void button(GuiGraphicsExtractor g,int x,int y,int w,String text,int mx,int my){g.fill(x,y,x+w,y+18,inside(mx,my,x,y,w,18)?0xFF3C3C55:0xFF252538);g.text(font,text,x+(w-font.width(text))/2,y+6,ConstellationTheme.TEXT,false);}
    private static boolean inside(int mx,int my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    @Override public void onClose(){Minecraft.getInstance().setScreenAndShow(parent);}
}
