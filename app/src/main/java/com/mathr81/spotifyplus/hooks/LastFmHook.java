package com.mathr81.spotifyplus.hooks;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;
import android.widget.Toast;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mathr81.spotifyplus.References;
import de.robv.android.xposed.*;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.FieldsMatcher;

public class LastFmHook extends SpotifyHook {
    private static final Set<ViewGroup> SHEETS = Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    protected void hook() {
        XposedHelpers.findAndHookConstructor(
                "com.spotify.bottomsheet.core.ScrollableContentWithHeaderLayout",
                lpparm.classLoader, android.content.Context.class, android.util.AttributeSet.class,
                new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        SHEETS.add((ViewGroup) p.thisObject);
                    }
                }
        );

        try {
            final Class<?> v6e = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("ContextMenuViewModel cannot contain items with duplicate itemResId. id=").fieldCount(4))).get(0).getInstance(lpparm.classLoader);
            final Class<?> h2e = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("ContextMenuViewModel cannot contain items with duplicate itemResId. id=").fieldCount(16))).get(0).getInstance(lpparm.classLoader);
            final Class<?> c3e = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("ContextMenuHeader(title="))).get(0).getInstance(lpparm.classLoader);
            final Class<?> f2e = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("mainScheduler").methodCount(2).fields(FieldsMatcher.create().add(FieldMatcher.create().type(int.class)).add(FieldMatcher.create().type(h2e))))).get(0).getInstance(lpparm.classLoader);
            final Class<?> artworkClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("androidx.credentials.TYPE_GET_PUBLIC_KEY_CREDENTIAL_DOM_EXCEPTION/androidx.credentials.TYPE_NO_MODIFICATION_ALLOWED_ERROR"))).get(0).getInstance(lpparm.classLoader);

            final Field c3eField = bridge.findField(FindField.create().searchInClass(Collections.singletonList(bridge.getClassData(v6e))).matcher(FieldMatcher.create().type(c3e))).get(0).getFieldInstance(lpparm.classLoader);
            final Field artworkField = bridge.findField(FindField.create().searchInClass(Collections.singletonList(bridge.getClassData(c3e))).matcher(FieldMatcher.create().type(artworkClass))).get(0).getFieldInstance(lpparm.classLoader);
            final Field listField = bridge.findField(FindField.create().searchInClass(Collections.singletonList(bridge.getClassData(v6e))).matcher(FieldMatcher.create().type(List.class))).get(0).getFieldInstance(lpparm.classLoader);
            var stringFields = bridge.findField(FindField.create().searchInClass(Collections.singletonList(bridge.getClassData(c3e))).matcher(FieldMatcher.create().type(String.class)));

            XposedBridge.hookAllMethods(f2e, "accept", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        SharedPreferences ref = References.getPreferences();
                        String username = ref.getString("last_fm_username", "null");
                        if (username.equals("null")) return;

                        Object consumer = p.thisObject;
                        Object vm = p.args[0]; // this should be v6e
                        Object headerObject = c3eField.get(vm);

                        Object artwork = artworkField.get(headerObject);
                        List<?> items = (List<?>) listField.get(vm);

                        String title = "";
                        String artist = "";
                        String subtitle = "";

                        String probablyTitle = (String) (stringFields.get(0).getFieldInstance(lpparm.classLoader)).get(headerObject);
                        String probablyArtist = (String) (stringFields.get(1).getFieldInstance(lpparm.classLoader)).get(headerObject);

                        if (probablyArtist.contains("•")) {
                            subtitle = probablyArtist;
                            artist = probablyArtist.split(" • ")[0];
                        } else title = probablyArtist;

                        if (probablyTitle.contains("•")) {
                            subtitle = probablyTitle;
                            artist = probablyTitle.split(" • ")[0];
                        } else title = probablyTitle;

                        if (title == null || title.isEmpty() || artist == null || artist.isEmpty()) return;
                        final String key = title + "|" + artist;

                        if(subtitle.contains("scrobbles")) return;
                        References.contextMenuTrack = new WeakReference<>(Pair.create(artist, title));

                        Activity activity = References.currentActivity;
                        OkHttpClient client = new OkHttpClient();
                        Request request;

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {                                      //  Yeah I know this is bad, but whatever
                            request = new Request.Builder().url("https://ws.audioscrobbler.com/2.0/?method=track.getInfo&api_key=3713c2e0b7493e945555b7f52dc4232e&artist=" + URLEncoder.encode(artist, StandardCharsets.UTF_8) + "&track=" + URLEncoder.encode(title, StandardCharsets.UTF_8) + "&format=json&user=" + URLEncoder.encode(username, StandardCharsets.UTF_8)).build();
                        } else {
                            request = new Request.Builder().url("https://ws.audioscrobbler.com/2.0/?method=track.getInfo&api_key=3713c2e0b7493e945555b7f52dc4232e&artist=" + URLEncoder.encode(artist) + "&track=" + URLEncoder.encode(title) + "&format=json&user=" + URLEncoder.encode(username)).build();
                        }

                        String finalTitle = title;
                        String finalSubtitle = subtitle;

                        WeakReference<Object> consumerRef = new WeakReference<>(consumer);
                        WeakReference<Object> artworkRef = new WeakReference<>(artwork);
                        WeakReference<List<?>> itemsRef = new WeakReference<>(items);

                        client.newCall(request).enqueue(new Callback() {
                            @Override
                            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                                if (response.isSuccessful()) {
                                    try {
                                        JsonObject root = JsonParser.parseString(response.body().string()).getAsJsonObject();
                                        String scrobbles = root.getAsJsonObject("track").get("userplaycount").getAsString();

                                        Object consumerObject = consumerRef.get();
                                        Object artworkObject = artworkRef.get();
                                        List<?> itemsObject = itemsRef.get();

                                        if (consumerObject == null || artworkObject == null || itemsObject == null) {
                                            XposedBridge.log("[SpotifyPlus] One of these is null");
                                            return;
                                        }

                                        Object newHeader = XposedHelpers.newInstance(c3e, finalTitle, artwork, finalSubtitle + " • " + scrobbles + " scrobbles");
                                        Object newVm = XposedHelpers.newInstance(v6e, newHeader, items, false);

                                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                            try {
                                                XposedHelpers.callMethod(consumerObject, "accept", newVm);
                                            }  catch(Throwable t) {
                                                XposedBridge.log(t);
                                            }
                                        });
                                    } catch (Exception e) {
                                        XposedBridge.log("[SpotifyPlus] Failed to fetch scrobbles");
                                        Toast.makeText(activity, "Failed to fetch scrobbles", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    XposedBridge.log("[SpotifyPlus] Failed to fetch scrobbles");
                                    Toast.makeText(activity, "Failed to fetch scrobbles", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                                XposedBridge.log("[SpotifyPlus] Failed to fetch scrobbles");
                                Toast.makeText(activity, "Failed to fetch scrobbles", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (Exception e) {
                        XposedBridge.log(e);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("hook f2e.accept failed: " + t);
        }
    }
}
