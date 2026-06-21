package com.froggylord.constellation.render;

/**
 * Colour palette and theme constants for the nebula/constellation UI.
 * Deep space base with warm amber-gold accents, star-white text.
 */
public final class NebulaTheme {

    private NebulaTheme() {}

    // -- palette --
    public static final int BG_DEEP      = 0xFF0A0A14;  // near-black with blue tint
    public static final int BG_PANEL     = 0xCC12121F;  // semi-transparent panel
    public static final int BORDER       = 0x33252535;  // subtle constellation-line border

    public static final int STAR_WHITE   = 0xFFF0EDE0;  // primary text
    public static final int STAR_DIM     = 0xFFC4C0B8;  // secondary text
    public static final int STAR_MUTED   = 0xFF6E6B65;  // tertiary/muted text

    public static final int ACCENT_GOLD  = 0xFFFFB830;  // warm amber-gold accent
    public static final int ACCENT_DIM   = 0x66FFB830;  // translucent accent
    public static final int ACCENT_BRIGHT= 0xFFFFCC55;  // bright gold for highlights

    public static final int RED          = 0xFFFF5252;  // warning/danger
    public static final int GREEN        = 0xFF52FF52;  // success/ready
    public static final int AQUA         = 0xFF52FFFF;  // info/mana
    public static final int PURPLE       = 0xFFC252FF;  // special

    // -- hud accent line colour --
    public static final int HUD_ACCENT   = ACCENT_GOLD;

    // -- secret/waypoint colours --
    public static final int CHEST_COLOUR    = 0xFFFFAA00;
    public static final int LEVER_COLOUR    = 0xFFAAFF00;
    public static final int WITHER_COLOUR   = 0xFF555555;
    public static final int BAT_COLOUR      = 0xFFAA00AA;
    public static final int KEY_COLOUR      = 0xFFFF5555;
    public static final int ITEM_COLOUR     = 0xFFFFFFFF;
    public static final int SUPERBOOM_COLOUR= 0xFFFF5555;
    public static final int STONK_COLOUR    = 0xFF00AAFF;
    public static final int PEARL_COLOUR    = 0xFF55FFFF;
    public static final int FAIRY_COLOUR    = 0xFFFF55FF;
    public static final int PRINCE_COLOUR   = 0xFFFFD700;

    // -- route line colours --
    public static final int ROUTE_WALK    = 0x99FFFFFF;
    public static final int ROUTE_ETHER   = 0x9900FFFF;
    public static final int ROUTE_INTERACT= 0x9900FF00;
    public static final int ROUTE_TNT     = 0x99FF0000;
    public static final int ROUTE_PEARL   = 0x99FF00FF;

    public static void init() {}
}
