package com.froggylord.constellation.hud;

import com.froggylord.constellation.constellation.ArtemisStarlyn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class StarlynCouponHudWidget extends ThemedHudWidget {
    private HudPosition position;private final BooleanSupplier gate;private boolean enabled=true;
    public StarlynCouponHudWidget(HudPosition position,BooleanSupplier gate){this.position=position;this.gate=gate;}
    @Override public String id(){return"artemis-starlyn-coupons";}@Override public HudPosition position(){return position;}
    @Override public void setPosition(HudPosition position){this.position=position;}@Override public boolean isEnabled(){return enabled&&gate.getAsBoolean();}
    @Override public void setEnabled(boolean enabled){this.enabled=enabled;}@Override public boolean visibleNow(){return isEnabled()&&ArtemisStarlyn.visible();}
    @Override public String editorLabel(){return"Agatha Coupon Profit";}@Override protected String title(){return"Profit per Agatha Coupon";}
    @Override protected List<Row> rows(){
        var cfg=ArtemisStarlyn.config();if(cfg==null)return List.of();List<Row> out=new ArrayList<>();
        for(var row:ArtemisStarlyn.rows()){String label=cfg.starlynCouponShowItem?row.name():"Offer";if(cfg.starlynCouponShowCouponCount)label+=" ("+row.coupons()+" coupons)";StringBuilder value=new StringBuilder(ArtemisStarlyn.coins(row.perCoupon(),row.complete())+"/coupon");if(cfg.starlynCouponShowSell)value.append(" | sell ").append(ArtemisStarlyn.coins(row.sell(),row.complete()));if(cfg.starlynCouponShowCost)value.append(" | cost ").append(ArtemisStarlyn.coins(row.cost(),row.complete()));if(cfg.starlynCouponShowProfit)value.append(" | total ").append(ArtemisStarlyn.coins(row.profit(),row.complete()));out.add(new Row("",label,value.toString(),!row.complete()?cfg.starlynCouponUnknownColor:row.perCoupon()>=0?cfg.starlynCouponPositiveColor:cfg.starlynCouponNegativeColor));}
        return out;
    }
}
