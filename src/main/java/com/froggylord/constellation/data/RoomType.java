package com.froggylord.constellation.data;

public enum RoomType {
    NORMAL, PUZZLE, TRAP, BLOOD, FAIRY, MINIBOSS, BOSS, UNKNOWN;

    public boolean skipsFingerprint() {
        return this != NORMAL && this != UNKNOWN;
    }
}
