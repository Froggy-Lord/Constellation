package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.ApolloCustomScoreboard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.BooleanSupplier;

// ported from CryptKit (GPL-3.0-only): gui/CryptScoreboard.java
// appearance cross-checked with SkyHanni (LGPL-3.0-or-later): features/gui/customscoreboard/RenderBackground.kt
public final class CustomScoreboardHudWidget implements HudElement {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public CustomScoreboardHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"apollo-custom-scoreboard";}@Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition position){this.position=position;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean enabled){this.enabled=enabled;}@Override public boolean visibleNow(){return isEnabled()&&ApolloCustomScoreboard.active();}
    @Override public String editorLabel(){return"Custom Scoreboard";}
    @Override public int width(){return dimensions(lines())[0];}@Override public int height(){return dimensions(lines())[1];}
    @Override public int previewWidth(){return Math.max(118,width());}@Override public int previewHeight(){return Math.max(58,height());}
    @Override public void render(GuiGraphicsExtractor graphics,int x,int y){draw(graphics,x,y,lines());}
    @Override public void renderPreview(GuiGraphicsExtractor graphics,int x,int y){List<ApolloCustomScoreboard.Line> rows=lines();if(rows.isEmpty())rows=List.of(new ApolloCustomScoreboard.Line("preview","Purse","12,345,678"),new ApolloCustomScoreboard.Line("preview2",null,"Village"));draw(graphics,x,y,rows);}
    private List<ApolloCustomScoreboard.Line> lines(){return ApolloCustomScoreboard.lines();}
    private int[] dimensions(List<ApolloCustomScoreboard.Line> rows){var cfg=ApolloCustomScoreboard.config();Font font=Minecraft.getInstance().font;int inner=font.width(ApolloCustomScoreboard.title());for(var row:rows)inner=Math.max(inner,row.label()==null?font.width(row.value()):font.width(row.label())+8+font.width(row.value()));int spacing=Math.clamp(cfg.customScoreboardLineSpacing,0,8);return new int[]{inner+12,7+font.lineHeight+4+rows.size()*(font.lineHeight+spacing)+3};}
    private void draw(GuiGraphicsExtractor graphics,int x,int y,List<ApolloCustomScoreboard.Line> rows){
        if(rows.isEmpty())return;var cfg=ApolloCustomScoreboard.config();Font font=Minecraft.getInstance().font;int[] size=dimensions(rows);int w=size[0],h=size[1];
        if(cfg.customScoreboardBackground)graphics.fill(x,y,x+w,y+h,cfg.customScoreboardBackgroundColor);
        if(cfg.customScoreboardOutline){int c=cfg.customScoreboardOutlineColor;graphics.fill(x,y,x+w,y+1,c);graphics.fill(x,y+h-1,x+w,y+h,c);graphics.fill(x,y,x+1,y+h,c);graphics.fill(x+w-1,y,x+w,y+h,c);}
        String title=ApolloCustomScoreboard.title();graphics.text(font,title,alignedX(x,w,font.width(title),cfg.customScoreboardTitleAlignment),y+4,cfg.customScoreboardTitleColor,cfg.customScoreboardTextShadow);
        int cursor=y+font.lineHeight+8,spacing=Math.clamp(cfg.customScoreboardLineSpacing,0,8);
        for(var row:rows){if(row.label()==null){graphics.text(font,row.value(),alignedX(x,w,font.width(row.value()),cfg.customScoreboardTextAlignment),cursor,cfg.customScoreboardServerColor,cfg.customScoreboardTextShadow);}
            else{graphics.text(font,row.label(),x+6,cursor,cfg.customScoreboardLabelColor,cfg.customScoreboardTextShadow);graphics.text(font,row.value(),x+w-6-font.width(row.value()),cursor,cfg.customScoreboardValueColor,cfg.customScoreboardTextShadow);}cursor+=font.lineHeight+spacing;}
    }
    private int alignedX(int x,int width,int textWidth,String alignment){return switch(alignment.toUpperCase(java.util.Locale.ROOT)){case"LEFT"->x+6;case"CENTER"->x+(width-textWidth)/2;default->x+width-6-textWidth;};}
}
