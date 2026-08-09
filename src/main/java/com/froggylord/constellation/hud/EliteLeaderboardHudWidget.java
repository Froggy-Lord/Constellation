package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.HerculesEliteLeaderboards;
import com.froggylord.constellation.constellation.HerculesGardenTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/leaderboarddisplays/EliteLeaderboardDisplayBase.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/leaderboarddisplays/WeightDisplay.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/leaderboarddisplays/CropDisplay.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/leaderboarddisplays/PestDisplay.kt
public final class EliteLeaderboardHudWidget extends ThemedHudWidget {
    private final HerculesEliteLeaderboards.Kind kind;private final BooleanSupplier configEnabled;private HudPosition position;private boolean enabled=true;
    public EliteLeaderboardHudWidget(HudPosition p,HerculesEliteLeaderboards.Kind k,BooleanSupplier e){position=p;kind=k;configEnabled=e;}
    @Override public String id(){return"elite-"+kind.name().toLowerCase(Locale.ROOT);}@Override public HudPosition position(){return position;}@Override public void setPosition(HudPosition p){position=p;}@Override public boolean isEnabled(){return enabled&&configEnabled.getAsBoolean();}@Override public void setEnabled(boolean e){enabled=e;}@Override public boolean visibleNow(){return isEnabled()&&HerculesEliteLeaderboards.snapshot(kind)!=null;}@Override public String editorLabel(){return"Elite "+title();}
    @Override protected String title(){return switch(kind){case WEIGHT->"Farming Weight";case CROP->"Crop Leaderboard";case PEST->"Pest Leaderboard";};}
    @Override protected List<Row> rows(){var s=HerculesEliteLeaderboards.snapshot(kind);if(s==null)return List.of();var cfg=HerculesGardenTracker.config();List<Row> out=new ArrayList<>();
        if(s.state()!=HerculesEliteLeaderboards.State.READY){out.add(new Row("","Status",switch(s.state()){case WAITING->"Waiting";case LOADING->"Loading";case UNRANKED->"Not ranked";case UNAVAILABLE->"Unavailable";default->"Ready";}));if(s.state()==HerculesEliteLeaderboards.State.UNRANKED&&s.amount()>0)out.add(new Row("","Amount",HerculesEliteLeaderboards.format(s.amount())));return out;}
        out.add(new Row("",s.label(),HerculesEliteLeaderboards.format(s.amount())));if(cfg.eliteShowRank)out.add(new Row("","Rank","#"+String.format(Locale.ROOT,"%,d",s.rank())));
        if(cfg.eliteShowOvertake&&s.next()!=null){out.add(new Row("","Next #"+(s.rank()-1),s.next().name()+" +"+HerculesEliteLeaderboards.format(s.next().amount()-s.amount())));double eta=HerculesEliteLeaderboards.etaSeconds(s);if(cfg.eliteShowEta&&(cfg.eliteEtaAlways||eta>=0))out.add(new Row("","Overtake ETA",eta<0?"Waiting":time(eta)));}
        if(cfg.eliteShowPrevious&&s.previous()!=null)out.add(new Row("","Behind",s.previous().name()+" "+HerculesEliteLeaderboards.format(s.amount()-s.previous().amount())));
        if(cfg.eliteUseRankGoals&&s.goal()>0&&s.rank()>s.goal())out.add(new Row("","Rank goal","#"+String.format(Locale.ROOT,"%,d",s.goal())));return out;}
    @Override protected List<Row> previewRows(){return List.of(new Row("","Amount","1.24b"),new Row("","Rank","#12,430"),new Row("","Next #12,429","Farmer +2.8m"),new Row("","Overtake ETA","42m 18s"));}
    private static String time(double raw){long s=Math.max(0,(long)Math.ceil(raw));long d=s/86400;s%=86400;long h=s/3600;s%=3600;long m=s/60;s%=60;if(d>0)return d+"d "+h+"h";if(h>0)return h+"h "+m+"m";if(m>0)return m+"m "+s+"s";return s+"s";}
}
