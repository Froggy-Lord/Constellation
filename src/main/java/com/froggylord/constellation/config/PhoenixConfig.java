package com.froggylord.constellation.config;

public class PhoenixConfig extends BaseConfigGroup {

    { enabled = false; }
    @Override public int currentVersion() { return 0; }

    public boolean fullbright = true;
    public boolean noHurtCam = true;
    public boolean noViewBob = true;
    public boolean autoSprint = false;
    public boolean hideLightning = true;
    public boolean hideFallingBlocks = true;
    public boolean hideFireOverlay = true;
    public boolean hideUnderwaterBlur = false;
    public boolean hideStatusEffects = true;
    public boolean hidePlayersInDungeon = false;
    public boolean etherwarpOverlay = true;
    public boolean scrollableTooltips = true;
    public boolean preventPlacingWeapons = false;
    public boolean noDeathAnimation = false;
    public boolean disableVignette = false;
    public boolean disableFog = false;
    public boolean instantSneak = false;
    public boolean signCalculator = true;
    public boolean hideAttachedArrows = true;
    public boolean itemProtection = true;
    public boolean preventDroppingValuable = true;
    public boolean protectStarredItems = true;
    public boolean protectRecombobulatedItems = true;
    public boolean protectValuableConsumables = true;
    public boolean showProtectedItemMarker = true;
    public boolean itemProtectionNotifications = true;
    public java.util.Set<String> protectedItemUuids = new java.util.HashSet<>();
    public java.util.Set<String> protectedSkyblockIds = new java.util.HashSet<>();
    public boolean hotbarScrollLock = false;
    public boolean wardrobeKeybinds = false;
    public boolean wardrobeArmorSets = true;
    public boolean wardrobeEquipmentSets = true;
    public boolean wardrobePreventUnequip = true;
    public boolean wardrobeSound = false;
    public boolean wardrobeFeedback = true;
    public boolean wardrobeConsumeInvalidKeys = true;
    public boolean wardrobeSlotLabels = true;
    public boolean wardrobeLabelTopRight = false;
    public boolean wardrobeSwapEnabled = false;
    public boolean wardrobeOpenKeybind = true;
    public String wardrobeKeyStyle = "HOTBAR";
    public int wardrobeClickCooldownMillis = 250;
    public int wardrobeSwapSlotOne = 1;
    public int wardrobeSwapSlotTwo = 2;
    public int wardrobeLabelColor = 0xFF55AAFF;
    // ported from Devonian (GPL-3.0-only): features/misc/inventory/SlotBinding.kt
    public boolean slotBinding = false;
    public boolean slotBindingProtect = true;
    public boolean slotBindingDynamicProfiles = true;
    public boolean slotBindingBorders = true;
    public boolean slotBindingLines = true;
    public boolean slotBindingHoverOnly = false;
    public boolean slotBindingShowWhileBinding = true;
    public boolean slotBindingSound = true;
    public boolean slotBindingFeedback = true;
    public boolean slotBindingAllowHotbarKeys = false;
    public int slotBindingLineMode = 2;
    public int slotBindingLineWidth = 1;
    public int slotBindingFixedColor = 0xFF55FFAA;
    public boolean slotBindingUseFixedColor = false;
    public String slotBindingSelectedProfile = "default";
    public java.util.Map<String, SlotBindingProfile> slotBindingProfiles = new java.util.LinkedHashMap<>();
    public boolean autoSaveReminder = true;
    public boolean hotbarLock = false;
    public boolean hotbarSwapHelper = false;
    public boolean viewmodelCustomize = false;
    public boolean nameTagShadows = true;
    public boolean disableNpcDialogue = true;
    // ported from Devonian (GPL-3.0-only): features/misc/CenturyCakeTimer.kt
    public boolean centuryCakeTimer = false;
    public boolean centuryCakeHud = true;
    public boolean centuryCakeOnlyExpired = true;
    public boolean centuryCakeShowUnknown = true;
    public boolean centuryCakeChatHelper = true;
    public boolean centuryCakeWarning = true;
    public boolean centuryCakeWarningChat = true;
    public boolean centuryCakeWarningTitle = false;
    public boolean centuryCakeWarningSound = true;
    public boolean centuryCakeExpiredChat = true;
    public boolean centuryCakeExpiredTitle = false;
    public boolean centuryCakeExpiredSound = true;
    public boolean centuryCakeShowSeconds = false;
    public boolean centuryCakeShowProfile = false;
    public int centuryCakeDurationHours = 48;
    public int centuryCakeWarningMinutes = 60;
    public int centuryCakeHelperWindowMinutes = 5;
    public int centuryCakeActiveColor = 0xFF55FF55;
    public int centuryCakeWarningColor = 0xFFFFFF55;
    public int centuryCakeExpiredColor = 0xFFFF5555;
    public int centuryCakeUnknownColor = 0xFFAAAAAA;
    public java.util.Map<String, Long> centuryCakeExpiryByProfile = new java.util.LinkedHashMap<>();
    // ported from Devonian (GPL-3.0-only): features/misc/WorldAge.kt
    public boolean worldAge = false;
    public boolean worldAgeHud = true;
    public boolean worldAgeHypixelOnly = true;
    public boolean worldAgeShowDay = true;
    public boolean worldAgeOneBased = false;
    public boolean worldAgeShowClock = true;
    public boolean worldAgeTwelveHourClock = false;
    public boolean worldAgeShowPhase = true;
    public boolean worldAgeShowTransition = true;
    public boolean worldAgeShowRealAge = false;
    public boolean worldAgeShowTicks = false;
    public int worldAgeDayColor = 0xFFFFAA00;
    public int worldAgeClockColor = 0xFF55FFFF;
    public int worldAgeDayPhaseColor = 0xFFFFFF55;
    public int worldAgeNightPhaseColor = 0xFF5555FF;
    // ported from Devonian (GPL-3.0-only): features/misc/Misc.kt, mixin/ScreenshotMixin.java
    public boolean autoCopyScreenshot = false;
    public boolean screenshotClipboardSuccessActionbar = true;
    public boolean screenshotClipboardSuccessChat = false;
    public boolean screenshotClipboardFailureChat = true;
    public boolean screenshotClipboardSound = false;
    public boolean screenshotClipboardKeepLast = true;
    public int screenshotClipboardRetries = 3;
    public int screenshotClipboardRetryDelayMillis = 75;

    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/speedpreset/SpeedPresets.java
    public boolean speedPresets = true;
    public boolean speedPresetsAutoProfile = false;
    public boolean speedPresetsFeedbackChat = false;
    public boolean speedPresetsFeedbackActionbar = true;
    public boolean speedPresetsFeedbackSound = true;
    public boolean speedPresetsHud = true;
    public boolean speedPresetsHudRecentOnly = true;
    public boolean speedPresetsHudName = true;
    public boolean speedPresetsHudSpeed = true;
    public boolean speedPresetsHudProfile = false;
    public int speedPresetsCooldownMillis = 500;
    public int speedPresetsHudSeconds = 4;
    public String speedPresetsSelectedProfile = "default";
    public java.util.Map<String, java.util.Map<String,Integer>> speedPresetProfiles = new java.util.LinkedHashMap<>();

