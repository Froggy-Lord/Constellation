package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.ArtemisConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;
import java.util.Set;

// ported from NoFrills (GPL-3.0-only): features/hunting/HuntaxeLock.java
public final class ArtemisHuntaxeLock {
    private static final Set<String> IDS=Set.of(
        "VENATOR_GENESIS","SILVA_DOMINUS","CURSUS_FERAE","APEX_PRAEDATOR","NEX_TITANUM");
    private static ArtemisConfig cfg;
    private static boolean initialized;
    private static int lockTicks;
    private static String lockedItem="";
    private static long blocked,allowed;

    private ArtemisHuntaxeLock(){}

    public static void init(ArtemisConfig config){
        cfg=config;if(initialized)return;initialized=true;
        UseItemCallback.EVENT.register((player,level,hand)->{
            if(!cfg.huntaxeLockAir)return InteractionResult.PASS;
            return interact(player.getItemInHand(hand),player.isShiftKeyDown());
        });
        UseBlockCallback.EVENT.register((player,level,hand,hit)->{
            if(!cfg.huntaxeLockBlocks)return InteractionResult.PASS;
            return interact(player.getItemInHand(hand),player.isShiftKeyDown());
        });
        ConstellationClient.tick().every(1,"artemis-huntaxe-lock",ArtemisHuntaxeLock::tick);
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->resetWindow());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->resetWindow());
    }

    private static InteractionResult interact(ItemStack stack,boolean sneaking){
        if(!active()||!huntaxe(stack))return InteractionResult.PASS;
        if(cfg.huntaxeLockSneakBypass&&sneaking){allowed++;resetWindow();return InteractionResult.PASS;}
        String item=identity(stack);
        if(lockTicks<=0||!item.equals(lockedItem)){
            lockedItem=item;lockTicks=Math.clamp(cfg.huntaxeLockTicks,1,40);blocked++;feedback();
            return InteractionResult.FAIL;
        }
        allowed++;
        if(cfg.huntaxeLockSingleUse)resetWindow();else lockTicks=Math.clamp(cfg.huntaxeLockTicks,1,40);
        return InteractionResult.PASS;
    }

    private static void tick(){
        if(lockTicks<=0)return;
        if(!active()){resetWindow();return;}
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||!identity(mc.player.getMainHandItem()).equals(lockedItem)&&!identity(mc.player.getOffhandItem()).equals(lockedItem)){resetWindow();return;}
        if(--lockTicks<=0)resetWindow();
    }

    private static boolean huntaxe(ItemStack stack){
        if(stack==null||stack.isEmpty())return false;
        String id=id(stack);if(IDS.contains(id))return true;
        String name=clean(stack.getHoverName().getString());
        if(!name.toLowerCase(Locale.ROOT).contains("huntaxe"))return false;
        ItemLore lore=stack.get(DataComponents.LORE);if(lore==null)return false;
        for(Component line:lore.lines())if(clean(line.getString()).startsWith("Ability: Absorptio"))return true;
        return false;
    }

    private static void feedback(){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        String text="Right-click again within "+ticksText()+" to use Absorptio.";
        if(cfg.huntaxeLockActionbar)mc.gui.hud.setOverlayMessage(Component.literal(text),false);
        if(cfg.huntaxeLockChat)mc.player.sendSystemMessage(Component.literal("§6[Huntaxe] §f"+text));
        if(cfg.huntaxeLockSound)mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(),.65f,.75f);
    }

    private static String ticksText(){double seconds=Math.clamp(cfg.huntaxeLockTicks,1,40)/20.0;return seconds==Math.rint(seconds)?String.format(Locale.ROOT,"%.0f seconds",seconds):String.format(Locale.ROOT,"%.2f seconds",seconds);}
    private static String identity(ItemStack stack){String id=id(stack);String uuid=extra(stack).getStringOr("uuid","");return(id.isBlank()?clean(stack.getHoverName().getString()):id)+"|"+uuid;}
    private static String id(ItemStack stack){return extra(stack).getStringOr("id","").toUpperCase(Locale.ROOT);}
    private static CompoundTag extra(ItemStack stack){CustomData data=stack==null?null:stack.get(DataComponents.CUSTOM_DATA);if(data==null)return new CompoundTag();CompoundTag root=data.copyTag(),extra=root.getCompoundOrEmpty("ExtraAttributes");return extra.isEmpty()?root:extra;}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim().replaceAll("\\s+"," ");}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.huntaxeLock&&ConstellationClient.loc().onHypixel();}
    private static void resetWindow(){lockTicks=0;lockedItem="";}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§6[Huntaxe] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource>d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("huntaxelock").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{resetWindow();local("Confirmation window cleared.");return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resetstats").executes(c->{blocked=allowed=0;return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("ticks").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(1,40)).executes(c->{cfg.huntaxeLockTicks=IntegerArgumentType.getInteger(c,"amount");resetWindow();save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Lock "+(cfg.huntaxeLock?"on":"off")+", "+cfg.huntaxeLockTicks+" ticks, "+(cfg.huntaxeLockSingleUse?"every use confirms":"confirmation window")+", session "+blocked+" blocked and "+allowed+" allowed.");return 1;}
    private static int option(String name,String raw){Boolean value=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.huntaxeLock=value;case"air"->cfg.huntaxeLockAir=value;case"blocks"->cfg.huntaxeLockBlocks=value;case"single"->cfg.huntaxeLockSingleUse=value;case"sneak"->cfg.huntaxeLockSneakBypass=value;case"actionbar"->cfg.huntaxeLockActionbar=value;case"chat"->cfg.huntaxeLockChat=value;case"sound"->cfg.huntaxeLockSound=value;default->{local("Unknown Huntaxe lock option.");return 0;}}resetWindow();save();return status();}
}
