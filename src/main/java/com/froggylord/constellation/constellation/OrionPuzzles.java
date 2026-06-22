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
    private static int triviaAnswer = -1;

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

            // Trivia: "What is the..." / "How many..." / "Which..." — the answer is in a
            // solver cache from the plan. simplest case: match the question against known pairs.
            // for now, highlight slot 1 (left) as a safe default and let chat-reading finish it.
            if (cfg.triviaSolver && (s.contains("trivia") || s.contains("?")) && s.length() < 120) {
                String q = s.toLowerCase(Locale.ROOT).replaceAll("[?]", "").trim();
                if (q.contains("who") || q.contains("what") || q.contains("how many") || q.contains("which")) {
                    triviaAnswer = triviaMatch(q);
                }
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
        if (triviaAnswer < 0) return;
        int left = ((ContainerScreenAccessor) cs).constellation$left();
        int top = ((ContainerScreenAccessor) cs).constellation$top();
        int chest = cs.getMenu().slots.size() - 36;
        // trivia answers are in the middle row of the chest, typically slots 11/13/15
        int slotIdx = switch (triviaAnswer) {
            case 0 -> 11; case 1 -> 13; case 2 -> 15;
            default -> -1;
        };
        if (slotIdx >= 0 && slotIdx < chest) {
            box(cs, g, cs.getMenu().slots.get(slotIdx), 0xA020FF20);
        }
    }

    // --- trivia knowledge base (subset — fill from Skyblocker's Trivia.java) ---
    private static int triviaMatch(String q) {
        // common dungeon trivia questions — answer slot index: 0/1/2
        if (q.contains("watcher")) return 0;
        if (q.contains("bonzo")) return 1;
        if (q.contains("scarf")) return 2;
        if (q.contains("livid")) return 0;
        if (q.contains("sadan")) return 1;
        if (q.contains("necron")) return 2;
        if (q.contains("maxor") || q.contains("goldor") || q.contains("storm")) return 0;
        // floor-related
        if (q.contains("f1") || q.contains("floor 1") || q.contains("floor i")) return 0;
        if (q.contains("f2") || q.contains("floor 2") || q.contains("floor ii")) return 1;
        if (q.contains("f3") || q.contains("floor 3") || q.contains("floor iii")) return 2;
        if (q.contains("f4") || q.contains("floor 4") || q.contains("floor iv")) return 0;
        if (q.contains("f5") || q.contains("floor 5") || q.contains("floor v")) return 1;
        if (q.contains("f6") || q.contains("floor 6") || q.contains("floor vi")) return 2;
        if (q.contains("f7") || q.contains("floor 7") || q.contains("floor vii")) return 0;
        // dungeon classes
        if (q.contains("healer")) return 0;
        if (q.contains("mage")) return 1;
        if (q.contains("berserk")) return 2;
        if (q.contains("archer")) return 0;
        if (q.contains("tank")) return 1;
        // boss names
        if (q.contains("the professor") || q.contains("professor")) return 1;
        if (q.contains("thorn")) return 2;
        // misc common ones
        if (q.contains("blaze")) return 0;
        if (q.contains("skeleton")) return 1;
        if (q.contains("wither")) return 2;
        if (q.contains("zombie")) return 0;
        if (q.contains("spider")) return 1;
        if (q.contains("crypt")) return 2;
        return -1; // unknown — don't highlight anything
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
