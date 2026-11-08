package com.asson.dshtool;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * 首页。说清「这个 App 管的是什么」+ 「dsh go 现在起没起来」，细节折叠。
 * 模型网关那些参数属于「本地网关」页，这里不掺和。
 */
public final class StatusPage {

    private StatusPage() {}

    public static android.view.View build(final Activity act, final DshPaths dp) {
        ScrollView sv = new ScrollView(act);
        final LinearLayout root = Ui.page(act);
        sv.addView(root);

        LinearLayout head = Ui.h(act);
        head.addView(Ui.text(act, "DSH GO", 22, Ui.TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button refresh = Ui.button(act, "刷新", null);
        head.addView(refresh);
        root.addView(head, Ui.lpW());
        root.addView(Ui.text(act,
                "DSH GO 手机端管理工具\n"
                        + "查看运行状态 · 维护提示词与插件 · 管理本地网关",
                11, Ui.MUTED), Ui.lpW());

        // 署名 / 群号 / 版本常驻首页。整行可点：勾了「启动时不再提示」之后，从这里还能把说明叫回来。
        String byText = "版本 " + appVersion(act)
                + "　·　作者 " + MainActivity.AUTHOR
                + "　·　交流群 QQ " + MainActivity.QQ_GROUP;
        android.text.SpannableString bySpan = new android.text.SpannableString(byText);
        int gs = byText.indexOf(MainActivity.QQ_GROUP);
        if (gs >= 0) {
            bySpan.setSpan(new android.text.style.ForegroundColorSpan(Ui.PRIMARY),
                    gs, gs + MainActivity.QQ_GROUP.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView byLine = Ui.text(act, "", 11, Ui.MUTED);
        byLine.setText(bySpan);
        byLine.setPadding(0, Ui.dp(act, 2), 0, 0);
        byLine.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (act instanceof MainActivity) ((MainActivity) act).showAbout(true);
            }
        });
        root.addView(byLine, Ui.lpW());
        Ui.space(act, root, 10);

        final LinearLayout overview = Ui.card(act);
        root.addView(overview, Ui.lpW());
        Ui.space(act, root, 8);

        final LinearLayout fileCard = Ui.card(act);
        root.addView(fileCard, Ui.lpW());

        final boolean[] filesOpen = {false};
        final Runnable[] renderFiles = new Runnable[1];

        final Runnable load = new Runnable() {
            @Override public void run() {
                new Thread(new Runnable() {
                    @Override public void run() {
                        final List<String[]> ports = Repo.ports();
                        final String[] dsh = Repo.dshGoStatus();
                        final String[] base = PluginRepo.compat(dp);
                        final List<String[]> files = Repo.allFiles(dp);
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                fillOverview(act, overview, ports, dsh, base);
                                renderFiles[0] = new Runnable() {
                                    @Override public void run() {
                                        fillFiles(act, fileCard, files, filesOpen, this);
                                    }
                                };
                                renderFiles[0].run();
                            }
                        });
                    }
                }).start();
            }
        };
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { load.run(); }
        });
        load.run();
        return sv;
    }

    // ---------------------------------------------------------------- dsh go 状态

    private static void fillOverview(Activity act, LinearLayout card, List<String[]> ports,
                                     String[] dsh, String[] base) {
        card.removeAllViews();

        int procs = 0;
        try { procs = Integer.parseInt(dsh[0]); } catch (Exception ignored) {}
        boolean uiOn = "1".equals(dsh[1]);

        boolean p63 = false, p64 = false;
        String pid63 = "", pid64 = "";
        for (String[] p : ports) {
            boolean on = "1".equals(p[1]);
            if ("7863".equals(p[0])) { p63 = on; pid63 = p[2]; }
            if ("7864".equals(p[0])) { p64 = on; pid64 = p[2]; }
        }

        // 三态：界面能用了才算「已启动」；进程在但 3080 没起是「启动中」
        String verdict;
        int vColor;
        if (uiOn && procs > 0) { verdict = "已启动"; vColor = Ui.OK; }
        else if (procs > 0) { verdict = "启动中"; vColor = Ui.BAD; }
        else { verdict = "未启动"; vColor = Ui.BAD; }

        LinearLayout title = Ui.h(act);
        title.addView(Ui.text(act, "DSH GO 状态", 15, Ui.TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        title.addView(Ui.text(act, verdict, 14, vColor));
        card.addView(title, Ui.lpW());
        Ui.space(act, card, 8);

        card.addView(row(act, "引擎进程",
                procs > 0 ? "运行中（" + procs + " 个）" : "没在跑",
                procs > 0), Ui.lpW());
        card.addView(row(act, "对话界面",
                uiOn ? "3080 端口监听中" : "3080 没监听", uiOn), Ui.lpW());

        // 底座版本 = 宿主 @deepseek-ai/dsh 的版本，决定插件补丁能命中多少条
        String ver = base.length > 0 ? base[0] : "";
        String level = base.length > 2 ? base[2] : "0";
        String shortVerdict = "2".equals(level) ? "匹配" : ("1".equals(level) ? "同版本线" : "不兼容");
        card.addView(row(act, "底座版本",
                ver.isEmpty() || "未探测到".equals(ver) ? "读不到（需要 root）" : ver + " · " + shortVerdict,
                !"0".equals(level) && !ver.isEmpty()), Ui.lpW());

        Ui.space(act, card, 6);
        Ui.divider(act, card);
        Ui.space(act, card, 6);
        // 走官方账号直连的人根本没有网关，端口全灭是正常状态，不能当红的看
        boolean gwAny = p63 || p64;
        card.addView(Ui.text(act, gwAny
                        ? "本地网关（可选）"
                        : "本地网关：没在用（官方账号直连不需要，这块可以不管）",
                11, Ui.MUTED), Ui.lpW());
        card.addView(portRow(act, "7863", "上游", p63, pid63, gwAny), Ui.lpW());
        card.addView(portRow(act, "7864", "代理", p64, pid64, gwAny), Ui.lpW());

        if (!uiOn) {
            Ui.space(act, card, 6);
            card.addView(Ui.text(act,
                    "没检测到界面：打开 DSH GO，它会自己把引擎拉起来。",
                    11, Ui.BAD), Ui.lpW());
        }
    }

    /** App 自己的版本号（不是 DSH GO 的底座版本）。 */
    private static String appVersion(Activity act) {
        try {
            String v = act.getPackageManager()
                    .getPackageInfo(act.getPackageName(), 0).versionName;
            return v == null || v.isEmpty() ? "—" : v;
        } catch (Exception e) {
            return "—";
        }
    }

    private static LinearLayout row(Activity act, String k, String v, boolean ok) {
        LinearLayout r = Ui.h(act);
        r.addView(Ui.text(act, k, 12, Ui.MUTED),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r.addView(Ui.text(act, v, 12, ok ? Ui.OK : Ui.BAD));
        return r;
    }

    /**
     * 端口行。gwOn=false 表示这台机器压根没用网关（官方直连），
     * 这时候「未监听」是正常状态，用灰字，不要红成故障。
     */
    private static LinearLayout portRow(Activity act, String port, String name,
                                        boolean on, String pid, boolean gwOn) {
        LinearLayout r = Ui.h(act);
        r.addView(Ui.mono(act, port, 12));
        r.addView(Ui.text(act, "  " + name, 11, Ui.MUTED),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        int color = on ? Ui.OK : (gwOn ? Ui.BAD : Ui.MUTED);
        r.addView(Ui.text(act, on ? ("监听中" + (pid.isEmpty() ? "" : " pid=" + pid)) : "未监听",
                11, color));
        return r;
    }

    // ---------------------------------------------------------------- 关键文件

    private static void fillFiles(final Activity act, final LinearLayout card, final List<String[]> files,
                                  final boolean[] open, final Runnable rerender) {
        card.removeAllViews();
        card.setOnClickListener(null);

        int total = files.size(), missing = 0;
        for (String[] f : files) if (parseLong(f[2], -1) < 0) missing++;

        LinearLayout head = Ui.h(act);
        head.addView(Ui.text(act, "关键文件", 12, Ui.MUTED),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        // 「未生成」不是故障：第一次用、或者还没注入过，这些文件本来就还没有
        head.addView(Ui.text(act, total + " 个"
                        + (missing == 0 ? " · 全部就绪" : " · 未生成 " + missing),
                12, missing == 0 ? Ui.OK : Ui.MUTED));
        card.addView(head, Ui.lpW());

        if (!open[0]) {
            Ui.space(act, card, 6);
            card.addView(Ui.text(act, missing == 0
                            ? "点这一块展开：每个文件的路径、作用、修改时间"
                            : "点这一块展开：看看缺哪几份。刚开始用缺几份是正常的。",
                    11, Ui.MUTED), Ui.lpW());
            if (missing > 0) {
                Button go = Ui.button(act, "去设置页注入", new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (act instanceof MainActivity) ((MainActivity) act).openTab(2);
                    }
                });
                card.addView(Ui.buttonRow(act, go), Ui.lpW());
            }
            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    open[0] = true;
                    rerender.run();
                }
            });
            return;
        }

        Ui.space(act, card, 4);
        String lastGroup = null;
        for (String[] f : files) {
            long size = parseLong(f[2], -1);
            boolean ok = size >= 0;

            String group = f.length > 5 ? f[5] : "";
            if (group != null && !group.isEmpty() && !group.equals(lastGroup)) {
                lastGroup = group;
                card.addView(Ui.sectionTitle(act, group), Ui.lpW());
            }

            LinearLayout r = Ui.h(act);
            r.addView(Ui.text(act, f[0], 13, Ui.TEXT),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            r.addView(Ui.text(act, ok ? size + " B" : "未生成", 12, ok ? Ui.OK : Ui.MUTED));
            card.addView(r, Ui.lpW());

            card.addView(Ui.mono(act, shortPath(f[1]), 11), Ui.lpW());
            if (f.length > 4 && !f[4].isEmpty()) {
                card.addView(Ui.text(act, f[4], 11, Ui.MUTED), Ui.lpW());
            }
            Ui.divider(act, card);
        }

        Ui.space(act, card, 6);
        Button collapse = Ui.button(act, "收起", new View.OnClickListener() {
            @Override public void onClick(View v) {
                open[0] = false;
                rerender.run();
            }
        });
        card.addView(Ui.buttonRow(act, collapse), Ui.lpW());
    }

    private static String shortPath(String p) {
        if (p == null) return "";
        String s = p.replace("/data/data/" + PluginRepo.PKG + "/files/runtime/rootfs", "…");
        if (s.length() > 48) s = "…" + s.substring(s.length() - 47);
        return s;
    }

    private static long parseLong(String s, long def) {
        try { return Long.parseLong(s); } catch (Exception e) { return def; }
    }
}
