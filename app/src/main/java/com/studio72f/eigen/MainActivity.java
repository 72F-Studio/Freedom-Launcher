package com.studio72f.eigen;

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

public final class MainActivity extends androidx.activity.ComponentActivity {
    private static final String[] BUILT_INS = {"social", "music", "video", "photos", "games", "travel", "tools"};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LauncherApps launcher;
    private final ArrayList<App> apps = new ArrayList<>(), visible = new ArrayList<>();
    private final ArrayList<String> categories = new ArrayList<>(), favorites = new ArrayList<>(), custom = new ArrayList<>();
    private final HashSet<String> disabled = new HashSet<>(), hidden = new HashSet<>();
    private final HashMap<String,String> labels = new HashMap<>();
    private final ArrayList<String> order = new ArrayList<>();
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
    /** Horizontal position of the app list while paging between categories (follows the finger, then springs). */
    private Motion.Spring pageSpring;
    private boolean returningFromApp;
    private float swapAt; private Runnable pendingSwap;
    // Text legibility over images: photo luminance percentiles (measured off the UI thread) and the resolved level.
    private volatile double photoDark=.05, photoBright=.5;
    private int legibility;
    // Material 3 tonal roles. Expressive themes take them from the wallpaper palette (Android 12+).
    private int primaryContainer, onPrimaryContainer, secondaryContainer, onSecondaryContainer, surfaceContainer;
    private AlphabetRail alphabet;
    private AppAdapter adapter;
    private MediaController controller;
    private SettingsSheet sheet;
    private final ArrayList<ContextRules.Rule> contexts = new ArrayList<>();
    private ContextRules.Rule activeContext;
    private String appliedLayout = "";
    // The Compose UI (home, side pages, search, assistant) reads this state; the activity keeps apps, widgets,
    // settings and dialogs. Created once, so the UI keeps its place across rebuilds.
    private final com.studio72f.eigen.ui.EigenState eigen = new com.studio72f.eigen.ui.EigenState();
    /** The Kelvin contexts editor edits rules in place and reports back here, where they are stored and applied. */
    private final ContextsHost contextsHost = new ContextsHost() {
        @Override public List<ContextRules.Rule> contexts() { return contexts; }
        @Override public String activeId() { return activeContext == null ? null : activeContext.id; }
        @Override public void changed() { contextsChanged(); }
        @Override public void addContext() {
            if (contexts.size() >= ContextStore.MAX_RULES) { toast(str(R.string.up_to_x_contexts, ContextStore.MAX_RULES)); return; }
            String[] kinds = {"morning", "work", "headphones", "night", "charging", "blank"};
            menu("Add a context", new String[]{"Morning", "Work", "Headphones", "Night", "Charging", "Blank"}, i -> {
                ContextRules.Rule r = ContextRules.template(kinds[i].equals("blank") ? "new context" : kinds[i], ContextStore.newId(contexts));
                if (!r.category.isEmpty() && !editableCategories().contains(r.category)) r.category = "";
                contexts.add(Math.max(0, contexts.size() - 1), r); contextsChanged(); eigen.setContextsPage(r.id);
            });
        }
        @Override public void holdMenu() { contextOverride(); }
        @Override public void hold(ContextRules.Rule r) { prefs.edit().putString("contextPin", r.id).putLong("contextPinUntil", System.currentTimeMillis() + 3600000L).apply(); contextsChanged(); toast(str(R.string.x_held_for_1_hour, r.name)); }
        @Override public void delete(ContextRules.Rule r) {
            for (String item : new ArrayList<>(r.widgets)) if (item.startsWith("widget:")) removeContextItem(r, item);
            contexts.remove(r); if (r.id.equals(prefs.getString("contextPin", ""))) prefs.edit().remove("contextPin").remove("contextPinUntil").apply();
            contextsChanged();
        }
        @Override public void pickTime(ContextRules.Rule r, boolean start) { MainActivity.this.pickTime(r, start); }
        @Override public void addItem(ContextRules.Rule r) {
            if (r.widgets.size() >= ContextStore.MAX_WIDGETS) { toast(str(R.string.this_context_is_full)); return; }
            ArrayList<String> addable = new ArrayList<>();
            for (String w : new String[]{"alarm", "agenda", "smartspace"}) if (!r.widgets.contains(w) && (Build.VERSION.SDK_INT >= 29 || !w.equals("smartspace"))) addable.add(w);
            ArrayList<String> labels = new ArrayList<>(); for (String w : addable) labels.add(itemLabel(w)); labels.add("App widget");
            menu("Add to the widgets page", labels.toArray(new String[0]), n -> {
                if (n == addable.size()) { addAppWidget(r); return; }
                String w = addable.get(n); r.widgets.add(w);
                if (w.equals("agenda") && !calendarAllowed()) requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, 49);
                contextsChanged();
            });
        }
        @Override public void removeItem(ContextRules.Rule r, String item) { removeContextItem(r, item); }
        @Override public String itemLabel(String item) { return MainActivity.this.itemLabel(item); }
        @Override public List<String> drawers() { return editableCategories(); }
    };
    private final com.studio72f.eigen.ui.EigenHost host = new com.studio72f.eigen.ui.EigenHost() {
        @Override public void launch(App app) { MainActivity.this.launch(app, null); eigen.setSearching(false); eigen.setQuery(""); }
        @Override public void launchFrom(App app, int left, int top, int width, int height) {
            Rect bounds = new Rect(left, top, left + width, top + height); Bundle options = null;
            if (Motion.on() && scene != null) {
                ActivityOptions o = ActivityOptions.makeClipRevealAnimation(scene, left, top, width, height);
                if (Build.VERSION.SDK_INT >= 33) o.setSplashScreenStyle(android.window.SplashScreen.SPLASH_SCREEN_STYLE_ICON);
                options = o.toBundle();
            }
            try { launcher.startMainActivity(app.component, app.user, bounds, options); returningFromApp = true; }
            catch (Exception e) { toast(str(R.string.app_unavailable_its_profile_may_be_pause)); loadApps(); }
            eigen.setSearching(false); eigen.setQuery("");
        }
        @Override public void appMenu(App app) { MainActivity.this.appMenu(app); }
        @Override public void homeMenu() { homescreenMenu(); }
        @Override public void openSettings() { MainActivity.this.openSettings(); }
        @Override public void addWidget() {
            ArrayList<String> ids = new ArrayList<>(), names = new ArrayList<>();
            String[] all = com.studio72f.eigen.ui.EigenWidgets.IDS, labels = com.studio72f.eigen.ui.EigenWidgets.NAMES;
            for (int i = 0; i < all.length; i++) if (!eigen.getWidgetOrder().contains(all[i])) { ids.add(all[i]); names.add(labels[i] + " · Eigen"); }
            names.add("App widget · from your apps");
            menu("Add a widget", names.toArray(new String[0]), n -> {
                if (n == ids.size()) { if (activeContext != null) addAppWidget(activeContext); return; }
                ArrayList<String> order = new ArrayList<>(eigen.getWidgetOrder()); order.add(ids.get(n)); setWidgetOrder(order);
            });
        }
        @Override public void setWidgetOrder(java.util.List<String> order) { prefs.edit().putString("widget_order", String.join(",", order)).apply(); eigen.setWidgetOrder(new ArrayList<>(order)); }
        @Override public void removeWidget(String id) {
            ArrayList<String> order = new ArrayList<>(eigen.getWidgetOrder()); order.remove(id); setWidgetOrder(order);
            if (id.startsWith("widget:") && activeContext != null) { removeContextItem(activeContext, id); contextsChanged(); buildWidgets(); }
        }
        @Override public void openAlarms() { safeStart(new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)); }
        @Override public void media(int action) { mediaAction(action); }
        @Override public void openNotificationAccess() { safeStart(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
        @Override public void setGlassLevel(float level) { prefs.edit().putFloat("glass_level", level).apply(); FrostedCard.setGlassLevel(level); updateBackdrop(); }
        @Override public void setFrost(boolean on) { prefs.edit().putString("glass_texture", on ? "frost" : "liquid").apply(); eigen.setFrost(on); }
        @Override public void setFrostLevel(float level) { prefs.edit().putFloat("frost_level", level).apply(); eigen.setFrostLevel(level); }
        @Override public boolean isDefaultHome() { return MainActivity.this.isDefaultHome(); }
        @Override public void makeDefaultHome() { defaultHome(); }
        @Override public void setColour(int index) { applyColour(index); }
        @Override public void setClock(String style) { prefs.edit().putString("clock_face", style).apply(); eigen.setClockStyle(style); }
        @Override public void chooseWallpaper() { try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), 44); } catch (ActivityNotFoundException e) { toast(str(R.string.no_image_picker_available)); } }
        @Override public void finishTour() { endTour(); }
        @Override public ContextsHost contexts() { return contextsHost; }
        @Override public boolean isFavorite(App app) { return favorites.contains(app.id); }
        @Override public void toggleFavorite(App app) {
            if (favorites.remove(app.id)) { saveState(); refreshList(false); return; }
            if (favorites.size() >= 20) { toast(str(R.string.favorites_holds_up_to_20_apps)); return; }
            favorites.add(app.id); hidden.remove(app.id); saveState(); refreshList(false);
        }
        @Override public void moveApp(App app) {
            ArrayList<String> destinations = editableCategories(); destinations.add("automatic");
            menu("Move " + app.name + " to", destinations.toArray(new String[0]), n -> { if (n == destinations.size() - 1) assignments.remove(app.id); else assignments.put(app.id, destinations.get(n)); saveState(); refreshList(false); });
        }
        @Override public void addDrawer() { nameDialog("new drawer", "", name -> { custom.add(name); saveState(); refreshCategories(); refreshList(false); }); }
        @Override public void openWallpaper() { wallpaperSettings(); }
        @Override public void appInfo(App app) { try { launcher.startAppDetailsActivity(app.component, app.user, null, null); } catch (Exception e) { toast(str(R.string.app_info_unavailable)); } }
        @Override public void uninstall(App app) { if (app.work) { toast(str(R.string.use_app_info_to_manage_work_apps)); return; } safeStart(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.component.getPackageName()))); }
        @Override public void setCardStyle(App app, String style) {
            try { JSONObject m = new JSONObject(prefs.getString("card_styles", "{}")); if (style.equals("classic")) m.remove(app.id); else m.put(app.id, style); prefs.edit().putString("card_styles", m.toString()).apply(); } catch (JSONException ignored) { }
            pushLayoutPrefs();
        }
        @Override public void setWide(App app, boolean wide) {
            HashSet<String> set = new HashSet<>(prefs.getStringSet("wide_cards", new HashSet<>())); if (wide) set.add(app.id); else set.remove(app.id);
            prefs.edit().putStringSet("wide_cards", set).apply(); pushLayoutPrefs();
        }
        @Override public void setConversationApps(java.util.Set<String> packages) { prefs.edit().putStringSet("conversation_apps", new HashSet<>(packages)).apply(); pushLayoutPrefs(); }
        @Override public void requestUsageAccess() { safeStart(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }
        @Override public void toggleDock(App app) {
            ArrayList<String> dock = new ArrayList<>(eigen.getDockIds());
            if (dock.remove(app.id)) { /* removed */ }
            else if (dock.size() >= 4) { toast("There's no room in the dock. Remove an app from it first."); return; }
            else dock.add(app.id);
            prefs.edit().putString("dock", String.join(",", dock)).apply(); pushLayoutPrefs();
        }
        @Override public void openCalendar() { safeStart(new Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis()))); }
        @Override public void useWallpaper(Bitmap image) { useWallpaperBitmap(image); }
        @Override public void setDim(float dim) { prefs.edit().putInt("wallpaperDim", Math.round(dim * 100)).apply(); eigen.setDim(dim); }
        @Override public String drawerOf(App app) { return group(app); }
        @Override public boolean hasDot(App app) { return prefs.getBoolean("dots", true) && MediaAccessService.hasDot(app.component.getPackageName(), app.user); }
    };
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
    private final EigenBilling.Listener billingListener = new EigenBilling.Listener() {
        @Override public void onProductDetailsUpdated() { if (sheet != null && sheet.isShowing()) sheet.refresh(); }
        @Override public void onPurchaseCompleted(String productId) { if (sheet != null && sheet.isShowing()) sheet.refresh(); }
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
    public static final class App {
        public String id, name, label, group; // label = the app's own name; name = custom label when renamed
        public ComponentName component;
        public UserHandle user;
        public boolean work;
        public Drawable icon;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 31) getSplashScreen().setOnExitAnimationListener(view -> view.remove());
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        launcher = getSystemService(LauncherApps.class);
        ContextRules.localize(getResources().getStringArray(R.array.rule_words), getResources().getStringArray(R.array.rule_days));
        widgetManager = AppWidgetManager.getInstance(this);
        widgetHost = new WidgetHosting.Host(this, 7272);
        readState();
        FrostedCard.setGlassLevel(prefs.getFloat("glass_level", 0.30f));
        restoreWallpaper();
        activeContext = resolveContext(); appliedLayout = layoutKey(activeContext); category = startCategory(activeContext);
        if (state != null) { category = state.getString("category", category); query = state.getString("query", ""); searching = state.getBoolean("searching"); eigen.setTourPage(state.getInt("tour", -1)); }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        launcher.registerCallback(appChanges, handler);
        MediaAccessService.setListener(() -> { if (adapter != null && !isDestroyed()) adapter.notifyDataSetChanged(); eigen.setDotsVersion(eigen.getDotsVersion() + 1); });
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        buildUi(); loadApps();
        EigenBilling.getInstance(this).addListener(billingListener);
        if (!prefs.getBoolean("introduced", false) && eigen.getTourPage() < 0) handler.postDelayed(this::welcome, 300);
    }
    @Override protected void onStart() { super.onStart(); try { widgetHost.startListening(); } catch (RuntimeException ignored) { } }
    @Override protected void onStop() { try { widgetHost.stopListening(); } catch (RuntimeException ignored) { } super.onStop(); }
    @Override protected void onResume() { super.onResume(); foreground = true; eigen.setResumeCount(eigen.getResumeCount() + 1); if (returningFromApp) { returningFromApp = false; homeEntrance(); eigen.setReturnPulse(eigen.getReturnPulse() + 1); } registerSignals(); agendaLoaded = 0; handler.removeCallbacks(tick); handler.post(tick); loadApps(); }
    @Override protected void onPause() { foreground = false; unregisterSignals(); handler.removeCallbacks(tick); handler.removeCallbacks(evaluate); super.onPause(); }
    @Override protected void onDestroy() { EigenBilling.getInstance(this).removeListener(billingListener); MediaAccessService.setListener(null); if (Build.VERSION.SDK_INT >= 29) com.kieronquinn.app.smartspacer.sdk.client.SmartspacerClient.Companion.close(); launcher.unregisterCallback(appChanges); for(GlassSurface glass:glassSurfaces)glass.stop(); if(pendingRestore!=null)ThemeEngine.deleteTree(pendingRestore.directory); if (controller != null) controller.unregisterCallback(mediaCallback); worker.shutdownNow(); handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) { out.putString("category", category); out.putString("query", query); out.putBoolean("searching", searching); out.putInt("tour", eigen.getTourPage()); super.onSaveInstanceState(out); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); dismissDialogs(); if (sheet != null) sheet.dismiss(); searching = false; query = ""; activeContext = resolveContext(); appliedLayout = layoutKey(activeContext); category = startCategory(activeContext); scrollPositions.clear(); eigen.goHome(); if (activeContext != null && !activeContext.category.isEmpty()) eigen.openOnly(activeContext.category); buildUi(); }
    /** Android 13+ (and required from SDK 36): Back arrives through OnBackInvokedCallback, not onBackPressed. */
    // Android 9–12 still deliver Back here; Android 13+ uses the OnBackInvokedCallback registered in onCreate.
    @android.annotation.SuppressLint({"GestureBackNavigation","MissingSuperCall"}) @Override public void onBackPressed() { handleBack(); } // Back is routed by handleBack, never the default finish
    private void handleBack() { if (eigen.getTourPage() == 0) { endTour(); return; } if (sheet != null && sheet.back()) return; if (eigen.handleBack()) return; if (searching) { searching = false; query = ""; hideKeyboard(); buildUi(); } else selectCategory(startCategory(activeContext), true); }

    private void readState() {
        favorites.clear();custom.clear();disabled.clear();assignments.clear();merges.clear();hidden.clear();labels.clear();order.clear();
        ArrayList<String> hiddenIds = new ArrayList<>(); readArray("hidden", hiddenIds); hidden.addAll(hiddenIds); readMap("labels", labels); readArray("order", order);
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
            .putString("merges", new JSONObject(merges).toString()).putString("hidden", new JSONArray(hidden).toString())
            .putString("labels", new JSONObject(labels).toString()).putString("order", new JSONArray(order).toString()).apply();
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
                        a.label = info.getLabel().toString(); a.name = labels.getOrDefault(a.id, a.label); a.work = !user.equals(android.os.Process.myUserHandle());
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
        // User order first; anything new keeps its default position after it.
        result.sort((a,b)->{int i=order.indexOf(a),j=order.indexOf(b);return Integer.compare(i<0?Integer.MAX_VALUE:i,j<0?Integer.MAX_VALUE:j);});
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
            TextView t=text(c,titleSize,selected?fg:MetroArt.alpha(muted,.62f));
            if(theme.expressive){t.setTypeface(Typeface.create(theme.typeface,selected?800:500,false));t.setLetterSpacing(-.02f);}
            if(legibility!=Legibility.OFF){if(!selected)t.setTextColor(MetroArt.alpha(fg,legibility==Legibility.STRONG?.78f:.62f));legible(t);}
            if(theme.liquid){t.setTextSize(Math.min(titleSize,36));t.setTypeface(Typeface.create(theme.typeface,600,false));t.setTextColor(selected?fg:MetroArt.alpha(fg,.38f));}
            t.setPadding(dp(26),dp(theme.liquid?12:0),dp(14),dp(theme.liquid?14:8));t.setSingleLine(true);t.setLetterSpacing(-.035f);
            t.setContentDescription(str(R.string.x_category_y, c, (selected?str(R.string.selected):"")));t.setOnClickListener(v->selectCategory(c,true));
            titles.addView(t);
        }
        rebuilding = true;
        if (categoryRail != null) {
            categoryRail.setMax(categories.size()-1); categoryRail.setProgress(categories.indexOf(category)); categoryRail.fitSlots(); categoryRail.moveTo(categories.indexOf(category));
            categoryRail.setContentDescription(str(R.string.category_switcher_x_selected_swipe_up_fo, category));
        }
        rebuilding = false;
        if (titleScroll != null) {
            titleScroll.post(() -> { int i = categories.indexOf(category); if (i >= 0 && i < titles.getChildCount()) titleScroll.smoothScrollTo(Math.max(0,titles.getChildAt(i).getLeft()-dp(8)),0); });
        }
    }

    // The preference chooses a physical hand, independently of locale direction.
    @android.annotation.SuppressLint("RtlHardcoded")
    private void buildUi() {
        rebuilding = true;
        for(GlassSurface glass:glassSurfaces)glass.stop();glassSurfaces.clear();glassBackdrop.clear();
        root=column();scene=new FrameLayout(this);backdropView=new View(this);
        scene.addView(backdropView,new FrameLayout.LayoutParams(-1,-1));
        activeWallpaperMode=prefs.getString("wallpaperMode","solid");legibility=resolveLegibility();
        boolean useSystemWallpaper=activeWallpaperMode.equals("system");
        if(useSystemWallpaper)getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        updateBackdrop();
        // Edge to edge: Compose pads for the system bars itself.
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setStatusBarColor(Color.TRANSPARENT); getWindow().setNavigationBarColor(Color.TRANSPARENT);
        buildWidgets();
        pushState(); pushApps();
        scene.addView(com.studio72f.eigen.ui.EigenUi.create(this, eigen, host), new FrameLayout.LayoutParams(-1,-1));
        setContentView(scene);
        refreshCategories(); refreshList(false); updateMedia(); attachSheet(); rebuilding = false;
    }
    /** Copies the look and wallpaper into the Compose state. */
    private void pushState(){
        eigen.setAccent(accent);
        eigen.setMonoIcons(prefs.getString("icons",theme.iconStyle).equals("mono"));
        eigen.setGlass(prefs.getFloat("glass_level",.3f));
        eigen.setFrost(!"liquid".equals(prefs.getString("glass_texture","frost")));
        eigen.setFrostLevel(prefs.getFloat("frost_level",.75f)); // a step sheerer than Kelvin's default: milky, not smoky, over a dimmed wallpaper
        eigen.setReduceMotion(theme.motionMs==0);
        eigen.setClockStyle(prefs.getString("clock_face","ratio"));
        eigen.setWallpaperMode(activeWallpaperMode==null?"solid":activeWallpaperMode);
        eigen.setWallpaper(wallpaper); eigen.setSoftWallpaper(softWallpaper);
        eigen.setDim(Math.min(.92f,prefs.getInt("wallpaperDim",75)/100f+(float)Legibility.extraDim(legibility)));
        eigen.setBlur(theme.blur);
        eigen.setNotificationAccess(mediaAccess());
        pushLayoutPrefs(); applyMonochrome();
        if(eigen.getSettingsOpen())eigen.setSettings(settingsModel());
    }
    /** Per-app card styles, wide cards, the dock, the unread mark and which apps feed Conversations. */
    private void pushLayoutPrefs(){
        HashMap<String,String> styles=new HashMap<>();
        try{JSONObject m=new JSONObject(prefs.getString("card_styles","{}"));Iterator<String> k=m.keys();while(k.hasNext()){String id=k.next();styles.put(id,m.getString(id));}}catch(JSONException ignored){}
        eigen.setCardStyles(styles);
        eigen.setWideCards(new HashSet<>(prefs.getStringSet("wide_cards",new HashSet<>())));
        String dock=prefs.getString("dock","");eigen.setDockIds(dock.isEmpty()?new ArrayList<>():new ArrayList<>(Arrays.asList(dock.split(","))));
        eigen.setDotStyle(prefs.getString("dot_style","dash"));
        eigen.setConversationApps(new HashSet<>(prefs.getStringSet("conversation_apps",new HashSet<>())));
        eigen.setMonochrome(prefs.getBoolean("monochrome",false));
        eigen.setColumns(prefs.getInt("grid_columns",4));
        eigen.setHomeBlur(prefs.getFloat("home_blur",0f));
    }
    /** Monochrome only: a black-and-white filter over the whole window, Compose and legacy views alike. */
    private void applyMonochrome(){
        boolean on=prefs.getBoolean("monochrome",false);View decor=getWindow().getDecorView();
        if(on){ColorMatrix m=new ColorMatrix();m.setSaturation(0);Paint p=new Paint();p.setColorFilter(new ColorMatrixColorFilter(m));decor.setLayerType(View.LAYER_TYPE_HARDWARE,p);}
        else decor.setLayerType(View.LAYER_TYPE_NONE,null);
    }
    /** Drawers for home (in the person's category order) and every app for search (hidden apps stay searchable). */
    private void pushApps(){
        ArrayList<com.studio72f.eigen.ui.Drawer> drawers=new ArrayList<>();
        for(String c:categories){if(c.equals("all apps"))continue; // all apps live in the swipe-up drawer
            List<App> in=appsIn(c);if(!in.isEmpty())drawers.add(new com.studio72f.eigen.ui.Drawer(c,in));}
        eigen.setDrawers(drawers);eigen.setAllApps(new ArrayList<>(apps));
    }
    private void updateClock() {
        if (date != null) {
            Date now = new Date();
            String text = new SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(now);
            date.setText(activeContext == null || activeContext.fallback ? text : str(R.string.date_with_context, text, activeContext.name));
        }

    }
    private void selectCategory(String next, boolean animate) {
        if (category.equals(next)) return;
        if (list != null) scrollPositions.put(category,list.getFirstVisiblePosition());
        int direction = Integer.compare(categories.indexOf(next),categories.indexOf(category));
        category = next; refreshCategories(); refreshList(true);
        if (animate && list != null) { enterList(direction, 0); }
        if (list != null) Motion.step(list);
    }
    /** New category content arrives from the side it was swiped toward, carrying the gesture's speed. */
    private void enterList(int direction, float velocity) {
        if (pageSpring == null || listArea == null) return;
        float w = Math.max(1, listArea.getWidth());
        pageSpring.animate(direction * w * .28f, 0, velocity, null);
        cascadeRows(direction);
    }
    /** Letter bubble: pops in on a spring, and gives a small bump on each new letter. */
    private void popIn(View v){if(!Motion.on())return;new Motion.Spring(Motion.PRESS,(x,s)->{v.setScaleX(x);v.setScaleY(x);v.setAlpha(Math.min(1,x));}).precision(.001f).animate(.6f,1f,0,null);}
    private void bump(View v){if(!Motion.on())return;new Motion.Spring(Motion.PRESS,(x,s)->{v.setScaleX(x);v.setScaleY(x);}).precision(.001f).animate(1.08f,1f,0,null);}
    /**
     * Launch depth: home pushes in toward the tapped icon and dims while the app opens. Recent Android versions
     * play their own open transition for third-party launchers, so this layer is what keeps launches physical there.
     */
    private void pushIn(Rect from){
        if(root==null||!Motion.on())return;
        final View content=root;int[] at=new int[2];content.getLocationOnScreen(at);
        content.setPivotX(from!=null?from.centerX()-at[0]:content.getWidth()/2f);content.setPivotY(from!=null?from.centerY()-at[1]:content.getHeight()/2f);
        new Motion.Spring(Motion.SPATIAL,(v,s)->{content.setScaleX(v);content.setScaleY(v);}).precision(.0005f).animate(1f,1.06f,0,null);
        new Motion.Spring(Motion.EFFECT,(v,s)->content.setAlpha(v)).precision(.002f).animate(1f,.55f,0,null);
    }
    /** Coming back from an app: home eases in from slightly zoomed out, the way the wallpaper settles. */
    private void homeEntrance() {
        if (root == null || !Motion.on()) return;
        final View content = root;
        Motion.Spring zoom = new Motion.Spring(Motion.SPATIAL, (v, s) -> { content.setScaleX(v); content.setScaleY(v); }).precision(.0005f);
        Motion.Spring fade = new Motion.Spring(Motion.EFFECT, (v, s) -> content.setAlpha(v)).precision(.002f);
        content.setPivotX(content.getWidth() / 2f); content.setPivotY(content.getHeight() * .45f);
        zoom.animate(.93f, 1f, 0, null); fade.animate(.2f, 1f, 0, null);
        cascadeRows(0);
    }
    /** Rows settle in one after another: a short offset and fade, staggered by position. */
    private void cascadeRows(int direction) {
        if (list == null || !Motion.on()) return;
        list.post(() -> {
            android.view.animation.PathInterpolator emphasized = new android.view.animation.PathInterpolator(.05f, .7f, .1f, 1f);
            for (int i = 0; i < list.getChildCount(); i++) {
                View row = list.getChildAt(i);
                row.animate().cancel();
                row.setTranslationX(direction * dp(14 + 5 * i)); row.setAlpha(0f);
                row.animate().translationX(0).alpha(1).setStartDelay(i * 18L).setDuration(420).setInterpolator(emphasized).start();
            }
        });
    }
    private void refreshList(boolean reset) {
        pushApps();
        if (adapter == null) return;
        visible.clear(); String needle = CategoryRules.normalized(query.trim());
        if (!searching && category.equals("favorites")) {
            for (String id : favorites) for (App a : apps) if (a.id.equals(id)) { visible.add(a); break; }
        } else for (App a : apps) {
            if (searching ? CategoryRules.normalized(a.name).contains(needle) || !a.name.equals(a.label) && CategoryRules.normalized(a.label).contains(needle)
                : !hidden.contains(a.id) && (category.equals("all apps") || group(a).equals(category))) visible.add(a);
        }
        adapter.notifyDataSetChanged();
        empty.setText(searching ?str(R.string.no_matches_try_another_name):category.equals("favorites") ?str(R.string.no_favorites_yet_hold_an_app_in_all_apps):str(R.string.no_apps_here_hold_an_app_to_move_it_here));
        empty.setVisibility(visible.isEmpty()?View.VISIBLE:View.GONE);
        if (alphabet != null) {
            alphabet.setVisibility(!searching && category.equals("favorites") || visible.isEmpty()?View.GONE:View.VISIBLE); alphabet.update();
        }
        if (reset) list.setSelection(searching?0:scrollPositions.getOrDefault(category,0));
    }
    private final class AppRow extends LinearLayout {
        final ImageView icon;
        final TextView label;
        final View dot;
        AppRow() {
            super(MainActivity.this); setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(10),dp(8),dp(10),dp(8)); highlight.setAlpha(0); setBackground(highlight); setMinimumHeight(dp(theme.liquid?Math.max(72,theme.rowHeight):theme.rowHeight));
            icon=new ImageView(MainActivity.this); icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            if(theme.liquid&&theme.glass>0){icon.setBackground(glassSurface(icon));icon.setPadding(dp(4),dp(4),dp(4),dp(4));}
            int iconFrame=theme.liquid&&theme.glass>0?Math.max(44,theme.iconSize+8):theme.iconSize;
            LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(dp(iconFrame),dp(iconFrame));
            imageParams.setMargins(0,0,dp(20),0); addView(icon,imageParams);
            label=text("",theme.liquid?Math.min(22,theme.appSize):theme.appSize,fg); label.setLetterSpacing(-.02f);label.setMaxLines(2);label.setEllipsize(TextUtils.TruncateAt.END);
            addView(label,new LinearLayout.LayoutParams(0,-2,1));
            // Notification dot: accent circle after the name (packages with notifications, never their content).
            dot=new View(MainActivity.this);GradientDrawable d=new GradientDrawable();d.setShape(GradientDrawable.OVAL);d.setColor(accent);dot.setBackground(d);
            dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams dotParams=new LinearLayout.LayoutParams(dp(8),dp(8));dotParams.setMargins(dp(10),0,dp(4),0);addView(dot,dotParams);
        }
        // Press: the row sinks slightly and dims, then springs back with a small bounce on release.
        private final GradientDrawable highlight = rounded(MetroArt.alpha(theme.expressive ? secondaryContainer : fg, theme.expressive ? 1f : .10f), 18);
        private final Motion.Spring press = new Motion.Spring(Motion.PRESS, (v, s) -> {
            float scale = 1f - .02f * v; setScaleX(scale); setScaleY(scale);
            highlight.setAlpha(Math.round(255 * Math.max(0, Math.min(1, v)) * (theme.expressive ? .55f : 1f))); }).precision(.001f);
        @Override public void setPressed(boolean pressed) {
            boolean was = isPressed(); super.setPressed(pressed);
            if (was != pressed) { setPivotX(dp(36)); setPivotY(getHeight() / 2f); press.animateTo(pressed ? 1f : 0f, 0, null); }
        }
        void bind(App app) { bind(app, false); }
        /** first: the top search result, which Enter opens; it reads bold. */
        void bind(App app, boolean first) {
            label.setText(app.work ? getString(R.string.work_app,app.name) : app.name);
            label.setTextColor(fg); legible(label); label.setTypeface(Typeface.create(theme.typeface,first?700:theme.liquid?450:400,false));
            String style=prefs.getString("icons",theme.iconStyle);
            icon.setVisibility(style.equals("none")?GONE:VISIBLE); icon.setImageDrawable(app.icon);
            if(style.equals("mono")) { ColorMatrix colors=new ColorMatrix();colors.setSaturation(0);icon.setColorFilter(new ColorMatrixColorFilter(colors));icon.setAlpha(.82f); }
            else {icon.clearColorFilter();icon.setAlpha(1f);}
            boolean notified=prefs.getBoolean("dots",true)&&MediaAccessService.hasDot(app.component.getPackageName(),app.user);
            dot.setVisibility(notified?VISIBLE:GONE);
            setContentDescription(str(R.string.x_y_z_4_s_hold_for_app_options, app.name, (app.work?str(R.string.work_profile):""), (notified?str(R.string.has_notifications):""), (hidden.contains(app.id)?str(R.string.hidden_from_lists):"")));
        }
    }
    private final class AppAdapter extends BaseAdapter {
        public int getCount(){return visible.size();}
        public Object getItem(int p){return visible.get(p);}
        public long getItemId(int p){return p;}
        public View getView(int position,View recycled,android.view.ViewGroup parent) {
            AppRow row = recycled instanceof AppRow ? (AppRow)recycled : new AppRow();
            row.bind(visible.get(position), searching && position == 0 && !query.trim().isEmpty()); return row;
        }
    }
    /** Category switcher inside the dock pill. Tap or scrub to switch; swipe up for quick settings. */
    private final class CategoryRail extends SeekBar {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float shown=-1,downX,downY;private boolean opened;
        CategoryRail() {
            super(MainActivity.this);
            setThumb(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            setProgressDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            setSplitTrack(false);setBackground(null);
        }
        /** Padding of half a slot puts SeekBar's track ends on the first and last icon centers. */
        void fitSlots(){int n=Math.max(1,categories.size()),w=getWidth();if(w==0)return;int pad=w/(2*n);if(pad!=getPaddingLeft())setPadding(pad,0,pad,0);}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);fitSlots();}
        private Motion.Spring indicator;
        void moveTo(int index){
            if(indicator==null)indicator=new Motion.Spring(theme.expressive?Motion.SPATIAL_EXPRESSIVE:Motion.SNAPPY,(v,sp)->{shown=v;invalidate();}).precision(.002f);
            if(shown<0){shown=index;indicator.snap(index);invalidate();return;}
            indicator.animate(shown,index,0,null);
        }
        @Override public boolean performClick(){return super.performClick();}
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getActionMasked()==MotionEvent.ACTION_UP&&!opened&&Math.hypot(e.getX()-downX,e.getY()-downY)<dp(8))performClick();
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
            // A horizontal capsule (M3 navigation-bar indicator, up to 56×32dp). Its height follows its width, so a
            // crowded dock gets a smaller capsule instead of one squeezed into a vertical rectangle.
            float x=left+step*shown,half=Math.min(slot/2f-dp(1),dp(28)),tall=Math.min(dp(16),half*.68f);
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
        d.setStroke(0, 0);
        return d;
    }
    @android.annotation.SuppressLint("ClickableViewAccessibility") // the touch listener only observes; clicks reach the button
    /** Round search button. Tap to search, hold for quick settings. Uses the theme's search mark only if a theme overrides it. */
    private View searchButton(){
        View glyph;
        boolean custom;try{custom=theme.uiIcon("search")!=null;}catch(IOException e){custom=false;}
        if(custom){MetroArt.Icon icon=new MetroArt.Icon(this,uiIcons,"search",fg,false);icon.setGlyphSize(18);glyph=icon;}
        else glyph=magnifier(theme.expressive?onPrimaryContainer:fg,7.5f,theme.expressive?2.6f:2.2f);
        FrameLayout button=new FrameLayout(this);
        GradientDrawable morph=null;
        if(theme.expressive){
            // M3 Expressive FAB: rounded square in the primary container that morphs toward a circle while pressed.
            morph=new GradientDrawable();morph.setColor(primaryContainer);morph.setCornerRadius(dp(18));button.setBackground(morph);button.setElevation(dp(3));
        }else button.setBackground(dockSurface(button));
        // Press physics: squash to 0.92, rubber-band toward the finger, stretch along the drag, spring back on release.
        final GradientDrawable shape=morph;final float[] down=new float[2];
        Motion.Spring tx=new Motion.Spring(Motion.RUBBER,(x,v)->button.setTranslationX(x)),ty=new Motion.Spring(Motion.RUBBER,(y,v)->button.setTranslationY(y));
        Motion.Spring sx=new Motion.Spring(Motion.SQUASH,(x,v)->button.setScaleX(x)).precision(.001f),sy=new Motion.Spring(Motion.SQUASH,(y,v)->button.setScaleY(y)).precision(.001f);
        Motion.Spring corner=shape==null?null:new Motion.Spring(Motion.SQUASH,(r,v)->shape.setCornerRadius(r));
        button.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN: down[0]=e.getRawX();down[1]=e.getRawY();sx.animateTo(.92f,0,null);sy.animateTo(.92f,0,null);if(corner!=null)corner.animate(shape.getCornerRadius(),dp(28),0,null);break;
                case MotionEvent.ACTION_MOVE:{
                    float dx=e.getRawX()-down[0],dy=e.getRawY()-down[1],dist=(float)Math.hypot(dx,dy),soft=dp(72);
                    tx.snap(Motion.follow(dx,dist,soft));ty.snap(Motion.follow(dy,dist,soft));
                    float stretch=.92f+.08f*Math.min(1,dist/soft);
                    if(Math.abs(dx)>=Math.abs(dy)){sx.snap(stretch);sy.snap(.92f);}else{sy.snap(stretch);sx.snap(.92f);}
                    break;}
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    tx.animateTo(0,0,null);ty.animateTo(0,0,null);sx.animateTo(1,0,null);sy.animateTo(1,0,null);if(corner!=null)corner.animateTo(dp(18),0,null);break;
            }
            return false; // observes only: the button's own click and long-click handling stays intact
        });
        button.addView(glyph,new FrameLayout.LayoutParams(-1,-1));glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        button.setForeground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(MetroArt.alpha(theme.expressive?onPrimaryContainer:accent,.16f)),null,theme.expressive?null:new android.graphics.drawable.ShapeDrawable(new android.graphics.drawable.shapes.OvalShape())));
        if(theme.expressive)button.setClipToOutline(true);
        button.setFocusable(true);button.setClickable(true);button.setContentDescription(str(R.string.search_apps_hold_for_settings));
        button.setOnClickListener(v->openSearch());
        button.setOnLongClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);openSettings();return true;});
        return button;
    }
    /** Opens the app zooming out of its icon; home choreographs back in on return. */
    private void launch(App app, View from) {
        hideKeyboard();
        Rect bounds = null; Bundle options = null;
        if (from instanceof AppRow) {
            View icon = ((AppRow) from).icon.getVisibility() == View.VISIBLE ? ((AppRow) from).icon : from;
            int[] at = new int[2]; icon.getLocationOnScreen(at);
            bounds = new Rect(at[0], at[1], at[0] + icon.getWidth(), at[1] + icon.getHeight());
            if (Motion.on()) {
                ActivityOptions o;
                try { // Zoom out of a snapshot of the icon as drawn (icon packs, mono filter, glass frame included).
                    Bitmap shot = Bitmap.createBitmap(Math.max(1, icon.getWidth()), Math.max(1, icon.getHeight()), Bitmap.Config.ARGB_8888);
                    icon.draw(new Canvas(shot));
                    o = ActivityOptions.makeThumbnailScaleUpAnimation(icon, shot, 0, 0);
                } catch (RuntimeException e) { o = ActivityOptions.makeScaleUpAnimation(icon, 0, 0, icon.getWidth(), icon.getHeight()); }
                if (Build.VERSION.SDK_INT >= 33) o.setSplashScreenStyle(android.window.SplashScreen.SPLASH_SCREEN_STYLE_ICON);
                options = o.toBundle();
            }
        }
        try { launcher.startMainActivity(app.component,app.user,bounds,options); returningFromApp = true; pushIn(bounds); }
        catch (Exception e) { toast(str(R.string.app_unavailable_its_profile_may_be_pause)); loadApps(); }
    }
    private void openSearch() {
        if (searching) { search.requestFocus(); return; }
        searching=true; query=""; buildUi(); search.requestFocus();
        if(Motion.on()){View bar=(View)search.getParent();bar.setAlpha(0);new Motion.Spring(Motion.SPATIAL,(y,v)->{bar.setTranslationY(y);bar.setAlpha(Math.max(0,1-Math.abs(y)/dp(24)));}).animate(-dp(24),0,0,null);cascadeRows(0);}
        search.postDelayed(() -> { if (searching && search != null) getSystemService(InputMethodManager.class).showSoftInput(search,InputMethodManager.SHOW_IMPLICIT); },150);
    }
    private void hideKeyboard() { if (scene!=null) getSystemService(InputMethodManager.class).hideSoftInputFromWindow(scene.getWindowToken(),0); }

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

    /** Category paging that follows the finger, rubber-bands at the ends and springs with the release velocity. */
    private final class SwipeFrame extends FrameLayout {
        float downX, downY, startX; boolean horizontal, armed; VelocityTracker velocity;
        SwipeFrame(){super(MainActivity.this);}
        private int neighbor(float dx){int i=categories.indexOf(category)+(dx<0?1:-1);return i>=0&&i<categories.size()?i:-1;}
        @Override public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
            if(searching) return false;
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){ downX=e.getX(); downY=e.getY(); horizontal=false; if(velocity!=null)velocity.recycle(); velocity=VelocityTracker.obtain(); }
            if(velocity!=null)velocity.addMovement(e);
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
                boolean railEdge = prefs.getBoolean("left",false)?downX<dp(44):downX>getWidth()-dp(44);
                if(!railEdge && Math.abs(e.getX()-downX)>dp(18) && Math.abs(e.getX()-downX)>Math.abs(e.getY()-downY)*1.4f){
                    horizontal=true; armed=false; if(pageSpring!=null)pageSpring.cancel();
                    startX=e.getX()-(pageSpring!=null?listArea.getTranslationX():0); // catch a settling page mid-flight
                    getParent().requestDisallowInterceptTouchEvent(true); return true; }
            }
            return false;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(velocity!=null)velocity.addMovement(e);
            if(!horizontal||pageSpring==null){ if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick(); return true; }
            float w=Math.max(1,getWidth()), dx=e.getX()-startX;
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_MOVE:{
                    float x=neighbor(dx)<0?Motion.rubber(dx,w*.14f):dx;
                    pageSpring.snap(x);
                    boolean commit=neighbor(dx)>=0&&Math.abs(dx)>w*.3f;
                    if(commit!=armed){armed=commit;if(commit)Motion.threshold(this);}
                    return true;}
                case MotionEvent.ACTION_UP:{
                    velocity.computeCurrentVelocity(1000); float vx=velocity.getXVelocity();
                    int next=neighbor(dx); boolean fling=Math.abs(vx)>dp(700)&&Math.signum(vx)==Math.signum(dx);
                    if(next>=0&&(Math.abs(dx)>w*.3f||fling)){
                        int direction=dx<0?1:-1; String target=categories.get(next);
                        // Carry the release velocity off-screen, swap content, then spring the new list in.
                        // Hand over to the next category as soon as the old list is a third of the way out.
                        swapAt=w*.33f; pendingSwap=()->{selectCategory(target,false);enterList(direction,pageSpring.velocity()*.6f);};
                        pageSpring.animateTo(-direction*w*.6f,vx,null);
                    } else pageSpring.animateTo(0,vx,null);
                    horizontal=false; performClick(); return true;}
                case MotionEvent.ACTION_CANCEL:
                    pageSpring.animateTo(0,0,null); horizontal=false; return true;
            }
            return true;
        }
    }
    /**
     * A–Z rail anchored low, near the thumb. While scrubbing, letters near the finger bow outward (a Gaussian
     * wave on a spring) and a round bubble follows the finger. The view is wide enough for the wave, but only
     * touches that start on the edge strip belong to it; everything else falls through to the list.
     */
    private final class AlphabetRail extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); final ArrayList<String> letters = new ArrayList<>(); final ArrayList<Integer> positions=new ArrayList<>(); int selected=-1;
        float wave, fingerX, fingerY; boolean active;
        final Motion.Spring waveSpring = new Motion.Spring(Motion.WAVE, (w, v) -> { wave = w; invalidate(); placeBubble(); }).precision(.002f);
        AlphabetRail(){super(MainActivity.this);setContentDescription(str(R.string.alphabet_scrubber_drag_to_jump_to_an_app));setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void update(){ letters.clear();positions.clear(); for(int i=0;i<visible.size();i++){String l=CategoryRules.initial(visible.get(i).name);if(!letters.contains(l)){letters.add(l);positions.add(i);}}invalidate(); }
        private boolean left(){return prefs.getBoolean("left",false);}
        private float strip(){return dp(40);}
        private float step(){return Math.min(dp(19),(getHeight()-dp(24))/(float)Math.max(1,letters.size()));}
        /** Bottom-anchored: the last letter sits at 88% of the list height (within thumb reach). */
        private float top(){return Math.max(dp(8),getHeight()*.88f-step()*letters.size());}
        private float railX(){return left()?strip()/2f:getWidth()-strip()/2f;}
        private float offsetFor(float y){
            float sigma=dp(82),d=y-fingerY,g=(float)Math.exp(-d*d/(2*sigma*sigma));
            float extra=Math.max(0,Math.min(dp(50),(strip()-Math.abs(fingerX-railX())*2)*.35f));
            return (dp(50)+extra)*g*wave*(left()?1:-1);
        }
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(letters.isEmpty())return;
            paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(12));float step=step(),start=top();
            if(legibility!=Legibility.OFF)paint.setShadowLayer(dp(3),0,dp(.5f),Color.luminance(fg)>=.4?MetroArt.alpha(Color.BLACK,.6f):MetroArt.alpha(Color.WHITE,.7f));else paint.clearShadowLayer();
            for(int i=0;i<letters.size();i++){
                float y=start+step*(i+.5f);boolean on=i==selected;
                paint.setTypeface(Typeface.create(Typeface.SANS_SERIF,on?700:500,false));
                paint.setColor(on?fg:MetroArt.alpha(fg,legibility==Legibility.OFF?.55f:.75f));
                c.drawText(letters.get(i),railX()+offsetFor(y),y+dp(4),paint);
            }}
        /** The bubble rides left of the rail, centred on the finger, scaling and fading with the wave. */
        void placeBubble(){
            if(letter==null)return;
            float size=dp(46),y=Math.max(top(),Math.min(fingerY,top()+step()*letters.size()))-size/2;
            float x=left()?railX()+dp(14)+strip()/2+Math.abs(offsetFor(fingerY)):railX()-dp(14)-strip()/2-size-Math.abs(offsetFor(fingerY));
            letter.setTranslationX(getLeft()+x);letter.setTranslationY(getTop()+y);
            letter.setAlpha(wave);letter.setScaleX(.85f+.15f*wave);letter.setScaleY(.85f+.15f*wave);
            letter.setVisibility(wave>.01f?VISIBLE:GONE);
        }
        @Override public boolean performClick(){super.performClick();return true;}
        @Override public boolean onTouchEvent(MotionEvent e){
            if(letters.isEmpty())return false;
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
                boolean onStrip=left()?e.getX()<=strip():e.getX()>=getWidth()-strip();
                if(!onStrip)return false; // not ours: rows underneath keep their taps
                active=true;waveSpring.animateTo(1,0,null);
            }
            if(!active)return false;
            fingerX=e.getX();fingerY=e.getY();
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){active=false;selected=-1;waveSpring.animateTo(0,0,null);invalidate();if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;}
            getParent().requestDisallowInterceptTouchEvent(true);
            int i=Math.max(0,Math.min(letters.size()-1,(int)((e.getY()-top())/step())));
            if(i!=selected){selected=i;list.setSelection(positions.get(i));letter.setText(letters.get(i));Motion.tick(this);}
            invalidate();placeBubble();return true;
        }
    }

    /** Long-press: the app's own shortcuts first, then 72F's actions. */
    private void appMenu(App app) {
        ArrayList<String> options=new ArrayList<>();ArrayList<Runnable> actions=new ArrayList<>();
        for(ShortcutInfo shortcut:shortcuts(app)){
            CharSequence title=shortcut.getShortLabel()!=null?shortcut.getShortLabel():shortcut.getLongLabel();if(title==null)continue;
            options.add(str(R.string.x_shortcut, title));actions.add(()->{try{launcher.startShortcut(shortcut,null,null);}catch(RuntimeException e){toast(str(R.string.shortcut_unavailable));}});
        }
        boolean favorite=favorites.contains(app.id),isHidden=hidden.contains(app.id);
        options.add(favorite?str(R.string.remove_favorite):str(R.string.add_to_favorites));actions.add(()->{if(favorite)favorites.remove(app.id);else if(favorites.size()<20){favorites.add(app.id);hidden.remove(app.id);}else{toast(str(R.string.favorites_holds_up_to_20_apps));return;}saveState();refreshList(false);});
        if(favorite){
            options.add(str(R.string.move_favorite_up));actions.add(()->moveFavorite(app,-1));
            options.add(str(R.string.move_favorite_down));actions.add(()->moveFavorite(app,1));
        }
        options.add(str(R.string.move_to_category));actions.add(()->{ArrayList<String> destinations=editableCategories();destinations.add("automatic");menu(str(R.string.move_to),destinations.toArray(new String[0]),n->{if(n==destinations.size()-1)assignments.remove(app.id);else assignments.put(app.id,destinations.get(n));saveState();refreshList(false);});});
        options.add(str(R.string.rename_x, (app.name.equals(app.label)?"":" · "+app.label)));actions.add(()->renameApp(app));
        options.add(isHidden?str(R.string.show_in_lists):str(R.string.hide_from_lists_still_in_search));actions.add(()->{if(isHidden)hidden.remove(app.id);else{hidden.add(app.id);favorites.remove(app.id);}saveState();refreshList(false);toast(isHidden?str(R.string.x_is_back_in_its_category, app.name):str(R.string.x_hidden_find_it_in_search, app.name));});
        options.add(str(R.string.app_info));actions.add(()->{try{launcher.startAppDetailsActivity(app.component,app.user,null,null);}catch(Exception e){toast(str(R.string.app_info_unavailable));}});
        options.add(str(R.string.uninstall));actions.add(()->{if(app.work){toast(str(R.string.use_app_info_to_manage_work_apps));return;}safeStart(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+app.component.getPackageName())));});
        menu(app.name,options.toArray(new String[0]),i->actions.get(i).run());
    }
    private void moveFavorite(App app,int by){int p=favorites.indexOf(app.id),to=p+by;if(p>=0&&to>=0&&to<favorites.size()){Collections.swap(favorites,p,to);saveState();refreshList(false);}}
    /** Dynamic, manifest and pinned shortcuts; only the default launcher may read them. */
    private List<ShortcutInfo> shortcuts(App app){
        ArrayList<ShortcutInfo> out=new ArrayList<>();
        try{
            if(!launcher.hasShortcutHostPermission())return out;
            LauncherApps.ShortcutQuery q=new LauncherApps.ShortcutQuery().setPackage(app.component.getPackageName()).setActivity(app.component)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC|LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST|LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
            List<ShortcutInfo> found=launcher.getShortcuts(q,app.user);if(found==null)return out;
            for(ShortcutInfo s:found)if(s.isEnabled())out.add(s);
            out.sort((a,b)->a.isDeclaredInManifest()!=b.isDeclaredInManifest()?(a.isDeclaredInManifest()?-1:1):Integer.compare(a.getRank(),b.getRank()));
            while(out.size()>5)out.remove(out.size()-1);
        }catch(RuntimeException ignored){} // locked work profile or revoked host access
        return out;
    }
    private void renameApp(App app){
        EditText input=new EditText(this);input.setSingleLine();input.setText(app.name);input.setSelectAllOnFocus(true);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        AlertDialog d=new AlertDialog.Builder(this).setTitle(str(R.string.rename)).setView(input).setNegativeButton(str(R.string.cancel),null)
            .setNeutralButton(str(R.string.original_name),(a,b)->{labels.remove(app.id);saveState();loadApps();}).setPositiveButton(str(R.string.save),null).create();showDialog(d);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String name=input.getText().toString().trim();if(name.isEmpty()){input.setError(str(R.string.enter_a_name));return;}
            if(name.equals(app.label))labels.remove(app.id);else labels.put(app.id,name);saveState();d.dismiss();loadApps();});
    }
    private void hiddenAppsSettings(){
        ArrayList<App> list=new ArrayList<>();for(App a:apps)if(hidden.contains(a.id))list.add(a);
        if(list.isEmpty()){message(str(R.string.hidden_apps),str(R.string.no_hidden_apps_hold_an_app_and_choose_hi));return;}
        String[] names=new String[list.size()];for(int i=0;i<names.length;i++)names[i]=list.get(i).name+" · show again";
        menu(str(R.string.hidden_apps),names,i->{hidden.remove(list.get(i).id);saveState();refreshList(false);toast(str(R.string.x_is_back, list.get(i).name));});
    }
    private String version(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(PackageManager.NameNotFoundException e){return "";}}
    private void aboutSettings(){menu(str(R.string.s_72f_x, version()),new String[]{str(R.string.about_72f),str(R.string.open_source_notices)},n->{if(n==1){try(java.io.InputStream notice=getAssets().open("THIRD-PARTY-NOTICE.txt");java.io.InputStream license=getAssets().open("material-icons-LICENSE.txt")){message(str(R.string.open_source_notices),new String(ThemeEngine.readBounded(notice,32768),java.nio.charset.StandardCharsets.UTF_8)+"\n\n"+new String(ThemeEngine.readBounded(license,32768),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){fileError(e);}return;}message(str(R.string.s_72f_x, version()),str(R.string.horizontal_categories_vertical_app_lists));});}
    private void categorySettings(){menu(str(R.string.categories),new String[]{prefs.getBoolean("categories",true)?str(R.string.turn_categories_off):str(R.string.turn_categories_on),str(R.string.create_category),str(R.string.show_or_hide_categories),str(R.string.merge_categories),str(R.string.rename_custom_category),str(R.string.delete_custom_category)},i->{
        if(i==0){prefs.edit().putBoolean("categories",!prefs.getBoolean("categories",true)).apply();refreshCategories();refreshList(true);}
        if(i==1)nameDialog("new category","",name->{custom.add(name);saveState();refreshCategories();});
        if(i==2){ArrayList<String> cs=editableCategories();boolean[] checked=new boolean[cs.size()];for(int j=0;j<cs.size();j++)checked[j]=!disabled.contains(cs.get(j));AlertDialog d=new AlertDialog.Builder(this).setTitle(str(R.string.visible_categories)).setMultiChoiceItems(cs.toArray(new String[0]),checked,(dialog,n,on)->{if(on)disabled.remove(cs.get(n));else disabled.add(cs.get(n));}).setPositiveButton(str(R.string.done),(a,b)->{saveState();refreshCategories();refreshList(true);}).create();d.setOnCancelListener(a->{saveState();refreshCategories();refreshList(true);});showDialog(d);}
        if(i==3){ArrayList<String> cs=editableCategories();menu(str(R.string.merge_from),cs.toArray(new String[0]),n->{String from=cs.remove(n);menu(str(R.string.merge_into),cs.toArray(new String[0]),to->{String target=cs.get(to);merges.put(from,target);disabled.remove(target);saveState();refreshCategories();refreshList(true);toast(str(R.string.merged_into_x, target));});});}
        if(i==4||i==5){ArrayList<String> cs=new ArrayList<>(custom);cs.removeAll(merges.keySet());if(cs.isEmpty()){toast(str(R.string.no_custom_categories_yet));return;}menu(i==4?str(R.string.rename):str(R.string.delete),cs.toArray(new String[0]),n->{String old=cs.get(n);if(i==4)nameDialog("rename category",old,name->{custom.set(custom.indexOf(old),name);assignments.replaceAll((k,v)->v.equals(old)?name:v);merges.replaceAll((k,v)->v.equals(old)?name:v);if(disabled.remove(old))disabled.add(name);if(category.equals(old))category=name;saveState();refreshCategories();refreshList(true);});else{showDialog(new AlertDialog.Builder(this).setTitle(str(R.string.delete_x, old)).setMessage(str(R.string.apps_remain_installed_and_return_to_auto)).setNegativeButton(str(R.string.cancel),null).setPositiveButton(str(R.string.delete),(a,b)->{custom.remove(old);disabled.remove(old);assignments.values().removeIf(v->v.equals(old));merges.values().removeIf(v->v.equals(old));saveState();refreshCategories();refreshList(true);}).create());}});}
    });}
    private interface NameAction{void accept(String s);}
    private void nameDialog(String title,String existing,NameAction action){
        EditText input=new EditText(this);input.setSingleLine();input.setText(existing);input.setSelectAllOnFocus(true);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton(str(R.string.cancel),null).setPositiveButton(str(R.string.save),null).create();showDialog(d);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String name=input.getText().toString().trim().toLowerCase(Locale.ROOT);ArrayList<String> used=editableCategories();used.addAll(merges.keySet());used.add("favorites");used.add("all apps");
            if(name.isEmpty()||!name.equals(existing)&&used.contains(name)){input.setError(str(R.string.choose_a_unique_category_name));return;}action.accept(name);d.dismiss();});
    }
    private void bindMedia(){
        if(!mediaWanted()){if(controller!=null)controller.unregisterCallback(mediaCallback);controller=null;updateMedia();return;}
        try{List<MediaController> sessions=getSystemService(MediaSessionManager.class).getActiveSessions(new ComponentName(this,MediaAccessService.class));MediaController next=sessions.isEmpty()?null:sessions.get(0);for(MediaController c:sessions)if(c.getPlaybackState()!=null&&c.getPlaybackState().getState()==PlaybackState.STATE_PLAYING){next=c;break;}
            if(controller!=null&&next!=null&&controller.getSessionToken().equals(next.getSessionToken())){updateMedia();return;}
            if(controller!=null)controller.unregisterCallback(mediaCallback);controller=next;if(controller!=null)controller.registerCallback(mediaCallback,handler);
        }catch(SecurityException e){if(controller!=null)controller.unregisterCallback(mediaCallback);controller=null;}
        updateMedia();
    }
    private void updateMedia(){pushMedia();if(mediaBox==null)return;boolean show=controller!=null;mediaBox.setVisibility(show?View.VISIBLE:View.GONE);if(!show)return;MediaMetadata m=controller.getMetadata();mediaTitle.setText(m==null?str(R.string.now_playing):m.getDescription().getTitle());mediaArtist.setText(m==null?"":m.getDescription().getSubtitle());if(playButton!=null)playButton.setMark(controller.getPlaybackState()!=null&&controller.getPlaybackState().getState()==PlaybackState.STATE_PLAYING?"pause":"play",fg);}
    private void pushMedia(){
        MediaMetadata m=controller==null?null:controller.getMetadata();
        CharSequence title=m==null?null:m.getDescription().getTitle(), artist=m==null?null:m.getDescription().getSubtitle();
        eigen.setMediaTitle(controller==null?null:title==null?str(R.string.now_playing):title.toString());
        eigen.setMediaArtist(artist==null?null:artist.toString());
        PlaybackState ps=controller==null?null:controller.getPlaybackState();
        eigen.setMediaPlaying(ps!=null&&ps.getState()==PlaybackState.STATE_PLAYING);
    }
    private void mediaAction(int action){if(controller==null)return;MediaController.TransportControls c=controller.getTransportControls();if(action==0)c.skipToPrevious();else if(action==2)c.skipToNext();else if(controller.getPlaybackState()!=null&&controller.getPlaybackState().getState()==PlaybackState.STATE_PLAYING)c.pause();else c.play();}

    private void themeSettings(){menu(str(R.string.appearance),new String[]{str(R.string.retro_cyan),str(R.string.retro_cobalt),str(R.string.retro_magenta),str(R.string.paper_light),str(R.string.wave),str(R.string.liquid_retro),str(R.string.material_3_expressive),str(R.string.import_theme),str(R.string.export_theme_package),str(R.string.title_size),str(R.string.app_icons),str(R.string.app_icon_packs),str(R.string.effects),str(R.string.launcher_icon_set)},i->{
        try{
            if(i<7){applyPreset(i);return;}
            i--;
            if(i==6)pickDocument(41,false,"application/octet-stream","72f-theme.72ftheme.zip");
            if(i==7)pickDocument(42,true,"application/zip","72f-theme.72ftheme.zip");
            if(i==8)menu(str(R.string.title_size),new String[]{str(R.string.compact_32),str(R.string.retro_60),str(R.string.oversized_72)},n->{try{JSONObject t=themeJson();t.put("titleSize",new int[]{32,60,72}[n]);applyTheme(t);persistTheme();buildUi();}catch(Exception e){fileError(e);}});
            if(i==9)menu(str(R.string.app_icons),new String[]{str(R.string.monochrome),str(R.string.original_color),str(R.string.text_only)},n->{prefs.edit().putString("icons",new String[]{"mono","color","none"}[n]).apply();buildUi();});
            if(i==10)chooseIconPack();if(i==11)effectsSettings();
            if(i==12)menu(str(R.string.launcher_icon_set),new String[]{str(R.string.retro),str(R.string.wave),str(R.string.material)},n->{try{JSONObject t=themeJson();t.put("uiIconSet",new String[]{"retro","wave","material"}[n]);t.remove("uiIcons");applyTheme(t);persistTheme();buildUi();}catch(Exception e){fileError(e);}});
        }catch(Exception e){fileError(e);}
    });}
    private void applyTheme(JSONObject t)throws Exception {
        ThemeEngine next=new ThemeEngine(t,BackupStore.themeDirectory(this,prefs));theme=next;uiIcons=new UiIcons(this,next);Motion.setEnabled(next.motionMs>0);
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
            surfaceContainer=dark?MetroArt.alpha(mix(bg,fg,.12f),.92f):MetroArt.alpha(mix(bg,fg,.06f),.88f); // opaque enough to read over photos
        }
    }
    private JSONObject themeJson(){try{return new JSONObject(theme.json.toString());}catch(JSONException e){throw new IllegalStateException(e);}}
    private void persistTheme(){prefs.edit().putString("theme",theme.json.toString()).apply();prepareSoftWallpaper();}
    private void effectsSettings(){menu(str(R.string.effects),new String[]{str(R.string.background_blur),str(R.string.glass_surfaces),str(R.string.glass_tuning_blur_tint_refraction),str(R.string.material_3_expressive_x, (theme.expressive?str(R.string.on):str(R.string.off))),str(R.string.wallpaper_colors_x, (Build.VERSION.SDK_INT<31?str(R.string.android_12):theme.dynamic?str(R.string.on):str(R.string.off)))},i->{
        if(i==0)menu(str(R.string.blur),new String[]{str(R.string.off),str(R.string.soft_8),str(R.string.frosted_18),str(R.string.deep_32)},n->changeEffect("blur",new int[]{0,8,18,32}[n]));
        if(i==1)menu(str(R.string.glass),new String[]{str(R.string.off),str(R.string.subtle),str(R.string.frosted),str(R.string.liquid_glass)},n->{try{JSONObject t=themeJson();t.put("glass",new int[]{0,20,40,65}[n]);t.put("liquid",n==3);applyTheme(t);persistTheme();buildUi();}catch(Exception e){fileError(e);}});
        if(i==2){glassTuning();return;}
        if(i==3)changeEffect("expressive",!theme.expressive);
        if(i==4){if(Build.VERSION.SDK_INT<31){toast(str(R.string.wallpaper_colors_need_android_12_or_newe));return;}changeEffect("dynamic",!theme.dynamic);}
    });}
    // Glass tuning: three live sliders. Rebuilds are debounced so dragging stays smooth.
    private JSONObject pendingGlass;
    private final Runnable applyGlass=()->{if(pendingGlass==null)return;try{applyTheme(pendingGlass);persistTheme();buildUi();}catch(Exception e){fileError(e);}pendingGlass=null;};
    private String getGlassLabel(float level) {
        if (level < 0.15f) return "Opaque (0.0)";
        if (level < 0.45f) return "Frosted (0.3)";
        if (level < 0.70f) return "Glass (0.55)";
        if (level < 0.90f) return "Liquid (0.8)";
        return "Clear (1.0)";
    }
    private void glassTuning(){
        if(sheet!=null)sheet.dismiss(); // show the whole screen while tuning
        LinearLayout box=column();box.setPadding(dp(24),dp(8),dp(24),dp(12));
        TextView note=text("Stepless Kelvin glass setting (0.0 Opaque to 1.0 Clear). Frosted (0.30) is the accessible default.",14,muted);
        note.setPadding(0,0,0,dp(8));box.addView(note);

        float currentGlass = prefs.getFloat("glass_level", 0.30f);
        TextView shown = text("Level: " + getGlassLabel(currentGlass) + " (" + String.format(Locale.ROOT, "%.2f", currentGlass) + ")", 16, fg);
        shown.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        shown.setPadding(0, dp(6), 0, dp(6));
        box.addView(shown);

        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(Math.round(currentGlass * 100));
        styleSlider(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b, int p, boolean user){
                if (!user) return;
                float lvl = p / 100f;
                prefs.edit().putFloat("glass_level", lvl).apply();
                FrostedCard.setGlassLevel(lvl);
                shown.setText("Level: " + getGlassLabel(lvl) + " (" + String.format(Locale.ROOT, "%.2f", lvl) + ")");
                pushApps();
                updateBackdrop();
            }
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
        box.addView(bar, new LinearLayout.LayoutParams(-1, dp(48)));

        // Preset quick chips: Opaque (0), Frosted (0.3), Glass (0.55), Liquid (0.8), Clear (1.0)
        LinearLayout chips = row();
        chips.setPadding(0, dp(10), 0, dp(4));
        chips.addView(chip("Opaque", false, v -> bar.setProgress(0)));
        chips.addView(chip("Frosted", false, v -> bar.setProgress(30)));
        chips.addView(chip("Glass", false, v -> bar.setProgress(55)));
        chips.addView(chip("Liquid", false, v -> bar.setProgress(80)));
        chips.addView(chip("Clear", false, v -> bar.setProgress(100)));
        box.addView(chips);

        TextView warning = text("Clear glass has no contrast protection.", 12, 0x88F1F2F3);
        warning.setPadding(0, dp(8), 0, 0);
        box.addView(warning);

        AlertDialog d=new AlertDialog.Builder(this).setTitle("Glass Settings (Kelvin)").setView(box)
            .setNeutralButton("Reset (0.30)",(a,b)->{
                prefs.edit().putFloat("glass_level", 0.30f).apply();
                FrostedCard.setGlassLevel(0.30f);
                pushApps();
                updateBackdrop();
            })
            .setPositiveButton(str(R.string.done),null).create();
        showDialog(d);
        if(d.getWindow()!=null)d.getWindow().setDimAmount(0f);
    }
    private interface Label{String of(int v);}
    private View glassSlider(String name,int value,int min,int max,Label label,IntChoice change){
        LinearLayout block=column();block.setPadding(0,dp(10),0,0);
        LinearLayout head=row();head.addView(text(name,17,fg),new LinearLayout.LayoutParams(0,-2,1));
        TextView shown=text(label.of(value),14,muted);head.addView(shown);block.addView(head);
        SeekBar bar=new SeekBar(this);bar.setMax(max-min);bar.setProgress(value-min);bar.setContentDescription(name);
        styleSlider(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean user){if(!user)return;shown.setText(label.of(p+min));change.accept(p+min);}
            public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){handler.removeCallbacks(applyGlass);applyGlass.run();}
        });
        block.addView(bar,new LinearLayout.LayoutParams(-1,dp(48)));return block;
    }
    private void tuneGlass(String key,int value){
        try{if(pendingGlass==null)pendingGlass=themeJson();pendingGlass.put(key,value);}catch(JSONException e){return;}
        handler.removeCallbacks(applyGlass);handler.postDelayed(applyGlass,90);
    }
    private void changeEffect(String key,Object value){try{JSONObject t=themeJson();t.put(key,value);applyTheme(t);persistTheme();buildUi();}catch(Exception e){fileError(e);}}
    private void chooseIconPack(){
        List<IconPacks.Pack> packs=IconPacks.installed(this);ArrayList<String> names=new ArrayList<>();names.add("system icons");for(IconPacks.Pack p:packs)names.add(p.label);
        if(packs.isEmpty()){message(str(R.string.icon_packs),str(R.string.install_an_adw_compatible_icon_pack_then));return;}
        menu(str(R.string.icon_packs),names.toArray(new String[0]),i->{if(i==0){prefs.edit().remove("iconPack").apply();loadApps();return;}
            IconPacks.Pack p=packs.get(i-1);worker.execute(()->{try{new IconPacks(this,p.packageName);prefs.edit().putString("iconPack",p.packageName).apply();handler.post(()->{loadApps();toast(str(R.string.x_applied, p.label));});}catch(Exception e){handler.post(()->fileError(e));}});
        });
    }
    private void pickDocument(int request,boolean create,String mime,String name){
        Intent intent=new Intent(create?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(create?mime:"*/*");
        if(create)intent.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(intent,request);
    }
    private void backupSettings(){menu(str(R.string.backup_restore),new String[]{str(R.string.export_launcher_backup),str(R.string.restore_launcher_backup)},i->{try{pickDocument(i==0?45:46,i==0,"application/zip","72f-launcher.72fbackup.zip");}catch(Exception e){fileError(e);}});}
    private void fileError(Exception error){if(!isDestroyed())message(str(R.string.couldn_t_use_this_file),error.getMessage()==null?str(R.string.choose_a_supported_72f_file):error.getMessage());}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==47||request==48){int id=prefs.getInt("pendingWidget",-1);if(id<0)return;if(result!=RESULT_OK){cancelWidget();return;}if(request==47)configureWidget(id);else placeWidget(id);return;}
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();if(request==44){importWallpaper(uri);return;}
        if(request!=41&&request!=42&&request!=45&&request!=46)return;
        final ThemeEngine currentTheme=theme;
        worker.execute(()->{
            try{
                if(request==42||request==45){try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Destination unavailable");if(request==42)currentTheme.exportSnapshot(this,prefs,out);else BackupStore.export(this,prefs,currentTheme,out);}handler.post(()->toast(request==42?str(R.string.theme_exported):str(R.string.backup_exported)));}
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
            handler.post(()->{if(isDestroyed())return;try{applyTheme(next.json);restoreWallpaper();prepareSoftWallpaper();buildUi();loadApps();toast(str(R.string.x_applied, next.name));}catch(Exception e){fileError(e);}});
        }catch(Exception e){ThemeEngine.deleteTree(stage);throw e;}
    }
    private void reviewRestore(BackupStore.Pending prepared){
        if(isDestroyed()){ThemeEngine.deleteTree(prepared.directory);return;}
        pendingRestore=prepared;
        try{
            AlertDialog d=new AlertDialog.Builder(this).setTitle(str(R.string.restore_launcher)).setMessage(prepared.summary())
                .setNegativeButton(str(R.string.cancel),(a,b)->{ThemeEngine.deleteTree(prepared.directory);pendingRestore=null;})
                .setPositiveButton(str(R.string.restore),(a,b)->{pendingRestore=null;worker.execute(()->{try{BackupStore.apply(this,prefs,prepared);handler.post(()->{if(isDestroyed())return;pendingRestore=null;wallpaper=null;softWallpaper=null;readState();activeContext=resolveContext();appliedLayout=layoutKey(activeContext);category=startCategory(activeContext);searching=false;query="";scrollPositions.clear();restoreWallpaper();buildUi();loadApps();toast(str(R.string.launcher_restored));});}catch(Exception e){handler.post(()->fileError(e));}});}).create();
            d.setOnCancelListener(a->{ThemeEngine.deleteTree(prepared.directory);pendingRestore=null;});showDialog(d);
        }catch(Exception e){ThemeEngine.deleteTree(prepared.directory);fileError(e);}
    }
    private GlassSurface glassSurface(View host){GlassSurface surface=new GlassSurface(host,scene,()->backdropView.getBackground(),glassBackdrop,theme,getResources().getDisplayMetrics().density);glassSurfaces.add(surface);host.setClipToOutline(true);if(theme.liquid)host.setElevation(dp(3));return surface;}
    /** 10th/90th percentile relative luminance of the photo; the spots that threaten dark and light text. */
    private void measurePhoto(Bitmap image){
        try{
            Bitmap small=Bitmap.createScaledBitmap(image,48,96,true);int[] px=new int[48*96];small.getPixels(px,0,48,0,0,48,96);if(small!=image)small.recycle();
            double[] l=new double[px.length];for(int i=0;i<px.length;i++)l[i]=Color.luminance(px[i]);Arrays.sort(l);
            photoDark=l[(int)(l.length*.1)];photoBright=l[(int)(l.length*.9)];
        }catch(RuntimeException ignored){}
    }
    private boolean overImage(){return "photo".equals(activeWallpaperMode)&&wallpaper!=null||"system".equals(activeWallpaperMode);}
    /** Picks Subtle or Strong from measured contrast; the phone wallpaper uses Android's color hints. */
    private int resolveLegibility(){
        int automatic=Legibility.SUBTLE;
        if(overImage()){
            double dark=photoDark,bright=photoBright;
            if("system".equals(activeWallpaperMode)){
                try{WallpaperColors colors=WallpaperManager.getInstance(this).getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                    if(colors!=null){double primary=Color.luminance(colors.getPrimaryColor().toArgb());boolean light=Build.VERSION.SDK_INT>=31?(colors.getColorHints()&WallpaperColors.HINT_SUPPORTS_DARK_TEXT)!=0:primary>.5;
                        bright=light?Math.max(.7,primary):Math.max(.25,Math.min(1,primary*1.8));dark=light?Math.min(.3,primary):primary*.5;}}
                catch(RuntimeException ignored){}
            }
            automatic=Legibility.auto(Color.luminance(fg),Color.luminance(bg),dark,bright,prefs.getInt("wallpaperDim",75)/100.0);
        }
        return Legibility.resolve(prefs.getString("legibility","auto"),overImage(),automatic);
    }
    /** Secondary text over images: lifted from muted grey toward the main text color. */
    private int secondaryInk(){return legibility==Legibility.OFF?muted:mix(muted,fg,legibility==Legibility.STRONG?.8f:.55f);}
    /** A soft halo behind text drawn on an image: dark behind light text, light behind dark text. */
    private void legible(TextView t){
        if(legibility==Legibility.OFF){t.setShadowLayer(0,0,0,0);return;}
        boolean lightText=Color.luminance(t.getCurrentTextColor())>=.4;
        float strong=legibility==Legibility.STRONG?1:0;
        t.setShadowLayer(dp(5+5*strong),0,dp(1),lightText?MetroArt.alpha(Color.BLACK,.45f+.3f*strong):MetroArt.alpha(Color.WHITE,.6f+.3f*strong));
    }
    private void updateBackdrop(){
        if(backdropView==null)return;glassBackdrop.clear();for(GlassSurface surface:glassSurfaces)surface.invalidateSelf();
        Bitmap image=Build.VERSION.SDK_INT<31&&softWallpaper!=null?softWallpaper:wallpaper;
        boolean system=activeWallpaperMode.equals("system");
        backdropView.setBackground(new MetroArt.Backdrop(activeWallpaperMode.equals("photo")?image:null,bg,theme.backdrop.equals("solid")?bg:accent,Math.min(.92f,prefs.getInt("wallpaperDim",75)/100f+(float)Legibility.extraDim(legibility)),system));
        float gLvl = prefs.getFloat("glass_level", 0.30f);
        int blurAmount = theme.blur > 0 ? theme.blur : Math.round(24f * Math.max(0f, 1f - gLvl * 0.8f));
        boolean grey=EigenPalette.isGreyscale(accent);
        ColorMatrix desaturate=new ColorMatrix();desaturate.setSaturation(grey?0:1.12f); // a little extra colour survives the blur, as on HyperOS
        if(Build.VERSION.SDK_INT>=31){
            RenderEffect colour=RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(desaturate));
            backdropView.setRenderEffect(blurAmount>0?RenderEffect.createChainEffect(colour,RenderEffect.createBlurEffect(dp(blurAmount),dp(blurAmount),Shader.TileMode.CLAMP)):colour);
            getWindow().setBackgroundBlurRadius(system?dp(blurAmount):0);
        }else{Paint layer=new Paint();layer.setColorFilter(new ColorMatrixColorFilter(desaturate));backdropView.setLayerType(View.LAYER_TYPE_HARDWARE,layer);}
    }
    private void prepareSoftWallpaper(){
        // Made on every version: app cards draw their slice of this frosted copy, lined up with the wallpaper.
        final int request=++effectGeneration;final Bitmap source=wallpaper;final int amount=Math.max(theme.blur,24);
        if(source==null){softWallpaper=null;eigen.setSoftWallpaper(null);return;}
        worker.execute(()->{Bitmap image=ThemeEffects.blur(source,amount);handler.post(()->{if(request==effectGeneration&&!isDestroyed()){softWallpaper=image;eigen.setSoftWallpaper(image);updateBackdrop();}else if(image!=source)image.recycle();});});
    }
    private void restoreWallpaper() {
        final int request=++wallpaperGeneration;
        File file=BackupStore.wallpaperFile(this,prefs);
        if(!prefs.getString("wallpaperMode","solid").equals("photo"))return;
        if(!file.exists())return;
        final File selectedFile=file;
        worker.execute(() -> {
            try {
                Bitmap image=decodeWallpaper(selectedFile);measurePhoto(image);
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
    /** Saves a generated wallpaper (an Eigen texture) as the launcher's photo, like an import. */
    private void useWallpaperBitmap(Bitmap image){
        final int request=++wallpaperGeneration;
        worker.execute(()->{
            try{
                measurePhoto(image);
                android.util.AtomicFile saved=new android.util.AtomicFile(new File(getFilesDir(),"wallpaper.jpg"));
                FileOutputStream output=saved.startWrite();
                try{if(!image.compress(Bitmap.CompressFormat.JPEG,94,output))throw new IOException("Couldn't save image");saved.finishWrite(output);}catch(IOException e){saved.failWrite(output);throw e;}
                prefs.edit().putString("wallpaperMode","photo").putString("wallpaperPath","wallpaper.jpg").apply();
                handler.post(()->{if(!isDestroyed()&&request==wallpaperGeneration){wallpaper=image;prepareSoftWallpaper();buildUi();toast(str(R.string.wallpaper_set));}});
            }catch(Exception e){handler.post(()->message(str(R.string.couldn_t_open_wallpaper),"The texture couldn't be saved. Try again."));}
        });
    }
    private void importWallpaper(Uri uri) {
        final int request=++wallpaperGeneration;
        toast(str(R.string.preparing_wallpaper));
        worker.execute(() -> {
            File incoming=null;
            try {
                incoming=File.createTempFile("wallpaper-",".image",getCacheDir());
                try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(incoming)) {
                    if(in==null)throw new IOException("Image unavailable");
                    byte[] buffer=new byte[8192];int n,total=0;
                    while((n=in.read(buffer))!=-1){total+=n;if(total>32*1024*1024)throw new IOException("Choose an image smaller than 32 MB");out.write(buffer,0,n);}
                }
                Bitmap image=decodeWallpaper(incoming);measurePhoto(image);
                android.util.AtomicFile saved=new android.util.AtomicFile(new File(getFilesDir(),"wallpaper.jpg"));
                FileOutputStream output=null;
                try { output=saved.startWrite();if(!image.compress(Bitmap.CompressFormat.JPEG,92,output))throw new IOException("Couldn't save image");saved.finishWrite(output); }
                catch(IOException error){if(output!=null)saved.failWrite(output);image.recycle();throw error;}
                prefs.edit().putString("wallpaperMode","photo").putString("wallpaperPath","wallpaper.jpg").apply();
                handler.post(() -> { if(!isDestroyed()&&request==wallpaperGeneration){wallpaper=image;prepareSoftWallpaper();buildUi();toast(str(R.string.wallpaper_set));}else image.recycle(); });
            } catch(Exception error) {
                handler.post(() -> {if(!isDestroyed())message(str(R.string.couldn_t_open_wallpaper),str(R.string.choose_a_readable_photo_or_image_up_to_3));});
            } finally { if(incoming!=null)incoming.delete(); }
        });
    }
    private void wallpaperSettings() {
        menu(str(R.string.wallpaper),new String[]{str(R.string.choose_a_photo),str(R.string.use_phone_wallpaper),str(R.string.solid_background),str(R.string.wallpaper_dimming),str(R.string.use_saved_photo),str(R.string.text_legibility_x, legibilitySummary()),"Set as phone wallpaper","Eigen textures"},i -> {
            if(i==7){eigen.setTextureSheet(true);return;}
            if(i==6){systemWallpaper();return;}
            if(i==5){legibilitySettings();return;}
            if(i==0){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),44);}catch(ActivityNotFoundException e){toast(str(R.string.no_image_picker_available));}}
            if(i==1||i==2){prefs.edit().putString("wallpaperMode",i==1?"system":"solid").apply();buildUi();}
            if(i==3){
                LinearLayout box=column();box.setPadding(dp(24),dp(12),dp(24),dp(18));
                TextView value=text(str(R.string.x_dim, prefs.getInt("wallpaperDim",75)),24,fg);box.addView(value);
                TextView explanation=text(str(R.string.adjust_the_wallpaper_brightness_behind_a),14,muted);explanation.setPadding(0,dp(8),0,dp(16));box.addView(explanation);
                SeekBar dimmer=new SeekBar(this);dimmer.setContentDescription(str(R.string.wallpaper_dimming_2));dimmer.setMax(90);dimmer.setProgress(prefs.getInt("wallpaperDim",75));
                styleSlider(dimmer);
                dimmer.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                    public void onProgressChanged(SeekBar bar,int amount,boolean user){if(!user)return;prefs.edit().putInt("wallpaperDim",amount).apply();value.setText(str(R.string.x_dim, amount));updateBackdrop();}
                    public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}
                });
                box.addView(dimmer,new LinearLayout.LayoutParams(-1,dp(48)));
                showDialog(new AlertDialog.Builder(this).setTitle(str(R.string.wallpaper_dimming)).setView(box).setPositiveButton(str(R.string.done),null).create());
            }
            if(i==4){if(!BackupStore.wallpaperFile(this,prefs).exists()){toast(str(R.string.choose_a_photo_first));return;}prefs.edit().putString("wallpaperMode","photo").apply();if(wallpaper==null)restoreWallpaper();else buildUi();}
        });
    }

    /**
     * Pushes the launcher's photo to Android as the home and/or lock screen wallpaper. It goes unblurred: Android
     * shows it as-is, and only the copy inside Eigen gets blur, glass and greyscale.
     */
    private void systemWallpaper() {
        File file=BackupStore.wallpaperFile(this,prefs);
        if(!file.exists()){toast(str(R.string.choose_a_photo_first));return;}
        menu("Set as phone wallpaper",new String[]{"Home screen","Lock screen","Home and lock screen"},n->{
            int which=n==0?android.app.WallpaperManager.FLAG_SYSTEM:n==1?android.app.WallpaperManager.FLAG_LOCK:android.app.WallpaperManager.FLAG_SYSTEM|android.app.WallpaperManager.FLAG_LOCK;
            worker.execute(()->{
                try(java.io.InputStream in=new java.io.FileInputStream(file)){
                    android.app.WallpaperManager.getInstance(this).setStream(in,null,true,which);
                    handler.post(()->toast(n==1?"Lock screen wallpaper set":n==0?"Phone wallpaper set":"Home and lock screen wallpaper set"));
                }catch(Exception e){handler.post(()->message("Couldn't set the wallpaper",e.getMessage()==null?"Android refused the image. Try a different photo.":e.getMessage()));}
            });
        });
    }
    private void assistantSettings() {
        boolean ready = !com.studio72f.eigen.ai.EigenAssistant.statusLabel().startsWith("Quick");
        menu("Assistant", new String[]{ com.studio72f.eigen.ai.EigenAssistant.statusLabel(), "Start a new conversation", "How your data is handled" }, i -> {
            if (i == 0) message("On-device model", ready
                ? "Eigen uses Gemini Nano through Android AICore. It runs on this phone's own chip, works offline, and nothing you type is sent anywhere."
                : "This phone doesn't have Android AICore, so Eigen can't run a language model here. Quick answers still work: open an app, ask the time or battery, or do a sum.");
            else if (i == 1) { com.studio72f.eigen.ai.EigenAssistant.clearHistory(); eigen.getChat().clear(); toast("Started a new conversation"); }
            else message("Your data", "The assistant and search answers run entirely on this phone. Eigen has no internet permission, keeps no chat log on disk, and sends nothing to any server.");
        });
    }

    private void homescreenMenu() {
        menu("Eigen", new String[]{"Assistant", "Settings", "Wallpaper", "Clock style", "Colour", "Widgets", "Search apps"}, i -> {
            if (i == 0) openAssistant(null);
            else if (i == 1) openSettings();
            else if (i == 2) wallpaperSettings();
            else if (i == 3) clockSettings();
            else if (i == 4) colourSettings();
            else if (i == 5) eigen.showPage(com.studio72f.eigen.ui.EigenState.PAGE_WIDGETS);
            else eigen.setSearching(true);
        });
    }
    private void clockSettings() {
        menu("Clock style", com.studio72f.eigen.ui.ClockStyles.NAMES, n -> { prefs.edit().putString("clock_face", com.studio72f.eigen.ui.ClockStyles.IDS[n]).apply(); buildUi(); if (sheet != null && sheet.isShowing()) sheet.refresh(); });
    }
    /** Kesar and its warm siblings, or Greyscale: monochrome icons, a white accent and a desaturated in-launcher wallpaper. */
    private void colourSettings() {
        int current = -1; for (int i = 0; i < EigenPalette.ACCENTS.length; i++) if (EigenPalette.ACCENTS[i] == accent) current = i;
        track(com.studio72f.eigen.ui.EigenSheets.menu(this, eigen, "Colour", EigenPalette.ACCENT_NAMES, this::applyColour, EigenPalette.ACCENTS, current));
    }
    private void applyColour(int n) {
        {
            try {
                JSONObject t = themeJson();
                t.put("accent", String.format("#%06X", EigenPalette.ACCENTS[n] & 0xFFFFFF));
                boolean grey = EigenPalette.isGreyscale(EigenPalette.ACCENTS[n]);
                t.put("iconStyle", grey ? "mono" : "color");
                prefs.edit().remove("icons").apply();
                applyTheme(t); persistTheme(); buildUi(); updateBackdrop();
                if (sheet != null && sheet.isShowing()) sheet.refresh();
            } catch (Exception e) { fileError(e); }
        }
    }
    private String colourName() {
        for (int i = 0; i < EigenPalette.ACCENTS.length; i++) if (EigenPalette.ACCENTS[i] == accent) return EigenPalette.ACCENT_NAMES[i];
        return EigenPalette.isGreyscale(accent) ? "Greyscale" : "Custom";
    }

    // Top zone: the active context decides which items fill the header and how tall it is.
    /**
     * Home shows only the clock (drawn in Compose). Everything else the active context asks for goes to the widgets
     * page on the right: the next alarm, the agenda and the app widgets. Smartspacer and media use their own cards.
     */
    private void buildWidgets() {
        clock=null; date=null; mediaBox=null; playButton=null;
        alarmText=new TextView(this); agendaText=new TextView(this);
        eigen.setAlarm(null); eigen.setAgenda(null);
        ContextRules.Rule rule=activeContext;
        updateAlarm();updateAgenda();
        LinkedHashMap<String,View> hosted=new LinkedHashMap<>();
        if(rule!=null)for(String item:rule.widgets){if(!item.startsWith("widget:"))continue;View widget=hostedWidget(rule,item);if(widget!=null)hosted.put(item,widget);}
        eigen.setHostedWidgets(hosted);
        pushWidgetOrder();
    }
    /** The widgets page's order: the saved one, minus app widgets this context no longer has, plus any new ones. */
    private void pushWidgetOrder(){
        ArrayList<String> order=new ArrayList<>(Arrays.asList(prefs.getString("widget_order",com.studio72f.eigen.ui.EigenWidgets.DEFAULT).split(",")));
        order.removeIf(id->id.isEmpty()||(id.startsWith("widget:")&&!eigen.getHostedWidgets().containsKey(id)));
        for(String id:eigen.getHostedWidgets().keySet())if(!order.contains(id))order.add(id);
        eigen.setWidgetOrder(order);
    }
    private LinearLayout.LayoutParams slotParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(14));return p;}
    private List<App> appsIn(String c){
        ArrayList<App> out=new ArrayList<>();
        if(c.equals("favorites")){for(String id:favorites)for(App a:apps)if(a.id.equals(id)){out.add(a);break;}}
        else for(App a:apps)if(!hidden.contains(a.id)&&(c.equals("all apps")||group(a).equals(c)))out.add(a);
        return out;
    }
    private void openAssistant(String question){
        if(sheet!=null&&sheet.isShowing())sheet.back();
        eigen.openAssistant(question);
    }
    // Smartspacer (optional app, API 29+): its Client SDK view renders At a Glance targets and complications.
    static final String SMARTSPACER="com.kieronquinn.app.smartspacer";
    private boolean smartspacerInstalled(){try{getPackageManager().getPackageInfo(SMARTSPACER,0);return true;}catch(PackageManager.NameNotFoundException e){return false;}}
    private View smartspaceView(boolean first){
        if(Build.VERSION.SDK_INT<29)return null;
        if(!smartspacerInstalled()){
            TextView hint=text(str(R.string.install_smartspacer_for_at_a_glance_card),13,muted);hint.setPadding(0,first?0:dp(12),0,0);
            hint.setOnClickListener(v->safeStart(new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+SMARTSPACER))));
            hint.setContentDescription(str(R.string.smartspacer_is_not_installed_tap_to_find));return hint;
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
        if(next==null){alarmText.setVisibility(View.GONE);eigen.setAlarm(null);return;}
        String pattern=android.text.format.DateFormat.is24HourFormat(this)?"EEE HH:mm":"EEE h:mm a";
        alarmText.setVisibility(View.VISIBLE);alarmText.setText(str(R.string.alarm_x, new SimpleDateFormat(pattern,Locale.getDefault()).format(new Date(next.getTriggerTime())).toUpperCase(Locale.getDefault())));
        alarmText.setContentDescription(str(R.string.next_alarm_x_tap_to_open_alarms, alarmText.getText()));
        eigen.setAlarm("Alarm "+new SimpleDateFormat(pattern,Locale.getDefault()).format(new Date(next.getTriggerTime())));
    }
    private boolean calendarAllowed(){return checkSelfPermission(android.Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED;}
    private void updateAgenda(){
        if(agendaText==null)return;
        agendaLoaded=System.currentTimeMillis();
        if(!calendarAllowed()){agendaText.setTextColor(secondaryInk());agendaText.setText(str(R.string.tap_to_show_your_calendar_here));agendaText.setOnClickListener(v->requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR},49));eigen.setAgenda("Allow calendar access in Settings to see your day here.");return;}
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
            handler.post(()->{if(target==agendaText){target.setTextColor(out.length()==0?secondaryInk():fg);target.setText(result);eigen.setAgenda(out.length()==0?"Nothing in the next 24 hours":result);}});
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
    // The widgets page always shows what is playing, so media sessions are always watched while Home is up.
    private boolean mediaWanted(){ return true; }
    private boolean mediaAccess(){String enabled=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");return enabled!=null&&enabled.contains(new ComponentName(this,MediaAccessService.class).flattenToString());}
    private void contextsChanged(){
        ContextRules.normalize(contexts);ContextStore.save(prefs,contexts);
        appliedLayout="";evaluateContext();bindMedia();if(sheet!=null)sheet.refresh();
        eigen.setContextsVersion(eigen.getContextsVersion()+1);if(eigen.getSettingsOpen())eigen.setSettings(settingsModel());
    }
    private void contextOverride(){
        ArrayList<String> names=new ArrayList<>();names.add("automatic");for(ContextRules.Rule r:contexts)names.add(r.name);
        menu(str(R.string.use_context),names.toArray(new String[0]),i->{
            if(i==0){prefs.edit().remove("contextPin").remove("contextPinUntil").apply();contextsChanged();toast(str(R.string.contexts_are_automatic));return;}
            ContextRules.Rule r=contexts.get(i-1);
            menu(str(R.string.hold_x, r.name),new String[]{str(R.string.for_1_hour),str(R.string.for_3_hours),str(R.string.until_tomorrow_morning)},n->{
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
            view.setContentDescription(str(R.string.x_widget_hold_for_options, info.loadLabel(getPackageManager())));
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
        if(target.widgets.size()>=ContextStore.MAX_WIDGETS){toast(str(R.string.this_context_is_full));return;}
        PackageManager pm=getPackageManager();
        ArrayList<AppWidgetProviderInfo> providers=new ArrayList<>(widgetManager.getInstalledProvidersForProfile(android.os.Process.myUserHandle()));
        if(providers.isEmpty()){toast(str(R.string.no_widgets_installed));return;}
        HashMap<AppWidgetProviderInfo,String> labels=new HashMap<>();
        for(AppWidgetProviderInfo info:providers){String app;try{app=pm.getApplicationLabel(pm.getApplicationInfo(info.provider.getPackageName(),0)).toString();}catch(Exception e){app=info.provider.getPackageName();}labels.put(info,app+" · "+info.loadLabel(pm));}
        Collator collator=Collator.getInstance();providers.sort((a,b)->collator.compare(labels.get(a),labels.get(b)));
        String[] names=new String[providers.size()];for(int i=0;i<names.length;i++)names[i]=labels.get(providers.get(i));
        menu(str(R.string.add_widget),names,i->{
            AppWidgetProviderInfo info=providers.get(i);int id=widgetHost.allocateAppWidgetId();
            prefs.edit().putInt("pendingWidget",id).putString("pendingWidgetContext",target.id).apply();
            if(widgetManager.bindAppWidgetIdIfAllowed(id,info.getProfile(),info.provider,null)){configureWidget(id);return;}
            Intent bind=new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,info.provider).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE,info.getProfile());
            try{startActivityForResult(bind,47);}catch(Exception e){cancelWidget();toast(str(R.string.this_phone_doesn_t_allow_adding_widgets));}
        });
    }
    private void widgetMenu(ContextRules.Rule rule,String item){
        int id=Integer.parseInt(item.substring(7));AppWidgetProviderInfo info=widgetManager.getAppWidgetInfo(id);
        boolean reconfigurable=Build.VERSION.SDK_INT>=31&&info!=null&&info.configure!=null&&(info.widgetFeatures&AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE)!=0;
        int height=rule.header>=0?rule.header:prefs.getInt("header",20);
        ArrayList<String> options=new ArrayList<>(Arrays.asList("remove from "+rule.name,"top zone · "+height+"%","edit "+rule.name+" context"));
        if(reconfigurable)options.add(str(R.string.widget_settings));
        menu(itemLabel(item),options.toArray(new String[0]),i->{
            if(i==0){removeContextItem(rule,item);contextsChanged();toast(str(R.string.widget_removed));}
            if(i==1)menu(str(R.string.top_zone_height),new String[]{"20%","30%","40%"},n->{rule.header=new int[]{20,30,40}[n];contextsChanged();});
            if(i==2)eigen.setContextsPage(rule.id);
            if(i==3)try{widgetHost.startAppWidgetConfigureActivityForResult(this,id,0,50,null);}catch(Exception e){toast(str(R.string.widget_settings_unavailable));}
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
        widgetHost.deleteAppWidgetId(id);toast(str(R.string.couldn_t_place_that_widget));
    }
    private void cancelWidget(){int id=prefs.getInt("pendingWidget",-1);if(id>=0)widgetHost.deleteAppWidgetId(id);prefs.edit().remove("pendingWidget").remove("pendingWidgetContext").apply();}
    private void removeContextItem(ContextRules.Rule rule,String item){
        rule.widgets.remove(item);
        if(item.startsWith("widget:")){boolean used=false;for(ContextRules.Rule r:contexts)used|=r.widgets.contains(item);if(!used)try{widgetHost.deleteAppWidgetId(Integer.parseInt(item.substring(7)));}catch(RuntimeException ignored){}}
    }
    private String itemLabel(String item){
        switch(item){case "smartspace":return "Smartspacer";case "clock":return "Clock and date";case "alarm":return "Next alarm";case "agenda":return "Agenda";case "media":return "Now playing";}
        try{AppWidgetProviderInfo info=widgetManager.getAppWidgetInfo(Integer.parseInt(item.substring(7)));if(info!=null)return info.loadLabel(getPackageManager())+" widget";}catch(RuntimeException ignored){}
        return "Missing widget";
    }

    // Settings: quick sheet that expands into the full page.
    /** Sheet colour: the theme background, deepened and lifted a touch so it keeps the palette's hue. */
    private int surface(){return theme.expressive?surfaceContainer:Color.luminance(bg)>.5?mix(bg,fg,.02f):mix(mix(bg,Color.BLACK,.35f),fg,.045f);}
    private int onAccent(){return Color.luminance(accent)>.45?0xff000000:0xffffffff;}
    private void attachSheet(){
        if(sheet==null){sheet=new SettingsSheet(this);}
        if(sheet.getParent()!=null)((android.view.ViewGroup)sheet.getParent()).removeView(sheet);
        scene.addView(sheet,new FrameLayout.LayoutParams(-1,-1));
        sheet.setColors(surface(),MetroArt.alpha(fg,.07f),MetroArt.alpha(muted,.4f));
        sheet.requestApplyInsets();
        if(sheet.isShowing())sheet.refresh();

        
    }
    private void openSettings(){if(searching){searching=false;query="";hideKeyboard();buildUi();}eigen.setSettings(settingsModel());eigen.setSettingsOpen(true);}
    private com.studio72f.eigen.ui.SettingsRow row(String title,String summary,String value,String icon,String tile,Boolean toggle,Runnable action){return new com.studio72f.eigen.ui.SettingsRow(title,summary,value,icon,tile,toggle,action);}
    /**
     * The Kelvin settings page's rows. Rows that still lead to an older editor (contexts, drawer order, support)
     * open it in the legacy sheet.
     */
    private List<com.studio72f.eigen.ui.SettingsSection> settingsModel(){
        ArrayList<com.studio72f.eigen.ui.SettingsSection> out=new ArrayList<>();
        String mode=prefs.getString("wallpaperMode","solid"),icons=prefs.getString("icons",theme.iconStyle);
        out.add(new com.studio72f.eigen.ui.SettingsSection("Look",Arrays.asList(
            row("Colour",null,colourName(),"palette","orange",null,this::colourSettings),
            row("Clock style",null,com.studio72f.eigen.ui.ClockStyles.NAMES[Math.max(0,Arrays.asList(com.studio72f.eigen.ui.ClockStyles.IDS).indexOf(prefs.getString("clock_face","ratio")))],"clock","violet",null,this::clockSettings),
            row("Wallpaper",mode.equals("photo")?"Your photo, or an Eigen texture":mode.equals("system")?"The phone's wallpaper":"Solid black",null,"photo","blue",null,this::wallpaperSettings),
            row("Home wallpaper","Blur behind your drawers",prefs.getFloat("home_blur",0f)==0f?"Sharp":prefs.getFloat("home_blur",0f)<1f?"Soft":"Frosted","photo","blue",null,()->menu("Home wallpaper",new String[]{"Sharp · just dimmed","Soft · a light blur","Frosted · a full blur"},n->{prefs.edit().putFloat("home_blur",new float[]{0f,.45f,1f}[n]).apply();pushState();})),
            row("App icons",null,icons.equals("mono")?"Monochrome":!prefs.getString("iconPack","").isEmpty()?"Icon pack":"Colour","apps","teal",null,()->menu("App icons",new String[]{"Colour","Monochrome","Icon pack"},n->{if(n==2)chooseIconPack();else setIcons(n==0?"color":"mono");})),
            row("Text legibility",legibilitySummary(),null,"text","grey",null,this::legibilitySettings),
            row("Monochrome only","Black and white over everything",null,"adjust","grey",prefs.getBoolean("monochrome",false),()->{prefs.edit().putBoolean("monochrome",!prefs.getBoolean("monochrome",false)).apply();pushState();}))));
        out.add(new com.studio72f.eigen.ui.SettingsSection("Home",Arrays.asList(
            row("Grid density","Cards in each row of a drawer",prefs.getInt("grid_columns",4)==4?"Roomy":prefs.getInt("grid_columns",4)==6?"Compact":"Balanced","apps","blue",null,()->menu("Grid density",new String[]{"Roomy · 4 in a row","Balanced · 5 in a row","Compact · 6 in a row"},n->{prefs.edit().putInt("grid_columns",4+n).apply();pushState();})),
            row("Drawers","Create, hide, merge and rename",null,"folder","blue",null,this::categorySettings),
            row("Drawer order",String.join(", ",editableCategories().subList(0,Math.min(3,editableCategories().size()))),null,"sort","blue",null,()->sheet.open(this::orderPage)),
            row("Hidden apps",hidden.isEmpty()?"None. Hold an app to hide it.":hidden.size()+" hidden, still in search",null,"eye-off","grey",null,this::hiddenAppsSettings),
            row("Unread mark","On a closed drawer with notifications",new String[]{"Dash","Dot","Colour","Off"}[Math.max(0,Arrays.asList("dash","dot","colour","off").indexOf(prefs.getString("dot_style","dash")))],"bell","orange",null,()->menu("Unread mark",new String[]{"Dash · after the name","Dot · after the name","Colour · the name turns orange","Off"},n->{prefs.edit().putString("dot_style",new String[]{"dash","dot","colour","off"}[n]).apply();pushState();})),
            row("Notification dots",mediaAccess()?null:"Needs notification access",null,"bell","red",mediaAccess()&&prefs.getBoolean("dots",true),()->{if(!mediaAccess()){safeStart(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));return;}prefs.edit().putBoolean("dots",!prefs.getBoolean("dots",true)).apply();eigen.setDotsVersion(eigen.getDotsVersion()+1);eigen.setSettings(settingsModel());}))));
        out.add(new com.studio72f.eigen.ui.SettingsSection("Your day",Arrays.asList(
            row("Contexts and widgets","Which widgets show, and when",null,"layers","green",null,()->eigen.setContextsPage("")),
            row("Assistant",com.studio72f.eigen.ai.EigenAssistant.statusLabel(),null,"sparkles","orange",null,this::assistantSettings))));
        out.add(new com.studio72f.eigen.ui.SettingsSection("Access",Arrays.asList(
            row("Notification access","Conversations, replies and dots",mediaAccess()?"On":"Off","message","green",null,()->safeStart(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))),
            row("Calendar access","Your agenda on the widgets page",calendarAllowed()?"On":"Off","calendar","red",null,()->{if(calendarAllowed())safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));else requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR},49);}),
            row("Usage access","Screen time on cards and app dashboards",com.studio72f.eigen.ui.Usage.INSTANCE.hasAccess(this)?"On":"Off","adjust","violet",null,()->safeStart(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))),
            row("Default home app",null,isDefaultHome()?"Eigen":null,"home","blue",null,this::defaultHome))));
        out.add(new com.studio72f.eigen.ui.SettingsSection("Backup and about",Arrays.asList(
            row("Backup and restore","Drawers, contexts, look and wallpaper",null,"archive","grey",null,this::backupSettings),
            row("Support Eigen",null,null,"heart","red",null,()->sheet.open(this::supportPage)),
            row("Replay the tour",null,null,"refresh","grey",null,()->{eigen.setSettingsOpen(false);startTour(0);}),
            row("About Eigen",version(),null,"info","grey",null,this::aboutSettings))));
        return out;
    }
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
    // Settings chrome: grouped rounded cards on the sheet surface, tinted (never solid) accents.
    private int card(){return theme.expressive?mix(surface(),fg,.07f):mix(surface(),fg,Color.luminance(surface())>.5?.05f:.065f);}
    private int tint(float amount){return theme.expressive?secondaryContainer:mix(card(),accent,amount);}
    private int tintInk(){return theme.expressive?onSecondaryContainer:fg;}
    private GradientDrawable rounded(int fill,float radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));return d;}
    private void foregroundRipple(View v){v.setForeground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(MetroArt.alpha(fg,.10f)),null,v.getBackground()));v.setClipToOutline(true);}
    private TextView sectionLabel(String label){TextView t=text(label,13.5f,mix(accent,muted,.3f));t.setTypeface(Typeface.create(theme.typeface,600,false));t.setLetterSpacing(.01f);t.setPadding(dp(34),dp(26),dp(26),dp(10));t.setAccessibilityHeading(true);return t;}
    /** A rounded card holding rows, separated by inset hairlines. */
    private LinearLayout group(View... rows){
        LinearLayout card=column();card.setBackground(rounded(card(),22));card.setClipToOutline(true);
        for(int i=0;i<rows.length;i++){
            if(i>0){View line=new View(this);line.setBackgroundColor(MetroArt.alpha(fg,.07f));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Math.max(1,dp(.75f)));lp.setMargins(dp(18),0,0,0);card.addView(line,lp);}
            card.addView(rows[i]);
        }
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(16),0,dp(16),0);card.setLayoutParams(p);
        return card;
    }
    private LinearLayout settingRow(String title,String summary,View.OnClickListener click){
        LinearLayout row=row();row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(60));row.setPadding(dp(18),dp(13),dp(12),dp(13));
        LinearLayout words=column();TextView name=text(title,17,fg);words.addView(name);
        if(summary!=null&&!summary.isEmpty()){TextView s=text(summary,13,muted);s.setPadding(0,dp(4),0,0);s.setMaxLines(2);s.setEllipsize(TextUtils.TruncateAt.END);s.setLineSpacing(dp(1),1);words.addView(s);}
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        MetroArt.Icon arrow=new MetroArt.Icon(this,uiIcons,"chevron",MetroArt.alpha(muted,.55f),false);arrow.setGlyphSize(16);arrow.setFocusable(false);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);arrow.setMinimumWidth(0);arrow.setMinimumHeight(0);
        row.addView(arrow,new LinearLayout.LayoutParams(dp(24),dp(24)));
        row.setFocusable(true);pressFeedback(row);row.setOnClickListener(click);row.setContentDescription(title+(summary==null?"":". "+summary));
        return row;
    }
    /** Pill chip: a 48dp touch target drawing a 40dp pill. */
    private TextView chip(String label,boolean on,View.OnClickListener click){
        TextView t=text(label,14,on?tintInk():fg);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(48));t.setMinWidth(dp(48));t.setPadding(dp(16),dp(8),dp(16),dp(8));
        if(on)t.setTypeface(Typeface.create(theme.typeface,600,false));
        GradientDrawable pill=rounded(on?tint(.30f):MetroArt.alpha(fg,.085f),100);pill.setStroke(0, 0);
        t.setBackground(new android.graphics.drawable.InsetDrawable(pill,0,dp(4),0,dp(4)));t.setPadding(dp(16),dp(8),dp(16),dp(8)); // after: the inset background would reset it
        t.setForeground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(MetroArt.alpha(fg,.10f)),null,new android.graphics.drawable.InsetDrawable(rounded(Color.WHITE,100),0,dp(4),0,dp(4))));
        t.setOnClickListener(click);t.setFocusable(true);
        t.setContentDescription(label+(on?", selected":""));return t;
    }
    private View chipRow(View... chips){
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setPadding(dp(16),0,dp(16),0);
        LinearLayout line=row();for(View c:chips){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(0,0,dp(8),0);line.addView(c,p);}
        scroll.addView(line);return scroll;
    }
    /** Segmented control: one rounded track, the chosen segment raised in the accent tint. */
    private void styleSlider(SeekBar bar){
        bar.setProgressTintList(ColorStateList.valueOf(accent));bar.setThumbTintList(ColorStateList.valueOf(accent));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(MetroArt.alpha(fg,.35f)));bar.setSplitTrack(false);
    }
    /** Quick toggle: soft tonal tile, tinted with the accent and marked with a dot when on. */
    private View tile(String label,String state,boolean on,View.OnClickListener click){
        LinearLayout t=column();t.setPadding(dp(12),dp(11),dp(10),dp(10));t.setBackground(rounded(on?tint(.24f):card(),18));
        TextView name=text(label,13.5f,on?tintInk():fg);name.setMaxLines(2);name.setTypeface(Typeface.create(theme.typeface,on?600:400,false));name.setLineSpacing(dp(1),1);t.addView(name,new LinearLayout.LayoutParams(-1,0,1));
        fitWords(name);
        LinearLayout foot=row();foot.setGravity(Gravity.CENTER_VERTICAL);
        if(on){View dot=new View(this);dot.setBackground(rounded(theme.expressive?onSecondaryContainer:accent,3));LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(dp(6),dp(6));dl.setMargins(0,0,dp(6),0);foot.addView(dot,dl);}
        TextView value=text(state.toLowerCase(Locale.ROOT),12,on?MetroArt.alpha(tintInk(),.8f):muted);value.setSingleLine();foot.addView(value);t.addView(foot);
        foregroundRipple(t);
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
    private static final String[] PRESETS={"Ratio Frosted","retro · cyan","retro · cobalt","retro · magenta","paper · light","Wave","Liquid Retro","Expressive"};
    private void applyPreset(int i){
        try{SharedPreferences.Editor edit=prefs.edit().remove("themeDir").remove("icons");if(PRESETS[i].equals("Wave"))edit.putString("wallpaperMode","solid");edit.apply();
            // Expressive and wallpaper colors are styles, like glass: they carry over when you switch palettes.
            JSONObject next=ThemeEngine.preset(PRESETS[i]);if(!next.has("expressive")){next.put("expressive",theme.expressive);next.put("dynamic",theme.dynamic);}
            applyTheme(next);persistTheme();buildUi();loadApps();}catch(Exception e){fileError(e);}
    }
    private View supportPage(){
        LinearLayout page=column();
        LinearLayout head=row();head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(14),dp(4),dp(18),0);
        head.addView(iconButton("back","Back",false,v->sheet.back()));
        TextView title=text(str(R.string.support_72f_title),28,fg);title.setPadding(dp(6),0,0,0);title.setAccessibilityHeading(true);
        head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        page.addView(head);
        TextView note=text(str(R.string.support_72f_intro),13.5f,muted);
        note.setPadding(dp(26),dp(6),dp(26),dp(14));note.setLineSpacing(dp(2),1);
        page.addView(note);

        EigenBilling billing=EigenBilling.getInstance(this);
        ArrayList<View> rows=new ArrayList<>();
        for(EigenBilling.DonationTier tier:EigenBilling.TIERS){
            String price=billing.getFormattedPrice(tier.id,tier.fallbackPrice);
            LinearLayout row=row();row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(64));row.setPadding(dp(18),dp(12),dp(16),dp(12));
            TextView emoji=text(tier.emoji,20,fg);emoji.setPadding(0,0,dp(14),0);
            row.addView(emoji);
            LinearLayout words=column();
            TextView name=text(tier.title,16.5f,fg);name.setTypeface(Typeface.create(theme.typeface,600,false));
            words.addView(name);
            TextView desc=text(tier.description,12.5f,muted);desc.setPadding(0,dp(2),0,0);
            words.addView(desc);
            row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            TextView priceChip=chip(price,true,v->billing.launchDonation(MainActivity.this,tier.id));
            priceChip.setContentDescription(tier.title+", "+price);
            row.addView(priceChip);
            row.setFocusable(true);pressFeedback(row);
            row.setOnClickListener(v->billing.launchDonation(MainActivity.this,tier.id));
            row.setContentDescription(tier.title+" "+price+". "+tier.description);
            rows.add(row);
        }
        page.addView(group(rows.toArray(new View[0])));
        TextView thankNote=text(str(R.string.support_72f_thanks),13,mix(accent,muted,.4f));
        thankNote.setGravity(Gravity.CENTER);thankNote.setPadding(dp(26),dp(20),dp(26),dp(20));
        page.addView(thankNote);
        return page;
    }
    /** Category order: favorites stays first and all apps last; everything between can move. */
    private View orderPage(){
        LinearLayout page=column();
        LinearLayout head=row();head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(14),dp(4),dp(18),0);
        head.addView(iconButton("back","Back",false,v->sheet.back()));
        TextView title=text(str(R.string.category_order),30,fg);title.setPadding(dp(6),0,0,0);title.setAccessibilityHeading(true);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        page.addView(head);
        TextView note=text(str(R.string.favorites_is_always_first_and_all_apps_l),13,muted);note.setPadding(dp(26),dp(4),dp(26),dp(8));page.addView(note);
        ArrayList<String> current=editableCategories();
        for(int i=0;i<current.size();i++){
            final int index=i;String c=current.get(i);
            LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(dp(26),dp(2),dp(14),dp(2));line.setMinimumHeight(dp(56));
            TextView label=text(c,18,disabled.contains(c)?muted:fg);line.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            if(index>0){TextView up=chip("↑",false,v->moveCategory(current,index,-1));up.setContentDescription(str(R.string.move_x_up, c));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(0,0,dp(6),0);line.addView(up,p);}
            if(index<current.size()-1){TextView down=chip("↓",false,v->moveCategory(current,index,1));down.setContentDescription(str(R.string.move_x_down, c));line.addView(down);}
            page.addView(line);
        }
        page.addView(settingRow(str(R.string.reset_to_default_order),null,v->{order.clear();saveState();refreshCategories();sheet.refresh();}));
        return page;
    }
    private void moveCategory(ArrayList<String> current,int index,int by){
        Collections.swap(current,index,index+by);order.clear();order.addAll(current);saveState();refreshCategories();sheet.refresh();
    }
    private interface IntChoice{void accept(int n);}
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

    private String legibilitySummary(){
        String mode=prefs.getString("legibility","auto");
        if(!overImage())return mode+" · applies over photos";
        return mode.equals("auto")?"auto · "+(legibility==Legibility.STRONG?"strong":"subtle")+" for this wallpaper":mode;
    }
    private void legibilitySettings(){
        menu(str(R.string.text_legibility),new String[]{str(R.string.auto_measures_your_wallpaper),str(R.string.subtle_soft_halo_lifted_grey_text),str(R.string.strong_stronger_halo_and_a_little_extra),str(R.string.off)},n->{
            prefs.edit().putString("legibility",Legibility.MODES[n]).apply();buildUi();
            if(n==0&&overImage())toast(str(R.string.auto_picked_x_for_this_wallpaper, (legibility==Legibility.STRONG?str(R.string.strong):str(R.string.subtle))));
        });
    }
    private void defaultHome(){try{if(Build.VERSION.SDK_INT>=29){RoleManager rm=getSystemService(RoleManager.class);if(rm.isRoleAvailable(RoleManager.ROLE_HOME)&&!rm.isRoleHeld(RoleManager.ROLE_HOME)){startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME),43);return;}}safeStart(new Intent(Settings.ACTION_HOME_SETTINGS));}catch(Exception e){safeStart(new Intent(Settings.ACTION_HOME_SETTINGS));}}
    /** The first-run tour is a Compose overlay (ui/Tour.kt); its page lives in [eigen]. */
    private void welcome(){if(isFinishing()||isDestroyed())return;startTour(0);}
    private void startTour(int page){if(sheet!=null)sheet.dismiss();dismissDialogs();eigen.setSettingsOpen(false);eigen.setContextsPage(null);eigen.setTourPage(page);}
    private void endTour(){prefs.edit().putBoolean("introduced",true).apply();eigen.setTourPage(-1);}
    private boolean isDefaultHome(){if(Build.VERSION.SDK_INT<29)return false;RoleManager rm=getSystemService(RoleManager.class);return rm!=null&&rm.isRoleHeld(RoleManager.ROLE_HOME);}
    private void setGlass(int amount,boolean liquid){try{JSONObject t=themeJson();t.put("glass",amount);t.put("liquid",liquid);if(liquid&&theme.blur==0)t.put("blur",18);applyTheme(t);persistTheme();buildUi();}catch(Exception e){fileError(e);}}
    private void setIcons(String style){prefs.edit().putString("icons",style).apply();buildUi();}
    private interface Choice{void accept(int i);}
    /** Rounded sheet surface for menus and dialogs: top corners only, it sits on the bottom edge. */
    private GradientDrawable sheetSurface(){GradientDrawable d=new GradientDrawable();d.setColor(surface());float r=dp(28);d.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});return d;}
    private View sheetHandle(){View h=new View(this);h.setBackground(rounded(MetroArt.alpha(muted,.4f),2));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(32),dp(4));p.gravity=Gravity.CENTER_HORIZONTAL;p.setMargins(0,0,0,dp(14));h.setLayoutParams(p);return h;}
    /** Every menu is a Kelvin sheet in its own window (it can open over the settings sheet). */
    private void menu(String title,String[] options,Choice action){
        if(options.length==0){toast(str(R.string.no_categories_available));return;}
        track(com.studio72f.eigen.ui.EigenSheets.menu(this,eigen,title,options,action::accept));
    }
    /** Open dialogs, closed when Home is pressed so Home always lands on a clean home screen. */
    private final ArrayList<Dialog> openDialogs=new ArrayList<>();
    private <T extends Dialog> T track(T d){openDialogs.add(d);d.setOnDismissListener(x->openDialogs.remove(d));return d;}
    private void dismissDialogs(){for(Dialog d:new ArrayList<>(openDialogs))if(d.isShowing())d.dismiss();openDialogs.clear();}
    private void showDialog(AlertDialog d){
        track(d);if(d.getWindow()!=null)d.getWindow().setWindowAnimations(R.style.SheetMotion);d.show();
        Window window=d.getWindow();if(window!=null)riseIn(window.getDecorView());
        if(window!=null){window.setBackgroundDrawable(sheetSurface());window.setGravity(Gravity.BOTTOM);window.setLayout(-1,-2);window.getDecorView().setPadding(dp(8),dp(14),dp(8),dp(12));tintDialog(window.getDecorView());}
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){android.widget.Button b=d.getButton(which);if(b!=null){b.setTextColor(accent);b.setAllCaps(false);b.setTypeface(Typeface.create(theme.typeface,600,false));}}
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
    private void message(String title,String body){track(com.studio72f.eigen.ui.EigenSheets.message(this,eigen,title,body==null?"":body));}
    private void safeStart(Intent intent){try{startActivity(intent);}catch(Exception e){toast(str(R.string.no_app_available_for_this_action));}}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    /** UI text from res/values/strings.xml (translatable); formats only when there are arguments. */
    private String str(int id,Object... args){return args.length==0?getString(id):getString(id,args);}
    /** The one search mark: a round lens with a short handle, drawn as a thin line (the dock button and the search field). */
    private View magnifier(int color, float radiusDp, float strokeDp){
        return new View(this){
            final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c){
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(strokeDp));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(color);
                float r=dp(radiusDp),cx=getWidth()/2f-r*.27f,cy=getHeight()/2f-r*.27f,d=r*.7071f;
                c.drawCircle(cx,cy,r,p);c.drawLine(cx+d+dp(1),cy+d+dp(1),cx+d+r*.8f,cy+d+r*.8f,p);
            }
        };
    }
    /** Sheets and dialogs rise from below on a spring; their dim fades in with the window. */
    private void riseIn(View content){
        if(!Motion.on())return;
        content.post(()->{float from=Math.max(dp(80),content.getHeight()*.35f);
            new Motion.Spring(Motion.SPATIAL,(y,v)->content.setTranslationY(y)).animate(from,0,0,null);});
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setTypeface(theme.typeface);t.setIncludeFontPadding(false);return t;}
    private TextView button(String label,String description,View.OnClickListener click){TextView t=text(label,25,fg);t.setGravity(Gravity.CENTER);t.setMinWidth(dp(56));t.setMinHeight(dp(48));t.setPadding(dp(12),dp(6),dp(12),dp(6));t.setContentDescription(description);t.setOnClickListener(click);android.util.TypedValue out=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,out,true);t.setBackgroundResource(out.resourceId);return t;}
}
