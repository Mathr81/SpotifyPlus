package com.mathr81.spotifyplus.beautifullyrics.entities;

public interface SyncableVocals {
    public void animate(double songTimestamp, double deltaTime, boolean isImmediate);
    public boolean isActive();
}
