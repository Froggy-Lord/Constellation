package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.PhoenixPetDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Devonian (GPL-3.0-only): features/misc/PetDisplay.kt
public final class PetDisplayHudWidget implements HudElement {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public PetDisplayHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"phoenix-pet-display";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition value){pos=value;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}@Override public boolean visibleNow(){return isEnabled()&&PhoenixPetDisplay.visible();}@Override public String editorLabel(){return"Active Pet";}
    private List<String> lines(){var data=PhoenixPetDisplay.state();var cfg=PhoenixPetDisplay.config();if(data==null)return List.of();ArrayList<String> out=new ArrayList<>();String name=(cfg.petDisplayLevel&&data.level>=0?"[Lvl "+data.level+"] ":"")+data.name;if(cfg.petDisplayCosmeticLevel&&data.cosmeticLevel>=0)name+=" ["+data.cosmeticLevel+"]";if(cfg.petDisplaySkin&&data.skinned)name+=" (Skinned)";out.add(name);if(cfg.petDisplayRarity&&!data.rarity.isBlank())out.add(data.rarity);if(cfg.petDisplayHeldItem&&!data.heldItem.isBlank())out.add(data.heldItem);if(cfg.petDisplayXp&&data.levelProgress>=0)out.add(String.format(java.util.Locale.ROOT,"%.1f%% to next level",data.levelProgress));if(cfg.petDisplayRate&&PhoenixPetDisplay.percentPerHour()>0)out.add(String.format(java.util.Locale.ROOT,"%.2f%%/h",PhoenixPetDisplay.percentPerHour()));if(cfg.petDisplayEta&&!PhoenixPetDisplay.eta().isBlank())out.add("ETA "+PhoenixPetDisplay.eta());if(cfg.petDisplaySource&&!data.source.isBlank())out.add(data.source);return out;}
    @Override public int width(){Font f=Minecraft.getInstance().font;int w=0;for(String line:lines())w=Math.max(w,f.width(line));return w+(PhoenixPetDisplay.config().petDisplayIcon?20:0);}@Override public int height(){return Math.max(16,lines().size()*Minecraft.getInstance().font.lineHeight);}
    @Override public int previewWidth(){return 112;}@Override public int previewHeight(){return 27;}
    @Override public void render(GuiGraphicsExtractor g,int x,int y){draw(g,x,y,lines(),false);}
    @Override public void renderPreview(GuiGraphicsExtractor g,int x,int y){draw(g,x,y,List.of("[Lvl 100] Golden Dragon","Legendary | Minos Relic","72.4% to next level"),true);}
    private void draw(GuiGraphicsExtractor g,int x,int y,List<String> lines,boolean preview){var cfg=PhoenixPetDisplay.config();int textX=x;if(cfg.petDisplayIcon){var icon=PhoenixPetDisplay.icon();if(!icon.isEmpty())g.item(icon,x,y);textX+=20;}Font f=Minecraft.getInstance().font;for(int i=0;i<lines.size();i++){int color=i==0?cfg.petDisplayNameColor:i==lines.size()-1&&lines.get(i).contains("%")?cfg.petDisplayProgressColor:cfg.petDisplayInfoColor;g.text(f,lines.get(i),textX,y+i*f.lineHeight,color,true);}}
}
