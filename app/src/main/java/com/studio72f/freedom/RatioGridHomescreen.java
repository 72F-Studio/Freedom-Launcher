package com.studio72f.freedom;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ratio × Kelvin Frosted Grid Homescreen Layout.
 *
 * Implements Ratio's iconic design language aligned with 72F-Kelvin guidelines:
 * - Clean section category drawers ("Messaging –", "Creativity –") without harsh outer bounding boxes
 * - Pure squircle app tiles (radius 18dp) with translucent frosted fill and ZERO stroke outlines
 * - Notification dots (Ratio signal yellow/accent) on active tiles
 * - Bottom floating search capsule (sunken glass pill)
 */
public final class RatioGridHomescreen extends LinearLayout {
    public interface AppLaunchListener {
        void onLaunchApp(MainActivity.App app, View view);
        void onAppMenu(MainActivity.App app);
        void onOpenSearch();
    }

    private final List<MainActivity.App> allApps = new ArrayList<>();
    private final Map<String, List<MainActivity.App>> categoryMap = new HashMap<>();
    private final Set<String> expandedCategories = new HashSet<>();
    private final List<String> orderedCategories = new ArrayList<>();

    private LinearLayout drawerContainer;
    private ScrollView scrollView;
    private AppLaunchListener launchListener;

    private int fgColor = 0xFFF1F2F3;
    private int accentColor = 0xFFFFEA00; // Ratio signature yellow / Kelvin accent
    private boolean monochromeIcons = true;
    private float density;

    public RatioGridHomescreen(Context context) {
        super(context);
        init();
    }

