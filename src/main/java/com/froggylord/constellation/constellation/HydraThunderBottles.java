package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HydraConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

// ported from Feesh (Apache-2.0): features/alerts/ThunderBottleChargedAlert.kt
// ported from Feesh (Apache-2.0): features/items/slottext/{ThunderBottleProgress,SlotTextRendererManager}.kt
public final class HydraThunderBottles {
    private record Bottle(String key,String emptyName,String displayName,int maxCharge,int color,Pattern charged) {}
    private static final List<Bottle> BOTTLES=List.of(
        new Bottle("thunder","Empty Thunder Bottle","Thunder Bottle",50_000,0xFFAA00AA,Pattern.compile("^> Your bottle of thunder has fully charged!$")),
        new Bottle("storm","Empty Storm Bottle","Storm Bottle",500_000,0xFFAA00AA,Pattern.compile("^> Your Storm in a Bottle has fully charged!$")),
        new Bottle("hurricane","Empty Hurricane Bottle","Hurricane Bottle",5_000_000,0xFFFFAA00,Pattern.compile("^> Your Hurricane in a Bottle has fully charged!$"))
    );
    private static HydraConfig cfg;
    private static boolean initialized;
    private static Bottle pending,last,lastAlertBottle;
    private static long pendingAt,lastSeenAt,lastAlertAt;

    private HydraThunderBottles() {}