    // ported from Devonian (GPL-3.0-only): features/misc/PetDisplay.kt, ShowSelectedPet.kt, tooltip/PetXP.kt
    public boolean petDisplay = true;
    public boolean petDisplayHud = true;
    public boolean petDisplayIcon = true;
    public boolean petDisplayLevel = true;
    public boolean petDisplayCosmeticLevel = true;
    public boolean petDisplaySkin = true;
    public boolean petDisplayRarity = true;
    public boolean petDisplayHeldItem = true;
    public boolean petDisplayXp = true;
    public boolean petDisplayRate = true;
    public boolean petDisplayEta = true;
    public boolean petDisplaySource = false;
    public boolean petDisplayPersistProfiles = true;
    public boolean petDisplayHighlightSelected = true;
    public boolean petDisplayTooltipTotalXp = true;
    public boolean petDisplayAutopetTitle = false;
    public boolean petDisplayAutopetTitleDungeonOnly = true;
    public int petDisplayHighlightColor = 0x8055FFFF;
    public int petDisplayNameColor = 0xFFFFAA00;
    public int petDisplayProgressColor = 0xFF55FF55;
    public int petDisplayInfoColor = 0xFF55FFFF;
    public java.util.Map<String, PetDisplayData> activePetsByProfile = new java.util.LinkedHashMap<>();

    // ported from SkyHanni (LGPL-2.1): api/CollectionApi.kt
    // ported from SkyblockCollectionTracker (LGPL-2.1): tracker/collection/TrackingRates.java, config/categories/overlay/CollectionConfig.java
    public boolean collectionTracker = true;
    public boolean collectionTrackerHud = true;
    public boolean collectionTrackerAutoSelect = true;
    public boolean collectionTrackerPersistProfiles = true;
    public boolean collectionTrackerShowTotal = true;
    public boolean collectionTrackerShowSession = true;
    public boolean collectionTrackerShowRate = true;
    public boolean collectionTrackerShowGoal = true;
    public boolean collectionTrackerShowEta = true;
    public boolean collectionTrackerShowLastGain = false;
    public boolean collectionTrackerShowFreshness = true;
    public int collectionTrackerNameColor = 0xFFFFAA00;
    public int collectionTrackerValueColor = 0xFF55FFFF;
    public int collectionTrackerProgressColor = 0xFF55FF55;
    public String collectionTrackerSelected = "";
    public long collectionTrackerGoal;
    public java.util.Map<String, java.util.Map<String, CollectionTrackerData>> collectionsByProfile = new java.util.LinkedHashMap<>();

    // ported from Devonian (GPL-3.0-only): features/misc/inventory/SlotBinding.kt
    public static class SlotBindingProfile {
        public String area = "";
        public java.util.Map<Integer, java.util.List<Integer>> binds = new java.util.LinkedHashMap<>();
        public java.util.Map<Integer, Integer> last = new java.util.LinkedHashMap<>();
        public java.util.Map<Integer, Integer> colors = new java.util.LinkedHashMap<>();
    }

    public static class PetDisplayData {
        public String name = "";
        public int level = -1;
        public int cosmeticLevel = -1;
        public String rarity = "";
        public String heldItem = "";
        public boolean skinned;
        public double totalXp = -1;
        public double levelProgress = -1;
        public String source = "";
        public long updatedAt;
    }

    public static class CollectionTrackerData {
        public String name = "";
        public long amount;
        public long updatedAt;
        public long goal;
    }
}
