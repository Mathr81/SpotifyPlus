package com.mathr81.spotifyplus.beautifullyrics.entities.lyrics;

import androidx.annotation.Nullable;
import com.mathr81.spotifyplus.beautifullyrics.entities.NaturalAlignment;

public class TransformedLyrics {
    public NaturalAlignment naturalAlignment;
    public String language;
    @Nullable
    public String romanizedLanguage;

    public ProviderLyrics lyrics;
}
