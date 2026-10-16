package dev.seventwo.launcher;

import android.app.*;
import android.app.role.RoleManager;
import android.appwidget.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.*;
import android.media.session.MediaController;
import android.net.Uri;
import android.os.*;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final String[] BUILT_INS = {"social", "music", "video", "photos", "games", "travel", "tools"};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LauncherApps launcher;
    private final ArrayList<App> apps = new ArrayList<>(), visible = new ArrayList<>();
    private final ArrayList<String> categories = new ArrayList<>(), favorites = new ArrayList<>(), custom = new ArrayList<>();
    private final HashSet<String> disabled = new HashSet<>();
    private final HashMap<String,String> assignments = new HashMap<>(), merges = new HashMap<>();
    private final HashMap<String,Integer> scrollPositions = new HashMap<>();
    private String category = "favorites", query = "";
    private boolean searching, rebuilding, foreground;
    private int generation, fg = Color.WHITE, bg = Color.BLACK, muted = 0xff898989, accent = 0xff39b9d4, titleSize = 60;
    private String font = "sans-serif-light";
    private LinearLayout root, titles, mediaBox;
    private HorizontalScrollView titleScroll;
    private FrameLayout listArea;
    private ListView list;
    private TextView clock, date, empty, letter, mediaTitle, mediaArtist;
    private Bitmap wallpaper, softWallpaper;
    private ThemeEngine theme;
    private IconPacks iconPack;
    private Ps3Backdrop waves;
    private View backdropView;
    private FrameLayout scene;
    private int effectGeneration, wallpaperGeneration;
    private BackupStore.Pending pendingRestore;
    private MetroArt.Icon playButton;
    private UiIcons uiIcons;
    private final ArrayList<GlassSurface> glassSurfaces=new ArrayList<>();
    private final GlassBackdrop glassBackdrop=new GlassBackdrop();
    private String activeWallpaperMode;
    private EditText search;
    private CategoryRail categoryRail;
    // Material 3 tonal roles. Expressive themes take them from the wallpaper palette (Android 12+).
    private int primaryContainer, onPrimaryContainer, secondaryContainer, onSecondaryContainer, surfaceContainer;
    private AlphabetRail alphabet;
    private AppAdapter adapter;
    private MediaController controller;
    private SettingsSheet sheet;
    private final ArrayList<ContextRules.Rule> contexts = new ArrayList<>();
    private ContextRules.Rule activeContext;
    private String appliedLayout = "";
    private WidgetHosting.Host widgetHost;
    private final HashMap<String,Integer> stackPages=new HashMap<>();
    private AppWidgetManager widgetManager;
    private TextView alarmText, agendaText;
    private long agendaLoaded;
    private boolean signalsRegistered;
    private final Runnable evaluate = this::evaluateContext;
    private final AudioDeviceCallback audioDevices = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] d) { signalChanged(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] d) { signalChanged(); }
    };
    private final BroadcastReceiver signalReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { signalChanged(); }
    };
    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { updateMedia(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { updateMedia(); }
        @Override public void onSessionDestroyed() { bindMedia(); }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!foreground) return;
            bindMedia(); evaluateContext(); updateClock(); updateAlarm(); if (System.currentTimeMillis() - agendaLoaded > 300000) updateAgenda();
            // Wake at the next minute boundary (or in 10 s) so the clock never lags a minute change.
            long now = System.currentTimeMillis(); handler.postDelayed(this, Math.min(10000, 60000 - now % 60000 + 50));
        }
    };
    private final LauncherApps.Callback appChanges = new LauncherApps.Callback() {
        @Override public void onPackageRemoved(String p, UserHandle u) { loadApps(); }
        @Override public void onPackageAdded(String p, UserHandle u) { loadApps(); }
        @Override public void onPackageChanged(String p, UserHandle u) { loadApps(); }
        @Override public void onPackagesAvailable(String[] p, UserHandle u, boolean r) { loadApps(); }
        @Override public void onPackagesUnavailable(String[] p, UserHandle u, boolean r) { loadApps(); }
    };
    private static final class App {
        String id, name, group;
        ComponentName component;
        UserHandle user;
        boolean work;
        Drawable icon;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 31) getSplashScreen().setOnExitAnimationListener(view -> view.remove());
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        launcher = getSystemService(LauncherApps.class);
        widgetManager = AppWidgetManager.getInstance(this);
        widgetHost = new WidgetHosting.Host(this, 7272);
        readState();
        restoreWallpaper();
        activeContext = resolveContext(); appliedLayout = layoutKey(activeContext); category = startCategory(activeContext);
        if (state != null) { category = state.getString("category", category); query = state.getString("query", ""); searching = state.getBoolean("searching"); }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        launcher.registerCallback(appChanges, handler);
        buildUi(); loadApps();
        if (!prefs.getBoolean("introduced", false)) handler.postDelayed(this::welcome, 500);
    }
    @Override protected void onStart() { super.onStart(); try { widgetHost.startListening(); } catch (RuntimeException ignored) { } }
    @Override protected void onStop() { try { widgetHost.stopListening(); } catch (RuntimeException ignored) { } super.onStop(); }
    @Override protected void onResume() { super.onResume(); foreground = true; registerSignals(); agendaLoaded = 0; if(waves!=null&&theme.animated&&android.animation.ValueAnimator.areAnimatorsEnabled())waves.start(); handler.removeCallbacks(tick); handler.post(tick); loadApps(); }
    @Override protected void onPause() { foreground = false; unregisterSignals(); if(waves!=null)waves.stop(); handler.removeCallbacks(tick); handler.removeCallbacks(evaluate); super.onPause(); }
    @Override protected void onDestroy() { if (Build.VERSION.SDK_INT >= 29) com.kieronquinn.app.smartspacer.sdk.client.SmartspacerClient.Companion.close(); launcher.unregisterCallback(appChanges); if(waves!=null)waves.stop();for(GlassSurface glass:glassSurfaces)glass.stop(); if(pendingRestore!=null)ThemeEngine.deleteTree(pendingRestore.directory); if (controller != null) controller.unregisterCallback(mediaCallback); worker.shutdownNow(); handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) { out.putString("category", category); out.putString("query", query); out.putBoolean("searching", searching); super.onSaveInstanceState(out); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); dismissDialogs(); if (sheet != null) sheet.dismiss(); searching = false; query = ""; activeContext = resolveContext(); appliedLayout = layoutKey(activeContext); category = startCategory(activeContext); scrollPositions.clear(); buildUi(); }
    @Override public void onBackPressed() { if (sheet != null && sheet.back()) return; if (searching) { searching = false; query = ""; hideKeyboard(); buildUi(); } else selectCategory(startCategory(activeContext), true); }

    private void readState() {
        favorites.clear();custom.clear();disabled.clear();assignments.clear();merges.clear();
        readArray("favorites", favorites); readArray("custom", custom);
        ArrayList<String> off = new ArrayList<>(); readArray("disabled", off); disabled.addAll(off);
        readMap("assignments", assignments); readMap("merges", merges);
        contexts.clear(); contexts.addAll(ContextStore.load(prefs));
        try { applyTheme(new JSONObject(prefs.getString("theme", "{}"))); } catch (Exception ignored) { try { applyTheme(new JSONObject()); } catch(Exception impossible) { throw new IllegalStateException(impossible); } }
    }
    private void readArray(String key, Collection<String> out) {
        try { JSONArray a = new JSONArray(prefs.getString(key, "[]")); for (int i = 0; i < a.length(); i++) out.add(a.getString(i)); } catch (JSONException ignored) { }
    }
    private void readMap(String key, Map<String,String> out) {
        try { JSONObject o = new JSONObject(prefs.getString(key, "{}")); Iterator<String> k = o.keys(); while (k.hasNext()) { String s = k.next(); out.put(s, o.getString(s)); } } catch (JSONException ignored) { }
    }
    private void saveState() {
        prefs.edit().putString("favorites", new JSONArray(favorites).toString()).putString("custom", new JSONArray(custom).toString())
            .putString("disabled", new JSONArray(disabled).toString()).putString("assignments", new JSONObject(assignments).toString())
            .putString("merges", new JSONObject(merges).toString()).apply();
    }
    private void loadApps() {
        if (worker.isShutdown()) return;
        final int request = ++generation;
        worker.execute(() -> {
            ArrayList<App> loaded = new ArrayList<>();
            final ThemeEngine selectedTheme=theme;
            IconPacks selectedPack=null;
            String selectedPackage=prefs.getString("iconPack","");
            if(!selectedPackage.isEmpty())try{selectedPack=new IconPacks(this,selectedPackage);}catch(Exception ignored){}
            iconPack=selectedPack;
            UserManager users = getSystemService(UserManager.class);
            for (UserHandle user : users.getUserProfiles()) {
                try {
                    for (LauncherActivityInfo info : launcher.getActivityList(null, user)) {
                        if (info.getComponentName().getPackageName().equals(getPackageName())) continue;
                        App a = new App(); a.component = info.getComponentName(); a.user = user;
                        a.id = users.getSerialNumberForUser(user) + ":" + a.component.flattenToString();
                        a.name = info.getLabel().toString(); a.work = !user.equals(android.os.Process.myUserHandle());
                        a.group = CategoryRules.classify(a.component.getPackageName(), info.getApplicationInfo().category);
                        try { a.icon = info.getBadgedIcon(0); } catch (RuntimeException ignored) { }
                        try {
                            File asset=selectedTheme.icon(a.component.flattenToString(),a.component.getPackageName());
                            if(asset!=null){Bitmap bitmap=android.graphics.ImageDecoder.decodeBitmap(ImageDecoder.createSource(asset),(decoder,size,source)->{float scale=Math.min(1f,256f/Math.max(size.getSize().getWidth(),size.getSize().getHeight()));decoder.setTargetSize(Math.max(1,Math.round(size.getSize().getWidth()*scale)),Math.max(1,Math.round(size.getSize().getHeight()*scale)));});a.icon=new android.graphics.drawable.BitmapDrawable(getResources(),bitmap);}
                            else if(selectedPack!=null){Drawable replacement=selectedPack.icon(a.component);if(replacement!=null)a.icon=replacement;}
                        } catch(Exception ignored){}
                        loaded.add(a);
                    }
                } catch (SecurityException | IllegalStateException ignored) { }
            }
            Collator collator = Collator.getInstance();
            loaded.sort((a,b) -> collator.compare(a.name,b.name));
            handler.post(() -> {
                if (isDestroyed() || request != generation) return;
                apps.clear(); apps.addAll(loaded);
                if (!prefs.getBoolean("seeded", false) && !apps.isEmpty()) {
                    for (App a : apps) if (!a.work && favoriteCandidate(a) && favorites.size() < 8) favorites.add(a.id);
                    if (favorites.isEmpty()) for (int i=0; i<Math.min(5,apps.size()); i++) favorites.add(apps.get(i).id);
                    prefs.edit().putBoolean("seeded", true).apply(); saveState();
                }
                refreshCategories(); refreshList(false);
            });
        });
    }
    private boolean favoriteCandidate(App a) {
        String p = a.component.getPackageName().toLowerCase(Locale.ROOT);
        return p.contains("dialer") || p.contains("messaging") || p.contains("camera") || p.contains("chrome") || p.contains("whatsapp") || p.contains("maps");
    }
    private String group(App a) { return CategoryRules.resolve(a.group, assignments.get(a.id), merges); }
    private ArrayList<String> editableCategories() {
        ArrayList<String> result = new ArrayList<>();
        for (String c : BUILT_INS) if (!merges.containsKey(c)) result.add(c);
        for (String c : custom) if (!merges.containsKey(c)) result.add(c);
        return result;
    }
    private void refreshCategories() {
        categories.clear(); categories.add("favorites");
        if (prefs.getBoolean("categories", true)) for (String c : editableCategories()) if (!disabled.contains(c)) categories.add(c);
        categories.add("all apps");
        if (!categories.contains(category)) category = "favorites";
        if (titles == null) return;
        titles.removeAllViews();
        for (String c : categories) {
            boolean selected=c.equals(category);
            TextView t=text(theme.layout.equals("xmb")?Character.toUpperCase(c.charAt(0))+c.substring(1):c,titleSize,selected?fg:MetroArt.alpha(muted,.62f));
            if(theme.expressive){t.setTypeface(Typeface.create(theme.typeface,selected?800:500,false));t.setLetterSpacing(-.02f);}
            if(theme.liquid){t.setTextSize(Math.min(titleSize,36));t.setTypeface(Typeface.create(theme.typeface,600,false));t.setTextColor(selected?fg:MetroArt.alpha(fg,.38f));}
            t.setPadding(dp(24),dp(theme.liquid?12:0),dp(12),dp(theme.liquid?14:6));t.setSingleLine(true);t.setLetterSpacing(theme.layout.equals("xmb")?0:-.035f);
            t.setContentDescription(c+" category"+(selected?", selected":""));t.setOnClickListener(v->selectCategory(c,true));
            if(theme.layout.equals("xmb")){
                LinearLayout item=column();item.setGravity(Gravity.CENTER_HORIZONTAL);item.setPadding(dp(6),dp(8),dp(6),dp(4));
                MetroArt.Icon mark=new MetroArt.Icon(this,uiIcons,c,selected?fg:MetroArt.alpha(muted,.7f),false);mark.setGlyphSize(34);mark.setFocusable(false);mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                item.addView(mark,new LinearLayout.LayoutParams(dp(52),dp(52)));t.setPadding(dp(12),0,dp(12),dp(6));item.addView(t,new LinearLayout.LayoutParams(-2,-2));item.setOnClickListener(v->selectCategory(c,true));
                if(selected)t.setShadowLayer(dp(8),0,0,MetroArt.alpha(accent,.55f));titles.addView(item,new LinearLayout.LayoutParams(-2,-2));
            }else titles.addView(t);
        }
        rebuilding = true; categoryRail.setMax(categories.size()-1); categoryRail.setProgress(categories.indexOf(category)); categoryRail.fitSlots(); categoryRail.moveTo(categories.indexOf(category)); rebuilding = false;
        categoryRail.setContentDescription("Category switcher, "+category+" selected. Swipe up for quick settings");
        titleScroll.post(() -> { int i = categories.indexOf(category); if (i >= 0 && i < titles.getChildCount()) titleScroll.smoothScrollTo(Math.max(0,titles.getChildAt(i).getLeft()-dp(8)),0); });
    }

    // The preference chooses a physical hand, independently of locale direction.
    @android.annotation.SuppressLint("RtlHardcoded")
    private void buildUi() {
        rebuilding = true;
        if(waves!=null)waves.stop();for(GlassSurface glass:glassSurfaces)glass.stop();glassSurfaces.clear();glassBackdrop.clear();
        root=column();scene=new FrameLayout(this);backdropView=new View(this);
        scene.addView(backdropView,new FrameLayout.LayoutParams(-1,-1));scene.addView(root,new FrameLayout.LayoutParams(-1,-1));
        activeWallpaperMode=prefs.getString("wallpaperMode","solid");
        boolean useSystemWallpaper=activeWallpaperMode.equals("system");
        if(useSystemWallpaper)getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        updateBackdrop();
        root.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets; });
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(Color.luminance(bg) > .5 ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        setContentView(scene); root.requestApplyInsets();
        if (searching) {
            LinearLayout row = row(); row.setPadding(dp(18), dp(8), dp(12), dp(8));
            search = new EditText(this); search.setTextColor(fg); search.setHintTextColor(muted); search.setTextSize(25); search.setSingleLine(); search.setHint("find an app"); search.setTypeface(theme.typeface); search.setText(query);
            row.addView(search,new LinearLayout.LayoutParams(0,dp(58),1)); row.addView(iconButton("close", "Close search", false, v -> onBackPressed())); root.addView(row);
            search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int a,int c,int f) {} public void onTextChanged(CharSequence s,int a,int b,int c) { query=s.toString(); refreshList(true); } public void afterTextChanged(Editable e) {} });
            search.setOnEditorActionListener((v,a,e) -> { if (!visible.isEmpty()) launch(visible.get(0)); return true; });
        } else {
            buildTopZone();
        }
        titleScroll = new CategoryTitles(); titleScroll.setHorizontalScrollBarEnabled(false); titleScroll.setClipToPadding(false);
        titles = row(); titleScroll.addView(titles,new HorizontalScrollView.LayoutParams(-2,-2));
        titleScroll.setVisibility(searching ? View.GONE : View.VISIBLE); root.addView(titleScroll);
        SwipeFrame swipe = new SwipeFrame(); root.addView(swipe,new LinearLayout.LayoutParams(-1,0,1));
        listArea = new FrameLayout(this); swipe.addView(listArea,new FrameLayout.LayoutParams(-1,-1));
        list = new ListView(this); list.setDivider(null); list.setCacheColorHint(Color.TRANSPARENT); list.setVerticalScrollBarEnabled(false); list.setClipToPadding(false);
        boolean left = prefs.getBoolean("left",false); list.setPadding(dp(left?48:24),dp(2),dp(left?24:48),dp(12));
        list.setVerticalFadingEdgeEnabled(true); list.setFadingEdgeLength(dp(24));
        list.setSelector(new android.graphics.drawable.ColorDrawable((accent & 0xffffff) | 0x12000000));
        adapter = new AppAdapter(); list.setAdapter(adapter); listArea.addView(list,new FrameLayout.LayoutParams(-1,-1));
        list.setOnItemClickListener((p,v,pos,id) -> launch(visible.get(pos)));
        list.setOnItemLongClickListener((p,v,pos,id) -> { appMenu(visible.get(pos)); return true; });
        empty = text("",20,muted); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(40),0,dp(40),0); listArea.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        alphabet = new AlphabetRail(); FrameLayout.LayoutParams railParams = new FrameLayout.LayoutParams(dp(40),-1, left?Gravity.LEFT:Gravity.RIGHT); listArea.addView(alphabet,railParams);
        letter = text("",64,accent); letter.setGravity(Gravity.CENTER); letter.setBackground(outline(MetroArt.alpha(bg,.96f),MetroArt.alpha(accent,.5f))); letter.setVisibility(View.GONE);
        FrameLayout.LayoutParams bubble = new FrameLayout.LayoutParams(dp(100),dp(100),Gravity.CENTER); listArea.addView(letter,bubble);
        // Dock: a category pill stretching to a floating search button (Niagara-style), both on one surface.
        LinearLayout footer = new SwipeUpFooter(); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(16),dp(6),dp(16),dp(14)); footer.setClipToPadding(false); footer.setClipChildren(false);
        categoryRail = new CategoryRail();
        categoryRail.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s,int p,boolean user) { if (user && !rebuilding && p < categories.size()) selectCategory(categories.get(p),false); }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { s.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); }
        });
        FrameLayout bar = new FrameLayout(this); bar.setBackground(dockSurface(bar));
        FrameLayout.LayoutParams dockRail=new FrameLayout.LayoutParams(-1,-1);dockRail.setMargins(dp(6),0,dp(6),0);bar.addView(categoryRail,dockRail);
        footer.addView(bar,new LinearLayout.LayoutParams(0,dp(56),1));
        View fab = searchButton(); LinearLayout.LayoutParams fabParams = new LinearLayout.LayoutParams(dp(56),dp(56)); fabParams.setMargins(dp(10),0,0,0);
        footer.addView(fab,fabParams);
        footer.setVisibility(searching?View.GONE:View.VISIBLE);
        root.addView(footer);
        refreshCategories(); refreshList(false); updateMedia(); attachSheet(); rebuilding = false;
    }
    private void updateClock() {
        if (clock == null || date == null) return;
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        Date now = new Date();
        clock.setText(new SimpleDateFormat(is24 ? "HH:mm" : "h:mm",Locale.getDefault()).format(now));
        String period = is24 ? "" : new SimpleDateFormat("a",Locale.getDefault()).format(now) + " · ";
        String context = activeContext == null || activeContext.fallback ? "" : "\n● " + activeContext.name.toUpperCase(Locale.getDefault());
        date.setText(period + new SimpleDateFormat("EEE",Locale.getDefault()).format(now).toUpperCase(Locale.getDefault()) + "\n"
            + new SimpleDateFormat("d MMMM",Locale.getDefault()).format(now).toUpperCase(Locale.getDefault()) + context);
    }
    private void selectCategory(String next, boolean animate) {
        if (category.equals(next)) return;
        if (list != null) scrollPositions.put(category,list.getFirstVisiblePosition());
        int direction = Integer.compare(categories.indexOf(next),categories.indexOf(category));
        category = next; refreshCategories(); refreshList(true);
        if (animate && theme.motionMs>0 && list != null && android.animation.ValueAnimator.areAnimatorsEnabled()) { list.setTranslationX(dp(32)*direction); list.setAlpha(.25f); list.animate().translationX(0).alpha(1).setDuration(theme.motionMs).setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)).start(); }
        if (list != null) list.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }
    private void refreshList(boolean reset) {
        if (adapter == null) return;
        visible.clear(); String needle = CategoryRules.normalized(query.trim());
        if (!searching && category.equals("favorites")) {
            for (String id : favorites) for (App a : apps) if (a.id.equals(id)) { visible.add(a); break; }
        } else for (App a : apps) {
            if (searching ? CategoryRules.normalized(a.name).contains(needle) : category.equals("all apps") || group(a).equals(category)) visible.add(a);
        }
        adapter.notifyDataSetChanged();
        empty.setText(searching ? "no matches\ntry another name" : category.equals("favorites") ? "no favorites yet\n\nHold an app in all apps\nto add a favorite." : "no apps here\n\nHold an app to move it here.");
        empty.setVisibility(visible.isEmpty()?View.VISIBLE:View.GONE);
        alphabet.setVisibility(!searching && category.equals("favorites") || visible.isEmpty()?View.GONE:View.VISIBLE); alphabet.update();
        if (reset) list.setSelection(searching?0:scrollPositions.getOrDefault(category,0));
    }
    private final class AppRow extends LinearLayout {
        final ImageView icon;
        final TextView label;
        AppRow() {
            super(MainActivity.this); setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(2),dp(11),dp(2),dp(11)); setMinimumHeight(dp(theme.liquid?Math.max(72,theme.rowHeight):theme.rowHeight));
            icon=new ImageView(MainActivity.this); icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            if(theme.liquid&&theme.glass>0){icon.setBackground(glassSurface(icon));icon.setPadding(dp(4),dp(4),dp(4),dp(4));}
            int iconFrame=theme.liquid&&theme.glass>0?Math.max(44,theme.iconSize+8):theme.iconSize;
            LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(dp(iconFrame),dp(iconFrame));
            imageParams.setMargins(0,0,dp(20),0); addView(icon,imageParams);
            label=text("",theme.liquid?Math.min(22,theme.appSize):theme.appSize,fg); label.setLetterSpacing(-.02f);label.setMaxLines(2);label.setEllipsize(TextUtils.TruncateAt.END);
            addView(label,new LinearLayout.LayoutParams(0,-2,1));
        }
        @Override public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            animate().cancel();
            float shift=pressed?dp(theme.liquid?2:5):0, opacity=pressed?(theme.liquid?.88f:.55f):1;
            if(theme.motionMs>0&&android.animation.ValueAnimator.areAnimatorsEnabled())
                animate().translationX(shift).alpha(opacity).setDuration(pressed?100:240)
                    .setInterpolator(new android.view.animation.PathInterpolator(.2f,0,.2f,1)).start();
            else {setTranslationX(shift);setAlpha(opacity);}
        }
        void bind(App app) {
            label.setText(app.work ? getString(R.string.work_app,app.name) : app.name);
            label.setTextColor(fg); label.setTypeface(theme.liquid?Typeface.create(theme.typeface,450,false):theme.typeface);
            String style=prefs.getString("icons",theme.iconStyle);
            icon.setVisibility(style.equals("none")?GONE:VISIBLE); icon.setImageDrawable(app.icon);
            if(style.equals("mono")) { ColorMatrix colors=new ColorMatrix();colors.setSaturation(0);icon.setColorFilter(new ColorMatrixColorFilter(colors));icon.setAlpha(.82f); }
            else {icon.clearColorFilter();icon.setAlpha(1f);}
            setContentDescription(app.name+(app.work?", work profile":"")+". Hold for app options");
        }
    }
    private final class AppAdapter extends BaseAdapter {
        public int getCount(){return visible.size();}
        public Object getItem(int p){return visible.get(p);}
        public long getItemId(int p){return p;}
        public View getView(int position,View recycled,android.view.ViewGroup parent) {
            AppRow row = recycled instanceof AppRow ? (AppRow)recycled : new AppRow();
            row.bind(visible.get(position)); return row;
        }
    }
    /** Category switcher inside the dock pill. Tap or scrub to switch; swipe up for quick settings. */
    private final class CategoryRail extends SeekBar {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float shown=-1,downX,downY;private boolean opened;
        private android.animation.ValueAnimator slide;
        CategoryRail() {
            super(MainActivity.this);
            setThumb(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            setProgressDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            setSplitTrack(false);setBackground(null);
        }
        /** Padding of half a slot puts SeekBar's track ends on the first and last icon centers. */
        void fitSlots(){int n=Math.max(1,categories.size()),w=getWidth();if(w==0)return;int pad=w/(2*n);if(pad!=getPaddingLeft())setPadding(pad,0,pad,0);}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);fitSlots();}
        void moveTo(int index){
            if(slide!=null)slide.cancel();
            if(shown<0||theme.motionMs==0||!android.animation.ValueAnimator.areAnimatorsEnabled()){shown=index;invalidate();return;}
            slide=android.animation.ValueAnimator.ofFloat(shown,index);slide.setDuration(theme.expressive?420:220);
            // Expressive motion: a spatial spring with a little overshoot.
            slide.setInterpolator(theme.expressive?new android.view.animation.OvershootInterpolator(1.4f):new android.view.animation.PathInterpolator(.2f,0,0,1));
            slide.addUpdateListener(a->{shown=(float)a.getAnimatedValue();invalidate();});slide.start();
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();opened=false;}
            if(opened)return true;
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&downY-e.getY()>dp(30)&&downY-e.getY()>Math.abs(e.getX()-downX)*1.5f){
                opened=true;MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.onTouchEvent(cancel);cancel.recycle();openSettings();return true;
            }
            return super.onTouchEvent(e);
        }
        @Override protected synchronized void onDraw(Canvas c) {
            if(categories.isEmpty())return;
            if(shown<0)shown=getProgress();
            float left=getPaddingLeft(),right=getWidth()-getPaddingRight(),y=getHeight()/2f;
            float step=(right-left)/Math.max(1,categories.size()-1),slot=categories.size()>1?step:getWidth();
            boolean icons=slot>=dp(28);
            // Sliding highlight behind the selected category.
            // Expressive: M3 navigation-bar pill indicator (wide, 32dp tall); otherwise a softer capsule.
            float x=left+step*shown,half=theme.expressive?Math.min(slot/2f-dp(2),dp(28)):Math.min(slot/2f-dp(3),dp(24)),tall=theme.expressive?dp(16):dp(20);
            paint.setStyle(Paint.Style.FILL);paint.setColor(secondaryContainer);
            if(icons)c.drawRoundRect(x-half,y-tall,x+half,y+tall,tall,tall,paint);
            int selectedInk=theme.expressive?onSecondaryContainer:accent;
            for(int i=0;i<categories.size();i++){
                float cx=left+step*i;boolean selected=i==getProgress();
                if(icons)uiIcons.draw(c,categories.get(i),cx,y,dp(selected?16:15),selected?selectedInk:MetroArt.alpha(fg,theme.expressive?.7f:.55f));
                else{paint.setColor(selected?accent:MetroArt.alpha(fg,.45f));c.drawCircle(cx,y,dp(selected?4:2.5f),paint);}
            }
        }
    }
    private android.graphics.drawable.Drawable dockSurface(View host){
        if(theme.glass>0){GlassSurface glass=glassSurface(host);glass.pill=true;return glass;}
        GradientDrawable d=new GradientDrawable();d.setCornerRadius(dp(28));d.setColor(surfaceContainer);
        if(!theme.expressive)d.setStroke(dp(1),MetroArt.alpha(fg,.10f));
        return d;
    }
    /** Round search button. Tap to search, hold for quick settings. Uses the theme's search mark only if a theme overrides it. */
    private View searchButton(){
        View glyph;
        boolean custom;try{custom=theme.uiIcon("search")!=null;}catch(IOException e){custom=false;}
        if(custom){MetroArt.Icon icon=new MetroArt.Icon(this,uiIcons,"search",fg,false);icon.setGlyphSize(18);glyph=icon;}
        else glyph=new View(this){
            final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c){
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(theme.expressive?2.6f:2.2f));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(theme.expressive?onPrimaryContainer:fg);
                float cx=getWidth()/2f-dp(2),cy=getHeight()/2f-dp(2),r=dp(7.5f);
                c.drawCircle(cx,cy,r,p);float d=r*.7071f;c.drawLine(cx+d+dp(1),cy+d+dp(1),cx+d+dp(6),cy+d+dp(6),p);
            }
        };
        FrameLayout button=new FrameLayout(this);
        if(theme.expressive){
            // M3 Expressive FAB: rounded square in the primary container that morphs toward a circle while pressed.
            GradientDrawable d=new GradientDrawable();d.setColor(primaryContainer);d.setCornerRadius(dp(18));button.setBackground(d);button.setElevation(dp(3));
            button.setOnTouchListener((v,e)->{int a=e.getActionMasked();if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){
                float to=a==MotionEvent.ACTION_DOWN?dp(28):dp(18);android.animation.ObjectAnimator.ofFloat(d,"cornerRadius",d.getCornerRadius(),to).setDuration(theme.motionMs>0?260:0).start();}
                return false;});
        }else button.setBackground(dockSurface(button));
        button.addView(glyph,new FrameLayout.LayoutParams(-1,-1));glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        button.setForeground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(MetroArt.alpha(theme.expressive?onPrimaryContainer:accent,.16f)),null,theme.expressive?null:new android.graphics.drawable.ShapeDrawable(new android.graphics.drawable.shapes.OvalShape())));
        if(theme.expressive)button.setClipToOutline(true);
        button.setFocusable(true);button.setClickable(true);button.setContentDescription("Search apps. Hold for settings");
        button.setOnClickListener(v->openSearch());
        button.setOnLongClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);openSettings();return true;});
        return button;
    }
    private void launch(App app) {
        hideKeyboard();
        try { launcher.startMainActivity(app.component,app.user,null,null); }
        catch (Exception e) { toast("App unavailable. Its profile may be paused."); loadApps(); }
    }
    private void openSearch() {
        if (searching) { search.requestFocus(); return; }
        searching=true; query=""; buildUi(); search.requestFocus();
        search.postDelayed(() -> { if (searching && search != null) getSystemService(InputMethodManager.class).showSoftInput(search,InputMethodManager.SHOW_IMPLICIT); },150);
    }
    private void hideKeyboard() { if (root!=null) getSystemService(InputMethodManager.class).hideSoftInputFromWindow(root.getWindowToken(),0); }

    private final class CategoryTitles extends HorizontalScrollView {
        float downX, downY;
        CategoryTitles() { super(MainActivity.this); }
        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = event.getX(); downY = event.getY();
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - downX, dy = event.getY() - downY;
                if (Math.abs(dx) > dp(36) && Math.abs(dx) > Math.abs(dy)) {
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel); cancel.recycle();
                    int next = categories.indexOf(category) + (dx < 0 ? 1 : -1);
                    if (next >= 0 && next < categories.size()) selectCategory(categories.get(next), true);
                    else refreshCategories();
                    return true;
                }
            }
            return super.dispatchTouchEvent(event);
        }
    }

    private final class SwipeFrame extends FrameLayout {
        float downX,downY; boolean horizontal;
        SwipeFrame(){super(MainActivity.this);}
        @Override public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
            if(searching) return false;
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){ downX=e.getX(); downY=e.getY(); horizontal=false; }
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
                boolean railEdge = prefs.getBoolean("left",false)?downX<dp(44):downX>getWidth()-dp(44);
                if(!railEdge && Math.abs(e.getX()-downX)>dp(28) && Math.abs(e.getX()-downX)>Math.abs(e.getY()-downY)*1.5f){ horizontal=true; return true; }
            }
            return false;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getActionMasked()==MotionEvent.ACTION_UP && horizontal){ int i=categories.indexOf(category)+(e.getX()<downX?1:-1); if(i>=0&&i<categories.size())selectCategory(categories.get(i),true); horizontal=false; }
            if(e.getActionMasked()==MotionEvent.ACTION_UP) performClick();
            if(e.getActionMasked()==MotionEvent.ACTION_CANCEL) horizontal=false;
            return true;
        }
    }
    private final class AlphabetRail extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); final ArrayList<String> letters = new ArrayList<>(); final ArrayList<Integer> positions=new ArrayList<>(); int selected=-1;
        AlphabetRail(){super(MainActivity.this);setContentDescription("Alphabet scrubber. Drag to jump to an app letter");setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void update(){ letters.clear();positions.clear(); for(int i=0;i<visible.size();i++){String l=CategoryRules.initial(visible.get(i).name);if(!letters.contains(l)){letters.add(l);positions.add(i);}}invalidate(); }
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(letters.isEmpty())return;paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(10));float step=Math.min(dp(22),(getHeight()-dp(16))/(float)letters.size());float start=(getHeight()-step*letters.size())/2;for(int i=0;i<letters.size();i++){paint.setColor(i==selected?accent:muted);c.drawText(letters.get(i),getWidth()/2f,start+step*(i+.5f)+dp(4),paint);}}
        @Override public boolean performClick(){super.performClick();return true;}
        @Override public boolean onTouchEvent(MotionEvent e){
            if(letters.isEmpty())return false;
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){selected=-1;letter.setVisibility(GONE);invalidate();if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;}
            getParent().requestDisallowInterceptTouchEvent(true);float step=Math.min(dp(22),(getHeight()-dp(16))/(float)letters.size());float start=(getHeight()-step*letters.size())/2;
            int i=Math.max(0,Math.min(letters.size()-1,(int)((e.getY()-start)/step)));
            if(i!=selected){selected=i;list.setSelection(positions.get(i));letter.setText(letters.get(i));letter.setVisibility(VISIBLE);performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);invalidate();}return true;
        }
    }

    private void appMenu(App app) {
        boolean favorite=favorites.contains(app.id);
        ArrayList<String> options=new ArrayList<>(Arrays.asList(favorite?"remove favorite":"add to favorites","move to category","app info","uninstall"));
        if(favorite){options.add("move favorite up");options.add("move favorite down");}
        menu(app.name,options.toArray(new String[0]), i -> {
            if(i==0){if(favorite)favorites.remove(app.id);else if(favorites.size()<20)favorites.add(app.id);else{toast("Favorites holds up to 20 apps");return;}saveState();refreshList(false);}
            if(i==1){ArrayList<String> destinations=editableCategories();destinations.add("automatic");menu("move to",destinations.toArray(new String[0]),n->{if(n==destinations.size()-1)assignments.remove(app.id);else assignments.put(app.id,destinations.get(n));saveState();refreshList(false);});}
            if(i==2)try{launcher.startAppDetailsActivity(app.component,app.user,null,null);}catch(Exception e){toast("App info unavailable");}
            if(i==3){if(app.work){toast("Use app info to manage work apps");return;}safeStart(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+app.component.getPackageName())));}
            if(i>=4){int p=favorites.indexOf(app.id),to=p+(i==4?-1:1);if(to>=0&&to<favorites.size()){Collections.swap(favorites,p,to);saveState();refreshList(false);}}
        });
    }
    private String version(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(PackageManager.NameNotFoundException e){return "";}}
    private void aboutSettings(){menu("72F "+version(),new String[]{"about 72F","open-source notices"},n->{if(n==1){try(java.io.InputStream notice=getAssets().open("THIRD-PARTY-NOTICE.txt");java.io.InputStream license=getAssets().open("material-icons-LICENSE.txt")){message("open-source notices",new String(ThemeEngine.readBounded(notice,32768),java.nio.charset.StandardCharsets.UTF_8)+"\n\n"+new String(ThemeEngine.readBounded(license,32768),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){fileError(e);}return;}message("72F "+version(),"Horizontal categories. Vertical app lists.\n\nSwipe sideways for categories; scroll vertically for apps. Hold apps to organize.\n\nOriginal native Android implementation. No ads, analytics, accounts, or internet permission. Theme files stay on your device.\n\nAutomatic categories use local heuristics; your choices take priority.\n\nContexts change the top zone and starting category by time, headphones, charging, Do Not Disturb or playing media. They are checked only while Home is on screen.");});}
    private void categorySettings(){menu("categories",new String[]{prefs.getBoolean("categories",true)?"turn categories off":"turn categories on","create category","show or hide categories","merge categories","rename custom category","delete custom category"},i->{
        if(i==0){prefs.edit().putBoolean("categories",!prefs.getBoolean("categories",true)).apply();refreshCategories();refreshList(true);}
        if(i==1)nameDialog("new category","",name->{custom.add(name);saveState();refreshCategories();});
        if(i==2){ArrayList<String> cs=editableCategories();boolean[] checked=new boolean[cs.size()];for(int j=0;j<cs.size();j++)checked[j]=!disabled.contains(cs.get(j));AlertDialog d=new AlertDialog.Builder(this).setTitle("visible categories").setMultiChoiceItems(cs.toArray(new String[0]),checked,(dialog,n,on)->{if(on)disabled.remove(cs.get(n));else disabled.add(cs.get(n));}).setPositiveButton("done",(a,b)->{saveState();refreshCategories();refreshList(true);}).create();d.setOnCancelListener(a->{saveState();refreshCategories();refreshList(true);});showDialog(d);}
        if(i==3){ArrayList<String> cs=editableCategories();menu("merge from",cs.toArray(new String[0]),n->{String from=cs.remove(n);menu("merge into",cs.toArray(new String[0]),to->{String target=cs.get(to);merges.put(from,target);disabled.remove(target);saveState();refreshCategories();refreshList(true);toast("Merged into "+target);});});}
        if(i==4||i==5){ArrayList<String> cs=new ArrayList<>(custom);cs.removeAll(merges.keySet());if(cs.isEmpty()){toast("No custom categories yet");return;}menu(i==4?"rename":"delete",cs.toArray(new String[0]),n->{String old=cs.get(n);if(i==4)nameDialog("rename category",old,name->{custom.set(custom.indexOf(old),name);assignments.replaceAll((k,v)->v.equals(old)?name:v);merges.replaceAll((k,v)->v.equals(old)?name:v);if(disabled.remove(old))disabled.add(name);if(category.equals(old))category=name;saveState();refreshCategories();refreshList(true);});else{track(new AlertDialog.Builder(this).setTitle("Delete "+old+"?").setMessage("Apps remain installed and return to automatic categories.").setNegativeButton("cancel",null).setPositiveButton("delete",(a,b)->{custom.remove(old);disabled.remove(old);assignments.values().removeIf(v->v.equals(old));merges.values().removeIf(v->v.equals(old));saveState();refreshCategories();refreshList(true);}).show());}});}
    });}
    private interface NameAction{void accept(String s);}
    private void nameDialog(String title,String existing,NameAction action){
        EditText input=new EditText(this);input.setSingleLine();input.setText(existing);input.setSelectAllOnFocus(true);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("cancel",null).setPositiveButton("save",null).create();showDialog(d);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String name=input.getText().toString().trim().toLowerCase(Locale.ROOT);ArrayList<String> used=editableCategories();used.addAll(merges.keySet());used.add("favorites");used.add("all apps");
            if(name.isEmpty()||!name.equals(existing)&&used.contains(name)){input.setError("Choose a unique category name");return;}action.accept(name);d.dismiss();});
    }
    private void headerSettings(){menu("top zone",new String[]{"default height · "+prefs.getInt("header",20)+"%","media access settings","calendar access"},i->{
        if(i==0)menu("top zone height",new String[]{"none","10%","20%","30%","40%"},n->{prefs.edit().putInt("header",n*10).apply();buildUi();});
        if(i==1)safeStart(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        if(i==2){if(calendarAllowed())safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));else requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR},49);}
    });}
    private void bindMedia(){
        if(!mediaWanted()){if(controller!=null)controller.unregisterCallback(mediaCallback);controller=null;updateMedia();return;}
        try{List<MediaController> sessions=getSystemService(MediaSessionManager.class).getActiveSessions(new ComponentName(this,MediaAccessService.class));MediaController next=sessions.isEmpty()?null:sessions.get(0);for(MediaController c:sessions)if(c.getPlaybackState()!=null&&c.getPlaybackState().getState()==PlaybackState.STATE_PLAYING){next=c;break;}
            if(controller!=null&&next!=null&&controller.getSessionToken().equals(next.getSessionToken())){updateMedia();return;}
            if(controller!=null)controller.unregisterCallback(mediaCallback);controller=next;if(controller!=null)controller.registerCallback(mediaCallback,handler);
        }catch(SecurityException e){if(controller!=null)controller.unregisterCallback(mediaCallback);controller=null;}
        updateMedia();
    }
    private void updateMedia(){if(mediaBox==null)return;boolean show=controller!=null;mediaBox.setVisibility(show?View.VISIBLE:View.GONE);if(!show)return;MediaMetadata m=controller.getMetadata();mediaTitle.setText(m==null?"now playing":m.getDescription().getTitle());mediaArtist.setText(m==null?"":m.getDescription().getSubtitle());if(playButton!=null)playButton.setMark(controller.getPlaybackState()!=null&&controller.getPlaybackState().getState()==PlaybackState.STATE_PLAYING?"pause":"play",fg);}
    private void mediaAction(int action){if(controller==null)return;MediaController.TransportControls c=controller.getTransportControls();if(action==0)c.skipToPrevious();else if(action==2)c.skipToNext();else if(controller.getPlaybackState()!=null&&controller.getPlaybackState().getState()==PlaybackState.STATE_PLAYING)c.pause();else c.play();}

    private void themeSettings(){menu("appearance",new String[]{"retro · cyan","retro · cobalt","retro · magenta","paper · light","Wave","Liquid Retro","Material 3 Expressive","import theme","export theme package","title size","app icons","app icon packs","effects","launcher icon set"},i->{
        try{
            if(i<7){applyPreset(i);return;}
            i--;
            if(i==6)pickDocument(41,false,"application/octet-stream","72f-theme.72ftheme.zip");
            if(i==7)pickDocument(42,true,"application/zip","72f-theme.72ftheme.zip");
            if(i==8)menu("title size",new String[]{"compact · 32","retro · 60","oversized · 72"},n->{try{JSONObject t=themeJson();t.put("titleSize",new int[]{32,60,72}[n]);applyTheme(t);persistTheme();buildUi();}catch(Exception e){fileError(e);}});
            if(i==9)menu("app icons",new String[]{"monochrome","original color","text only"},n->{prefs.edit().putString("icons",new String[]{"mono","color","none"}[n]).apply();buildUi();});
            if(i==10)chooseIconPack();if(i==11)effectsSettings();
            if(i==12)menu("launcher icon set",new String[]{"Retro","Wave","Material"},n->{try{JSONObject t=themeJson();t.put("uiIconSet",new String[]{"retro","wave","material"}[n]);t.remove("uiIcons");applyTheme(t);persistTheme();buildUi();}catch(Exception e){fileError(e);}});
        }catch(Exception e){fileError(e);}
    });}
    private void applyTheme(JSONObject t)throws Exception {
        ThemeEngine next=new ThemeEngine(t,BackupStore.themeDirectory(this,prefs));theme=next;uiIcons=new UiIcons(this,next);
        bg=next.background;fg=next.text;muted=next.muted;accent=next.accent;font=next.font;titleSize=next.titleSize;
        boolean dark=Color.luminance(bg)<.5;
        if(next.dynamic&&Build.VERSION.SDK_INT>=31){
            dark=(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)!=android.content.res.Configuration.UI_MODE_NIGHT_NO;
            bg=getColor(dark?android.R.color.system_neutral1_900:android.R.color.system_neutral1_10);
            fg=getColor(dark?android.R.color.system_neutral1_100:android.R.color.system_neutral1_900);
            muted=getColor(dark?android.R.color.system_neutral2_200:android.R.color.system_neutral2_700);
            accent=getColor(dark?android.R.color.system_accent1_200:android.R.color.system_accent1_600);
            primaryContainer=getColor(dark?android.R.color.system_accent1_700:android.R.color.system_accent1_100);
            onPrimaryContainer=getColor(dark?android.R.color.system_accent1_100:android.R.color.system_accent1_900);
            secondaryContainer=getColor(dark?android.R.color.system_accent2_700:android.R.color.system_accent2_100);
            onSecondaryContainer=getColor(dark?android.R.color.system_accent2_100:android.R.color.system_accent2_900);
            surfaceContainer=mix(bg,getColor(dark?android.R.color.system_neutral1_800:android.R.color.system_neutral1_50),dark?.6f:1f);
        }else if(next.expressive){
            // Tonal roles derived from this theme's own accent, so Expressive works with any palette.
            primaryContainer=mix(bg,accent,dark?.42f:.24f);onPrimaryContainer=dark?mix(accent,Color.WHITE,.72f):mix(accent,Color.BLACK,.62f);
            secondaryContainer=mix(bg,mix(accent,muted,.55f),dark?.32f:.20f);onSecondaryContainer=dark?mix(accent,Color.WHITE,.78f):mix(accent,Color.BLACK,.66f);
            surfaceContainer=mix(bg,fg,dark?.09f:.05f);
        }else{
            primaryContainer=accent;onPrimaryContainer=onAccent();secondaryContainer=MetroArt.alpha(accent,Color.luminance(accent)>.6?.22f:.18f);onSecondaryContainer=accent;
            surfaceContainer=dark?MetroArt.alpha(mix(bg,fg,.12f),.92f):MetroArt.alpha(fg,.06f);
        }
    }
    private JSONObject themeJson(){try{return new JSONObject(theme.json.toString());}catch(JSONException e){throw new IllegalStateException(e);}}
    private void persistTheme(){prefs.edit().putString("theme",theme.json.toString()).apply();prepareSoftWallpaper();}
    private void effectsSettings(){menu("effects",new String[]{"background blur","glass surfaces","wave animation · "+(theme.animated?"on":"off"),"material 3 expressive · "+(theme.expressive?"on":"off"),"wallpaper colors · "+(Build.VERSION.SDK_INT<31?"Android 12+":theme.dynamic?"on":"off")},i->{
        if(i==3)changeEffect("expressive",!theme.expressive);
        if(i==4){if(Build.VERSION.SDK_INT<31){toast("Wallpaper colors need Android 12 or newer");return;}changeEffect("dynamic",!theme.dynamic);}
        if(i==0)menu("blur",new String[]{"off","soft · 8","frosted · 18","deep · 32"},n->changeEffect("blur",new int[]{0,8,18,32}[n]));
        if(i==1)menu("glass",new String[]{"off","subtle","frosted","liquid glass"},n->{try{JSONObject t=themeJson();t.put("glass",new int[]{0,20,40,65}[n]);t.put("liquid",n==3);applyTheme(t);persistTheme();buildUi();}catch(Exception e){fileError(e);}});
        if(i==2)changeEffect("animated",!theme.animated);
    });}
    private void changeEffect(String key,Object value){try{JSONObject t=themeJson();t.put(key,value);applyTheme(t);persistTheme();buildUi();}catch(Exception e){fileError(e);}}
    private void chooseIconPack(){
        List<IconPacks.Pack> packs=IconPacks.installed(this);ArrayList<String> names=new ArrayList<>();names.add("system icons");for(IconPacks.Pack p:packs)names.add(p.label);
        if(packs.isEmpty()){message("icon packs","Install an ADW-compatible icon pack, then come back here. System icons are active.");return;}
        menu("icon packs",names.toArray(new String[0]),i->{if(i==0){prefs.edit().remove("iconPack").apply();loadApps();return;}
            IconPacks.Pack p=packs.get(i-1);worker.execute(()->{try{new IconPacks(this,p.packageName);prefs.edit().putString("iconPack",p.packageName).apply();handler.post(()->{loadApps();toast(p.label+" applied");});}catch(Exception e){handler.post(()->fileError(e));}});
        });
    }
    private void pickDocument(int request,boolean create,String mime,String name){
        Intent intent=new Intent(create?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(create?mime:"*/*");
        if(create)intent.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(intent,request);
    }
    private void backupSettings(){menu("backup & restore",new String[]{"export launcher backup","restore launcher backup"},i->{try{pickDocument(i==0?45:46,i==0,"application/zip","72f-launcher.72fbackup.zip");}catch(Exception e){fileError(e);}});}
    private void fileError(Exception error){if(!isDestroyed())message("Couldn't use this file",error.getMessage()==null?"Choose a supported 72F file.":error.getMessage());}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==47||request==48){int id=prefs.getInt("pendingWidget",-1);if(id<0)return;if(result!=RESULT_OK){cancelWidget();return;}if(request==47)configureWidget(id);else placeWidget(id);return;}
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();if(request==44){importWallpaper(uri);return;}
        if(request!=41&&request!=42&&request!=45&&request!=46)return;
        final ThemeEngine currentTheme=theme;
        worker.execute(()->{
            try{
                if(request==42||request==45){try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Destination unavailable");if(request==42)currentTheme.exportSnapshot(this,prefs,out);else BackupStore.export(this,prefs,currentTheme,out);}handler.post(()->toast(request==42?"Theme exported":"Backup exported"));}
                if(request==41)importTheme(uri);
                if(request==46){BackupStore.Pending prepared;try(InputStream in=getContentResolver().openInputStream(uri)){prepared=BackupStore.prepare(this,in);}handler.post(()->reviewRestore(prepared));}
            }catch(Exception e){handler.post(()->fileError(e));}
        });
    }
    private void importTheme(Uri uri)throws Exception {
        File stage=new File(getFilesDir(),"imports/"+UUID.randomUUID());
        try{
            if(!stage.mkdirs())throw new IOException("Cannot prepare theme");
            File input=new File(stage,"source");try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(input)){if(in==null)throw new IOException("File unavailable");byte[] buffer=new byte[8192];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>ThemeEngine.MAX_PACKAGE)throw new IOException("Theme too large");out.write(buffer,0,n);}}
            boolean zip;try(InputStream in=new FileInputStream(input)){zip=in.read()=='P'&&in.read()=='K';}
            File assets=new File(stage,"theme");JSONObject definition;
            if(zip){try(InputStream in=new FileInputStream(input)){ThemeEngine.unpack(in,assets);}definition=ThemeEngine.readJson(new File(assets,"theme.json"));}
            else definition=ThemeEngine.readJson(input);
            ThemeEngine next=new ThemeEngine(definition,zip?assets:null);input.delete();
            SharedPreferences.Editor edit=prefs.edit().putString("themeDir",zip?"imports/"+stage.getName()+"/theme":"").putString("theme",next.json.toString()).remove("icons");
            String photo=next.json.optString("wallpaper","");
            if(!photo.isEmpty())edit.putString("wallpaperMode","photo").putString("wallpaperPath","imports/"+stage.getName()+"/theme/"+photo);
            else if(next.json.has("wallpaperMode"))edit.putString("wallpaperMode",next.json.getString("wallpaperMode"));
            edit.putString("iconPack",next.json.optString("iconPack",""));
            if(!edit.commit())throw new IOException("Cannot persist theme");
            handler.post(()->{if(isDestroyed())return;try{applyTheme(next.json);restoreWallpaper();prepareSoftWallpaper();buildUi();loadApps();toast(next.name+" applied");}catch(Exception e){fileError(e);}});
        }catch(Exception e){ThemeEngine.deleteTree(stage);throw e;}
    }
    private void reviewRestore(BackupStore.Pending prepared){
        if(isDestroyed()){ThemeEngine.deleteTree(prepared.directory);return;}
        pendingRestore=prepared;
        try{
            AlertDialog d=new AlertDialog.Builder(this).setTitle("restore launcher?").setMessage(prepared.summary())
                .setNegativeButton("cancel",(a,b)->{ThemeEngine.deleteTree(prepared.directory);pendingRestore=null;})
                .setPositiveButton("restore",(a,b)->{pendingRestore=null;worker.execute(()->{try{BackupStore.apply(this,prefs,prepared);handler.post(()->{if(isDestroyed())return;pendingRestore=null;wallpaper=null;softWallpaper=null;readState();activeContext=resolveContext();appliedLayout=layoutKey(activeContext);category=startCategory(activeContext);searching=false;query="";scrollPositions.clear();restoreWallpaper();buildUi();loadApps();toast("Launcher restored");});}catch(Exception e){handler.post(()->fileError(e));}});}).create();
            d.setOnCancelListener(a->{ThemeEngine.deleteTree(prepared.directory);pendingRestore=null;});showDialog(d);
        }catch(Exception e){ThemeEngine.deleteTree(prepared.directory);fileError(e);}
    }
    private GlassSurface glassSurface(View host){GlassSurface surface=new GlassSurface(host,scene,()->backdropView.getBackground(),glassBackdrop,theme,getResources().getDisplayMetrics().density);glassSurfaces.add(surface);host.setClipToOutline(true);if(theme.liquid)host.setElevation(dp(3));return surface;}
    private void updateBackdrop(){
        if(backdropView==null)return;glassBackdrop.clear();for(GlassSurface surface:glassSurfaces)surface.invalidateSelf();if(waves!=null)waves.stop();waves=null;
        Bitmap image=Build.VERSION.SDK_INT<31&&softWallpaper!=null?softWallpaper:wallpaper;
        boolean system=activeWallpaperMode.equals("system");
        if(theme.backdrop.equals("waves")&&!activeWallpaperMode.equals("photo")&&!system){waves=new Ps3Backdrop(bg,accent);backdropView.setBackground(waves);if(foreground&&theme.animated&&android.animation.ValueAnimator.areAnimatorsEnabled())waves.start();}
        else backdropView.setBackground(new MetroArt.Backdrop(activeWallpaperMode.equals("photo")?image:null,bg,theme.backdrop.equals("solid")?bg:accent,prefs.getInt("wallpaperDim",60)/100f,system));
        if(Build.VERSION.SDK_INT>=31){backdropView.setRenderEffect(theme.blur>0?RenderEffect.createBlurEffect(dp(theme.blur),dp(theme.blur),Shader.TileMode.CLAMP):null);getWindow().setBackgroundBlurRadius(system?dp(theme.blur):0);}
    }
    private void prepareSoftWallpaper(){
        final int request=++effectGeneration;final Bitmap source=wallpaper;final int amount=theme.blur;
        if(Build.VERSION.SDK_INT>=31||source==null||amount==0){softWallpaper=null;return;}
        worker.execute(()->{Bitmap image=ThemeEffects.blur(source,amount);handler.post(()->{if(request==effectGeneration&&!isDestroyed()){softWallpaper=image;updateBackdrop();}else if(image!=source)image.recycle();});});
    }
    private void restoreWallpaper() {
        final int request=++wallpaperGeneration;
        File file=BackupStore.wallpaperFile(this,prefs);
        if(!prefs.getString("wallpaperMode","solid").equals("photo"))return;
        if(!file.exists())return;
        final File selectedFile=file;
        worker.execute(() -> {
            try {
                Bitmap image=decodeWallpaper(selectedFile);
                handler.post(() -> { if(!isDestroyed()&&request==wallpaperGeneration){wallpaper=image;prepareSoftWallpaper();buildUi();}else image.recycle(); });
            } catch(IOException ignored) { }
        });
    }
    private Bitmap decodeWallpaper(File file) throws IOException {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file),(decoder,info,source) -> {
            int w=info.getSize().getWidth(),h=info.getSize().getHeight();
            float scale=Math.min(1f,2048f/Math.max(w,h));
            decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            // Keep full color precision: wallpapers often contain smooth gradients.
        });
    }
    private void importWallpaper(Uri uri) {
        final int request=++wallpaperGeneration;
        toast("Preparing wallpaper…");
        worker.execute(() -> {
            File incoming=null;
            try {
                incoming=File.createTempFile("wallpaper-",".image",getCacheDir());
                try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(incoming)) {
                    if(in==null)throw new IOException("Image unavailable");
                    byte[] buffer=new byte[8192];int n,total=0;
                    while((n=in.read(buffer))!=-1){total+=n;if(total>32*1024*1024)throw new IOException("Choose an image smaller than 32 MB");out.write(buffer,0,n);}
                }
                Bitmap image=decodeWallpaper(incoming);
                android.util.AtomicFile saved=new android.util.AtomicFile(new File(getFilesDir(),"wallpaper.jpg"));
                FileOutputStream output=null;
                try { output=saved.startWrite();if(!image.compress(Bitmap.CompressFormat.JPEG,92,output))throw new IOException("Couldn't save image");saved.finishWrite(output); }
                catch(IOException error){if(output!=null)saved.failWrite(output);image.recycle();throw error;}
                prefs.edit().putString("wallpaperMode","photo").putString("wallpaperPath","wallpaper.jpg").apply();
                handler.post(() -> { if(!isDestroyed()&&request==wallpaperGeneration){wallpaper=image;prepareSoftWallpaper();buildUi();toast("Wallpaper set");}else image.recycle(); });
            } catch(Exception error) {
                handler.post(() -> {if(!isDestroyed())message("Couldn't open wallpaper","Choose a readable photo or image, up to 32 MB.");});
            } finally { if(incoming!=null)incoming.delete(); }
        });
    }
    private void wallpaperSettings() {
        menu("wallpaper",new String[]{"choose a photo","use phone wallpaper","solid background","wallpaper dimming","use saved photo"},i -> {
            if(i==0){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),44);}catch(ActivityNotFoundException e){toast("No image picker available");}}
            if(i==1||i==2){prefs.edit().putString("wallpaperMode",i==1?"system":"solid").apply();buildUi();}
            if(i==3){
                LinearLayout box=column();box.setPadding(dp(24),dp(12),dp(24),dp(18));
                TextView value=text(prefs.getInt("wallpaperDim",60)+"% dim",24,fg);box.addView(value);
                TextView explanation=text("Adjust the wallpaper brightness behind apps.",14,muted);explanation.setPadding(0,dp(8),0,dp(16));box.addView(explanation);
                SeekBar dimmer=new SeekBar(this);dimmer.setContentDescription("Wallpaper dimming");dimmer.setMax(90);dimmer.setProgress(prefs.getInt("wallpaperDim",60));
                dimmer.setProgressTintList(ColorStateList.valueOf(accent));dimmer.setThumbTintList(ColorStateList.valueOf(accent));
                dimmer.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                    public void onProgressChanged(SeekBar bar,int amount,boolean user){if(!user)return;prefs.edit().putInt("wallpaperDim",amount).apply();value.setText(amount+"% dim");updateBackdrop();}
                    public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}
                });
                box.addView(dimmer,new LinearLayout.LayoutParams(-1,dp(48)));
                showDialog(new AlertDialog.Builder(this).setTitle("wallpaper dimming").setView(box).setPositiveButton("done",null).create());
            }
            if(i==4){if(!BackupStore.wallpaperFile(this,prefs).exists()){toast("Choose a photo first");return;}prefs.edit().putString("wallpaperMode","photo").apply();if(wallpaper==null)restoreWallpaper();else buildUi();}
        });
    }

    // Top zone: the active context decides which items fill the header and how tall it is.
    private void buildTopZone() {
        clock=null; date=null; mediaBox=null; alarmText=null; agendaText=null; playButton=null;
        ContextRules.Rule rule=activeContext;
        int percent=rule!=null&&rule.header>=0?rule.header:prefs.getInt("header",20);
        if(percent<=0||rule==null||rule.widgets.isEmpty())return;
        LinearLayout header=column(); header.setPadding(dp(26),dp(10),dp(26),dp(12)); header.setGravity(Gravity.CENTER_VERTICAL);
        int height=Math.max(90,(int)(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density*percent/100));
        boolean first=true;WidgetHosting.Stack stack=null;
        for(String item:rule.widgets){
            switch(item){
                case "clock":{
                    LinearLayout timeRow=row(); timeRow.setGravity(Gravity.CENTER_VERTICAL);
                    clock=text("",theme.layout.equals("xmb")?30:percent<=10?34:46,fg); clock.setLetterSpacing(-.045f); clock.setSingleLine(true);
                    timeRow.addView(clock,new LinearLayout.LayoutParams(0,-2,1.2f));
                    View divider=new View(this); divider.setBackgroundColor(MetroArt.alpha(accent,.75f));
                    LinearLayout.LayoutParams lineParams=new LinearLayout.LayoutParams(dp(1),dp(38)); lineParams.setMargins(dp(14),0,dp(18),0); timeRow.addView(divider,lineParams);
                    date=text("",11,muted); date.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); date.setLineSpacing(dp(6),1); date.setLetterSpacing(.1f);
                    date.setContentDescription("Date and active context. Tap for quick settings"); date.setOnClickListener(v->openSettings()); date.setMinHeight(dp(48)); date.setGravity(Gravity.CENTER_VERTICAL);
                    timeRow.addView(date,new LinearLayout.LayoutParams(0,-2,1)); header.addView(timeRow); updateClock();
                    clock.setContentDescription("Clock. Tap to open alarms"); clock.setOnClickListener(v->safeStart(new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)));
                    break;}
                case "alarm":
                    alarmText=text("",13,muted); alarmText.setLetterSpacing(.08f); alarmText.setPadding(0,first?0:dp(12),0,0); alarmText.setSingleLine();
                    alarmText.setOnClickListener(v->safeStart(new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))); header.addView(alarmText); updateAlarm(); break;
                case "agenda":
                    agendaText=text("",14,fg); agendaText.setLineSpacing(dp(4),1); agendaText.setPadding(0,first?0:dp(12),0,0); agendaText.setMaxLines(3); agendaText.setEllipsize(TextUtils.TruncateAt.END);
                    header.addView(agendaText); updateAgenda(); break;
                case "smartspace":{
                    View smartspace=smartspaceView(first);
                    if(smartspace!=null)header.addView(smartspace);
                    break;}
                case "media":{
                    mediaBox=column(); mediaBox.setPadding(0,first?0:dp(18),0,0);
                    TextView playing=text("NOW PLAYING",9,accent); playing.setLetterSpacing(.14f); mediaBox.addView(playing);
                    LinearLayout mediaRow=row(); mediaRow.setGravity(Gravity.CENTER_VERTICAL);
                    LinearLayout metadata=column(); metadata.setPadding(0,dp(6),dp(8),0);
                    mediaTitle=text("",18,fg); mediaArtist=text("",12,muted);
                    mediaTitle.setSingleLine(); mediaTitle.setEllipsize(TextUtils.TruncateAt.END); mediaArtist.setSingleLine();
                    metadata.addView(mediaTitle); metadata.addView(mediaArtist); mediaRow.addView(metadata,new LinearLayout.LayoutParams(0,-2,1));
                    playButton=iconButton("play","Play or pause",true,v->mediaAction(1));
                    mediaRow.addView(iconButton("previous","Previous track",false,v->mediaAction(0)));
                    mediaRow.addView(playButton); mediaRow.addView(iconButton("next","Next track",false,v->mediaAction(2)));
                    mediaBox.addView(mediaRow); mediaBox.setVisibility(View.GONE); header.addView(mediaBox);
                    break;}
                default:{
                    // All app widgets of a context share one swipeable stack at the first widget's position.
                    View widget=hostedWidget(rule,item);
                    if(widget==null)break;
                    if(stack==null){stack=new WidgetHosting.Stack(this,accent,MetroArt.alpha(muted,.45f));LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,0,1);wp.setMargins(-dp(8),first?0:dp(10),-dp(8),0);header.addView(stack,wp);}
                    stack.add(widget);
                }
            }
            first=false;
        }
        if(stack!=null){final String id=rule.id;stack.setPage(stackPages.getOrDefault(id,0),page->stackPages.put(id,page));stack.setContentDescription(stack.count()+" widgets. Swipe sideways to switch, hold for options");}
        if(theme.glass>0){header.setBackground(glassSurface(header));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(height));hp.setMargins(dp(16),dp(8),dp(16),dp(8));root.addView(header,hp);}
        else root.addView(header,new LinearLayout.LayoutParams(-1,dp(height)));
    }
    // Smartspacer (optional app, API 29+): its Client SDK view renders At a Glance targets and complications.
    static final String SMARTSPACER="com.kieronquinn.app.smartspacer";
    private boolean smartspacerInstalled(){try{getPackageManager().getPackageInfo(SMARTSPACER,0);return true;}catch(PackageManager.NameNotFoundException e){return false;}}
    private View smartspaceView(boolean first){
        if(Build.VERSION.SDK_INT<29)return null;
        if(!smartspacerInstalled()){
            TextView hint=text("install Smartspacer for At a Glance cards",13,muted);hint.setPadding(0,first?0:dp(12),0,0);
            hint.setOnClickListener(v->safeStart(new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+SMARTSPACER))));
            hint.setContentDescription("Smartspacer is not installed. Tap to find it in your app store");return hint;
        }
        try{
            View view=getLayoutInflater().inflate(R.layout.smartspacer_view,root,false);
            ((com.kieronquinn.app.smartspacer.sdk.client.views.BcSmartspaceView)view).setTintColour(fg);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(96));p.setMargins(-dp(12),first?0:dp(8),-dp(12),0);view.setLayoutParams(p);
            return view;
        }catch(RuntimeException e){return null;}
    }
    private void updateAlarm(){
        if(alarmText==null)return;
        AlarmManager.AlarmClockInfo next=getSystemService(AlarmManager.class).getNextAlarmClock();
        if(next==null){alarmText.setVisibility(View.GONE);return;}
        String pattern=android.text.format.DateFormat.is24HourFormat(this)?"EEE HH:mm":"EEE h:mm a";
        alarmText.setVisibility(View.VISIBLE);alarmText.setText("ALARM  "+new SimpleDateFormat(pattern,Locale.getDefault()).format(new Date(next.getTriggerTime())).toUpperCase(Locale.getDefault()));
        alarmText.setContentDescription("Next alarm "+alarmText.getText()+". Tap to open alarms");
    }
    private boolean calendarAllowed(){return checkSelfPermission(android.Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED;}
    private void updateAgenda(){
        if(agendaText==null)return;
        agendaLoaded=System.currentTimeMillis();
        if(!calendarAllowed()){agendaText.setTextColor(muted);agendaText.setText("tap to show your calendar here");agendaText.setOnClickListener(v->requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR},49));return;}
        final TextView target=agendaText;final boolean is24=android.text.format.DateFormat.is24HourFormat(this);
        target.setOnClickListener(v->safeStart(new Intent(Intent.ACTION_VIEW,Uri.parse("content://com.android.calendar/time/"+System.currentTimeMillis()))));
        worker.execute(()->{
            StringBuilder out=new StringBuilder();long now=System.currentTimeMillis();
            Uri.Builder range=CalendarContract.Instances.CONTENT_URI.buildUpon();ContentUris.appendId(range,now);ContentUris.appendId(range,now+86400000L);
            try(android.database.Cursor c=getContentResolver().query(range.build(),new String[]{CalendarContract.Instances.TITLE,CalendarContract.Instances.BEGIN,CalendarContract.Instances.ALL_DAY,CalendarContract.Instances.END},
                CalendarContract.Instances.VISIBLE+"=1",null,CalendarContract.Instances.BEGIN+" ASC")){
                int lines=0;SimpleDateFormat time=new SimpleDateFormat(is24?"HH:mm":"h:mm",Locale.getDefault());
                while(c!=null&&c.moveToNext()&&lines<3){
                    if(c.getLong(3)<now)continue;
                    String title=c.getString(0);if(title==null||title.trim().isEmpty())title="busy";
                    if(lines++>0)out.append('\n');
                    out.append(c.getInt(2)==1?"today":time.format(new Date(c.getLong(1)))).append("   ").append(title.trim());
                }
            }catch(RuntimeException ignored){}
            String result=out.length()==0?"nothing in the next 24 hours":out.toString();
            handler.post(()->{if(target==agendaText){target.setTextColor(out.length()==0?muted:fg);target.setText(result);}});
        });
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==49){agendaLoaded=0;updateAgenda();if(sheet!=null)sheet.refresh();}
    }

    // Contexts: evaluated only while Home is visible.
    private ContextRules.Rule resolveContext(){
        String pin=prefs.getString("contextPin",null);
        if(pin!=null&&prefs.getLong("contextPinUntil",0)<System.currentTimeMillis()){prefs.edit().remove("contextPin").remove("contextPinUntil").apply();pin=null;}
        boolean playing=controller!=null&&controller.getPlaybackState()!=null&&controller.getPlaybackState().getState()==PlaybackState.STATE_PLAYING;
        return ContextRules.resolve(contexts,ContextStore.read(this,playing),pin);
    }
    private static String layoutKey(ContextRules.Rule r){return r==null?"":r.id+"|"+r.header+"|"+r.widgets+"|"+r.name;}
    private String startCategory(ContextRules.Rule r){return r==null||r.category.isEmpty()?"favorites":r.category;}
    private void evaluateContext(){
        if(contexts.isEmpty())return;
        ContextRules.Rule previous=activeContext,next=resolveContext();
        String key=layoutKey(next);
        if(key.equals(appliedLayout)){activeContext=next;return;}
        boolean switched=previous==null||!previous.id.equals(next.id);
        activeContext=next;appliedLayout=key;
        // Only follow the new context if you're still where the old one put you.
        if(switched&&!searching&&category.equals(startCategory(previous))){scrollPositions.remove(startCategory(next));category=startCategory(next);}
        if(root!=null)buildUi();
        bindMedia();
    }
    private void signalChanged(){handler.removeCallbacks(evaluate);handler.postDelayed(evaluate,1500);}
    private void registerSignals(){
        if(signalsRegistered)return;signalsRegistered=true;
        getSystemService(AudioManager.class).registerAudioDeviceCallback(audioDevices,handler);
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_POWER_CONNECTED);filter.addAction(Intent.ACTION_POWER_DISCONNECTED);filter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(signalReceiver,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(signalReceiver,filter);
    }
    private void unregisterSignals(){
        if(!signalsRegistered)return;signalsRegistered=false;
        getSystemService(AudioManager.class).unregisterAudioDeviceCallback(audioDevices);
        try{unregisterReceiver(signalReceiver);}catch(IllegalArgumentException ignored){}
    }
    private boolean mediaWanted(){
        if(activeContext!=null&&activeContext.widgets.contains("media"))return true;
        for(ContextRules.Rule r:contexts)if(r.enabled&&r.playing!=ContextRules.ANY)return true;
        return false;
    }
    private boolean mediaAccess(){String enabled=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");return enabled!=null&&enabled.contains(new ComponentName(this,MediaAccessService.class).flattenToString());}
    private void contextsChanged(){
        ContextRules.normalize(contexts);ContextStore.save(prefs,contexts);
        appliedLayout="";evaluateContext();bindMedia();if(sheet!=null)sheet.refresh();
    }
    private void contextOverride(){
        ArrayList<String> names=new ArrayList<>();names.add("automatic");for(ContextRules.Rule r:contexts)names.add(r.name);
        menu("use context",names.toArray(new String[0]),i->{
            if(i==0){prefs.edit().remove("contextPin").remove("contextPinUntil").apply();contextsChanged();toast("Contexts are automatic");return;}
            ContextRules.Rule r=contexts.get(i-1);
            menu("hold "+r.name,new String[]{"for 1 hour","for 3 hours","until tomorrow morning"},n->{
                long until;Calendar c=Calendar.getInstance();
                if(n<2)until=System.currentTimeMillis()+(n==0?1:3)*3600000L;
                else{c.add(Calendar.DAY_OF_YEAR,c.get(Calendar.HOUR_OF_DAY)<4?0:1);c.set(Calendar.HOUR_OF_DAY,4);c.set(Calendar.MINUTE,0);until=c.getTimeInMillis();}
                prefs.edit().putString("contextPin",r.id).putLong("contextPinUntil",until).apply();contextsChanged();
            });
        });
    }
    private String pinSummary(){
        String pin=prefs.getString("contextPin",null);if(pin==null||activeContext==null||!pin.equals(activeContext.id))return null;
        String pattern=android.text.format.DateFormat.is24HourFormat(this)?"EEE HH:mm":"EEE h:mm a";
        return "held until "+new SimpleDateFormat(pattern,Locale.getDefault()).format(new Date(prefs.getLong("contextPinUntil",0))).toLowerCase(Locale.getDefault());
    }

    // Third-party widgets.
    private View hostedWidget(ContextRules.Rule rule,String item){
        if(!item.startsWith("widget:"))return null;
        try{
            int id=Integer.parseInt(item.substring(7));AppWidgetProviderInfo info=widgetManager.getAppWidgetInfo(id);if(info==null)return null;
            AppWidgetHostView view=widgetHost.createView(this,id,info);
            if(Build.VERSION.SDK_INT>=31)view.setColorResources(widgetColors());
            view.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot){float d=getResources().getDisplayMetrics().density;int w=Math.round((r-l)/d),h=Math.round((b-t)/d);view.updateAppWidgetSize(new Bundle(),w,h,w,h);}});
            view.setContentDescription(info.loadLabel(getPackageManager())+" widget. Hold for options");
            if(view instanceof WidgetHosting.HostedView)((WidgetHosting.HostedView)view).onLongPress=()->widgetMenu(rule,item);
            return view;
        }catch(RuntimeException e){return null;}
    }
    /** Maps Material You palette slots to the 72F theme so dynamic-color widgets match it. */
    @android.annotation.SuppressLint("DiscouragedApi")
    private android.util.SparseIntArray widgetColors(){
        android.util.SparseIntArray colors=new android.util.SparseIntArray();
        int[] shades={0,10,50,100,200,300,400,500,600,700,800,900,1000};
        for(String family:new String[]{"accent1","accent2","accent3","neutral1","neutral2"}){
            int base=family.startsWith("accent")?accent:family.equals("neutral1")?mix(bg,fg,.5f):mix(bg,muted,.7f);
            for(int shade:shades){
                int id=getResources().getIdentifier("system_"+family+"_"+shade,"color","android");if(id==0)continue;
                float t=shade/1000f;colors.put(id,t<.5f?mix(Color.WHITE,base,t*2):mix(base,Color.BLACK,(t-.5f)*2));
            }
        }
        return colors;
    }
    private static int mix(int a,int b,float t){return Color.rgb(Math.round(Color.red(a)+(Color.red(b)-Color.red(a))*t),Math.round(Color.green(a)+(Color.green(b)-Color.green(a))*t),Math.round(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}
    private void addAppWidget(ContextRules.Rule target){
        if(target.widgets.size()>=ContextStore.MAX_WIDGETS){toast("This context is full");return;}
        PackageManager pm=getPackageManager();
        ArrayList<AppWidgetProviderInfo> providers=new ArrayList<>(widgetManager.getInstalledProvidersForProfile(android.os.Process.myUserHandle()));
        if(providers.isEmpty()){toast("No widgets installed");return;}
        HashMap<AppWidgetProviderInfo,String> labels=new HashMap<>();
        for(AppWidgetProviderInfo info:providers){String app;try{app=pm.getApplicationLabel(pm.getApplicationInfo(info.provider.getPackageName(),0)).toString();}catch(Exception e){app=info.provider.getPackageName();}labels.put(info,app+" · "+info.loadLabel(pm));}
        Collator collator=Collator.getInstance();providers.sort((a,b)->collator.compare(labels.get(a),labels.get(b)));
        String[] names=new String[providers.size()];for(int i=0;i<names.length;i++)names[i]=labels.get(providers.get(i));
        menu("add widget",names,i->{
            AppWidgetProviderInfo info=providers.get(i);int id=widgetHost.allocateAppWidgetId();
            prefs.edit().putInt("pendingWidget",id).putString("pendingWidgetContext",target.id).apply();
            if(widgetManager.bindAppWidgetIdIfAllowed(id,info.getProfile(),info.provider,null)){configureWidget(id);return;}
            Intent bind=new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,info.provider).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE,info.getProfile());
            try{startActivityForResult(bind,47);}catch(Exception e){cancelWidget();toast("This phone doesn't allow adding widgets here");}
        });
    }
    private void widgetMenu(ContextRules.Rule rule,String item){
        int id=Integer.parseInt(item.substring(7));AppWidgetProviderInfo info=widgetManager.getAppWidgetInfo(id);
        boolean reconfigurable=Build.VERSION.SDK_INT>=31&&info!=null&&info.configure!=null&&(info.widgetFeatures&AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE)!=0;
        int height=rule.header>=0?rule.header:prefs.getInt("header",20);
        ArrayList<String> options=new ArrayList<>(Arrays.asList("remove from "+rule.name,"top zone · "+height+"%","edit "+rule.name+" context"));
        if(reconfigurable)options.add("widget settings");
        menu(itemLabel(item),options.toArray(new String[0]),i->{
            if(i==0){removeContextItem(rule,item);contextsChanged();toast("Widget removed");}
            if(i==1)menu("top zone height",new String[]{"20%","30%","40%"},n->{rule.header=new int[]{20,30,40}[n];contextsChanged();});
            if(i==2){sheet.open(this::quickPage);sheet.push(()->contextPage(rule));}
            if(i==3)try{widgetHost.startAppWidgetConfigureActivityForResult(this,id,0,50,null);}catch(Exception e){toast("Widget settings unavailable");}
        });
    }
    private void configureWidget(int id){
        AppWidgetProviderInfo info=widgetManager.getAppWidgetInfo(id);
        if(info!=null&&info.configure!=null){try{widgetHost.startAppWidgetConfigureActivityForResult(this,id,0,48,null);return;}catch(Exception ignored){}}
        placeWidget(id);
    }
    private void placeWidget(int id){
        String target=prefs.getString("pendingWidgetContext","");prefs.edit().remove("pendingWidget").remove("pendingWidgetContext").apply();
        for(ContextRules.Rule r:contexts)if(r.id.equals(target)){if(r.widgets.size()<ContextStore.MAX_WIDGETS){r.widgets.add("widget:"+id);if(r.header==0||r.header<0&&prefs.getInt("header",20)<30)r.header=30;contextsChanged();return;}}
        widgetHost.deleteAppWidgetId(id);toast("Couldn't place that widget");
    }
    private void cancelWidget(){int id=prefs.getInt("pendingWidget",-1);if(id>=0)widgetHost.deleteAppWidgetId(id);prefs.edit().remove("pendingWidget").remove("pendingWidgetContext").apply();}
    private void removeContextItem(ContextRules.Rule rule,String item){
        rule.widgets.remove(item);
        if(item.startsWith("widget:")){boolean used=false;for(ContextRules.Rule r:contexts)used|=r.widgets.contains(item);if(!used)try{widgetHost.deleteAppWidgetId(Integer.parseInt(item.substring(7)));}catch(RuntimeException ignored){}}
    }
    private String itemLabel(String item){
        switch(item){case "smartspace":return "Smartspacer";case "clock":return "clock & date";case "alarm":return "next alarm";case "agenda":return "agenda";case "media":return "media controls";}
        try{AppWidgetProviderInfo info=widgetManager.getAppWidgetInfo(Integer.parseInt(item.substring(7)));if(info!=null)return info.loadLabel(getPackageManager())+" · widget";}catch(RuntimeException ignored){}
        return "missing widget";
    }

    // Settings: quick sheet that expands into the full page.
    private int surface(){return theme.expressive?surfaceContainer:Color.luminance(bg)>.5?bg:0xff101114;}
    private int onAccent(){return Color.luminance(accent)>.45?0xff000000:0xffffffff;}
    private void attachSheet(){
        if(sheet==null){sheet=new SettingsSheet(this);}
        if(sheet.getParent()!=null)((android.view.ViewGroup)sheet.getParent()).removeView(sheet);
        scene.addView(sheet,new FrameLayout.LayoutParams(-1,-1));
        sheet.setColors(surface(),MetroArt.alpha(muted,.22f),MetroArt.alpha(muted,.45f));
        sheet.requestApplyInsets();
        if(sheet.isShowing())sheet.refresh();
    }
    private void openSettings(){if(searching){searching=false;query="";hideKeyboard();buildUi();}sheet.open(this::quickPage);}
    private final class SwipeUpFooter extends LinearLayout {
        float downX,downY;boolean opened;
        SwipeUpFooter(){super(MainActivity.this);setOrientation(VERTICAL);}
        @Override public boolean onInterceptTouchEvent(MotionEvent e){
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();opened=false;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!opened&&downY-e.getY()>dp(36)&&downY-e.getY()>Math.abs(e.getX()-downX)*1.5f){opened=true;openSettings();return true;}
            return false;
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();opened=false;return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!opened&&downY-e.getY()>dp(36)){opened=true;openSettings();}
            if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
    private TextView sectionLabel(String label){TextView t=text(label.toUpperCase(Locale.ROOT),11,accent);t.setLetterSpacing(.16f);t.setPadding(dp(26),dp(28),dp(26),dp(6));t.setAccessibilityHeading(true);return t;}
    private LinearLayout settingRow(String title,String summary,View.OnClickListener click){
        LinearLayout row=row();row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(60));row.setPadding(dp(26),dp(10),dp(18),dp(10));
        LinearLayout words=column();words.addView(text(title,19,fg));
        if(summary!=null&&!summary.isEmpty()){TextView s=text(summary,13,muted);s.setPadding(0,dp(4),0,0);s.setMaxLines(2);s.setEllipsize(TextUtils.TruncateAt.END);words.addView(s);}
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        MetroArt.Icon arrow=new MetroArt.Icon(this,uiIcons,"arrow",MetroArt.alpha(muted,.6f),false);arrow.setFocusable(false);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow,new LinearLayout.LayoutParams(dp(28),dp(32)));
        row.setFocusable(true);pressFeedback(row);row.setOnClickListener(click);row.setContentDescription(title+(summary==null?"":". "+summary));
        return row;
    }
    private GradientDrawable shape(int fill,int border){GradientDrawable d=outline(fill,theme.expressive?Color.TRANSPARENT:border);d.setCornerRadius(theme.expressive?dp(20):theme.layout.equals("xmb")?dp(14):dp(2));return d;}
    private TextView chip(String label,boolean on,View.OnClickListener click){
        int fill=theme.expressive?secondaryContainer:primaryContainer,ink=theme.expressive?onSecondaryContainer:onPrimaryContainer;
        TextView t=text(label,14,on?ink:fg);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(48));t.setMinWidth(dp(48));t.setPadding(dp(14),dp(8),dp(14),dp(8));
        t.setBackground(shape(on?fill:theme.expressive?MetroArt.alpha(fg,.06f):Color.TRANSPARENT,on?fill:MetroArt.alpha(muted,.4f)));t.setOnClickListener(click);t.setFocusable(true);
        t.setContentDescription(label+(on?", selected":""));return t;
    }
    private View chipRow(View... chips){
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setPadding(dp(26),dp(4),dp(26),dp(4));
        LinearLayout line=row();for(View c:chips){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(0,0,dp(8),0);line.addView(c,p);}
        scroll.addView(line);return scroll;
    }
    /** Metro tile: filled when on, outlined when off. */
    private View tile(String label,String state,boolean on,View.OnClickListener click){
        LinearLayout t=column();t.setPadding(dp(10),dp(10),dp(10),dp(10));t.setBackground(shape(on?primaryContainer:MetroArt.alpha(fg,theme.expressive?.07f:.04f),on?primaryContainer:MetroArt.alpha(muted,.3f)));
        if(theme.expressive)t.setPadding(dp(14),dp(12),dp(12),dp(12));
        TextView name=text(label,14,on?onPrimaryContainer:fg);name.setMaxLines(2);t.addView(name,new LinearLayout.LayoutParams(-1,0,1));
        if(theme.expressive)name.setTypeface(Typeface.create(theme.typeface,on?650:500,false));
        fitWords(name);
        TextView value=text(state,11,on?MetroArt.alpha(onPrimaryContainer,.8f):muted);value.setLetterSpacing(.06f);value.setSingleLine();t.addView(value);
        t.setFocusable(true);t.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);click.onClick(v);});t.setContentDescription(label+", "+state);
        return t;
    }
    /** Shrinks text until its longest word fits on one line, so narrow tiles never break inside a word. */
    private void fitWords(TextView t){
        t.addOnLayoutChangeListener((v,l,top,r,b,ol,ot,or,ob)->{
            int width=r-l-t.getPaddingLeft()-t.getPaddingRight();if(width<=0||r-l==or-ol)return;
            float longest=0;for(String word:t.getText().toString().split(" "))longest=Math.max(longest,t.getPaint().measureText(word));
            if(longest>width&&t.getTextSize()>dp(10)){t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,Math.max(dp(10),t.getTextSize()*width/longest*.92f));}
        });
    }
    private LinearLayout tileGrid(View... tiles){
        LinearLayout grid=column();grid.setPadding(dp(22),dp(6),dp(22),dp(6));
        for(int i=0;i<tiles.length;i+=4){
            LinearLayout line=row();
            for(int j=i;j<i+4;j++){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(78),1);p.setMargins(dp(4),dp(4),dp(4),dp(4));if(j<tiles.length)line.addView(tiles[j],p);else line.addView(new View(this),p);}
            grid.addView(line);
        }
        return grid;
    }
    private static final String[] PRESETS={"retro · cyan","retro · cobalt","retro · magenta","paper · light","Wave","Liquid Retro","Expressive"};
    private void applyPreset(int i){
        try{SharedPreferences.Editor edit=prefs.edit().remove("themeDir").remove("icons");if(PRESETS[i].equals("Wave"))edit.putString("wallpaperMode","solid");edit.apply();
            // Expressive and wallpaper colors are styles, like glass: they carry over when you switch palettes.
            JSONObject next=ThemeEngine.preset(PRESETS[i]);if(!next.has("expressive")){next.put("expressive",theme.expressive);next.put("dynamic",theme.dynamic);}
            applyTheme(next);persistTheme();buildUi();loadApps();}catch(Exception e){fileError(e);}
    }
    private View swatches(){
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setPadding(dp(22),dp(4),dp(22),dp(4));scroll.setClipToPadding(false);
        LinearLayout line=row();
        for(int i=0;i<PRESETS.length;i++){
            final int index=i;JSONObject preset;try{preset=ThemeEngine.preset(PRESETS[i]);}catch(JSONException e){continue;}
            int pBg=Color.parseColor(preset.optString("background","#000000")),pAccent=Color.parseColor(preset.optString("accent","#39B9D4"));
            boolean selected=theme.name.equals(preset.optString("name"));
            LinearLayout item=column();item.setGravity(Gravity.CENTER_HORIZONTAL);item.setPadding(dp(4),dp(4),dp(4),dp(4));
            View swatch=new View(this);GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{pBg,pBg,pAccent});
            d.setCornerRadius(theme.layout.equals("xmb")?dp(26):dp(2));d.setStroke(dp(selected?3:1),selected?accent:MetroArt.alpha(muted,.35f));swatch.setBackground(d);
            item.addView(swatch,new LinearLayout.LayoutParams(dp(52),dp(52)));
            TextView name=text(PRESETS[i].replace("retro · ",""),11,selected?fg:muted);name.setPadding(0,dp(6),0,0);item.addView(name);
            item.setFocusable(true);item.setContentDescription(PRESETS[i]+" theme"+(selected?", active":""));item.setOnClickListener(v->applyPreset(index));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(72),-2);line.addView(item,p);
        }
        scroll.addView(line);return scroll;
    }
    private View quickPage(){
        LinearLayout page=column();
        // Active context and why.
        LinearLayout ctx=row();ctx.setGravity(Gravity.CENTER_VERTICAL);ctx.setPadding(dp(26),dp(6),dp(18),dp(8));
        LinearLayout words=column();
        TextView over=text("CONTEXT",10,accent);over.setLetterSpacing(.16f);words.addView(over);
        String name=activeContext==null?"everyday":activeContext.name;
        TextView title=text(name,30,fg);title.setPadding(0,dp(4),0,dp(2));words.addView(title);
        String pinned=pinSummary();
        words.addView(text(pinned!=null?pinned:activeContext==null||activeContext.fallback?"used when no other context matches":"because "+ContextRules.describe(activeContext),13,muted));
        ctx.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        ctx.addView(chip(pinned!=null?"held":"auto",pinned!=null,v->contextOverride()));
        ctx.setOnClickListener(v->{if(activeContext!=null)sheet.push(()->contextPage(activeContext));});pressFeedback(ctx);
        page.addView(ctx);
        page.addView(swatches());
        String icons=prefs.getString("icons",theme.iconStyle);
        int effective=activeContext!=null&&activeContext.header>=0?activeContext.header:prefs.getInt("header",20);
        boolean media=activeContext!=null&&activeContext.widgets.contains("media");
        boolean isDefault=Build.VERSION.SDK_INT>=29&&getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_HOME);
        page.addView(tileGrid(
            tile("categories",prefs.getBoolean("categories",true)?"ON":"OFF",prefs.getBoolean("categories",true),v->{prefs.edit().putBoolean("categories",!prefs.getBoolean("categories",true)).apply();refreshCategories();refreshList(true);sheet.refresh();}),
            tile("app icons",icons.equals("mono")?"MONO":icons.equals("color")?"COLOR":"TEXT",!icons.equals("none"),v->{prefs.edit().putString("icons",icons.equals("mono")?"color":icons.equals("color")?"none":"mono").apply();buildUi();}),
            tile("scrubber",prefs.getBoolean("left",false)?"LEFT":"RIGHT",true,v->{prefs.edit().putBoolean("left",!prefs.getBoolean("left",false)).apply();buildUi();}),
            tile("media here",media?"ON":"OFF",media,v->{if(activeContext==null)return;if(media)removeContextItem(activeContext,"media");else{activeContext.widgets.add(0<activeContext.widgets.size()?1:0,"media");if(!mediaAccess())message("media controls","Enable 72F media controls in notification access so the top zone can show what's playing.");}contextsChanged();}),
            tile("blur",theme.blur==0?"OFF":theme.blur<=8?"SOFT":theme.blur<=18?"FROSTED":"DEEP",theme.blur>0,v->changeEffect("blur",theme.blur==0?8:theme.blur<=8?18:theme.blur<=18?32:0)),
            tile("glass",theme.glass==0?"OFF":theme.liquid?"LIQUID":theme.glass<=20?"SUBTLE":"FROSTED",theme.glass>0,v->{int next=theme.glass==0?20:theme.liquid?0:theme.glass<=20?40:65;try{JSONObject t=themeJson();t.put("glass",next);t.put("liquid",next==65);applyTheme(t);persistTheme();buildUi();}catch(Exception e){fileError(e);}}),
            tile("top zone",effective==0?"OFF":effective+"%",effective>0,v->{int next=(effective+10)%50;if(activeContext!=null&&activeContext.header>=0){activeContext.header=next;contextsChanged();}else{prefs.edit().putInt("header",next).apply();appliedLayout="";buildUi();}}),
            tile("default home",isDefault?"YES":"SET",isDefault,v->defaultHome()),
            tile("expressive",theme.expressive?"ON":"OFF",theme.expressive,v->changeEffect("expressive",!theme.expressive)),
            tile("wallpaper colors",Build.VERSION.SDK_INT<31?"12+":theme.dynamic?"ON":"OFF",theme.dynamic,v->{if(Build.VERSION.SDK_INT<31){toast("Wallpaper colors need Android 12 or newer");return;}changeEffect("dynamic",!theme.dynamic);}),
            tile("waves",theme.animated?"ON":"OFF",theme.animated,v->changeEffect("animated",!theme.animated)),
            tile("title size",titleSize<=40?"S":titleSize<=62?"M":"L",true,v->changeEffect("titleSize",titleSize<=40?60:titleSize<=62?72:32))));
        // Wallpaper dimming.
        LinearLayout dim=row();dim.setGravity(Gravity.CENTER_VERTICAL);dim.setPadding(dp(26),dp(4),dp(18),dp(4));
        TextView dimLabel=text("dim "+prefs.getInt("wallpaperDim",60)+"%",14,muted);dim.addView(dimLabel,new LinearLayout.LayoutParams(dp(72),-2));
        SeekBar dimmer=new SeekBar(this);dimmer.setContentDescription("Wallpaper dimming");dimmer.setMax(90);dimmer.setProgress(prefs.getInt("wallpaperDim",60));
        dimmer.setProgressTintList(ColorStateList.valueOf(accent));dimmer.setThumbTintList(ColorStateList.valueOf(accent));
        dimmer.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int amount,boolean user){if(!user)return;prefs.edit().putInt("wallpaperDim",amount).apply();dimLabel.setText("dim "+amount+"%");updateBackdrop();}
            public void onStartTrackingTouch(SeekBar bar){bar.getParent().requestDisallowInterceptTouchEvent(true);}public void onStopTrackingTouch(SeekBar bar){}
        });
        dim.addView(dimmer,new LinearLayout.LayoutParams(0,dp(48),1));page.addView(dim);
        // Anchor: everything below is revealed by swiping the sheet up.
        LinearLayout more=row();more.setGravity(Gravity.CENTER);more.setPadding(0,dp(8),0,dp(8));
        MetroArt.Icon up=new MetroArt.Icon(this,uiIcons,"chevron",muted,false);up.setRotation(-90);up.setFocusable(false);up.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        more.addView(up,new LinearLayout.LayoutParams(dp(24),dp(24)));
        TextView moreLabel=text("swipe up for all settings",13,muted);moreLabel.setPadding(dp(6),0,0,0);more.addView(moreLabel);
        more.setFocusable(true);more.setContentDescription("All settings");more.setOnClickListener(v->sheet.expand());pressFeedback(more);
        page.addView(more,new LinearLayout.LayoutParams(-1,dp(48)));
        sheet.setPeekAnchor(more);
        page.addView(fullSettings());
        return page;
    }
    private View fullSettings(){
        LinearLayout page=column();
        page.addView(sectionLabel("contexts"));
        TextView intro=text("The top zone and starting category follow the first context that matches. Checked only while Home is on screen.",13,muted);intro.setPadding(dp(26),0,dp(26),dp(6));page.addView(intro);
        for(ContextRules.Rule r:contexts){
            String summary=(r.enabled||r.fallback?ContextRules.describe(r):"off")+" → "+(r.widgets.isEmpty()?"no top zone":r.widgets.size()+" item"+(r.widgets.size()==1?"":"s"))+(r.category.isEmpty()?"":", opens "+r.category);
            LinearLayout row=settingRow((activeContext!=null&&r.id.equals(activeContext.id)?"● ":"")+r.name,summary,v->sheet.push(()->contextPage(r)));
            if(!r.enabled&&!r.fallback)row.setAlpha(.55f);
            page.addView(row);
        }
        page.addView(settingRow("add context","morning, work, headphones, night, charging or blank",v->{
            if(contexts.size()>=ContextStore.MAX_RULES){toast("Up to "+ContextStore.MAX_RULES+" contexts");return;}
            String[] kinds={"morning","work","headphones","night","charging","blank"};
            menu("add context",kinds,i->{ContextRules.Rule r=ContextRules.template(kinds[i].equals("blank")?"new context":kinds[i],ContextStore.newId(contexts));if(!r.category.isEmpty()&&!editableCategories().contains(r.category))r.category="";contexts.add(Math.max(0,contexts.size()-1),r);contextsChanged();sheet.push(()->contextPage(r));});
        }));
        page.addView(settingRow("override","hold a context for a while, or return to automatic",v->contextOverride()));

        page.addView(sectionLabel("home & categories"));
        page.addView(settingRow("set as default launcher",null,v->defaultHome()));
        page.addView(settingRow("categories & folders",(prefs.getBoolean("categories",true)?editableCategories().size()-disabled.size()+" visible":"off")+" · create, hide, merge, rename",v->categorySettings()));
        page.addView(settingRow("scrubber side",prefs.getBoolean("left",false)?"left":"right",v->{prefs.edit().putBoolean("left",!prefs.getBoolean("left",false)).apply();buildUi();}));

        page.addView(sectionLabel("appearance"));
        page.addView(settingRow("themes",theme.name+" · presets, import, export",v->themeSettings()));
        page.addView(settingRow("title size",String.valueOf(titleSize),v->menu("title size",new String[]{"compact · 32","retro · 60","oversized · 72"},n->{try{JSONObject t=themeJson();t.put("titleSize",new int[]{32,60,72}[n]);applyTheme(t);persistTheme();buildUi();}catch(Exception e){fileError(e);}})));
        String icons=prefs.getString("icons",theme.iconStyle);
        page.addView(settingRow("app icons",(icons.equals("mono")?"monochrome":icons.equals("color")?"original color":"text only")+(prefs.getString("iconPack","").isEmpty()?"":" · icon pack"),v->menu("app icons",new String[]{"monochrome","original color","text only","icon packs"},n->{if(n==3){chooseIconPack();return;}prefs.edit().putString("icons",new String[]{"mono","color","none"}[n]).apply();buildUi();})));
        page.addView(settingRow("effects","blur "+theme.blur+" · glass "+theme.glass+" · waves "+(theme.animated?"on":"off")+(theme.expressive?" · expressive":"")+(theme.dynamic?" · wallpaper colors":""),v->effectsSettings()));
        page.addView(settingRow("launcher icon set",theme.uiIconSet,v->menu("launcher icon set",new String[]{"Retro","Wave","Material"},n->{try{JSONObject t=themeJson();t.put("uiIconSet",new String[]{"retro","wave","material"}[n]);t.remove("uiIcons");applyTheme(t);persistTheme();buildUi();}catch(Exception e){fileError(e);}})));

        page.addView(sectionLabel("top zone"));
        page.addView(settingRow("default height",prefs.getInt("header",20)==0?"off":prefs.getInt("header",20)+"% of the screen · contexts can override",v->menu("top zone height",new String[]{"none","10%","20%","30%","40%"},n->{prefs.edit().putInt("header",n*10).apply();appliedLayout="";buildUi();})));
        page.addView(settingRow("media access",mediaAccess()?"allowed":"needed for media controls and the media-playing condition",v->safeStart(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        page.addView(settingRow("calendar access",calendarAllowed()?"allowed":"needed for the agenda item",v->headerSettings()));

        page.addView(sectionLabel("wallpaper"));
        String mode=prefs.getString("wallpaperMode","solid");
        page.addView(settingRow("wallpaper",(mode.equals("photo")?"photo":mode.equals("system")?"phone wallpaper":"solid")+" · "+prefs.getInt("wallpaperDim",60)+"% dim",v->wallpaperSettings()));

        page.addView(sectionLabel("backup & about"));
        page.addView(settingRow("backup & restore","favorites, categories, contexts, theme and photo",v->backupSettings()));
        page.addView(settingRow("about 72F",version()+" · open-source notices",v->aboutSettings()));
        return page;
    }
    private static final String[] TRI={"any","yes","no"};
    private View contextPage(ContextRules.Rule r){
        LinearLayout page=column();
        LinearLayout head=row();head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(14),dp(4),dp(18),0);
        head.addView(iconButton("back","Back",false,v->sheet.back()));
        TextView title=text(r.name,32,fg);title.setPadding(dp(6),0,0,0);title.setMinHeight(dp(48));title.setGravity(Gravity.CENTER_VERTICAL);
        if(!r.fallback){title.setOnClickListener(v->renameContext(r));title.setContentDescription(r.name+". Tap to rename");}
        head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        if(!r.fallback)head.addView(chip(r.enabled?"on":"off",r.enabled,v->{r.enabled=!r.enabled;contextsChanged();}));
        page.addView(head);
        TextView why=text(r.fallback?"Used when no other context matches.":ContextRules.describe(r),13,muted);why.setPadding(dp(26),dp(4),dp(26),0);page.addView(why);

        if(!r.fallback){
            page.addView(sectionLabel("when · days"));
            View[] days=new View[8];
            days[0]=chip("every day",r.days==0,v->{r.days=0;contextsChanged();});
            for(int d=0;d<7;d++){final int bit=1<<d;days[d+1]=chip(ContextRules.DAYS[d],r.days!=0&&(r.days&bit)!=0,v->{r.days^=bit;if(r.days==127)r.days=0;contextsChanged();});}
            page.addView(chipRow(days));
            page.addView(sectionLabel("when · time"));
            page.addView(chipRow(
                chip("all day",r.allDay(),v->{r.start=0;r.end=0;contextsChanged();}),
                chip(r.allDay()?"from …":"from "+ContextRules.time(r.start),!r.allDay(),v->pickTime(r,true)),
                chip(r.allDay()?"until …":"until "+ContextRules.time(r.end),!r.allDay(),v->pickTime(r,false))));
            page.addView(sectionLabel("when · conditions"));
            page.addView(conditionRow("headphones",r.headphones,n->{r.headphones=n;contextsChanged();}));
            page.addView(conditionRow("charging",r.charging,n->{r.charging=n;contextsChanged();}));
            page.addView(conditionRow("do not disturb",r.dnd,n->{r.dnd=n;contextsChanged();}));
            page.addView(conditionRow("media playing",r.playing,n->{r.playing=n;if(n!=0&&!mediaAccess())message("media playing","Enable 72F media controls in notification access so this condition can see playback.");contextsChanged();}));
        }

        page.addView(sectionLabel("then · open on"));
        ArrayList<String> targets=new ArrayList<>();targets.add("");targets.addAll(editableCategories());targets.add("all apps");
        View[] cats=new View[targets.size()];
        for(int i=0;i<cats.length;i++){String c=targets.get(i);cats[i]=chip(c.isEmpty()?"favorites":c,r.category.equals(c),v->{r.category=c;contextsChanged();});}
        page.addView(chipRow(cats));

        page.addView(sectionLabel("then · top zone height"));
        int[] heights={-1,0,10,20,30,40};View[] hs=new View[heights.length];
        for(int i=0;i<heights.length;i++){int h=heights[i];hs[i]=chip(h<0?"default":h==0?"off":h+"%",r.header==h,v->{r.header=h;contextsChanged();});}
        page.addView(chipRow(hs));

        page.addView(sectionLabel("then · top zone items"));
        for(int i=0;i<r.widgets.size();i++){
            final int index=i;String item=r.widgets.get(i);
            LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(dp(26),dp(2),dp(12),dp(2));line.setMinimumHeight(dp(52));
            TextView label=text(itemLabel(item),18,fg);line.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            if(index>0){TextView upButton=chip("↑",false,v->{Collections.swap(r.widgets,index,index-1);contextsChanged();});upButton.setContentDescription("Move "+itemLabel(item)+" up");LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(0,0,dp(6),0);line.addView(upButton,p);}
            TextView remove=chip("remove",false,v->{removeContextItem(r,item);contextsChanged();});remove.setContentDescription("Remove "+itemLabel(item));line.addView(remove);
            page.addView(line);
        }
        ArrayList<String> addable=new ArrayList<>();for(String w:ContextRules.WIDGETS)if(!r.widgets.contains(w)&&(Build.VERSION.SDK_INT>=29||!w.equals("smartspace")))addable.add(w);
        page.addView(settingRow("add item",r.widgets.size()>=ContextStore.MAX_WIDGETS?"this context is full":"clock, alarm, agenda, media, Smartspacer or any app widget",v->{
            if(r.widgets.size()>=ContextStore.MAX_WIDGETS){toast("This context is full");return;}
            ArrayList<String> labels=new ArrayList<>();for(String w:addable)labels.add(itemLabel(w));labels.add("app widget…");
            menu("add to top zone",labels.toArray(new String[0]),n->{
                if(n==addable.size()){addAppWidget(r);return;}
                String w=addable.get(n);r.widgets.add(w);
                if(w.equals("agenda")&&!calendarAllowed())requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR},49);
                if(w.equals("media")&&!mediaAccess())message("media controls","Enable 72F media controls in notification access so the top zone can show what's playing.");
                contextsChanged();
            });
        }));

        if(!r.fallback){
            page.addView(sectionLabel("order"));
            int position=contexts.indexOf(r);
            page.addView(settingRow("priority",(position+1)+" of "+(contexts.size()-1)+" · the first match wins",v->menu("priority",new String[]{"move up","move down"},n->{int p=contexts.indexOf(r),to=p+(n==0?-1:1);if(to>=0&&to<contexts.size()-1){Collections.swap(contexts,p,to);contextsChanged();}})));
            page.addView(settingRow("hold this context","use it now, regardless of conditions",v->{prefs.edit().putString("contextPin",r.id).putLong("contextPinUntil",System.currentTimeMillis()+3600000L).apply();contextsChanged();toast(r.name+" held for 1 hour");}));
            page.addView(settingRow("delete context",null,v->track(new AlertDialog.Builder(this).setTitle("Delete "+r.name+"?").setNegativeButton("cancel",null).setPositiveButton("delete",(a,b)->{
                for(String item:new ArrayList<>(r.widgets))if(item.startsWith("widget:")){r.widgets.remove(item);removeContextItem(r,item);}
                contexts.remove(r);if(r.id.equals(prefs.getString("contextPin","")))prefs.edit().remove("contextPin").remove("contextPinUntil").apply();sheet.back();contextsChanged();}).show())));
        }
        return page;
    }
    private interface IntChoice{void accept(int n);}
    private View conditionRow(String label,int value,IntChoice change){
        LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(dp(26),dp(2),dp(20),dp(2));line.setMinimumHeight(dp(50));
        line.addView(text(label,17,fg),new LinearLayout.LayoutParams(0,-2,1));
        for(int i=0;i<3;i++){final int n=i;TextView c=chip(TRI[i],value==i,v->change.accept(n));c.setContentDescription(label+": "+TRI[i]+(value==i?", selected":""));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(dp(6),0,0,0);line.addView(c,p);}
        return line;
    }
    private void pickTime(ContextRules.Rule r,boolean start){
        int current=start?(r.allDay()?7*60:r.start):(r.allDay()?9*60:r.end);
        TimePickerDialog picker=new TimePickerDialog(this,(view,h,m)->{
            int minute=h*60+m;
            if(start){if(r.allDay())r.end=(minute+120)%1440;r.start=minute;}else{if(r.allDay())r.start=(minute+1320)%1440;r.end=minute;}
            if(r.start==r.end)r.end=(r.end+60)%1440;
            contextsChanged();
        },current/60,current%60,android.text.format.DateFormat.is24HourFormat(this));
        track(picker);picker.show();
    }
    private void renameContext(ContextRules.Rule r){
        EditText input=new EditText(this);input.setSingleLine();input.setText(r.name);input.setSelectAllOnFocus(true);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        AlertDialog d=new AlertDialog.Builder(this).setTitle("rename context").setView(input).setNegativeButton("cancel",null).setPositiveButton("save",null).create();showDialog(d);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String name=input.getText().toString().trim().toLowerCase(Locale.ROOT);if(name.isEmpty()){input.setError("Enter a name");return;}r.name=name;d.dismiss();contextsChanged();});
    }

    private void defaultHome(){try{if(Build.VERSION.SDK_INT>=29){RoleManager rm=getSystemService(RoleManager.class);if(rm.isRoleAvailable(RoleManager.ROLE_HOME)&&!rm.isRoleHeld(RoleManager.ROLE_HOME)){startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME),43);return;}}safeStart(new Intent(Settings.ACTION_HOME_SETTINGS));}catch(Exception e){safeStart(new Intent(Settings.ACTION_HOME_SETTINGS));}}
    private void welcome(){
        if(isFinishing()||isDestroyed())return;
        prefs.edit().putBoolean("introduced",true).apply();
        showDialog(new AlertDialog.Builder(this).setTitle("welcome to 72F")
            .setMessage("Swipe sideways to explore categories. Scroll to find your apps.\n\nHold an app to organize it. The bar at the bottom moves between categories; the side alphabet gets you there faster.\n\nSearch is the round button. Hold it, or swipe up on the bar, for settings.\n\nFavorites is always home.")
            .setPositiveButton("let’s go",(d,w)->{}).setNeutralButton("set as default",(d,w)->defaultHome()).create());
    }
    private interface Choice{void accept(int i);}
    private void menu(String title,String[] options,Choice action){
        if(options.length==0){toast("No categories available");return;}
        Dialog dialog=new Dialog(this);
        LinearLayout sheet=column();sheet.setPadding(dp(26),dp(22),dp(26),dp(24));
        sheet.setBackground(outline(Color.luminance(bg)>.5?bg:0xff101114,MetroArt.alpha(muted,.18f)));
        TextView heading=text(title,38,fg);heading.setPadding(0,dp(8),0,dp(18));sheet.addView(heading);
        ScrollView scroller=new ScrollView(this);scroller.setVerticalScrollBarEnabled(false);
        LinearLayout choices=column();scroller.addView(choices);
        for(int i=0;i<options.length;i++){
            final int index=i;
            LinearLayout option=row();option.setGravity(Gravity.CENTER_VERTICAL);option.setMinimumHeight(dp(54));option.setPadding(0,dp(10),0,dp(10));
            LinearLayout words=column();TextView label=text(options[i],20,fg);words.addView(label);
            option.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            MetroArt.Icon arrow=new MetroArt.Icon(this,uiIcons,"arrow",MetroArt.alpha(muted,.7f),false);arrow.setFocusable(false);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            option.addView(arrow,new LinearLayout.LayoutParams(dp(32),dp(36)));
            option.setFocusable(true);pressFeedback(option);option.setOnClickListener(v->{dialog.dismiss();action.accept(index);});choices.addView(option);
        }
        int maxHeight=(int)(getResources().getDisplayMetrics().heightPixels*.60f);
        choices.measure(View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().widthPixels-dp(52),View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        sheet.addView(scroller,new LinearLayout.LayoutParams(-1,Math.min(maxHeight,choices.getMeasuredHeight())));
        TextView close=text("close",14,accent);close.setGravity(Gravity.CENTER_VERTICAL);close.setMinHeight(dp(48));close.setPadding(0,dp(12),0,0);close.setOnClickListener(v->dialog.dismiss());close.setFocusable(true);sheet.addView(close);
        track(dialog);dialog.setContentView(sheet);Window window=dialog.getWindow();
        if(window!=null){window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.5f);window.setGravity(Gravity.BOTTOM);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
        dialog.show();if(window!=null)window.setLayout(-1,-2);
    }
    /** Open dialogs, closed when Home is pressed so Home always lands on a clean home screen. */
    private final ArrayList<Dialog> openDialogs=new ArrayList<>();
    private <T extends Dialog> T track(T d){openDialogs.add(d);d.setOnDismissListener(x->openDialogs.remove(d));return d;}
    private void dismissDialogs(){for(Dialog d:new ArrayList<>(openDialogs))if(d.isShowing())d.dismiss();openDialogs.clear();}
    private void showDialog(AlertDialog d){
        track(d);d.show();
        Window window=d.getWindow();
        if(window!=null){window.setBackgroundDrawable(outline(Color.luminance(bg)>.5?bg:0xff101114,MetroArt.alpha(muted,.2f)));window.setGravity(Gravity.BOTTOM);window.setLayout(-1,-2);tintDialog(window.getDecorView());}
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){android.widget.Button b=d.getButton(which);if(b!=null)b.setTextColor(accent);}
    }
    private void tintDialog(View view){
        if(view instanceof TextView){TextView t=(TextView)view;t.setTextColor(fg);t.setTypeface(theme.typeface);if(t instanceof EditText)((EditText)t).setBackgroundTintList(ColorStateList.valueOf(accent));}
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)tintDialog(group.getChildAt(i));}
    }
    private GradientDrawable outline(int fill,int border){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setStroke(dp(1),border);return d;}
    private void pressFeedback(View view){view.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(MetroArt.alpha(accent,.13f)),null,new android.graphics.drawable.ColorDrawable(Color.WHITE)));}
    private MetroArt.Icon iconButton(String mark,String description,boolean ring,View.OnClickListener click){
        MetroArt.Icon icon=new MetroArt.Icon(this,uiIcons,mark,fg,ring);icon.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));icon.setContentDescription(description);icon.setOnClickListener(click);pressFeedback(icon);return icon;
    }
    private void message(String title,String body){showDialog(new AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("okay",null).create());}
    private void safeStart(Intent intent){try{startActivity(intent);}catch(Exception e){toast("No app available for this action");}}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setTypeface(theme.typeface);t.setIncludeFontPadding(false);return t;}
    private TextView button(String label,String description,View.OnClickListener click){TextView t=text(label,25,fg);t.setGravity(Gravity.CENTER);t.setMinWidth(dp(56));t.setMinHeight(dp(48));t.setPadding(dp(12),dp(6),dp(12),dp(6));t.setContentDescription(description);t.setOnClickListener(click);android.util.TypedValue out=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,out,true);t.setBackgroundResource(out.resourceId);return t;}
}
