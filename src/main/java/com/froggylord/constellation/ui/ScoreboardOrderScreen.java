package com.froggylord.constellation.ui;

import com.froggylord.constellation.constellation.ApolloCustomScoreboard;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// ported from CryptKit (GPL-3.0-only): gui/ScoreboardOrderScreen.java
// searchable filtering cross-checked with SkyHanni (LGPL-3.0-or-later): config custom scoreboard draggable entries
public final class ScoreboardOrderScreen extends Screen {
    private final Screen parent;private final List<String> order=new ArrayList<>();private final Map<String,String> labels=new LinkedHashMap<>();
    private EditBox search;private int scroll;
    public ScoreboardOrderScreen(Screen parent){super(Component.literal("Scoreboard Lines"));this.parent=parent;build();}
    @Override public boolean isPauseScreen(){return false;}
    private void build(){
        var cfg=ApolloCustomScoreboard.config();for(var line:ApolloCustomScoreboard.lines())labels.put(line.id(),line.label()==null?line.value():line.label());labels.putAll(ApolloCustomScoreboard.labels());
        for(String id:cfg.customScoreboardOrder){if(id.equals("server")){for(String key:labels.keySet())if(key.startsWith("srv:")&&!order.contains(key))order.add(key);}else if(!order.contains(id))order.add(id);}
        for(String id:labels.keySet())if(!order.contains(id))order.add(id);
    }
    @Override protected void init(){search=new EditBox(font,14,26,Math.min(240,width-28),18,Component.literal("Search lines"));search.setHint(Component.literal("search lines"));search.setResponder(value->scroll=0);addRenderableWidget(search);}
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int mx,int my,float delta){
        graphics.fill(0,0,width,height,0xD8080810);graphics.text(font,"Scoreboard Lines",14,11,ConstellationTheme.ACCENT_BRIGHT,false);
        graphics.text(font,"click a name to show or hide it",width-14-font.width("click a name to show or hide it"),14,ConstellationTheme.TEXT_MUTED,false);
        int y=52-scroll;for(String id:visible()){if(y+17>=52&&y<height-29){boolean hidden=ApolloCustomScoreboard.config().customScoreboardHidden.contains(id);boolean hover=inside(mx,my,12,y,width-24,17);graphics.fill(12,y,width-12,y+17,hover?0xFF303044:0xC020202C);graphics.text(font,label(id),18,y+5,hidden?ConstellationTheme.TEXT_MUTED:ConstellationTheme.TEXT,false);button(graphics,width-112,y+1,28,"Up",mx,my);button(graphics,width-80,y+1,34,"Down",mx,my);button(graphics,width-42,y+1,28,hidden?"Show":"Hide",mx,my);}y+=20;}
        button(graphics,12,height-23,62,"Reset",mx,my);button(graphics,80,height-23,62,"Done",mx,my);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean dbl){
        int mx=(int)event.x(),my=(int)event.y();if(inside(mx,my,12,height-23,62,18)){ApolloCustomScoreboard.resetOrder(true);order.clear();build();scroll=0;return true;}if(inside(mx,my,80,height-23,62,18)){onClose();return true;}
        int y=52-scroll;List<String> rows=visible();for(String id:rows){if(inside(mx,my,12,y,width-24,17)){int index=order.indexOf(id);if(inside(mx,my,width-112,y+1,28,15)){if(index>0)java.util.Collections.swap(order,index,index-1);}
                else if(inside(mx,my,width-80,y+1,34,15)){if(index>=0&&index<order.size()-1)java.util.Collections.swap(order,index,index+1);}
                else{var hidden=ApolloCustomScoreboard.config().customScoreboardHidden;if(!hidden.remove(id))hidden.add(id);}save();return true;}y+=20;}return super.mouseClicked(event,dbl);
    }
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){int max=Math.max(0,visible().size()*20-(height-83));scroll=Math.clamp(scroll-(int)(sy*24),0,max);return true;}
    @Override public boolean keyPressed(KeyEvent event){if(event.key()==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}return super.keyPressed(event);}
    private List<String> visible(){String needle=search==null?"":search.getValue().trim().toLowerCase(Locale.ROOT);return order.stream().filter(id->needle.isBlank()||label(id).toLowerCase(Locale.ROOT).contains(needle)).toList();}
    private String label(String id){if(id.startsWith("srv:"))return labels.getOrDefault(id,id.substring(4).replace('_',' '));return labels.getOrDefault(id,id);}
    private void save(){ApolloCustomScoreboard.config().customScoreboardOrder=new ArrayList<>(order);ApolloCustomScoreboard.save();}
    private void button(GuiGraphicsExtractor graphics,int x,int y,int w,String text,int mx,int my){graphics.fill(x,y,x+w,y+15,inside(mx,my,x,y,w,15)?0xFF3C3C55:0xFF252538);graphics.text(font,text,x+(w-font.width(text))/2,y+4,ConstellationTheme.TEXT,false);}
    private static boolean inside(int mx,int my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    @Override public void onClose(){save();Minecraft.getInstance().setScreenAndShow(parent);}
}
