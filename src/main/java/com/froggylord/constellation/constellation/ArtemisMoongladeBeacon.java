package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.ArtemisConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/foraging/MoongladeBeacon.kt
// cross-checked against NoFrills (GPL-3.0-only): features/solvers/BeaconTuningSolver.java
public final class ArtemisMoongladeBeacon {
    public record TuneView(String name,String referenceColor,String currentColor,Integer colorOffset,
                           String referenceSpeed,String currentSpeed,Integer speedOffset,
                           String referencePitch,String currentPitch,Integer pitchOffset,boolean solved){}
    private enum Color {
        WHITE,ORANGE,MAGENTA,LIGHT_BLUE,YELLOW,LIME,PINK,CYAN,PURPLE,BLUE,BROWN,GREEN,RED;
        static Color from(ItemStack stack){
            if(stack==null||stack.isEmpty())return null;
            String path=BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if(!path.endsWith("_stained_glass_pane"))return null;
            try{return valueOf(path.substring(0,path.length()-"_stained_glass_pane".length()).toUpperCase(Locale.ROOT));}
            catch(IllegalArgumentException ignored){return null;}
        }
        String display(){String value=name().toLowerCase(Locale.ROOT).replace('_',' ');return Character.toUpperCase(value.charAt(0))+value.substring(1);}
    }
    private enum Speed {
        ONE(52,1),TWO(42,2),THREE(32,3),FOUR(22,4),FIVE(12,5);
        final int ticks,level;Speed(int ticks,int level){this.ticks=ticks;this.level=level;}
        static Speed closest(double ticks){return Arrays.stream(values()).min((a,b)->Double.compare(Math.abs(a.ticks-ticks),Math.abs(b.ticks-ticks))).orElse(null);}
        static Speed level(int level){return Arrays.stream(values()).filter(v->v.level==level).findFirst().orElse(null);}
        String display(){return Integer.toString(level);}
    }
    private enum Pitch {
        LOW(.0952381f),NORMAL(.7936508f),HIGH(1.4920635f);
        final float packetPitch;Pitch(float packetPitch){this.packetPitch=packetPitch;}
        static Pitch from(float value){return Arrays.stream(values()).filter(v->Math.abs(v.packetPitch-value)<.0001f).findFirst().orElse(null);}
        static Pitch lore(String value){return Arrays.stream(values()).filter(v->v.name().equalsIgnoreCase(value)).findFirst().orElse(null);}
        String display(){String value=name().toLowerCase(Locale.ROOT);return Character.toUpperCase(value.charAt(0))+value.substring(1);}
    }
    private static final Pattern CURRENT_COLOR=Pattern.compile("^Current color: (.+)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern CURRENT_SPEED=Pattern.compile("^Current speed: ([1-5])$",Pattern.CASE_INSENSITIVE);
    private static final Pattern CURRENT_PITCH=Pattern.compile("^Current pitch: (Low|Normal|High)$",Pattern.CASE_INSENSITIVE);
    private static final Tune NORMAL=new Tune(false),ENCHANTED=new Tune(true);
    private static ArtemisConfig cfg;
    private static boolean initialized,upgrade,readyAlerted,stereoWarned;
    private static String title="";
    private static int ticks;

    private ArtemisMoongladeBeacon(){}

    public static void init(ArtemisConfig config){
        cfg=config;if(initialized)return;initialized=true;
        ConstellationClient.tick().every(1,"artemis-moonglade-beacon",ArtemisMoongladeBeacon::tick);
        ConstellationClient.instance().packets().register(packet->{if(packet instanceof ClientboundSoundPacket sound)onSound(sound);});
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
    }

    private static void tick(){
        Minecraft mc=Minecraft.getInstance();
        if(!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)||!menu(screen)){if(!title.isEmpty())reset();return;}
        ticks++;
        if(cfg.moongladeBeaconStereoWarning&&!stereoWarned&&mc.player!=null&&isStereoPants(mc.player.getItemBySlot(EquipmentSlot.LEGS))){
            stereoWarned=true;local("Stereo Pants sounds can interfere with pitch detection.");
        }
        NORMAL.read(screen);if(upgrade)ENCHANTED.read(screen);else ENCHANTED.clear();
        boolean solved=NORMAL.solved()&&(!upgrade||ENCHANTED.solved());
        if(solved&&!readyAlerted&&cfg.moongladeBeaconReadyAlert){readyAlerted=true;alert();}
        if(!solved)readyAlerted=false;
    }

