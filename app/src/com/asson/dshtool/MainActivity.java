package com.asson.dshtool;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    /**
     * 署名与交流群。
     * 这里刻意不写明文：常量会进 dex 字符串池，扫一遍就被人拿走改了。
     * 数字表在 Ui.TINT_*，还原逻辑在 Repo，这一层只拿到结果。
     */
    /** 这些是相册/视频的选择器，选中也拿不到 .md / .zip，列进来只会让人误点。 */
    private static final java.util.List<String> MEDIA_PICKERS = java.util.Arrays.asList(
            "com.android.photopicker",
            "com.android.providers.media.module",
            "com.coloros.gallery3d",
            "com.coloros.video",
            "com.heytap.yoli");

    public static final String AUTHOR = Repo.authorOf();
    public static final String QQ_GROUP = Repo.groupOf();

    private static final String ABOUT_PREF = "dsh_about";
    private static final String ABOUT_MUTE = "mute";

    private DshPaths paths;
    private FrameLayout content;
    private LinearLayout rootView;
    private final TextView[] tabs = new TextView[5];
    private int current = -1;

    // ---------- 启动检查（启动图上跑，结果在说明弹窗里再列一遍）----------
    private volatile boolean bootDone;
    private volatile boolean bootRoot;
    private volatile boolean bootFresh;
    private volatile String bootPkg = PluginRepo.PKG;
    private volatile String bootHome = "";
    private FrameLayout splashVeil;
    private LinearLayout splashLog;
    private long splashAt;

    private final String[] titles = {"首页", "提示词", "设置", "网关", "日志"};

    // ---------- 选文件（导入 zip / 替换提示词共用） ----------
    public interface FilePick { void onPicked(Uri uri); }
    private static final int REQ_FILE = 4711;
    private FilePick filePick;

    /**
     * 选文件：弹一个从底部起来的「打开方式」面板，列出所有能提供文件的 App。
     *
     * 为什么不用系统的 createChooser：这台机器上 GET_CONTENT 被系统直接解析到文档界面，
     * 连 `am start` 都不弹选择器，第三方的 MT 管理器根本没机会出现。
     * 所以自己列，但样子照系统那份做：图标 + 名字 + 行内留白，底部圆角面板。
     */
    public void pickFile(FilePick cb) {
        filePick = cb;

        android.content.pm.PackageManager pm = getPackageManager();
        final java.util.List<String> labels = new java.util.ArrayList<>();
        final java.util.List<android.graphics.drawable.Drawable> icons = new java.util.ArrayList<>();
        final java.util.List<Intent> intents = new java.util.ArrayList<>();
        final java.util.List<String> seen = new java.util.ArrayList<>();

        String[] types = {"*/*", "application/octet-stream", "text/*", "application/zip"};
        for (String t : types) {
            Intent q = new Intent(Intent.ACTION_GET_CONTENT);
            q.addCategory(Intent.CATEGORY_OPENABLE);
            q.setType(t);
            java.util.List<android.content.pm.ResolveInfo> ris;
            try {
                ris = pm.queryIntentActivities(q, 0);
            } catch (Exception e) {
                continue;
            }
            for (android.content.pm.ResolveInfo ri : ris) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (getPackageName().equals(pkg)) continue;
                if (seen.contains(pkg)) continue;
                if (MEDIA_PICKERS.contains(pkg)) continue;   // 相册/视频那类拿不到 .md / .zip
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(t);
                i.setClassName(pkg, ri.activityInfo.name);
                seen.add(pkg);
                // 用 App 自己的名字，别用那个 activity 的标签（MT 的 activity 标签就叫「选择」）
                CharSequence name = ri.activityInfo.applicationInfo != null
                        ? ri.activityInfo.applicationInfo.loadLabel(pm)
                        : ri.loadLabel(pm);
                labels.add(String.valueOf(name));
                icons.add(ri.loadIcon(pm));
                intents.add(i);
            }
        }

        // 文件管理器排前面：MT / Termux / 各家管理器，其余的排后面
        sortFileManagersFirst(labels, icons, intents);

        // 系统文件管理器永远留一个，放最后
        Intent doc = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        doc.addCategory(Intent.CATEGORY_OPENABLE);
        doc.setType("*/*");
        labels.add("系统文件管理器");
        icons.add(getDrawable(android.R.drawable.sym_def_app_icon));
        intents.add(doc);

        showOpenWith(labels, icons, intents);
    }

    /** 把明显是文件管理器的排前面（MT / Termux / Mixplorer / RE / 各家自带管理器），其余保持原序。 */
    private void sortFileManagersFirst(java.util.List<String> labels,
                                       java.util.List<android.graphics.drawable.Drawable> icons,
                                       java.util.List<Intent> intents) {
        String[] rank = {"bin.mt.plus", "com.termux", "com.mixplorer", "com.speedsoftware.rootexplorer",
                "me.zhanghai.android.files", "com.estrongs.android", "com.ghisler.android.TotalCommander"};
        java.util.List<String> l0 = new java.util.ArrayList<>(labels);
        java.util.List<android.graphics.drawable.Drawable> i0 = new java.util.ArrayList<>(icons);
        java.util.List<Intent> n0 = new java.util.ArrayList<>(intents);
        labels.clear(); icons.clear(); intents.clear();
        for (String r : rank) {
            for (int k = 0; k < n0.size(); k++) {
                Intent it = n0.get(k);
                String pkg = it.getComponent() == null ? "" : it.getComponent().getPackageName();
                if (pkg.startsWith(r)) {
                    labels.add(l0.get(k));
                    icons.add(i0.get(k));
                    intents.add(it);
                    l0.remove(k); i0.remove(k); n0.remove(k);
                    k--;
                }
            }
        }
        labels.addAll(l0);
        icons.addAll(i0);
        intents.addAll(n0);
    }

    /** 底部「打开方式」面板：一行一个 App（图标 + 名字），最后一行取消。 */
    private void showOpenWith(final java.util.List<String> labels,
                              final java.util.List<android.graphics.drawable.Drawable> icons,
                              final java.util.List<Intent> intents) {
        final android.app.Dialog dlg = new android.app.Dialog(this);
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout box = Ui.v(this);
        box.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));

        TextView title = Ui.text(this, "打开方式", 15, Ui.TEXT);
        title.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 10));
        box.addView(title, Ui.lpW());

        for (int i = 0; i < labels.size(); i++) {
            final Intent target = intents.get(i);
            LinearLayout row = Ui.h(this);
            row.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
            row.setClickable(true);
            android.widget.ImageView iv = new android.widget.ImageView(this);
            android.graphics.drawable.Drawable ic = icons.get(i);
            if (ic != null) iv.setImageDrawable(ic);
            row.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26)));
            TextView tv = Ui.text(this, labels.get(i), 14, Ui.TEXT);
            tv.setPadding(Ui.dp(this, 14), 0, 0, 0);
            row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    dlg.dismiss();
                    openPicker(target);
                }
            });
            box.addView(row, Ui.lpW());
        }

        Ui.divider(this, box);
        TextView cancel = Ui.text(this, "取消", 14, Ui.MUTED);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 6));
        cancel.setClickable(true);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlg.dismiss(); }
        });
        box.addView(cancel, Ui.lpW());

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        float r = Ui.dp(this, 16);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        box.setBackground(bg);

        dlg.setContentView(box);
        android.view.Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
        }
        dlg.show();
    }

    /** 打开选中的那个选择器；没有对应 App 时把回调清掉，别让界面卡在「等你选文件」。 */
    private void openPicker(Intent intent) {
        try {
            startActivityForResult(intent, REQ_FILE);
        } catch (Exception e) {
            filePick = null;
            android.widget.Toast.makeText(this, "这个 App 打不开选文件界面，换一个试试",
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE) {
            FilePick cb = filePick;
            filePick = null;
            if (cb != null && res == RESULT_OK && data != null && data.getData() != null) {
                cb.onPicked(data.getData());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        paths = new DshPaths(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        rootView = root;

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        final LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xFFE1EBF8);
        bar.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(titles[i]);
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { switchTo(idx); }
            });
            bar.addView(t, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            tabs[i] = t;
        }
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        // targetSdk 35+ 强制 edge-to-edge，内容会画到状态栏和导航栏底下（标题顶到状态栏上）。
        // 这里把系统栏的 inset 变成 root 的 padding，让内容整体缩回安全区。
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top, bottom, left, right;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets i = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    top = i.top; bottom = i.bottom; left = i.left; right = i.right;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                    left = insets.getSystemWindowInsetLeft();
                    right = insets.getSystemWindowInsetRight();
                }
                v.setPadding(left, top, right, 0);
                // 底部留给标签栏自己吃，颜色才连成一片
                int p = Ui.dp(MainActivity.this, 6);
                bar.setPadding(0, p, 0, p + bottom);
                return insets;
            }
        });
        root.requestApplyInsets();

        switchTo(0);

        // 启动图先铺上，root / 包名 / DSH_HOME 的检查就在它上面跑完，再收起来弹说明。
        rootView.post(new Runnable() {
            @Override public void run() { showSplash(); }
        });
    }

    /** 别的页面要跳标签时用（比如首页「去设置页注入」）。 */
    public void openTab(int idx) {
        current = -1;
        switchTo(idx);
    }

    private void switchTo(int idx) {
        if (idx == current) return;
        current = idx;
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setTextColor(i == idx ? Ui.TEXT : Ui.MUTED);
        }
        content.removeAllViews();
        View page;
        switch (idx) {
            case 0: page = StatusPage.build(this, paths); break;
            case 1: page = PromptPage.build(this, paths); break;
            case 2: page = SettingsPage.build(this, paths, new Runnable() {
                @Override public void run() { current = -1; switchTo(0); }
            }); break;
            case 3: page = GatewayPage.build(this, paths); break;
            default: page = LogPage.build(this, paths); break;
        }
        content.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * 没 root 不再全屏挡死 —— 加一条横幅，功能降级但界面还能看。
     * 无 root 下能用的：导出注入包、看路径、看提示词内容。
     */
    private void showNoRoot() {
        if (rootView == null) return;
        for (int i = 0; i < rootView.getChildCount(); i++) {
            if ("noroot-banner".equals(rootView.getChildAt(i).getTag())) return;
        }
        TextView b = Ui.text(this,
                "没有 root：插件注入、网关启停、日志清空都用不了。界面可以看，操作会提示要 root。",
                11, 0xFF8A4B00);
        b.setTag("noroot-banner");
        b.setBackgroundColor(0xFFFFF6E5);
        int p = Ui.dp(this, 10);
        b.setPadding(p, p, p, p);
        rootView.addView(b, 0, Ui.lpW());
    }

    // ---------------------------------------------------------------- 云更新

    /** manual=true 时无论有没有新版本都给反馈；false 只在真有新版本时才弹。 */
    public void checkUpdate(final boolean manual) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final Update.Info info = Update.fetch(paths.updateUrl());
                    final int cur = Update.versionCode(MainActivity.this);
                    if (info.code <= cur) {
                        if (manual) {
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    android.widget.Toast.makeText(MainActivity.this,
                                            "已是最新版本（" + Update.versionName(MainActivity.this) + "）",
                                            android.widget.Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                        return;
                    }
                    runOnUiThread(new Runnable() {
                        @Override public void run() { askUpdate(info); }
                    });
                } catch (Exception e) {
                    if (!manual) return;
                    final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            android.widget.Toast.makeText(MainActivity.this,
                                    "检查更新失败：" + msg, android.widget.Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void askUpdate(final Update.Info info) {
        StringBuilder sb = new StringBuilder();
        sb.append("新版本 ").append(info.name)
          .append("（当前 ").append(Update.versionName(this)).append("）");
        if (info.size > 0) sb.append("\n大小 ").append(info.size / 1024 / 1024).append(" MB");
        if (info.notes != null && !info.notes.isEmpty()) sb.append("\n\n").append(info.notes);
        sb.append("\n\n下载后自动安装，重开 App 生效。");
        Ui.dialog(this)
                .setTitle("有新版本")
                .setMessage(sb.toString())
                .setPositiveButton("更新", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        doUpdate(info);
                    }
                })
                .setNegativeButton("稍后", null)
                .show();
    }

    private void doUpdate(final Update.Info info) {
        final android.app.ProgressDialog pd = new android.app.ProgressDialog(this);
        pd.setMessage("正在下载…");
        pd.setCancelable(false);
        pd.show();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final java.io.File apk = Update.download(MainActivity.this, info);
                    runOnUiThread(new Runnable() {
                        @Override public void run() { pd.setMessage("正在安装…"); }
                    });
                    final String r = Update.install(apk);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            pd.dismiss();
                            if ("OK".equals(r)) {
                                Ui.dialog(MainActivity.this)
                                        .setTitle("已安装 " + info.name)
                                        .setMessage("重开 App 生效。")
                                        .setPositiveButton("知道了", null)
                                        .show();
                            } else {
                                Ui.dialog(MainActivity.this)
                                        .setTitle("安装失败")
                                        .setMessage(r + "\n\nAPK 已下到：\n" + apk.getAbsolutePath())
                                        .setPositiveButton("知道了", null)
                                        .show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            pd.dismiss();
                            Ui.dialog(MainActivity.this)
                                    .setTitle("更新失败")
                                    .setMessage(msg)
                                    .setPositiveButton("知道了", null)
                                    .show();
                        }
                    });
                }
            }
        }).start();
    }

    // ---------------------------------------------------------------- 启动图

    /**
     * 启动画面 + 启动自检。
     *
     * 图像：原图完整居中，四周用同一张图的模糊版铺满 —— 单图 CENTER_CROP 时屏幕比图更窄，
     * 左侧竖排字会被裁掉一截；三层叠法（模糊 cover → 暗幕 → 原图 fit）任何比例都不裁字。
     *
     * 自检：root → DSH GO 包名 → DSH_HOME，逐条写到图下方。检查没跑完就不收图，
     * 但最多等 9 秒，卡住也要让人进得去（进不去才是真事故）。
     */
    private void showSplash() {
        android.view.ViewGroup host = findViewById(android.R.id.content);
        if (host == null) { startBootCheck(); showAbout(false); return; }

        android.graphics.drawable.Drawable fgDraw = assetDrawable("splash.png");
        if (fgDraw == null) { startBootCheck(); showAbout(false); return; }
        android.graphics.drawable.Drawable bgDraw = assetDrawable("splash_bg.png");

        final FrameLayout veil = new FrameLayout(this);
        veil.setBackgroundColor(0xFF10161D);

        android.widget.ImageView bg = new android.widget.ImageView(this);
        if (bgDraw != null) {
            bg.setImageDrawable(bgDraw);
            bg.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        }
        veil.addView(bg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View scrim = new View(this);
        scrim.setBackgroundColor(0x52000000);
        veil.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        android.widget.ImageView fg = new android.widget.ImageView(this);
        fg.setImageDrawable(fgDraw);
        fg.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        veil.addView(fg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 自检结果贴在底部，一行一条，跑完为止。底下垫一层半透明底，
        // 图再亮也不会把字吃掉。
        LinearLayout log = Ui.v(this);
        int pad = Ui.dp(this, 14);
        log.setPadding(pad, pad, pad, pad);
        log.setBackground(Ui.bg(this, 0x8C0B1219, 10));

        LinearLayout logWrap = Ui.v(this);
        int sideGap = Ui.dp(this, 18);
        logWrap.setPadding(sideGap, 0, sideGap, Ui.dp(this, 38));
        logWrap.addView(log, Ui.lpW());

        FrameLayout.LayoutParams logLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        logLp.gravity = Gravity.BOTTOM;
        veil.addView(logWrap, logLp);

        host.addView(veil, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        fg.setAlpha(0f);
        fg.setScaleX(1.04f);
        fg.setScaleY(1.04f);
        fg.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(820).start();

        splashVeil = veil;
        splashLog = log;
        splashAt = System.currentTimeMillis();

        startBootCheck();
        tickSplash();
    }

    /** 往启动图上追加一行自检结果。浅色 + 阴影，压在图上读得清。 */
    private void splashLine(String s, int color) {
        if (splashLog == null) return;
        TextView t = Ui.text(this, s, 11, color);
        t.setShadowLayer(4f, 0f, 1f, 0xCC000000);
        splashLog.addView(t, Ui.lpW());
    }

    private void say(final String s, final int color) {
        runOnUiThread(new Runnable() {
            @Override public void run() { splashLine(s, color); }
        });
    }

    /** 每 120ms 看一眼：自检完了、且图至少露了 1.8 秒（够看清三行），就收；最多等 9 秒。 */
    private void tickSplash() {
        final FrameLayout veil = splashVeil;
        if (veil == null) return;
        long t = System.currentTimeMillis() - splashAt;
        if ((bootDone && t >= 1800) || t >= 9000) { closeSplash(); return; }
        veil.postDelayed(new Runnable() {
            @Override public void run() { tickSplash(); }
        }, 120);
    }

    private void closeSplash() {
        final FrameLayout veil = splashVeil;
        splashVeil = null;
        splashLog = null;
        if (veil == null) return;
        veil.animate().alpha(0f).setDuration(280).withEndAction(new Runnable() {
            @Override public void run() {
                if (veil.getParent() instanceof ViewGroup) {
                    ((ViewGroup) veil.getParent()).removeView(veil);
                }
                showAbout(false);
            }
        }).start();
    }

    /**
     * 启动自检，顺序不能反：没有 root 什么都读不到；包名不对，DSH_HOME 与插件作用域全错。
     * 走完把首页重画一遍（路径可能刚被改写），最后才放启动图走。
     */
    private void startBootCheck() {
        new Thread(new Runnable() {
            @Override public void run() {
                boolean ok;
                try {
                    ok = RootShell.hasRoot();
                } catch (Exception e) {
                    ok = false;
                }
                bootRoot = ok;
                say(ok ? "root：已授权" : "root：没拿到权限", ok ? 0xFF9BE7B8 : 0xFFF5A9A2);
                if (ok) {
                    // ---- 包名：先认手选的（还装着就用），否则按探测结果挑一个最像的 ----
                    java.util.List<String> pkgs = Repo.detectDshPkgs();
                    PluginRepo.PKGS.clear();
                    PluginRepo.PKGS.addAll(pkgs);

                    String chosen = "";
                    String over = paths.pkgOverride();
                    if (!over.isEmpty() && pkgs.contains(over)) {
                        chosen = over;
                    } else if (!over.isEmpty()) {
                        // 手选的那个不在已装列表里了（DSH GO 换名重装），作废重挑
                        paths.setPkgOverride("");
                    }
                    if (chosen.isEmpty() && !pkgs.isEmpty()) {
                        for (String p : pkgs) {
                            if (RootShell.exists("/data/data/" + p
                                    + "/files/runtime/rootfs/root/.dsh/profiles")
                                    || RootShell.exists("/data/user/0/" + p
                                    + "/files/runtime/rootfs/root/.dsh/profiles")) {
                                chosen = p;
                                break;
                            }
                        }
                        if (chosen.isEmpty()) chosen = pkgs.get(0);
                    }
                    if (!chosen.isEmpty()) {
                        PluginRepo.PKG = chosen;
                        bootPkg = chosen;
                        say("DSH GO：" + chosen
                                + (pkgs.size() > 1 ? "（共 " + pkgs.size() + " 个候选）" : ""),
                                0xFFD6E7F7);
                    } else {
                        bootPkg = PluginRepo.PKG;
                        say("DSH GO：没探到，沿用默认 " + PluginRepo.PKG, 0xFFF5A9A2);
                    }

                    // ---- 路径 ----
                    String pick = pickHome(Repo.detectDshHome(), PluginRepo.PKG);
                    if (pick != null) {
                        bootHome = pick;
                        if (!pick.equals(paths.dshHome())) {
                            paths.setDshHome(pick);
                            String prof = Repo.detectProfile(paths);
                            if (!prof.isEmpty()) paths.setProfileName(prof);
                        }
                        say("DSH_HOME：" + shortHome(pick), 0xFFD6E7F7);
                    } else {
                        say("DSH_HOME：没找到，去「设置 · 手动指定」填", 0xFFF5A9A2);
                    }

                    // profile 对齐：机器上常有 web / headless / dsh-app-safe 好几份，
                    // 停在错的那份上，界面上看到的大小和内容全是别人的。用户手动定过就不动。
                    if (!paths.profileLocked()) {
                        String prof = Repo.detectProfile(paths);
                        if (!prof.isEmpty() && !prof.equals(paths.profileName())) {
                            paths.setProfileName(prof);
                        }
                    }
                    if (pick != null) {
                        say("profile：" + paths.profileName(), 0xFFD6E7F7);
                    }

                    // ---- 首次使用：提示词还没注入过，这是正常的，给条明路 ----
                    bootFresh = pick != null && !RootShell.exists(paths.agents());
                    if (bootFresh) {
                        say("提示词还没注入：到「设置」页点「一键注入」", 0xFFF3DFA8);
                    }

                    // 老机器上共享存储那份工作区 AGENTS.md 已经存在的话，先替用户加进来；
                    // 之后路径全交给用户自己管（别人的工作区不在这儿）
                    if (paths.workspaceAgents().isEmpty()
                            && RootShell.exists("/sdcard/.dsh/AGENTS.md")) {
                        paths.addWorkspaceAgents("/sdcard/.dsh/AGENTS.md");
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (!bootRoot) showNoRoot();
                        current = -1;
                        switchTo(0);
                        bootDone = true;
                        // 自检走完再查更新：网络慢也不拖启动，没新版本就静默
                        if (paths.updateAuto()) checkUpdate(false);
                    }
                });
            }
        }).start();
    }

    /**
     * 候选里挑真正的 proot home。
     * 顺序：落在识别出的包名目录下且带 profiles → 同包名优先 → 不在 /sdcard 且带 profiles → 兜底。
     * 先对齐包名很重要：DSH_HOME 不在 PKG 目录下时，插件的作用域校验会拒绝写入。
     */
    private static String pickHome(java.util.List<String> homes, String pkg) {
        String p1 = "/data/data/" + pkg + "/";
        String p2 = "/data/user/0/" + pkg + "/";
        for (String h : homes) {
            if ((h.startsWith(p1) || h.startsWith(p2)) && RootShell.exists(h + "/profiles")) return h;
        }
        for (String h : homes) {
            if (h.startsWith(p1) || h.startsWith(p2)) return h;
        }
        for (String h : homes) {
            if (!h.startsWith("/sdcard") && RootShell.exists(h + "/profiles")) return h;
        }
        for (String h : homes) {
            if (!h.startsWith("/sdcard")) return h;
        }
        return null;
    }

    /** 弹窗里的自检摘要：跟启动图上那几行一致，缺什么顺手给一句怎么办。 */
    private String bootSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("root：").append(bootRoot
                ? "已授权"
                : "没拿到 —— 到 KernelSU / Magisk 里给本 App 授权后重进");
        sb.append("\nDSH GO：").append(bootPkg == null || bootPkg.isEmpty()
                ? "没探到，用默认包名" : bootPkg);
        sb.append("\nDSH_HOME：").append(bootHome.isEmpty()
                ? "没找到 —— 到「设置 · 手动指定」填" : shortHome(bootHome));
        if (bootRoot && bootFresh) {
            sb.append("\n下一步：到「设置」页点「一键注入」，再把插件打上（一键清洗）");
        }
        return sb.toString();
    }

    private static String shortHome(String p) {
        String s = p.replace("/data/data/" + PluginRepo.PKG + "/files/runtime/rootfs", "…rootfs");
        s = s.replace("/data/data/" + PluginRepo.PKG + "/files/rootfs", "…rootfs");
        return s.length() > 46 ? "…" + s.substring(s.length() - 45) : s;
    }

    /** 从 assets 读一张图；读不到返回 null，调用方自己决定退化成什么样。 */
    private android.graphics.drawable.Drawable assetDrawable(String name) {
        try {
            java.io.InputStream in = getAssets().open(name);
            android.graphics.drawable.Drawable d =
                    android.graphics.drawable.Drawable.createFromStream(in, name);
            in.close();
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 启动说明

    /**
     * 启动时的说明弹窗：这个 App 是干什么的、作者是谁、出了问题去哪问。
     *
     * 措辞按第三方介绍写 —— 只讲「它是什么、能做什么、找谁」，不出现开发者自述口吻。
     * force=true 时忽略「启动时不再提示」，首页那行署名点进来用的就是这个入口。
     */
    public void showAbout(boolean force) {
        SharedPreferences sp = getSharedPreferences(ABOUT_PREF, MODE_PRIVATE);
        if (!force && sp.getBoolean(ABOUT_MUTE, false)) return;

        String body =
                "配套 DSH GO 使用的手机端管理工具，只读状态、改配置，不参与它本身的运行。\n"
                        + "\n"
                        + "· 运行状态 —— 进程、界面端口、底座版本\n"
                        + "· 提示词 —— 五层都能看能改，改完点一下生效\n"
                        + "· 插件 —— 一键注入，按宿主版本打补丁\n"
                        + "· 网关与日志 —— 可选；官方账号直连不用管\n"
                        + "\n"
                        + "首次打开：关键文件显示「未生成」是正常的，到「设置」点一次「一键注入」即可。";

        TextView tv = Ui.text(this, body, 12, Ui.TEXT);
        tv.setLineSpacing(Ui.dp(this, 5), 1f);

        // 作者与群号并成一行，固定在滚动区外面
        String byText = "作者 " + AUTHOR + "　·　交流群 QQ " + QQ_GROUP;
        android.text.SpannableString bySpan = new android.text.SpannableString(byText);
        bySpan.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                0, ("作者 " + AUTHOR).length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int gs = byText.indexOf(QQ_GROUP);
        if (gs >= 0) {
            bySpan.setSpan(new android.text.style.ForegroundColorSpan(Ui.PRIMARY),
                    gs, gs + QQ_GROUP.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            bySpan.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    gs, gs + QQ_GROUP.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView by = Ui.text(this, "", 13, Ui.TEXT);
        by.setText(bySpan);
        by.setPadding(0, Ui.dp(this, 6), 0, 0);

        final CheckBox mute = new CheckBox(this);
        mute.setText("启动时不再提示");
        mute.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        mute.setTextColor(Ui.MUTED);
        mute.setChecked(false);

        // 启动自检结果：root / DSH GO 包名 / DSH_HOME，三行，不用滑也能看见
        TextView boot = Ui.text(this, bootSummary(), 11, bootRoot ? Ui.OK : Ui.BAD);
        boot.setLineSpacing(Ui.dp(this, 3), 1f);

        LinearLayout box = Ui.v(this);
        int side = Ui.dp(this, 4);
        box.setPadding(side, 0, side, 0);
        // framed=false：原生弹窗自带留白，内容块别再描一层框
        // 不写死高度：默认字号下正好铺满，字号调大撑出屏幕时也能在框内滑，不会丢内容。
        android.widget.ScrollView svText = Ui.innerScroll(this);
        svText.addView(tv);
        box.addView(svText, Ui.lpW());
        Ui.space(this, box, 6);
        Ui.divider(this, box);
        box.addView(boot, Ui.lpW());
        box.addView(by, Ui.lpW());
        box.addView(mute, Ui.lpW());

        final android.app.AlertDialog dlg = Ui.dialog(this)
                .setTitle("关于 DSH GO 工具箱")
                .setView(box)
                .setPositiveButton("知道了", null)
                .setNegativeButton("复制群号", null)
                .create();

        dlg.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface d) {
                if (mute.isChecked()) {
                    getSharedPreferences(ABOUT_PREF, MODE_PRIVATE).edit()
                            .putBoolean(ABOUT_MUTE, true).apply();
                }
            }
        });
        dlg.show();

        // 复制群号不关窗：抄完群号还得接着看下面的说明
        dlg.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        android.content.ClipboardManager cm =
                                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("QQ群", QQ_GROUP));
                            android.widget.Toast.makeText(MainActivity.this,
                                    "已复制群号 " + QQ_GROUP,
                                    android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
}
