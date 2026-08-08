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
        String retainedValue=search==null?"":search.getValue();boolean focused=search!=null&&search.isFocused();double retainedScroll=scroll;
        search=new EditBox(font,14,27,Math.min(240,width-28),18,Component.literal("Search destinations"));
        search.setHint(Component.literal("search destinations"));
        search.setResponder(value->scroll=0);
        search.setValue(retainedValue);
        addRenderableWidget(search);
        scroll=retainedScroll;if(focused){search.setFocused(true);setFocused(search);}
    }

    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){
        ConstellationUi.background(g,width,height,delta);
        ConstellationUi.header(g,font,"Glacite Tunnel Maps",AquilaTunnelMaps.routeStatus(),width);
        ConstellationTheme.search(g,12,25,Math.min(244,width-24),22,search.isFocused());
        int top=52,bottom=height-31,rowHeight=20;List<String> rows=rows();scroll=Math.clamp(scroll,0,Math.max(0,rows.size()*rowHeight-(height-84)));int y=top-(int)scroll;
        ConstellationUi.panel(g,8,top-4,width-16,bottom-top+8);
        g.enableScissor(8,top,width-8,bottom);
        for(String name:rows){
            if(y+17>=top&&y<bottom){
                boolean active=name.equals(AquilaTunnelMaps.activeDestination());
                int color=active?0xFF285B55:inside(mx,my,12,y,width-24,17)?0xFF303044:0xC020202C;
                ConstellationTheme.surface(g,12,y,width-24,17,color,active?ConstellationTheme.ACCENT_DIM:ConstellationTheme.BORDER_SOFT);
                g.text(font,name,18,y+5,active?0xFF55FFFF:ConstellationTheme.TEXT,false);
                if(active)g.text(font,"active",width-18-font.width("active"),y+5,0xFF55FFFF,false);
            }
            y+=rowHeight;
        }
        g.disableScissor();
        ConstellationUi.scrollbar(g,width-11,top,bottom-top,bottom-top,rows.size()*rowHeight,(int)scroll);
        button(g,12,height-24,70,"Stop route",mx,my);
        button(g,88,height-24,72,"Next spot",mx,my);
        button(g,166,height-24,78,"Campfire",mx,my);
        String help="click a destination to route";int helpX=width-14-font.width(help);
        if(helpX>=252)g.text(font,help,helpX,height-18,ConstellationTheme.TEXT_MUTED,false);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event,boolean dbl){
        int mx=(int)event.x(),my=(int)event.y();
        if(inside(mx,my,12,height-24,70,18)){AquilaTunnelMaps.clear();return true;}
        if(inside(mx,my,88,height-24,72,18)){AquilaTunnelMaps.nextFromUi();return true;}
        if(inside(mx,my,166,height-24,78,18)){AquilaTunnelMaps.campfireFromUi();return true;}
        int y=52-(int)scroll;for(String name:rows()){if(my>=52&&my<height-31&&inside(mx,my,12,y,width-24,17)){AquilaTunnelMaps.choose(name);return true;}y+=20;}
        return super.mouseClicked(event,dbl);
    }
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){int max=Math.max(0,rows().size()*20-(height-84));scroll=Math.clamp(scroll-sy*24,0,max);return true;}
    private List<String> rows(){String value=search==null?"":search.getValue().trim().toLowerCase(Locale.ROOT);return AquilaTunnelMaps.destinations().stream().filter(name->value.isBlank()||name.toLowerCase(Locale.ROOT).contains(value)).toList();}
    private void button(GuiGraphicsExtractor g,int x,int y,int w,String text,int mx,int my){ConstellationUi.button(g,font,x,y,w,18,text,inside(mx,my,x,y,w,18),false);}
    private static boolean inside(int mx,int my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    @Override public void onClose(){Minecraft.getInstance().setScreenAndShow(parent);}
}
