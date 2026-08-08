package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.PhoenixPetDisplay;
import com.froggylord.constellation.render.ConstellationTheme;
import com.froggylord.constellation.ui.ConstellationUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ported from Devonian (GPL-3.0-only): features/misc/PetDisplay.kt
public final class PetDisplayHudWidget implements HudElement {
    private final BooleanSupplier gate;private HudPosition pos;private boolean enabled=true;
    public PetDisplayHudWidget(HudPosition pos,BooleanSupplier gate){this.pos=pos;this.gate=gate;}
    @Override public String id(){return"phoenix-pet-display";}@Override public HudPosition position(){return pos;}@Override public void setPosition(HudPosition value){pos=value;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}@Override public boolean visibleNow(){return isEnabled()&&PhoenixPetDisplay.visible();}@Override public String editorLabel(){return"Active Pet";}
    private List<String> lines(){var data=PhoenixPetDisplay.state();var cfg=PhoenixPetDisplay.config();if(data==null)return List.of();StringBuilder identity=new StringBuilder(data.name);if(cfg.petDisplayLevel&&data.level>=0)identity.append(" L").append(data.level);if(cfg.petDisplayCosmeticLevel&&data.cosmeticLevel>=0)identity.append(" C").append(data.cosmeticLevel);if(cfg.petDisplaySkin&&data.skinned)identity.append(" Skin");ArrayList<String> info=new ArrayList<>();if(cfg.petDisplayRarity&&!data.rarity.isBlank())info.add(data.rarity);if(cfg.petDisplayHeldItem&&!data.heldItem.isBlank())info.add(data.heldItem);if(cfg.petDisplayXp&&data.levelProgress>=0)info.add(String.format(java.util.Locale.ROOT,"%.1f%%",data.levelProgress));if(cfg.petDisplayRate&&PhoenixPetDisplay.percentPerHour()>0)info.add(String.format(java.util.Locale.ROOT,"%.2f%%/h",PhoenixPetDisplay.percentPerHour()));if(cfg.petDisplayEta&&!PhoenixPetDisplay.eta().isBlank())info.add("ETA "+PhoenixPetDisplay.eta());if(cfg.petDisplaySource&&!data.source.isBlank())info.add(data.source);return info.isEmpty()?List.of(identity.toString()):List.of(identity.toString(),String.join(" ",info));}
    @Override public int width(){Font f=Minecraft.getInstance().font;List<String> lines=lines();int w=0;for(int i=0;i<lines.size();i++)w=Math.max(w,f.width(lines.get(i))+(i==0&&PhoenixPetDisplay.config().petDisplayIcon&&!PhoenixPetDisplay.icon().isEmpty()?20:0));return Math.min(190,w+8);}@Override public int height(){return Math.max(16,lines().size()*Minecraft.getInstance().font.lineHeight)+8;}
    @Override public int previewWidth(){return 190;}@Override public int previewHeight(){return 26;}
    @Override public void render(GuiGraphicsExtractor g,int x,int y){draw(g,x,y,lines(),false);}
    @Override public void renderPreview(GuiGraphicsExtractor g,int x,int y){draw(g,x,y,List.of("Golden Dragon | L200 | Skin","LEGENDARY | Minos Relic | 72.4%"),true);}
    private void draw(GuiGraphicsExtractor g,int x,int y,List<String> lines,boolean preview){if(lines.isEmpty())return;var cfg=PhoenixPetDisplay.config();int w=preview?previewWidth():width(),h=preview?previewHeight():height();
        // ported from Dross Pickles (MIT): hud/EntityHud.java
        g.fill(x,y,x+w,y+h,0xAA0E0E22);g.outline(x,y,w,h,ConstellationTheme.BORDER_SOFT);int baseX=x+4,textY=y+4;ItemStack icon=PhoenixPetDisplay.icon();if(preview&&icon.isEmpty())icon=new ItemStack(Items.PLAYER_HEAD);boolean showIcon=cfg.petDisplayIcon&&!icon.isEmpty();if(showIcon)g.item(icon,baseX,textY);Font f=Minecraft.getInstance().font;for(int i=0;i<lines.size();i++){int color=i==0?cfg.petDisplayNameColor:lines.get(i).contains("%")?cfg.petDisplayProgressColor:cfg.petDisplayInfoColor;int lineX=baseX+(i==0&&showIcon?20:0);g.text(f,ConstellationUi.fit(f,lines.get(i),Math.max(8,x+w-4-lineX)),lineX,textY+i*f.lineHeight,color,true);}}
}
