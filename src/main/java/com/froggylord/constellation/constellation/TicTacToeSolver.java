package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.data.RoomTransform;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class TicTacToeSolver {
    private static OrionConfig cfg;
    private static AABB target;
    private static long lastScanTick = Long.MIN_VALUE;

    private TicTacToeSolver() {}

    public static void init(OrionConfig config) {
        cfg = config;
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!active()) {
            reset();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        long tick = mc.level.getGameTime();
        if (tick != lastScanTick && (tick & 1L) == 0) {
            lastScanTick = tick;
            target = solve(mc);
        }
        if (target != null) ctx.highlight(target, 0xA000FF40, true);
    }

    // ported from Skyblocker (LGPL):
    // skyblock/dungeon/puzzle/TicTacToe.java
    private static AABB solve(Minecraft mc) {
        AABB search = mc.player.getBoundingBox().inflate(21);
        List<ItemFrame> frames = mc.level.getEntitiesOfClass(ItemFrame.class, search, ItemFrame::hasFramedMap);
        if (frames.size() == 9 || (frames.size() & 1) == 0) return null;

        char[][] board = new char[3][3];
        for (ItemFrame frame : frames) {
            MapItemSavedData map = mc.level.getMapData(frame.getFramedMapId(frame.getItem()));
            if (map == null || map.colors.length <= 8256) continue;
            BlockPos pos = frame.blockPosition();
            long[] relative = RoomTransform.actualToRelative(RoomMatch.currentDir(), RoomMatch.anchorX(), RoomMatch.anchorZ(),
                pos.getX(), pos.getY(), pos.getZ());
            int row = switch ((int) relative[1]) {
                case 72 -> 0;
                case 71 -> 1;
                case 70 -> 2;
                default -> -1;
            };
            int column = switch ((int) relative[2]) {
                case 17 -> 0;
                case 16 -> 1;
                case 15 -> 2;
                default -> -1;
            };
            if (row < 0 || column < 0) continue;
            int colour = map.colors[8256] & 0xFF;
            if (colour == 114) board[row][column] = 'X';
            else if (colour == 33) board[row][column] = 'O';
        }

        Move best = bestMove(board);
        if (best == null) return null;
        BlockPos button = findButton(mc, best);
        return button == null ? null : new AABB(button);
    }

    // Devonian and NoammAddons validate the actual stone button rather than
    // blindly drawing the adjacent item-frame coordinate.
    private static BlockPos findButton(Minecraft mc, Move move) {
        for (int x : new int[]{7, 8}) {
            long[] world = RoomTransform.relativeToActual(RoomMatch.currentDir(), RoomMatch.anchorX(), RoomMatch.anchorZ(),
                x, 72 - move.row(), 17 - move.column());
            BlockPos pos = new BlockPos((int) world[0], (int) world[1], (int) world[2]);
            if (mc.level.getBlockState(pos).is(Blocks.STONE_BUTTON)) return pos;
        }
        return null;
    }

    // ported from Skyblocker (LGPL): utils/tictactoe/TicTacToeUtils.java
    private static Move bestMove(char[][] board) {
        Move best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (board[row][column] != '\0') continue;
                board[row][column] = 'O';
                int score = alphabeta(board, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, false);
                board[row][column] = '\0';
                if (best == null || score > bestScore) {
                    best = new Move(row, column);
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private static int alphabeta(char[][] board, int alpha, int beta, int depth, boolean maximize) {
        int score = score(board);
        if (score == 10 || score == -10) return score;
        if (!hasMoves(board)) return 0;
        if (maximize) {
            int best = Integer.MIN_VALUE;
            for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
                if (board[row][column] != '\0') continue;
                board[row][column] = 'O';
                best = Math.max(best, alphabeta(board, alpha, beta, depth + 1, false));
                board[row][column] = '\0';
                alpha = Math.max(alpha, best);
                if (beta <= alpha) return best - depth;
            }
            return best - depth;
        }
        int best = Integer.MAX_VALUE;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
            if (board[row][column] != '\0') continue;
            board[row][column] = 'X';
            best = Math.min(best, alphabeta(board, alpha, beta, depth + 1, true));
            board[row][column] = '\0';
            beta = Math.min(beta, best);
            if (beta <= alpha) return best + depth;
        }
        return best + depth;
    }

    private static int score(char[][] board) {
        for (int i = 0; i < 3; i++) {
            if (board[i][0] != '\0' && board[i][0] == board[i][1] && board[i][1] == board[i][2])
                return board[i][0] == 'O' ? 10 : -10;
            if (board[0][i] != '\0' && board[0][i] == board[1][i] && board[1][i] == board[2][i])
                return board[0][i] == 'O' ? 10 : -10;
        }
        if (board[0][0] != '\0' && board[0][0] == board[1][1] && board[1][1] == board[2][2])
            return board[0][0] == 'O' ? 10 : -10;
        if (board[0][2] != '\0' && board[0][2] == board[1][1] && board[1][1] == board[2][0])
            return board[0][2] == 'O' ? 10 : -10;
        return 0;
    }

    private static boolean hasMoves(char[][] board) {
        for (char[] row : board) for (char cell : row) if (cell == '\0') return true;
        return false;
    }

    private static boolean active() {
        return cfg != null && cfg.ticTacToeSolver && ConstellationClient.loc().inDungeons()
            && RoomMatch.isMatched() && RoomMatch.currentRoom().contains("tic-tac-toe");
    }

    private static void reset() {
        target = null;
        lastScanTick = Long.MIN_VALUE;
    }

    private record Move(int row, int column) {}
}
