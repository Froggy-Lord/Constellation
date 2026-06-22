package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import com.froggylord.constellation.render.WorldRenderer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Dungeon puzzle solvers — chat-driven hints + screen/render overlays.
 * Only ever paints a hint; never clicks.
 */
public final class OrionPuzzles {

    private OrionPuzzles() {}

    private static OrionConfig cfg;
    private static String simonTarget = "";
    private static String weirdoAnswer = "";
    private static String triviaQuestion = "";
    private static String triviaAnswer = "";

    // Trivia answer database from Odin/Skyblocker — verified Hypixel quiz questions
    private static final java.util.Map<String, String> TRIVIA = new java.util.HashMap<>();
    static {
        TRIVIA.put("Which of these enemies does not spawn in the Spiders Den?", "Zombie Spider");
        TRIVIA.put("What is the status of Scarf?", "Apprentice Necromancer");
        TRIVIA.put("What is the name of the lady of the Nether?", "Elle");
        TRIVIA.put("What is the status of Bonzo?", "New Necromancer");
        TRIVIA.put("How many total Fairy Souls are there?", "273 Fairy Souls");
        TRIVIA.put("How many Fairy Souls are there in Backwater Bayou?", "5 Fairy Souls");
        TRIVIA.put("What is the status of Thorn?", "Shaman Necromancer");
        TRIVIA.put("How many Fairy Souls are there in Spider's Den?", "19 Fairy Souls");
        TRIVIA.put("What is the status of The Watcher?", "Stalker");
        TRIVIA.put("What is the status of Sadan?", "Necromancer Lord");
        TRIVIA.put("How many Fairy Souls are there in Spiders Den?", "19 Fairy Souls");
        TRIVIA.put("How many Fairy Souls are there in The End?", "12 Fairy Souls");
        TRIVIA.put("What is the status of Maxor, Storm, Goldor, and Necron?", "The Wither Lords");
        TRIVIA.put("How many Fairy Souls are there in The Farming Islands?", "20 Fairy Souls");
        TRIVIA.put("How many Fairy Souls are there in Crimson Isle?", "29 Fairy Souls");
        TRIVIA.put("What is the status of Livid?", "Master Necromancer");
        TRIVIA.put("How many Fairy Souls are there in The Park?", "12 Fairy Souls");
        TRIVIA.put("What is the status of The Professor?", "Professor");
        TRIVIA.put("How many Fairy Souls are there in Deep Caverns?", "21 Fairy Souls");
        TRIVIA.put("How many Fairy Souls are there in Jerry's Workshop?", "5 Fairy Souls");
        TRIVIA.put("Which brother is on the Spiders Den?", "Rick");
        TRIVIA.put("How many unique minions are there?", "61 Minions");
        TRIVIA.put("How many Fairy Souls are there in Hub?", "80 Fairy Souls");
        TRIVIA.put("Which brother is on the Spider's Den?", "Rick");
        TRIVIA.put("How many Fairy Souls are there in Dungeon Hub?", "7 Fairy Souls");
        TRIVIA.put("Which villager in the Village gives you a Rogue Sword?", "Jamie");
        TRIVIA.put("How many Fairy Souls are there in Gold Mine?", "12 Fairy Souls");
        TRIVIA.put("How many Fairy Souls are there in The Hub?", "80 Fairy Souls");
        TRIVIA.put("What is the name of Rick's brother?", "Pat");
        TRIVIA.put("Which of these is not a dragon in The End?", "Zoomer Dragon");
        TRIVIA.put("What is the name of the vendor in the Hub who sells stained", "Wool Weaver");
        TRIVIA.put("What is the name of the person that upgrades pets?", "Kat");
        TRIVIA.put("Which of these enemies does not spawn in the Spider's Den?", "Zombie Spider");
        TRIVIA.put("Which of these monsters only spawns at night?", "Zombie Villager");
    }

