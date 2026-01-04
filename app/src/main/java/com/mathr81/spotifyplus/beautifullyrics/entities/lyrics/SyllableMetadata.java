package com.mathr81.spotifyplus.beautifullyrics.entities.lyrics;

import com.google.gson.annotations.SerializedName;

public class SyllableMetadata extends VocalMetadata {
    @SerializedName("IsPartOfWord")
    public boolean isPartOfWord;
}
