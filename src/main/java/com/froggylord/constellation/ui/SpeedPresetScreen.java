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
    private final Screen parent;private EditBox search,name,speed;private int scroll;private String selected="",message="";private boolean messageError,confirmDelete,confirmReset;
    public SpeedPresetScreen(Screen parent){super(Component.literal("Speed Presets"));this.parent=parent;}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init(){
        String searchValue=search==null?"":search.getValue(),nameValue=name==null?"":name.getValue(),speedValue=speed==null?"":speed.getValue();boolean searchFocus=search!=null&&search.isFocused(),nameFocus=name!=null&&name.isFocused(),speedFocus=speed!=null&&speed.isFocused();int retainedScroll=scroll;
        search=new EditBox(font,14,26,Math.min(190,width-28),18,Component.literal("Search presets"));search.setHint(Component.literal("search presets"));search.setResponder(value->{scroll=0;confirmDelete=confirmReset=false;});addRenderableWidget(search);
        name=new EditBox(font,14,height-47,120,18,Component.literal("Preset name"));name.setHint(Component.literal("preset name"));name.setMaxLength(16);name.setResponder(value->{confirmDelete=confirmReset=false;});addRenderableWidget(name);
        speed=new EditBox(font,140,height-47,48,18,Component.literal("Speed"));speed.setHint(Component.literal("speed"));speed.setMaxLength(3);speed.setResponder(value->{confirmDelete=confirmReset=false;});addRenderableWidget(speed);
        search.setValue(searchValue);name.setValue(nameValue);speed.setValue(speedValue);scroll=retainedScroll;if(speedFocus){speed.setFocused(true);setFocused(speed);}else if(nameFocus){name.setFocused(true);setFocused(name);}else if(searchFocus){search.setFocused(true);setFocused(search);}
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int mx,int my,float delta){
        ConstellationUi.background(graphics,width,height,delta);
        ConstellationUi.header(graphics,font,"Speed Presets","Profile "+PhoenixSpeedPresets.profile(),width);
        ConstellationTheme.search(graphics,12,24,Math.min(194,width-24),22,search.isFocused());
        int top=52,bottom=height-53;
        ConstellationUi.panel(graphics,8,top-4,width-16,bottom-top+8);
        List<java.util.Map.Entry<String,Integer>> rows=rows();
        scroll=Math.clamp(scroll,0,Math.max(0,rows.size()*20-(height-105)));
        int y=top-scroll;
        graphics.enableScissor(8,top,width-8,bottom);
        for(var entry:rows){if(y+17>=top&&y<bottom){boolean active=entry.getKey().equals(selected);ConstellationTheme.surface(graphics,12,y,width-24,17,active?0xFF285B55:inside(mx,my,12,y,width-24,17)?0xFF303044:0xC020202C,active?ConstellationTheme.ACCENT_DIM:ConstellationTheme.BORDER_SOFT);graphics.text(font,entry.getKey(),18,y+5,active?0xFF55FFFF:ConstellationTheme.TEXT,false);String value=Integer.toString(entry.getValue());graphics.text(font,value,width-18-font.width(value),y+5,0xFFFFFF55,false);}y+=20;}
        graphics.disableScissor();
        ConstellationUi.scrollbar(graphics,width-11,top,bottom-top,bottom-top,rows.size()*20,scroll);
        ConstellationTheme.search(graphics,name.getX()-2,name.getY()-2,name.getWidth()+4,name.getHeight()+4,name.isFocused());
        ConstellationTheme.search(graphics,speed.getX()-2,speed.getY()-2,speed.getWidth()+4,speed.getHeight()+4,speed.isFocused());
        int actions=Math.max(194,width-232);button(graphics,actions,height-47,52,"Save",mx,my);button(graphics,actions+56,height-47,52,confirmDelete?"Confirm":"Delete",mx,my);button(graphics,actions+112,height-47,48,"Use",mx,my);button(graphics,actions+164,height-47,54,confirmReset?"Confirm":"Reset",mx,my);button(graphics,width-68,height-23,54,"Done",mx,my);if(!message.isBlank())graphics.text(font,message,14,height-22,messageError?0xFFFF7777:0xFF77FFAA,false);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean dbl){
        int mx=(int)event.x(),my=(int)event.y(),actions=Math.max(194,width-232);if(inside(mx,my,actions,height-47,52,18)){confirmDelete=confirmReset=false;saveEntry();return true;}if(inside(mx,my,actions+56,height-47,52,18)){if(selected.isBlank()){message="Select a preset to delete";messageError=true;confirmDelete=confirmReset=false;}else if(confirmDelete){PhoenixSpeedPresets.remove(selected);selected="";name.setValue("");speed.setValue("");message="Preset deleted";messageError=false;confirmDelete=false;}else{confirmDelete=true;confirmReset=false;}return true;}if(inside(mx,my,actions+112,height-47,48,18)){confirmDelete=confirmReset=false;String value=selected.isBlank()?name.getValue():selected;if(!value.isBlank())PhoenixSpeedPresets.use(value);return true;}if(inside(mx,my,actions+164,height-47,54,18)){if(confirmReset){PhoenixSpeedPresets.reset();selected="";message="Defaults restored";messageError=false;confirmReset=false;}else{confirmReset=true;confirmDelete=false;}return true;}if(inside(mx,my,width-68,height-23,54,18)){onClose();return true;}
        int y=52-scroll;for(var entry:rows()){if(my>=52&&my<height-53&&inside(mx,my,12,y,width-24,17)){confirmDelete=confirmReset=false;selected=entry.getKey();name.setValue(entry.getKey());speed.setValue(Integer.toString(entry.getValue()));return true;}y+=20;}confirmDelete=confirmReset=false;return super.mouseClicked(event,dbl);
    }
    private void saveEntry(){String id=name.getValue().trim();int value;try{value=Integer.parseInt(speed.getValue());}catch(Exception e){message="Enter a speed from 0 to 500";messageError=true;return;}if(!PhoenixSpeedPresets.set(id,value)){message="Names start with a letter; speed is 0 to 500";messageError=true;return;}if(!selected.isBlank()&&!selected.equalsIgnoreCase(id))PhoenixSpeedPresets.remove(selected);selected=id.toLowerCase(Locale.ROOT);message="Saved "+selected;messageError=false;}
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){int max=Math.max(0,rows().size()*20-(height-105));scroll=Math.clamp(scroll-(int)(sy*24),0,max);return true;}
    private List<java.util.Map.Entry<String,Integer>> rows(){String value=search==null?"":search.getValue().trim().toLowerCase(Locale.ROOT);return PhoenixSpeedPresets.presets().entrySet().stream().filter(entry->value.isBlank()||entry.getKey().contains(value)).toList();}
    private void button(GuiGraphicsExtractor graphics,int x,int y,int w,String text,int mx,int my){ConstellationUi.button(graphics,font,x,y,w,18,text,inside(mx,my,x,y,w,18),false);}
    private static boolean inside(int mx,int my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    @Override public boolean keyPressed(KeyEvent event){if(event.key()==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}return super.keyPressed(event);}
    @Override public void onClose(){Minecraft.getInstance().setScreenAndShow(parent);}
}
