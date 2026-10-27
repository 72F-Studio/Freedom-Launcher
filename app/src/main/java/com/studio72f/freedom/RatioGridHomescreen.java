package com.studio72f.freedom;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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
 * Ratio × Minimaa Frosted Grid Homescreen Layout.
 *
 * Implements Ratio's collapsible category drawers with 4-column frosted app tiles,
 * paired with Minimaa's clean typographic discipline.
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

    private int fgColor = Color.WHITE;
    private int accentColor = 0xFF39B9D4;
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
        setPadding(dp(16), dp(8), dp(16), dp(12));

        // Default open categories
        expandedCategories.add("favorites");
        expandedCategories.add("social");
        expandedCategories.add("messaging");

        scrollView = new ScrollView(getContext());
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipToPadding(false);

        drawerContainer = new LinearLayout(getContext());
        drawerContainer.setOrientation(VERTICAL);

        scrollView.addView(drawerContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        // Bottom Persistent Search Bar ("Tap to search 🔍")
        LinearLayout searchBar = new LinearLayout(getContext());
        searchBar.setOrientation(HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(16), dp(12), dp(16), dp(12));
        FrostedCard.applyInteractive(searchBar, 24);

        TextView searchLabel = new TextView(getContext());
        searchLabel.setText("Tap to search");
        searchLabel.setTextSize(14);
        searchLabel.setTextColor(0x99FFFFFF);
        searchBar.addView(searchLabel, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        TextView searchIcon = new TextView(getContext());
        searchIcon.setText("🔍");
        searchIcon.setTextSize(14);
        searchBar.addView(searchIcon);

        searchBar.setOnClickListener(v -> {
            if (launchListener != null) launchListener.onOpenSearch();
        });

        LayoutParams searchParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(0, dp(10), 0, dp(4));
        addView(searchBar, searchParams);
    }

    public void setLaunchListener(AppLaunchListener listener) {
        this.launchListener = listener;
    }

    public void setColors(int fg, int accent) {
        this.fgColor = fg;
        this.accentColor = accent;
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
            lp.setMargins(0, 0, 0, dp(12));
            drawerContainer.addView(drawerView, lp);
        }
    }

    private View createCategoryDrawer(String categoryName, List<MainActivity.App> apps) {
        LinearLayout drawer = new LinearLayout(getContext());
        drawer.setOrientation(VERTICAL);
        FrostedCard.apply(drawer, 18);

        boolean isExpanded = expandedCategories.contains(categoryName);

        // Header Row
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView title = new TextView(getContext());
        title.setText(categoryName.substring(0, 1).toUpperCase(Locale.ROOT) + categoryName.substring(1));
        title.setTextSize(17);
        title.setTextColor(fgColor);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        TextView count = new TextView(getContext());
        count.setText(String.valueOf(apps.size()));
        count.setTextSize(13);
        count.setTextColor(0x77FFFFFF);
        count.setPadding(0, 0, dp(10), 0);
        header.addView(count);

        TextView chevron = new TextView(getContext());
        chevron.setText(isExpanded ? "▾" : "▸");
        chevron.setTextSize(16);
        chevron.setTextColor(0xAAFFFFFF);
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
            grid.setPadding(dp(10), 0, dp(10), dp(14));

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
        LinearLayout tile = new LinearLayout(getContext());
        tile.setOrientation(VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(6), dp(10), dp(6), dp(8));
        FrostedCard.applyInteractive(tile, 12);

        ImageView iconView = new ImageView(getContext());
        iconView.setImageDrawable(app.icon);
        LayoutParams iconParams = new LayoutParams(dp(42), dp(42));
        iconParams.setMargins(0, 0, 0, dp(6));
        tile.addView(iconView, iconParams);

        TextView label = new TextView(getContext());
        CharSequence appTitle = app.name != null ? app.name : app.label;
        label.setText(appTitle);
        label.setTextSize(11);
        label.setTextColor(0xEEFFFFFF);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(label);

        tile.setOnClickListener(v -> {
            if (launchListener != null) launchListener.onLaunchApp(app, v);
        });

        tile.setOnLongClickListener(v -> {
            if (launchListener != null) launchListener.onAppMenu(app);
            return true;
        });

        return tile;
    }
}