    public static void init(HydraConfig config){cfg=config;if(initialized)return;initialized=true;ConstellationClient.tick().every(1,"hydra-thunder-bottles",HydraThunderBottles::tick);ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());}

    private static void onChat(String message){if(!active()||!cfg.thunderBottleAlert)return;for(Bottle bottle:BOTTLES)if(bottle.charged.matcher(message).matches()&&selected(bottle)){pending=bottle;pendingAt=System.currentTimeMillis()+Math.clamp(cfg.thunderBottleAlertDelayMillis,0,5000);last=bottle;lastSeenAt=System.currentTimeMillis();return;}}
    private static void tick(){if(!configured()){reset();return;}if(!cfg.thunderBottleAlert){pending=null;pendingAt=0;return;}if(!active()||pending==null)return;long now=System.currentTimeMillis();if(now<pendingAt)return;Bottle bottle=pending;pending=null;if(!selected(bottle))return;if(bottle==lastAlertBottle&&now-lastAlertAt<Math.clamp(cfg.thunderBottleDuplicateCooldownSeconds,0,30)*1000L)return;lastAlertBottle=bottle;lastAlertAt=now;alert(bottle);}

    // ported from Feesh (Apache-2.0): features/alerts/ThunderBottleChargedAlert.kt
    private static void alert(Bottle bottle){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;String message=format(cfg.thunderBottleAlertTemplate,bottle);if(cfg.thunderBottleAlertTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(message).withColor(bottle.color&0xFFFFFF));}if(cfg.thunderBottleAlertChat)local(message);if(cfg.thunderBottleAlertSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.9f,1.1f);}

    // ported from Feesh (Apache-2.0): features/items/slottext/ThunderBottleProgress.kt
    public static void drawSlot(GuiGraphicsExtractor graphics,Slot slot){if(!active()||!cfg.thunderBottleProgress||slot==null)return;ItemStack stack=slot.getItem();if(stack.isEmpty())return;String name=clean(stack.getHoverName().getString());Bottle bottle=BOTTLES.stream().filter(b->b.emptyName.equals(name)&&selected(b)).findFirst().orElse(null);if(bottle==null)return;CustomData data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return;CompoundTag root=data.copyTag(),extra=root.contains("thunder_charge")?root:root.getCompoundOrEmpty("ExtraAttributes");double charge=Math.max(0,extra.getDoubleOr("thunder_charge",0));int percent=Math.clamp((int)(charge/bottle.maxCharge*100),0,100);String text=cfg.thunderBottleProgressShowCharge?shortNumber(charge)+"/"+shortNumber(bottle.maxCharge):percent+"%";int occupied=LyraSlotText.occupiedCorners(stack),corner=(occupied&1)==0?1:(occupied&2)==0?2:(occupied&4)==0?4:(occupied&8)==0?8:0;if(corner!=0)draw(graphics,Minecraft.getInstance().font,slot.x,slot.y,text,corner);}

    // ported from Feesh (Apache-2.0): features/items/slottext/SlotTextRendererManager.kt
    private static void draw(GuiGraphicsExtractor graphics,Font font,int x,int y,String text,int corner){float configured=Math.clamp(cfg.thunderBottleProgressScalePercent,40,100)/100f,scale=Math.min(configured,Math.max(.1f,14f/Math.max(1,font.width(text))));float width=font.width(text)*scale,height=font.lineHeight*scale;float drawX=(corner==2||corner==8)?x+15-width:x+1,drawY=(corner==4||corner==8)?y+1:y+15-height;graphics.pose().pushMatrix();graphics.pose().translate(drawX,drawY);graphics.pose().scale(scale,scale);graphics.text(font,text,0,0,cfg.thunderBottleProgressColor,cfg.thunderBottleProgressShadow);graphics.pose().popMatrix();}

    private static boolean selected(Bottle bottle){return switch(bottle.key){case"thunder"->cfg.thunderBottleTrackThunder;case"storm"->cfg.thunderBottleTrackStorm;case"hurricane"->cfg.thunderBottleTrackHurricane;default->false;};}
    private static String format(String template,Bottle bottle){String value=template==null?"":template;return value.replace("{bottle}",bottle.displayName).replace("{type}",bottle.key).replace("{max-charge}",Integer.toString(bottle.maxCharge));}
    private static String shortNumber(double value){if(value>=1_000_000)return String.format(Locale.ROOT,"%.1fm",value/1_000_000);if(value>=1_000)return String.format(Locale.ROOT,"%.0fk",value/1_000);return String.format(Locale.ROOT,"%.0f",value);}
    private static boolean configured(){return cfg!=null&&cfg.enabled&&cfg.thunderBottleSuite;}
    private static boolean active(){return configured()&&ConstellationClient.loc().onHypixel();}
    private static String clean(String value){String plain=ChatFormatting.stripFormatting(value);return plain==null?"":plain.trim();}
    private static void reset(){pending=null;pendingAt=0;last=null;lastAlertBottle=null;lastSeenAt=0;lastAlertAt=0;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("thunderbottle").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->{reset();local("Bottle alert state cleared.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("delay").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("milliseconds",IntegerArgumentType.integer(0,5000)).executes(c->setNumber("delay",IntegerArgumentType.getInteger(c,"milliseconds"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("cooldown").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(0,30)).executes(c->setNumber("cooldown",IntegerArgumentType.getInteger(c,"seconds"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("scale").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("percent",IntegerArgumentType.integer(40,100)).executes(c->setNumber("scale",IntegerArgumentType.getInteger(c,"percent"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("template").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString()).executes(c->template(StringArgumentType.getString(c,"text"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"argb"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){String recent=last==null?"none":last.displayName+" "+Math.max(0,(System.currentTimeMillis()-lastSeenAt)/1000)+"s ago";local("Alert "+on(cfg.thunderBottleAlert)+", progress "+on(cfg.thunderBottleProgress)+", delay "+cfg.thunderBottleAlertDelayMillis+"ms; recent "+recent+".");return 1;}
    private static int setNumber(String type,int value){if(type.equals("delay"))cfg.thunderBottleAlertDelayMillis=value;else if(type.equals("cooldown"))cfg.thunderBottleDuplicateCooldownSeconds=value;else cfg.thunderBottleProgressScalePercent=value;save();return status();}
    private static int template(String value){String clean=value==null?"":value.trim();if(clean.isEmpty()||clean.length()>160){local("Template must contain 1-160 characters.");return 0;}cfg.thunderBottleAlertTemplate=clean;save();return status();}
    private static int color(String raw){try{String value=raw.startsWith("#")?raw.substring(1):raw;if(value.length()==6)value="FF"+value;if(value.length()!=8)throw new NumberFormatException();cfg.thunderBottleProgressColor=(int)Long.parseLong(value,16);save();return status();}catch(NumberFormatException ignored){local("Color must be RRGGBB or AARRGGBB hex.");return 0;}}
    private static int option(String name,String raw){Boolean value=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"alert"->cfg.thunderBottleAlert=value;case"title"->cfg.thunderBottleAlertTitle=value;case"chat"->cfg.thunderBottleAlertChat=value;case"sound"->cfg.thunderBottleAlertSound=value;case"progress"->cfg.thunderBottleProgress=value;case"charge"->cfg.thunderBottleProgressShowCharge=value;case"shadow"->cfg.thunderBottleProgressShadow=value;case"thunder"->cfg.thunderBottleTrackThunder=value;case"storm"->cfg.thunderBottleTrackStorm=value;case"hurricane"->cfg.thunderBottleTrackHurricane=value;default->{local("Option must be alert, title, chat, sound, progress, charge, shadow, thunder, storm, or hurricane.");return 0;}}save();return status();}
    private static String on(boolean value){return value?"on":"off";}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a75[Thunder Bottle] \u00a7f"+text));}
}