    private static boolean menu(AbstractContainerScreen<?> screen){
        if(cfg==null||!cfg.enabled||!cfg.moongladeBeacon||ConstellationClient.loc().area()!=SkyblockArea.GALATEA)return false;
        String current=clean(screen.getTitle().getString());
        boolean accepted=current.equals("Tune Frequency")||current.equals("Upgrade Signal Strength");
        if(!accepted)return false;
        if(!current.equals(title)){reset();title=current;upgrade=current.equals("Upgrade Signal Strength");}
        return true;
    }

    private static void onSound(ClientboundSoundPacket packet){
        if(title.isEmpty()||!activeArea())return;
        if(!packet.getSound().value().location().equals(SoundEvents.NOTE_BLOCK_BASS.value().location()))return;
        Pitch pitch=Pitch.from(packet.getPitch());if(pitch==null)return;
        long now=System.currentTimeMillis(),tolerance=Math.clamp(cfg.moongladeBeaconPitchToleranceMillis,25,500);
        Tune best=null;long distance=Long.MAX_VALUE;
        for(Tune tune:upgrade?List.of(NORMAL,ENCHANTED):List.of(NORMAL)){
            long d=Math.abs(now-tune.nextReferencePitchAt);
            if(tune.nextReferencePitchAt>0&&d<=tolerance&&d<distance&&Math.abs(now-tune.nextCurrentPitchAt)>d){best=tune;distance=d;}
        }
        if(best!=null)best.hear(pitch);
    }

    public static void drawSlot(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> screen,Slot slot){
        if(slot==null||!menu(screen))return;
        Tune tune=tuneForControl(slot.index);if(tune==null)return;
        Integer offset=tune.offset(slot.index);
        if(offset==null)return;
        if(offset==0&&cfg.moongladeBeaconHighlightCorrect)graphics.fill(slot.x,slot.y,slot.x+16,slot.y+16,cfg.moongladeBeaconCorrectColor);
        if(offset!=0&&cfg.moongladeBeaconOffsetLabels)graphics.text(Minecraft.getInstance().font,(offset>0?"+":"")+offset,slot.x+1,slot.y+1,0xFF55FF55,true);
    }

    public static boolean shouldBlockClick(AbstractContainerScreen<?> screen,Slot slot,int button,ContainerInput input){
        if(slot==null||!menu(screen)||!cfg.moongladeBeaconPreventOverClicking||bypass())return false;
        Tune tune=tuneForControl(slot.index);Integer offset=tune==null?null:tune.offset(slot.index);
        if(offset==null||offset!=0)return false;
        local("That setting already matches. Hold Control to bypass.");
        return true;
    }

    public static boolean shouldMiddleClick(AbstractContainerScreen<?> screen,int slotId,int button,ContainerInput input){
        if(!cfg.moongladeBeaconUseMiddleClick||button!=0||input!=ContainerInput.PICKUP||!menu(screen))return false;
        return tuneForControl(slotId)!=null;
    }

    public static boolean visible(){return cfg!=null&&cfg.enabled&&cfg.moongladeBeacon&&cfg.moongladeBeaconHud&&!title.isEmpty();}
    public static ArtemisConfig config(){return cfg;}
    public static List<TuneView> views(){
        if(title.isEmpty())return List.of();
        List<TuneView> out=new ArrayList<>();out.add(NORMAL.view("Normal"));
        if(upgrade)out.add(ENCHANTED.view("Enchanted"));
        return out;
    }

    private static Tune tuneForControl(int slot){
        if(NORMAL.isControl(slot))return NORMAL;
        return upgrade&&ENCHANTED.isControl(slot)?ENCHANTED:null;
    }