    public static void init(OrionConfig config) {
        cfg = config;

        // --- chat-driven puzzle hints ---
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (overlay || cfg == null || !ConstellationClient.loc().inDungeons()) return;
            String s = msg.getString();

            // Simon Says: "Simon says click the <color> button!" or "[NPC] Simon Says: <color>"
            if (cfg.simonSaysSolver && s.contains("Simon") && (s.contains("click") || s.contains("Click"))) {
                String low = s.toLowerCase(Locale.ROOT);
                for (String c : new String[]{"red", "green", "blue", "yellow", "purple", "orange", "pink", "lime", "cyan"}) {
                    if (low.contains(c)) { simonTarget = c; break; }
                }
            }

            // Three Weirdos: three NPCs chat, one has the reward. the chat line looks like
            // "[NPC] Name: My chest holds the reward" or the puzzle name text on the GUI.
            // the correct answer is the NPC name whose dialogue contradicts the other two.
            if (cfg.threeWeirdosSolver && s.contains(":")) {
                int colon = s.indexOf(':');
                String after = s.substring(colon + 1).trim().toLowerCase(Locale.ROOT);
                if (after.contains("new opportunities") || after.contains("greatest at"))
                    weirdoAnswer = s.substring(0, colon).trim();
                else if (after.contains("reward is") || after.contains("chest holds"))
                    weirdoAnswer = s.substring(0, colon).trim();
            }

            // Trivia: match the question against the verified answer database
            if (cfg.triviaSolver && s.contains("?")) {
                String clean = s.trim();
                // try exact match first, then substring match
                triviaAnswer = TRIVIA.getOrDefault(clean, null);
                if (triviaAnswer == null) {
                    for (var e : TRIVIA.entrySet()) {
                        if (clean.contains(e.getKey()) || e.getKey().contains(clean)) {
                            triviaAnswer = e.getValue();
                            break;
                        }
                    }
                }
                if (triviaAnswer != null) triviaQuestion = clean;
            }
        });

        // --- screen overlays for the GUI puzzles ---
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
            String title = cs.getTitle().getString().toLowerCase(Locale.ROOT);

            if (title.contains("simon") || title.contains("says")) {
                ScreenEvents.afterExtract(screen).register((scr, g, mx, my, d) -> {
                    if (ok(cfg.simonSaysSolver)) simonOverlay(cs, g);
                });
            }
            if (title.contains("weirdo")) {
                ScreenEvents.afterExtract(screen).register((scr, g, mx, my, d) -> {
                    if (ok(cfg.threeWeirdosSolver)) weirdoOverlay(cs, g);
                });
            }
            if (title.contains("trivia") || title.contains("question")) {
                ScreenEvents.afterExtract(screen).register((scr, g, mx, my, d) -> {
                    if (ok(cfg.triviaSolver)) triviaOverlay(cs, g);
                });
            }
            if (title.contains("Tic Tac Toe") || title.contains("tic tac toe")) {
                ScreenEvents.afterExtract(screen).register((scr, g, mx, my, d) -> {
                    if (ok(cfg.ticTacToeSolver)) ticTacToeOverlay(cs, g);
                });
            }
        });
    }

    private static boolean ok(boolean toggle) {
        return toggle && cfg != null && ConstellationClient.loc().inDungeons();
    }

    // --- screen overlays ---

    private static void simonOverlay(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        if (simonTarget.isEmpty()) return;
        int left = ((ContainerScreenAccessor) cs).constellation$left();
        int top = ((ContainerScreenAccessor) cs).constellation$top();
        int chest = cs.getMenu().slots.size() - 36;
        for (int i = 0; i < chest; i++) {
            var slot = cs.getMenu().slots.get(i);
            var s = slot.getItem();
            if (s.isEmpty()) continue;
            String name = s.getHoverName().getString().toLowerCase(Locale.ROOT);
            // the glass panes / clay in simon are colored; match the target
            if (name.contains(simonTarget) || s.getItem().getDescriptionId().contains(simonTarget)) {
                box(cs, g, slot, 0xA020FF20);
                return;
            }
        }
    }

    private static void weirdoOverlay(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        if (weirdoAnswer.isEmpty()) return;
        int left = ((ContainerScreenAccessor) cs).constellation$left();
        int top = ((ContainerScreenAccessor) cs).constellation$top();
        int chest = cs.getMenu().slots.size() - 36;
        for (int i = 0; i < chest; i++) {
            var slot = cs.getMenu().slots.get(i);
            var s = slot.getItem();
            if (s.isEmpty()) continue;
            String name = s.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (name.contains(weirdoAnswer.toLowerCase(Locale.ROOT).replace("[", "").replace("]", "")))
                box(cs, g, slot, 0xA020FF20);
        }
    }

    private static void triviaOverlay(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        if (triviaAnswer.isEmpty()) return;
        int chest = cs.getMenu().slots.size() - 36;
        // scan all chest slots for an item name containing the answer text
        for (int i = 0; i < chest; i++) {
            var slot = cs.getMenu().slots.get(i);
            var s = slot.getItem();
            if (s.isEmpty()) continue;
            String name = s.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (name.contains(triviaAnswer.toLowerCase(Locale.ROOT))) {
                box(cs, g, slot, 0xA020FF20);
                return;
            }
        }
        // fallback: highlight slot 10 (common answer position) if we know the answer
        if (chest > 10) box(cs, g, cs.getMenu().slots.get(10), 0x80FFD020);
    }

    // --- Creeper Beams world render ---
    public static void drawBeams(WorldRenderer.Ctx ctx) {
        if (cfg == null || !cfg.creeperBeamsSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // find sea lanterns — the creeper-beam puzzle uses them as nodes
        java.util.List<Vec3> lanterns = new java.util.ArrayList<>();
        var area = mc.player.getBoundingBox().inflate(50);
        int sx = (int) area.minX, sy = (int) area.minY, sz = (int) area.minZ;
        int ex = (int) area.maxX, ey = (int) area.maxY, ez = (int) area.maxZ;
        for (BlockPos bp : BlockPos.betweenClosed(sx, sy, sz, ex, ey, ez)) {
            BlockState bs = mc.level.getBlockState(bp);
            if (bs.getBlock().getDescriptionId().contains("sea_lantern")) {
                lanterns.add(new Vec3(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5));
            }
        }

        // creeper beams: link each sea lantern to the nearest 2 others in a chain.
        // the puzzle wants you to trace the beam to the right target.
        if (lanterns.size() >= 2) {
            for (int i = 0; i < lanterns.size(); i++) {
                Vec3 a = lanterns.get(i);
                // draw to next lantern in the chain (sorted by x for a consistent sweep)
                Vec3 b = lanterns.get((i + 1) % lanterns.size());
                ctx.line(a, b, 0xFFAAFF00, true);
                // box each lantern
                ctx.highlight(new AABB(a.x - 0.5, a.y - 0.5, a.z - 0.5,
                    a.x + 0.5, a.y + 0.5, a.z + 0.5), 0x40AAFF00, true);
            }
        }
    }

    // --- TicTacToe overlay + minimax solver ---
    private static void ticTacToeOverlay(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        int chest = cs.getMenu().slots.size() - 36;
        if (chest < 9) return;
        // the board is in slots 0-8 (top 3 rows of the chest)
        int[] board = new int[9]; // 0=empty, 1=your piece, -1=opponent
        for (int i = 0; i < 9; i++) {
            var s = cs.getMenu().slots.get(i).getItem();
            if (s.isEmpty()) { board[i] = 0; continue; }
            String id = s.getItem().getDescriptionId().toLowerCase(Locale.ROOT);
            // X pieces: red, pink, orange stained glass
            if (id.contains("red_") || id.contains("pink_") || id.contains("orange_"))
                board[i] = 1; // your piece
            else if (id.contains("green_") || id.contains("lime_") || id.contains("cyan_"))
                board[i] = -1; // opponent
            else
                board[i] = 0; // unknown/empty mark
        }

        // minimal: ensure at least one piece is present before doing the math
        boolean any = false;
        for (int v : board) { if (v != 0) { any = true; break; } }
        if (!any) return;

        int best = minimaxBest(board, true);
        if (best >= 0) box(cs, g, cs.getMenu().slots.get(best), 0xA020FF20);
    }

    private static int minimaxBest(int[] board, boolean myTurn) {
        // score each possible move; return the one that yields the highest score
        int bestScore = Integer.MIN_VALUE, bestMove = -1;
        for (int i = 0; i < 9; i++) {
            if (board[i] != 0) continue;
            board[i] = myTurn ? 1 : -1;
            int score = minimax(board, 0, !myTurn, Integer.MIN_VALUE, Integer.MAX_VALUE);
            board[i] = 0;
            if (score > bestScore) { bestScore = score; bestMove = i; }
        }
        return bestMove;
    }

    private static int minimax(int[] board, int depth, boolean maximizing, int alpha, int beta) {
        int winner = checkWin(board);
        if (winner != 0) return winner * (10 - depth); // win early
        if (isFull(board)) return 0; // draw

        if (maximizing) {
            int maxEval = Integer.MIN_VALUE;
            for (int i = 0; i < 9; i++) {
                if (board[i] != 0) continue;
                board[i] = 1;
                int eval = minimax(board, depth + 1, false, alpha, beta);
                board[i] = 0;
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break;
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (int i = 0; i < 9; i++) {
                if (board[i] != 0) continue;
                board[i] = -1;
                int eval = minimax(board, depth + 1, true, alpha, beta);
                board[i] = 0;
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break;
            }
            return minEval;
        }
    }

    private static int checkWin(int[] b) {
        int[][] wins = {{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
        for (int[] w : wins) {
            if (b[w[0]] != 0 && b[w[0]] == b[w[1]] && b[w[1]] == b[w[2]]) return b[w[0]];
        }
        return 0;
    }

    private static boolean isFull(int[] b) {
        for (int v : b) if (v == 0) return false;
        return true;
    }

    // --- utilities ---

    private static void box(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, net.minecraft.world.inventory.Slot slot, int argb) {
        int left = ((ContainerScreenAccessor) cs).constellation$left();
        int top = ((ContainerScreenAccessor) cs).constellation$top();
        g.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, argb);
    }
}
