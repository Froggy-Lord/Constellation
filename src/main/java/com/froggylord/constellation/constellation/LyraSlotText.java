package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.LyraConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-or-later): manager, scaling, and four-corner placement
// skyblock/item/slottext/SlotTextManager.java and SlotText.java
// ported from Skyblocker (LGPL-3.0-or-later): slottext/adders/{PetLevel,NewYearCake,EnchantmentLevel,PotionLevel,MinionLevel,RancherBootsSpeed}Adder.java
public final class LyraSlotText {
    private enum Position { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    private record Text(String value, Position position, int color) {}
    private static final Pattern PET = Pattern.compile("(?:\\S+\\s+)?\\[Lvl (\\d+)].*");
    private static final Pattern MINION = Pattern.compile(".* Minion ([IVXLCDM]+)");
    private static final Pattern RANCHER = Pattern.compile("Current Speed Cap: (\\d+)(?: ?(\\d+))?");
    private static LyraConfig cfg;

    private LyraSlotText() {}
    public static void init(LyraConfig config) { cfg=config; }

    public static void drawSlot(GuiGraphicsExtractor graphics, Slot slot) {
        if (cfg==null||!cfg.enabled||!cfg.slotText||!ConstellationClient.loc().onHypixel()||slot==null) return;
        ItemStack stack=slot.getItem(); if(stack.isEmpty()) return;
        Font font=Minecraft.getInstance().font;
        for(Text text:text(stack)) draw(graphics,font,slot.x,slot.y,text);
    }

    static void drawStack(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        if (cfg==null||!cfg.enabled||!cfg.slotText||stack==null||stack.isEmpty()) return;
        Font font=Minecraft.getInstance().font;
        for(Text value:text(stack)) draw(graphics,font,x,y,value);
    }

    public static int occupiedCorners(ItemStack stack) {
        if(cfg==null||!cfg.enabled||!cfg.slotText||stack==null||stack.isEmpty())return 0;
        int occupied=0;for(Text value:text(stack))occupied|=switch(value.position()){case BOTTOM_LEFT->1;case BOTTOM_RIGHT->2;case TOP_LEFT->4;case TOP_RIGHT->8;};return occupied;
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("slottext")
            .executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word())
                        .executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }

    private static List<Text> text(ItemStack stack) {
        List<Text> out=new ArrayList<>(); CompoundTag extra=extra(stack); String name=plain(stack.getHoverName());
        if(cfg.slotTextPetLevel&&stack.is(Items.PLAYER_HEAD)&&extra.getStringOr("id","").equals("PET")){
            Matcher matcher=PET.matcher(name);if(matcher.matches()&&!matcher.group(1).equals("100")&&!matcher.group(1).equals("200"))out.add(new Text(matcher.group(1),Position.TOP_LEFT,0xFFFFDDC1));
        }
        if(cfg.slotTextCakeYear&&stack.is(Items.CAKE)){int year=extra.getIntOr("new_years_cake",0);if(year>0)out.add(new Text(Integer.toString(year),Position.BOTTOM_LEFT,0xFF74C7EC));}
        if(cfg.slotTextEnchantLevel&&stack.is(Items.ENCHANTED_BOOK)){
            CompoundTag enchantments=extra.getCompoundOrEmpty("enchantments");int level=0;
            if(enchantments.keySet().size()==1){String key=enchantments.keySet().iterator().next();level=enchantments.getIntOr(key,0);}else{int split=name.lastIndexOf(' ');if(split>0)level=roman(name.substring(split+1));}
            if(level>0)out.add(new Text(Integer.toString(level),Position.BOTTOM_LEFT,0xFFFFDDC1));
        }
        if(cfg.slotTextPotionLevel){int level=extra.getIntOr("potion_level",0);if(level>0&&!name.contains("Healer")&&!name.contains("Class Passives"))out.add(new Text(Integer.toString(level),Position.BOTTOM_RIGHT,0xFFFFDDC1));}
        if(cfg.slotTextMinionLevel&&stack.is(Items.PLAYER_HEAD)){Matcher matcher=MINION.matcher(name);if(matcher.matches()){int level=roman(matcher.group(1));if(level>0)out.add(new Text(Integer.toString(level),Position.TOP_RIGHT,0xFFFFDDC1));}}
        if(cfg.slotTextRancherSpeed&&extra.getStringOr("id","").equals("RANCHERS_BOOTS")){
            Matcher matcher=loreMatch(stack,RANCHER);if(matcher!=null)out.add(new Text(matcher.group(2)==null?matcher.group(1):matcher.group(2),Position.BOTTOM_LEFT,0xFFFFDDC1));
        }
        if(cfg.slotTextStars){int stars=extra.getIntOr("upgrade_level",extra.getIntOr("dungeon_item_level",0));if(stars>0)out.add(new Text(Integer.toString(stars),Position.TOP_RIGHT,0xFFFFAA00));}
        return out;
    }

