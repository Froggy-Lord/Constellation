package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.render.WorldRenderer;

public final class OrionPuzzles {

    private OrionPuzzles() {}

    private static OrionConfig cfg;

    public static void init(OrionConfig config) {
        cfg = config;

        // three weirdos and quiz live in their own classes now — they are world puzzles,
        // not container screens, so the old overlays here could never fire
        ThreeWeirdosSolver.init(config);
        QuizSolver.init(config);
        SimonSaysSolver.init(config);
        TicTacToeSolver.init(config);
    }

    

    public static void drawBeams(WorldRenderer.Ctx ctx) {
        SimonSaysSolver.draw(ctx);
        TicTacToeSolver.draw(ctx);
        if (cfg == null || !cfg.creeperBeamsSolver) return;
        if (!ConstellationClient.loc().inDungeons()) return;
        // only in the actual creeper beams room - sea lanterns show up all over the place otherwise
        if (!RoomMatch.isMatched() || !RoomMatch.currentRoom().contains("creeper")) return;
        // proper correct-links solve (ported from Skyblocker) — the 5 beam pairs
        // whose lines pass closest to the creeper, not a naive lantern ring
        CreeperBeamsSolver.draw(ctx);
    }

    
}
