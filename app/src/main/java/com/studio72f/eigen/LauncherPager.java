package com.studio72f.eigen;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

/**
 * 3-Page Horizontal Pager for 72F-Launcher:
 * - Page 0 (Left):   "Root" Widgets & Tools
 * - Page 1 (Center): Main Homescreen (PS3 List or Ratio Grid)
 * - Page 2 (Right):  "Tree" Conversations & Direct Inline Reply Hub
 */
public final class LauncherPager extends ViewPager {
    public static final int PAGE_ROOT = 0;
    public static final int PAGE_HOME = 1;
    public static final int PAGE_TREE = 2;

    private View rootView;
    private View homeView;
    private View treeView;

    public LauncherPager(Context context) {
        super(context);
        init();
    }

    public LauncherPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOffscreenPageLimit(2);
    }

    public void setPages(View root, View home, View tree) {
        this.rootView = root;
        this.homeView = home;
        this.treeView = tree;

        setAdapter(new PagerAdapter() {
            @Override
            public int getCount() {
                return 3;
            }

            @Override
            public boolean isViewFromObject(View view, Object object) {
                return view == object;
            }

            @Override
            public Object instantiateItem(ViewGroup container, int position) {
                View page = null;
                switch (position) {
                    case PAGE_ROOT: page = rootView; break;
                    case PAGE_HOME: page = homeView; break;
                    case PAGE_TREE: page = treeView; break;
                }
                if (page != null) {
                    if (page.getParent() != null) {
                        ((ViewGroup) page.getParent()).removeView(page);
                    }
                    container.addView(page);
                }
                return page;
            }

            @Override
            public void destroyItem(ViewGroup container, int position, Object object) {
                container.removeView((View) object);
            }
        });

        // Default to center home page
        setCurrentItem(PAGE_HOME, false);
    }

    public boolean handleBackPressed() {
        if (getCurrentItem() != PAGE_HOME) {
            setCurrentItem(PAGE_HOME, true);
            return true;
        }
        return false;
    }
}
