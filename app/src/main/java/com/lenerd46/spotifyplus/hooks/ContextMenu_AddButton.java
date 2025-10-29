package com.lenerd46.spotifyplus.hooks;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.lenerd46.spotifyplus.R;
import com.lenerd46.spotifyplus.References;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.lang.reflect.Field;
import java.util.*;

public class ContextMenu_AddButton extends SpotifyHook {

    // guard: only hook each adapter CLASS once
    private static final Set<Class<?>> HOOKED_ADAPTER_CLASSES =
            Collections.synchronizedSet(new HashSet<>());

    @Override
    protected void hook() {
        XposedHelpers.findAndHookConstructor(
                "com.spotify.bottomsheet.core.ScrollableContentWithHeaderLayout",
                lpparm.classLoader,
                Context.class, AttributeSet.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        final ViewGroup sheet = (ViewGroup) param.thisObject;
                        sheet.post(() -> {
                            View rv = findContextMenuRecycler(sheet);
                            if (rv != null) hookAdapterWhenReady(rv);
                        });
                    }
                }
        );
    }

    private View findContextMenuRecycler(ViewGroup root) {
        ArrayDeque<View> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            View v = q.removeFirst();
            if ("RecyclerView".equals(v.getClass().getSimpleName())) {
                try {
                    int id = v.getId();
                    if (id != View.NO_ID &&
                            "context_menu_rows".equals(v.getResources().getResourceEntryName(id))) {
                        return v;
                    }
                } catch (Throwable ignore) {
                }
                return v;
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) q.addLast(g.getChildAt(i));
            }
        }
        return null;
    }

    private void hookAdapterWhenReady(View rv) {
        try {
            Object ad = XposedHelpers.callMethod(rv, "getAdapter");
            if (ad != null) {
                hookAdapterClass(ad.getClass());
                return;
            }
        } catch (Throwable ignore) {
        }

        try {
            XposedBridge.hookAllMethods(rv.getClass(), "setAdapter", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object ad = (param.args != null && param.args.length > 0) ? param.args[0] : null;
                    if (ad != null) hookAdapterClass(ad.getClass());
                }
            });
        } catch (Throwable ignore) {
        }
    }

    private void hookAdapterClass(Class<?> cls) {
        if (!HOOKED_ADAPTER_CLASSES.add(cls)) return;

        XposedBridge.hookAllMethods(cls, "getItemCount", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                int orig = (int) param.getResult();
                param.setResult(orig + 1);
            }
        });

        XposedBridge.hookAllMethods(cls, "getItemViewType", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                int pos = (int) param.args[0];
                if (pos == 0) {
                    param.setResult(1); // reuse Spotify's normal type
                } else {
                    param.args[0] = pos - 1;
                }
            }
        });

        XposedBridge.hookAllMethods(cls, "onBindViewHolder", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object holder = param.args[0];
                int pos = (int) param.args[1];

                if (pos == 0) {
                    View item = (View) XposedHelpers.getObjectField(holder, "itemView");
                    if (item != null) {
                        TextView title = findPrimaryText(item);
                        if (title != null) title.setText("Open in Last.fm"); // Spotify is very inconsistent with how they name their buttons in this list, so I'm not really sure what to capitalize?

//                        ImageView icon = findFirstIcon(item);
//                        if (icon != null) {
//                            try {
//                                icon.setImageDrawable(References.modResources.getDrawable(R.drawable.add_circle));
//                            } catch (Throwable ignore) {
//                            }
//                        }

                        ensureOurIcon(item);

                        item.setContentDescription("Beautiful Lyrics");
                        item.setOnClickListener(v -> {
                            XposedBridge.log("[SpotifyPlus] Beautiful Lyrics clicked");
                            // TODO: start your action here
                        });
                    }
                    param.setResult(null);
                } else {
                    param.args[1] = pos - 1;
                }
            }
        });
    }

    // helpers
    private TextView findPrimaryText(View root) {
        TextView best = null;
        float bestSize = -1f;
        ArrayDeque<View> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            View v = q.removeFirst();
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                float sz = tv.getTextSize();
                if (sz > bestSize) {
                    best = tv;
                    bestSize = sz;
                }
            } else if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) q.addLast(g.getChildAt(i));
            }
        }
        return best;
    }

    private ImageView findFirstIcon(View root) {
        ArrayDeque<View> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            View v = q.removeFirst();
            if (v instanceof ImageView) return (ImageView) v;
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) q.addLast(g.getChildAt(i));
            }
        }
        return null;
    }

    private ImageView findLeadingIcon(View root) {
        // Prefer by id name if present
        ArrayDeque<View> q = new ArrayDeque<>();
        q.add(root);
        ImageView first = null;
        while (!q.isEmpty()) {
            View v = q.removeFirst();
            if (v instanceof ImageView) {
                if (first == null) first = (ImageView) v;
                try {
                    int id = v.getId();
                    if (id != View.NO_ID) {
                        String name = v.getResources().getResourceEntryName(id);
                        if (name.contains("icon") || name.contains("leading")) return (ImageView) v;
                    }
                } catch (Throwable ignored) {
                }
            } else if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) q.addLast(g.getChildAt(i));
            }
        }
        return first;
    }

    private ViewGroup findIconContainer(View root) {
        ImageView icon = findLeadingIcon(root);
        if (icon == null) return null;
        ViewParent p = icon.getParent();
        return (p instanceof ViewGroup) ? (ViewGroup) p : null;
    }

    private static final int TAG_SPOTIFYPLUS_ICON = 0x53474C59; // any unique int

    private void ensureOurIcon(View item) {
        ViewGroup container = findIconContainer(item); // parent of the leading icon
        if (container == null) return;

        // Find Spotify's leading icon and hide it
        ImageView spotifyIcon = findLeadingIcon(item);
        if (spotifyIcon != null) spotifyIcon.setVisibility(View.GONE);

        // Add our own once
        ImageView ours = (ImageView) container.getTag(TAG_SPOTIFYPLUS_ICON);
        if (ours == null) {
            ours = new ImageView(container.getContext());
            // Try to copy size from the original icon if we found it
            if (spotifyIcon != null && spotifyIcon.getLayoutParams() != null) {
                ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                        spotifyIcon.getLayoutParams().width,
                        spotifyIcon.getLayoutParams().height);
                ours.setLayoutParams(lp);
            }
            ours.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            container.addView(ours, 0); // insert at start so it sits where the icon was
            container.setTag(TAG_SPOTIFYPLUS_ICON, ours);
        }

        Drawable d = References.modResources.getDrawable(R.drawable.add_circle);
        ours.setImageDrawable(d);
        ours.setImageTintList(null);
        ours.setColorFilter(null);
    }
}