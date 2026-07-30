package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.AndromedaMirrorverse;
import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/rift/area/mirrorverse/DanceRoomHelper.kt
public final class MirrorverseDanceHudWidget implements HudElement {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public MirrorverseDanceHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"andromeda-mirrorverse-dance";}@Override public HudPosition position(){return position;}@Override public void setPosition(HudPosition value){position=value;}
    @Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}@Override public void setEnabled(boolean value){enabled=value;}
    @Override public boolean visibleNow(){return isEnabled()&&!AndromedaMirrorverse.danceLines().isEmpty();}@Override public String editorLabel(){return"Mirrorverse Dance";}
    @Override public int width(){return size(lines())[0];}@Override public int height(){return size(lines())[1];}
    @Override public int previewWidth(){return size(preview())[0];}@Override public int previewHeight(){return size(preview())[1];}
    @Override public void render(GuiGraphicsExtractor graphics,int x,int y){draw(graphics,x,y,lines());}
    @Override public void renderPreview(GuiGraphicsExtractor graphics,int x,int y){draw(graphics,x,y,preview());}
    private List<String> lines(){return AndromedaMirrorverse.danceLines();}
    private List<String> preview(){List<String> value=lines();return value.isEmpty()?List.of("§7Now: §eMove §f0:850","§7Next: §5Sneak","§7Later: §bJump"):value;}
    private int gap(){return Math.clamp(AndromedaMirrorverse.danceSpacing(),-5,10);}
    private int[] size(List<String> lines){var font=Minecraft.getInstance().font;int width=font.width("Mirrorverse Dance");for(String line:lines)width=Math.max(width,font.width(line));int step=Math.max(1,font.lineHeight+gap());return new int[]{width+10,8+font.lineHeight+3+Math.max(1,lines.size())*step};}
    private void draw(GuiGraphicsExtractor graphics,int x,int y,List<String> lines){if(lines.isEmpty())return;var font=Minecraft.getInstance().font;int[] size=size(lines);graphics.fill(x,y,x+size[0],y+size[1],ConstellationTheme.PANEL);graphics.fill(x,y,x+2,y+size[1],ConstellationTheme.ACCENT);graphics.text(font,"Mirrorverse Dance",x+5,y+4,ConstellationTheme.ACCENT_BRIGHT,true);int cy=y+7+font.lineHeight;for(String line:lines){graphics.text(font,line,x+5,cy,0xFFFFFFFF,true);cy+=Math.max(1,font.lineHeight+gap());}}
}