    private static void draw(GuiGraphicsExtractor graphics,Font font,int x,int y,Text text){
        int width=font.width(text.value());float scale=cfg.slotTextScaleToFit&&width>16?16f/width:1f;
        float drawX=switch(text.position()){case TOP_LEFT,BOTTOM_LEFT->0;case TOP_RIGHT,BOTTOM_RIGHT->16-width*scale;};
        float drawY=switch(text.position()){case TOP_LEFT,TOP_RIGHT->0;case BOTTOM_LEFT,BOTTOM_RIGHT->16-font.lineHeight*scale+2;};
        graphics.pose().pushMatrix();graphics.pose().translate(x+drawX,y+drawY);graphics.pose().scale(scale,scale);graphics.text(font,text.value(),0,0,text.color(),true);graphics.pose().popMatrix();
    }

    private static Matcher loreMatch(ItemStack stack,Pattern pattern){ItemLore lore=stack.get(DataComponents.LORE);if(lore==null)return null;for(Component line:lore.lines()){Matcher matcher=pattern.matcher(plain(line));if(matcher.find())return matcher;}return null;}
    private static CompoundTag extra(ItemStack stack){CustomData data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return new CompoundTag();CompoundTag root=data.copyTag(),legacy=root.getCompoundOrEmpty("ExtraAttributes");return legacy.isEmpty()?root:legacy;}
    private static String plain(Component component){String value=ChatFormatting.stripFormatting(component.getString());return value==null?component.getString():value;}
    private static int roman(String value){int total=0,previous=0;for(int i=value.length()-1;i>=0;i--){int current=switch(value.charAt(i)){case'I'->1;case'V'->5;case'X'->10;case'L'->50;case'C'->100;case'D'->500;case'M'->1000;default->0;};if(current==0)return 0;total+=current<previous?-current:current;previous=current;}return total;}
    private static int status(){local("§eSlot text "+on(cfg.slotText)+": pets "+on(cfg.slotTextPetLevel)+", cakes "+on(cfg.slotTextCakeYear)+", enchants "+on(cfg.slotTextEnchantLevel)+", potions "+on(cfg.slotTextPotionLevel)+", minions "+on(cfg.slotTextMinionLevel)+", Rancher "+on(cfg.slotTextRancherSpeed)+", stars "+on(cfg.slotTextStars)+".");return 1;}
    private static int option(String name,String state){Boolean value=parseState(state);if(value==null){local("§cState must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled","slottext"->cfg.slotText=value;case"pet","pets"->cfg.slotTextPetLevel=value;case"cake","cakes"->cfg.slotTextCakeYear=value;case"enchant","enchants"->cfg.slotTextEnchantLevel=value;case"potion","potions"->cfg.slotTextPotionLevel=value;case"minion","minions"->cfg.slotTextMinionLevel=value;case"rancher","speed"->cfg.slotTextRancherSpeed=value;case"stars","star"->cfg.slotTextStars=value;case"scale"->cfg.slotTextScaleToFit=value;default->{local("§cOption must be enabled, pet, cake, enchant, potion, minion, rancher, stars, or scale.");return 0;}}ConstellationClient.saveConfig();local("§aSlot-text option updated.");return 1;}
    private static Boolean parseState(String state){return switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"§aon":"§coff";}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5Lyra §8> §f"+text));}
}
