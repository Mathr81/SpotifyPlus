package com.mathr81.spotifyplus;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.Button;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mathr81.spotifyplus.hooks.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import org.luckypray.dexkit.DexKitBridge;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XposedLoader implements IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {
    static {
        System.loadLibrary("dexkit");
    }

    private DexKitBridge bridge;
    private String modulePath = null;
    private static final String MODULE_VERSION = "0.6";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.spotify.music")) return;
        XposedBridge.log("[SpotifyPlus] Loading SpotifyPlus v" + MODULE_VERSION);

        if (bridge == null) {
            try {
                bridge = DexKitBridge.create(lpparam.appInfo.sourceDir);
            } catch (Exception e) {
                XposedBridge.log(e);
            }
        }

        XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                References.currentActivity = activity;
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "onActivityResult", int.class, int.class, Intent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                int requestCode = (int) param.args[0];
                Intent data = (Intent) param.args[2];

                if (requestCode == 9072022 && data != null) {
                    Uri tree = data.getData();
                    ContentResolver content = ((Activity) param.thisObject).getContentResolver();
                    content.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                    SharedPreferences prefs = ((Activity) param.thisObject).getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
                    prefs.edit().putString("scripts_directory", tree.toString()).apply();
                }
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                Typeface beautifulFont = References.beautifulFont.get();

                if (beautifulFont != null) return;

                try {
                    Resources resources = XModuleResources.createInstance(modulePath, null);
                    beautifulFont = Typeface.createFromAsset(resources.getAssets(), "fonts/lyrics_medium.ttf");

                    XposedBridge.log("[SpotifyPlus] Successfully loaded font!");
                } catch (Throwable t) {
                    XposedBridge.log("[SpotifyPlus] Failed to load font (error)");
                    XposedBridge.log(t);
                }

                if (beautifulFont != null) {
                    References.beautifulFont = new WeakReference<>(beautifulFont);
                }

                navigateToStartupPage(activity);
            }
        });

        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!
        // ADD ANIMATED ALBUM ARTWORK!!!!!!!!!

        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Context context = (Context) param.args[0];
                cleanUpCache(context);

//                new SettingsFlyoutHook(context).init(lpparam, bridge);
//                new ScriptManager().init(context, lpparam.classLoader);
                ScriptManager.getInstance().init(context, lpparam.classLoader);
                new BeautifulLyricsHook().init(lpparam, bridge);
                new SocialHook().init(lpparam, bridge);
                new RemoveCreateButtonHook(context).init(lpparam, bridge);
//                new ContextMenuHook().init(lpparam, bridge);
//                new PageInjectionDebugHook().init(lpparam, bridge);
//                new PageInjectionHook().init(lpparam, bridge);
//                new PremiumHook().init(lpparam, bridge);
                new ContextMenuHookV2().init(lpparam, bridge);
                new LastFmHook().init(lpparam, bridge);
                new ContextMenu_AddButton().init(lpparam, bridge);
            }
        });
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        modulePath = startupParam.modulePath;
    }

    private void navigateToStartupPage(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        String page = prefs.getString("startup_page", "HOME");

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setPackage("com.spotify.music");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        switch (page) {
            case "HOME":
                intent.setData(Uri.parse("spotify:home"));
                break;

            case "SEARCH":
                intent.setData(Uri.parse("spotify:search"));
                break;

            case "EXPLORE":
                intent.setData(Uri.parse("spotify:find"));
                break;

            case "LIBRARY":
                intent.setData(Uri.parse("spotify:collection"));
                break;
        }

        activity.startActivity(intent);
    }

    public boolean hasInternet(Context ctx) {
        try {
            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            if (android.os.Build.VERSION.SDK_INT >= 23) {
                android.net.Network nw = cm.getActiveNetwork();
                if (nw == null) return false;
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(nw);
                if (caps == null) return false;
                // INTERNET = can reach the internet, VALIDATED = actually has connectivity (not just a Wi‑Fi w/o backhaul)
                return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            } else {
                @SuppressWarnings("deprecation")
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                @SuppressWarnings("deprecation")
                boolean connected = (ni != null && ni.isConnected());
                return connected;
            }
        } catch (Throwable t) {
            // Never crash due to OEM weirdness
            de.robv.android.xposed.XposedBridge.log("[SpotifyPlus] hasInternet() failed: " + t);
            return false;
        }
    }

    private void cleanUpCache(Context context) {
        File[] files = context.getCacheDir().listFiles();

        for (File file : files) {
            if (file.getName().endsWith(".apk")) {
                file.delete();
            }
        }
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable {
        if (resparam.packageName.equals("com.spotify.music")) {
            References.modResources = XModuleResources.createInstance(modulePath, resparam.res);
            References.xresources = resparam.res;
        }
    }
}
