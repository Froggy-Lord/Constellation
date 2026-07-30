package com.froggylord.constellation.ui;

import com.froggylord.constellation.constellation.PhoenixSpeedPresets;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/speedpreset/SpeedPresetsScreen.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/speedpreset/SpeedPresetListWidget.java
public final class SpeedPresetScreen extends Screen {
    private final Screen parent;private EditBox search,name,speed;private int scroll;private String selected="",message="";
    public SpeedPresetScreen(Screen parent){super(Component.literal("Speed Presets"));this.parent=parent;}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init(){
        search=new EditBox(font,14,26,Math.min(190,width-28),18,Component.literal("Search presets"));search.setHint(Component.literal("search presets"));search.setResponder(value->scroll=0);addRenderableWidget(search);
        name=new EditBox(font,14,height-47,120,18,Component.literal("Preset name"));name.setHint(Component.literal("preset name"));name.setMaxLength(16);addRenderableWidget(name);
        speed=new EditBox(font,140,height-47,48,18,Component.literal("Speed"));speed.setHint(Component.literal("speed"));speed.setMaxLength(3);addRenderableWidget(speed);
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int mx,int my,float delta){
        graphics.fill(0,0,width,height,0xD8080810);graphics.text(font,"Speed Presets",14,11,ConstellationTheme.ACCENT_BRIGHT,false);graphics.text(font,"profile "+PhoenixSpeedPresets.profile(),width-14-font.width("profile "+PhoenixSpeedPresets.profile()),14,ConstellationTheme.TEXT_MUTED,false);
        int y=52-scroll;for(var entry:rows()){if(y+17>=52&&y<height-53){boolean active=entry.getKey().equals(selected);graphics.fill(12,y,width-12,y+17,active?0xFF285B55:inside(mx,my,12,y,width-24,17)?0xFF303044:0xC020202C);graphics.text(font,entry.getKey(),18,y+5,active?0xFF55FFFF:ConstellationTheme.TEXT,false);String value=Integer.toString(entry.getValue());graphics.text(font,value,width-18-font.width(value),y+5,0xFFFFFF55,false);}y+=20;}
        int actions=Math.max(194,width-232);button(graphics,actions,height-47,52,"Save",mx,my);button(graphics,actions+56,height-47,52,"Delete",mx,my);button(graphics,actions+112,height-47,48,"Use",mx,my);button(graphics,actions+164,height-47,54,"Reset",mx,my);button(graphics,width-68,height-23,54,"Done",mx,my);if(!message.isBlank())graphics.text(font,message,14,height-22,0xFFFF7777,false);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean dbl){
        int mx=(int)event.x(),my=(int)event.y(),actions=Math.max(194,width-232);if(inside(mx,my,actions,height-47,52,18)){saveEntry();return true;}if(inside(mx,my,actions+56,height-47,52,18)){if(!selected.isBlank()){PhoenixSpeedPresets.remove(selected);selected="";name.setValue("");speed.setValue("");message="";}return true;}if(inside(mx,my,actions+112,height-47,48,18)){String value=selected.isBlank()?name.getValue():selected;if(!value.isBlank())PhoenixSpeedPresets.use(value);return true;}if(inside(mx,my,actions+164,height-47,54,18)){PhoenixSpeedPresets.reset();selected="";message="Defaults restored";return true;}if(inside(mx,my,width-68,height-23,54,18)){onClose();return true;}
        int y=52-scroll;for(var entry:rows()){if(inside(mx,my,12,y,width-24,17)){selected=entry.getKey();name.setValue(entry.getKey());speed.setValue(Integer.toString(entry.getValue()));return true;}y+=20;}return super.mouseClicked(event,dbl);
    }
    private void saveEntry(){String id=name.getValue().trim();int value;try{value=Integer.parseInt(speed.getValue());}catch(Exception e){message="Enter a speed from 0 to 500";return;}if(!PhoenixSpeedPresets.set(id,value)){message="Names start with a letter; speed is 0 to 500";return;}if(!selected.isBlank()&&!selected.equalsIgnoreCase(id))PhoenixSpeedPresets.remove(selected);selected=id.toLowerCase(Locale.ROOT);message="Saved "+selected;}
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){int max=Math.max(0,rows().size()*20-(height-105));scroll=Math.clamp(scroll-(int)(sy*24),0,max);return true;}
    private List<java.util.Map.Entry<String,Integer>> rows(){String value=search==null?"":search.getValue().trim().toLowerCase(Locale.ROOT);return PhoenixSpeedPresets.presets().entrySet().stream().filter(entry->value.isBlank()||entry.getKey().contains(value)).toList();}
    private void button(GuiGraphicsExtractor graphics,int x,int y,int w,String text,int mx,int my){graphics.fill(x,y,x+w,y+18,inside(mx,my,x,y,w,18)?0xFF3C3C55:0xFF252538);graphics.text(font,text,x+(w-font.width(text))/2,y+5,ConstellationTheme.TEXT,false);}
    private static boolean inside(int mx,int my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    @Override public boolean keyPressed(KeyEvent event){if(event.key()==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}return super.keyPressed(event);}
    @Override public void onClose(){Minecraft.getInstance().setScreenAndShow(parent);}
}
