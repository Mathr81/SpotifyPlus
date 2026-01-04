package com.mathr81.spotifyplus.hooks;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class ContextMenuHookV2 extends SpotifyHook {
    private static final Set<Object> HOOKED_ADAPTERS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ViewGroup> WATCHED_SHEETS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean GLOBAL_ADDVIEW_HOOKED = false;

    @Override
    protected void hook() {
        // A) Per-sheet: mark the sheet so the global hook knows to watch inside it
        XposedHelpers.findAndHookConstructor(
                "com.spotify.bottomsheet.core.ScrollableContentWithHeaderLayout",
                lpparm.classLoader,
                Context.class, AttributeSet.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        ViewGroup sheet = (ViewGroup) param.thisObject;
                        WATCHED_SHEETS.add(sheet);
                    }
                }
        );

        // B) Global: hook ALL addView calls and filter by ancestry
        if (!GLOBAL_ADDVIEW_HOOKED) {
            GLOBAL_ADDVIEW_HOOKED = true;
            XposedBridge.hookAllMethods(ViewGroup.class, "addView", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    ViewGroup parent = (ViewGroup) p.thisObject;

                    // Only care if this parent lives under one of our sheets
                    ViewGroup owningSheet = findOwningSheet(parent);
                    if (owningSheet == null) return;

                    // Find the child argument
                    View child = null;
                    for (Object a : p.args) {
                        if(a.getClass().getName().contains("ComposeView")) {
                        }

//                        XposedBridge.log("[SpotifyPlus] Child: " + a.toString());

                        if (a instanceof View) {
                            child = (View) a;
                            break;
                        }
                    }
                    if (child == null) return;
//                    XposedBridge.log("[SpotifyPlus] Child: " + child.toString());
                }
            });
        }
    }

    private ViewGroup findOwningSheet(View v) {
        View cur = v;
        while (cur != null) {
            if (cur instanceof ViewGroup && WATCHED_SHEETS.contains(cur)) {
                return (ViewGroup) cur;
            }
            ViewParent p = cur.getParent();
            cur = (p instanceof View) ? (View) p : null;
        }
        return null;
    }
}