    private static void alert(){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        if(cfg.moongladeBeaconReadyChat)local("All beacon settings match.");
        if(cfg.moongladeBeaconReadyTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("Moonglade Beacon Ready").withColor(cfg.moongladeBeaconReadyColor&0xFFFFFF));}
        if(cfg.moongladeBeaconReadySound)mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP,.8f,1.3f);
    }

    private static final class Tune {
        final boolean enchanted;Color referenceColor,currentColor;Speed referenceSpeed,currentSpeed;Pitch referencePitch,currentPitch;
        int referenceSlot=-1,lastReferenceTick;long nextReferencePitchAt,nextCurrentPitchAt;
        final List<Integer> intervals=new ArrayList<>();final List<Pitch> pitches=new ArrayList<>();
        Tune(boolean enchanted){this.enchanted=enchanted;}
        int base(){return upgrade&&!enchanted?37:46;}
        int colorSlot(){return base();}int speedSlot(){return base()+2;}int pitchSlot(){return base()+4;}
        boolean isControl(int slot){return slot==colorSlot()||slot==speedSlot()||slot==pitchSlot();}
        void read(AbstractContainerScreen<?> screen){
            List<Slot> slots=screen.getMenu().slots;
            for(int i=10;i<=16&&i<slots.size();i++)readMoving(slots.get(i),true);
            for(int i=28;i<=34&&i<slots.size();i++)readMoving(slots.get(i),false);
            if(pitchSlot()>=slots.size())return;
            currentColor=colorLore(slots.get(colorSlot()).getItem());
            currentSpeed=speedLore(slots.get(speedSlot()).getItem());
            currentPitch=pitchLore(slots.get(pitchSlot()).getItem());
            if(currentSpeed!=null)nextCurrentPitchAt=System.currentTimeMillis()+currentSpeed.ticks*50L;
        }
        void readMoving(Slot slot,boolean reference){
            ItemStack stack=slot.getItem();if(stack.isEmpty()||stack.hasFoil()!=enchanted)return;
            Color color=Color.from(stack);if(color==null)return;
            if(reference){
                referenceColor=color;
                if(referenceSlot!=slot.index){
                    if(referenceSlot>=0){
                        int difference=ticks-lastReferenceTick;if(difference>0){intervals.add(difference);if(intervals.size()>Math.clamp(cfg.moongladeBeaconSpeedSamples,3,20))intervals.remove(0);calculateSpeed();}
                    }
                    referenceSlot=slot.index;lastReferenceTick=ticks;
                    if(referenceSpeed!=null)nextReferencePitchAt=System.currentTimeMillis()+referenceSpeed.ticks*50L;
                }
            }else currentColor=color;
        }
        void calculateSpeed(){
            if(intervals.size()<(upgrade?3:2))return;
            List<Integer> sorted=intervals.stream().sorted().toList();double median=sorted.size()%2==0?(sorted.get(sorted.size()/2-1)+sorted.get(sorted.size()/2))/2.0:sorted.get(sorted.size()/2);
            double average=intervals.stream().filter(v->v>=median*.8&&v<=median*1.2).mapToInt(Integer::intValue).average().orElse(median);
            referenceSpeed=Speed.closest(average);
        }
        void hear(Pitch pitch){
            pitches.add(pitch);if(pitches.size()>6)pitches.remove(0);
            if(pitches.size()<3)return;
            referencePitch=Arrays.stream(Pitch.values()).max((a,b)->Long.compare(pitches.stream().filter(v->v==a).count(),pitches.stream().filter(v->v==b).count())).orElse(null);
        }
        Integer offset(int slot){
            if(slot==colorSlot())return ArtemisMoongladeBeacon.offset(referenceColor,currentColor);
            if(slot==speedSlot())return ArtemisMoongladeBeacon.offset(referenceSpeed,currentSpeed);
            if(slot==pitchSlot())return ArtemisMoongladeBeacon.offset(referencePitch,currentPitch);
            return null;
        }
        boolean solved(){return offset(colorSlot())!=null&&offset(colorSlot())==0&&offset(speedSlot())!=null&&offset(speedSlot())==0&&offset(pitchSlot())!=null&&offset(pitchSlot())==0;}
        TuneView view(String name){return new TuneView(name,display(referenceColor),display(currentColor),offset(colorSlot()),display(referenceSpeed),display(currentSpeed),offset(speedSlot()),display(referencePitch),display(currentPitch),offset(pitchSlot()),solved());}
        void clear(){referenceColor=currentColor=null;referenceSpeed=currentSpeed=null;referencePitch=currentPitch=null;referenceSlot=-1;lastReferenceTick=0;nextReferencePitchAt=nextCurrentPitchAt=0;intervals.clear();pitches.clear();}
    }

    private static <E extends Enum<E>> Integer offset(E reference,E current){
        if(reference==null||current==null)return null;int size=reference.getDeclaringClass().getEnumConstants().length;
        int value=(reference.ordinal()-current.ordinal()+size)%size;return value>size/2?value-size:value;
    }
    private static String display(Enum<?> value){
        if(value==null)return"?";if(value instanceof Color color)return color.display();if(value instanceof Speed speed)return speed.display();if(value instanceof Pitch pitch)return pitch.display();return value.name();
    }
    private static Color colorLore(ItemStack stack){for(String line:lore(stack)){Matcher m=CURRENT_COLOR.matcher(line);if(m.matches())for(Color color:Color.values())if(color.display().equalsIgnoreCase(m.group(1)))return color;}return null;}
    private static Speed speedLore(ItemStack stack){for(String line:lore(stack)){Matcher m=CURRENT_SPEED.matcher(line);if(m.matches())return Speed.level(Integer.parseInt(m.group(1)));}return null;}
    private static Pitch pitchLore(ItemStack stack){for(String line:lore(stack)){Matcher m=CURRENT_PITCH.matcher(line);if(m.matches())return Pitch.lore(m.group(1));}return null;}
    private static List<String> lore(ItemStack stack){ItemLore lore=stack.get(DataComponents.LORE);return lore==null?List.of():lore.lines().stream().map(v->clean(v.getString())).toList();}
    private static boolean isStereoPants(ItemStack stack){return stack!=null&&!stack.isEmpty()&&clean(stack.getHoverName().getString()).equalsIgnoreCase("Stereo Pants");}
    private static boolean bypass(){if(!cfg.moongladeBeaconControlBypass)return false;var window=Minecraft.getInstance().getWindow();return InputConstants.isKeyDown(window,GLFW.GLFW_KEY_LEFT_CONTROL)||InputConstants.isKeyDown(window,GLFW.GLFW_KEY_RIGHT_CONTROL);}
    private static boolean activeArea(){return cfg!=null&&cfg.enabled&&cfg.moongladeBeacon&&ConstellationClient.loc().area()==SkyblockArea.GALATEA;}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim().replaceAll("\\s+"," ");}
    private static void reset(){title="";upgrade=false;ticks=0;readyAlerted=false;stereoWarned=false;NORMAL.clear();ENCHANTED.clear();}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§2[Beacon] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource>d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("moongladebeacon").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("tolerance").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("milliseconds",IntegerArgumentType.integer(25,500)).executes(c->{cfg.moongladeBeaconPitchToleranceMillis=IntegerArgumentType.getInteger(c,"milliseconds");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("samples").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(3,20)).executes(c->{cfg.moongladeBeaconSpeedSamples=IntegerArgumentType.getInteger(c,"amount");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Moonglade solver "+on(cfg.moongladeBeacon)+", middle click "+on(cfg.moongladeBeaconUseMiddleClick)+", over-click protection "+on(cfg.moongladeBeaconPreventOverClicking)+".");return 1;}
    private static int option(String name,String raw){Boolean value=parse(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.moongladeBeacon=value;case"hud"->cfg.moongladeBeaconHud=value;case"middleclick","middle"->cfg.moongladeBeaconUseMiddleClick=value;case"overclick","protect"->cfg.moongladeBeaconPreventOverClicking=value;case"control","bypass"->cfg.moongladeBeaconControlBypass=value;case"highlight"->cfg.moongladeBeaconHighlightCorrect=value;case"labels","offsetlabels"->cfg.moongladeBeaconOffsetLabels=value;case"reference"->cfg.moongladeBeaconShowReference=value;case"current"->cfg.moongladeBeaconShowCurrent=value;case"offsets"->cfg.moongladeBeaconShowOffsets=value;case"solved"->cfg.moongladeBeaconShowSolved=value;case"stereo"->cfg.moongladeBeaconStereoWarning=value;case"alert"->cfg.moongladeBeaconReadyAlert=value;case"chat"->cfg.moongladeBeaconReadyChat=value;case"title"->cfg.moongladeBeaconReadyTitle=value;case"sound"->cfg.moongladeBeaconReadySound=value;default->{local("Unknown beacon option.");return 0;}}save();return status();}
    private static Boolean parse(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
}