    public RatioGridHomescreen(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private void init() {
        setOrientation(VERTICAL);
        density = getResources().getDisplayMetrics().density;
        setPadding(dp(16), dp(4), dp(16), dp(12));

        // Default open drawers
        expandedCategories.add("favorites");
        expandedCategories.add("social");
        expandedCategories.add("messaging");
        expandedCategories.add("creativity");

        scrollView = new ScrollView(getContext());
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipToPadding(false);

        drawerContainer = new LinearLayout(getContext());
        drawerContainer.setOrientation(VERTICAL);

        scrollView.addView(drawerContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        // Bottom Persistent Search Capsule ("Tap to search 🔍")
        LinearLayout searchBar = new LinearLayout(getContext());
        searchBar.setOrientation(HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(20), dp(13), dp(20), dp(13));
        FrostedCard.applySunken(searchBar, FrostedCard.RADIUS_PILL_DP);

        TextView searchLabel = new TextView(getContext());
        searchLabel.setText("Tap to search");
        searchLabel.setTextSize(14);
        searchLabel.setTextColor(0x88F1F2F3);
        searchLabel.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        searchBar.addView(searchLabel, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        TextView searchIcon = new TextView(getContext());
        searchIcon.setText("🔍");
        searchIcon.setTextSize(14);
        searchIcon.setAlpha(0.65f);
        searchBar.addView(searchIcon);

        searchBar.setOnClickListener(v -> {
            if (launchListener != null) launchListener.onOpenSearch();
        });

        LayoutParams searchParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(dp(4), dp(10), dp(4), dp(4));
        addView(searchBar, searchParams);
    }

    public void setLaunchListener(AppLaunchListener listener) {
        this.launchListener = listener;
    }

    public void setColors(int fg, int accent) {
        this.fgColor = fg != 0 ? fg : 0xFFF1F2F3;
        this.accentColor = accent != 0 ? accent : 0xFFFFEA00;
    }

    public void setMonochromeIcons(boolean mono) {
        this.monochromeIcons = mono;
    }

    public void setApps(List<MainActivity.App> apps, List<String> categories, Map<String, String> assignments, Map<String, String> merges) {
        allApps.clear();
        if (apps != null) allApps.addAll(apps);

        categoryMap.clear();
        orderedCategories.clear();

        // Categorize apps
        for (MainActivity.App app : allApps) {
            String cat = CategoryRules.resolve(app.group, assignments.get(app.id), merges);
            if (cat == null || cat.isEmpty()) cat = "tools";

            List<MainActivity.App> list = categoryMap.get(cat);
            if (list == null) {
                list = new ArrayList<>();
                categoryMap.put(cat, list);
            }
            list.add(app);
        }

        // Add favorites category if any
        List<MainActivity.App> favs = new ArrayList<>();
        for (MainActivity.App app : allApps) {
            if (categories.contains("favorites") && app.group != null && app.group.equals("favorites")) {
                favs.add(app);
            }
        }
        if (!favs.isEmpty()) {
            categoryMap.put("favorites", favs);
            orderedCategories.add("favorites");
        }

        for (String c : categories) {
            if (!c.equals("favorites") && !c.equals("all apps") && categoryMap.containsKey(c)) {
                orderedCategories.add(c);
            }
        }

        // Include any remaining categories
        for (String c : categoryMap.keySet()) {
            if (!orderedCategories.contains(c)) {
                orderedCategories.add(c);
            }
        }

        renderDrawers();
    }

    private void renderDrawers() {
        drawerContainer.removeAllViews();

        for (String cat : orderedCategories) {
            List<MainActivity.App> appsInCat = categoryMap.get(cat);
            if (appsInCat == null || appsInCat.isEmpty()) continue;

            View drawerView = createCategoryDrawer(cat, appsInCat);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(14));
            drawerContainer.addView(drawerView, lp);
        }
    }

    private View createCategoryDrawer(String categoryName, List<MainActivity.App> apps) {
        LinearLayout drawer = new LinearLayout(getContext());
        drawer.setOrientation(VERTICAL);

        boolean isExpanded = expandedCategories.contains(categoryName);

        // Header Row - clean typographic section without harsh bounding boxes
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(12), dp(8), dp(6));

        TextView title = new TextView(getContext());
        String displayTitle = categoryName.substring(0, 1).toUpperCase(Locale.ROOT) + categoryName.substring(1) + " –";
        title.setText(displayTitle);
        title.setTextSize(20);
        title.setTextColor(fgColor);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        header.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        TextView count = new TextView(getContext());
        count.setText(apps.size() + " apps");
        count.setTextSize(13);
        count.setTextColor(0xAA97989B);
        count.setPadding(0, 0, dp(8), 0);
        header.addView(count);

        TextView chevron = new TextView(getContext());
        chevron.setText(isExpanded ? "−" : "+");
        chevron.setTextSize(16);
        chevron.setTextColor(0x8897989B);
        header.addView(chevron);

        header.setOnClickListener(v -> {
            if (expandedCategories.contains(categoryName)) {
                expandedCategories.remove(categoryName);
            } else {
                expandedCategories.add(categoryName);
            }
            renderDrawers();
        });

        drawer.addView(header);

        // 4-Column Grid for apps if expanded
        if (isExpanded) {
            GridLayout grid = new GridLayout(getContext());
            grid.setColumnCount(4);
            grid.setPadding(dp(4), dp(4), dp(4), dp(8));

            for (MainActivity.App app : apps) {
                View tile = createAppTile(app);
                GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
                glp.width = 0;
                glp.height = LayoutParams.WRAP_CONTENT;
                glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                glp.setMargins(dp(4), dp(4), dp(4), dp(4));
                grid.addView(tile, glp);
            }

            drawer.addView(grid);
        }

        return drawer;
    }

    private View createAppTile(MainActivity.App app) {
        FrameLayout tileFrame = new FrameLayout(getContext());
        FrostedCard.applyInteractive(tileFrame, FrostedCard.RADIUS_TILE_DP);

        LinearLayout tileContent = new LinearLayout(getContext());
        tileContent.setOrientation(VERTICAL);
        tileContent.setGravity(Gravity.CENTER_HORIZONTAL);
        tileContent.setPadding(dp(6), dp(10), dp(6), dp(8));
        tileContent.setMinimumHeight(dp(76));

        ImageView iconView = new ImageView(getContext());
        iconView.setImageDrawable(app.icon);
        if (monochromeIcons) {
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0);
            iconView.setColorFilter(new ColorMatrixColorFilter(cm));
            iconView.setAlpha(0.92f);
        } else {
            iconView.clearColorFilter();
            iconView.setAlpha(1f);
        }

        LayoutParams iconParams = new LayoutParams(dp(34), dp(34));
        iconParams.setMargins(0, dp(2), 0, dp(6));
        tileContent.addView(iconView, iconParams);

        TextView label = new TextView(getContext());
        CharSequence appTitle = app.name != null ? app.name : app.label;
        label.setText(appTitle);
        label.setTextSize(11);
        label.setTextColor(0xEEF1F2F3);
        label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tileContent.addView(label);

        tileFrame.addView(tileContent, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        // Notification Indicator Dot (Ratio Yellow / Kelvin Accent)
        boolean hasNotification = MediaAccessService.hasDot(app.component.getPackageName(), app.user);
        if (hasNotification) {
            View dot = new View(getContext());
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(accentColor);
            dot.setBackground(dotBg);

            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dp(6), dp(6));
            dotParams.gravity = Gravity.TOP | Gravity.END;
            dotParams.setMargins(0, dp(6), dp(6), 0);
            tileFrame.addView(dot, dotParams);
        }

        tileFrame.setOnClickListener(v -> {
            if (launchListener != null) launchListener.onLaunchApp(app, v);
        });

        tileFrame.setOnLongClickListener(v -> {
            if (launchListener != null) launchListener.onAppMenu(app);
            return true;
        });

        return tileFrame;
    }
}
