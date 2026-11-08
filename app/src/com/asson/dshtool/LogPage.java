package com.asson.dshtool;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class LogPage {

    private LogPage() {}

    public static View build(final Activity act, final DshPaths dp) {
        LinearLayout root = Ui.page(act);

        LinearLayout head = Ui.h(act);
        final Button[] tabs = new Button[3];
        final String[] labels = {"网关", "兜底", "上游"};
        final String[] paths = {dp.gwLog(), dp.guardLog(), dp.upstreamLog()};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            tabs[i] = Ui.button(act, labels[i], null);
            tabs[i].setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
            head.addView(tabs[i]);
        }
        root.addView(head, Ui.lpW());
        root.addView(Ui.text(act, "网关跑起来才有内容；没装网关这里就是空的。", 11, Ui.MUTED), Ui.lpW());

        final TextView pathLabel = Ui.mono(act, shortPath(paths[0]), 11);
        root.addView(pathLabel, Ui.lpW());
        Ui.space(act, root, 6);

        final ScrollView sv = new ScrollView(act);
        final TextView body = Ui.output(act, "点「刷新」读日志");
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        final int[] current = {0};

        final Runnable load = new Runnable() {
            @Override public void run() {
                final int idx = current[0];
                pathLabel.setText(shortPath(paths[idx]));
                body.setText("加载中…");
                new Thread(new Runnable() {
                    @Override public void run() {
                        final String t = RootShell.tail(paths[idx], 300);
                        final boolean has = RootShell.exists(paths[idx]);
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (!t.isEmpty()) { body.setText(t); return; }
                                body.setText(has
                                        ? "这份日志现在是空的。跑一次网关再有内容。"
                                        : "还没生成这份日志。\n\n装上网关、启动过一次才会有。"
                                          + "没装网关的话，这一页可以不用管。");
                            }
                        });
                    }
                }).start();
            }
        };

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            tabs[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    current[0] = idx;
                    for (int k = 0; k < 3; k++) tabs[k].setTextColor(k == idx ? Ui.TEXT : Ui.MUTED);
                    load.run();
                }
            });
        }

        Button refresh = Ui.button(act, "刷新", new View.OnClickListener() {
            @Override public void onClick(View v) { load.run(); }
        });
        Button clear = Ui.button(act, "清空", new View.OnClickListener() {
            @Override public void onClick(View v) {
                final int idx = current[0];
                final String p = paths[idx];
                Ui.dialog(act)
                        .setTitle("清空 " + labels[idx] + "？")
                        .setMessage("清空前会把最后 200 行存成 " + labels[idx] + ".prev，"
                                + "原文件归零。\n\n原文件：\n" + p)
                        .setPositiveButton("清空", new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface d, int w) {
                                body.setText("清空中…");
                                new Thread(new Runnable() {
                                    @Override public void run() {
                                        final String msg = clearLog(p);
                                        act.runOnUiThread(new Runnable() {
                                            @Override public void run() { body.setText(msg); }
                                        });
                                    }
                                }).start();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        Button copyTail = Ui.button(act, "复制最后 50 行", new View.OnClickListener() {
            @Override public void onClick(View v) {
                final int idx = current[0];
                new Thread(new Runnable() {
                    @Override public void run() {
                        final String t = RootShell.tail(paths[idx], 50);
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (t.trim().isEmpty()) {
                                    android.widget.Toast.makeText(act, "这份日志是空的",
                                            android.widget.Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                android.content.ClipboardManager cm =
                                        (android.content.ClipboardManager) act.getSystemService(
                                                android.content.Context.CLIPBOARD_SERVICE);
                                if (cm != null) {
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("log", t));
                                    android.widget.Toast.makeText(act, "已复制最后 50 行",
                                            android.widget.Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                }).start();
            }
        });
        root.addView(Ui.buttonRow(act, refresh, copyTail, clear), Ui.lpW());

        tabs[0].setTextColor(Ui.TEXT);
        load.run();
        return root;
    }

    /**
     * 清空日志：先把最后 200 行存成 <文件>.prev，再把原文件截成 0。
     * 留 .prev 是为了「清完想回看上一段」，又不像整份备份那样占空间。
     */
    private static String clearLog(String path) {
        String prev = path + ".prev";
        String owner = RootShell.exec("stat -c '%u:%g' " + RootShell.q(path), 10).text();
        boolean okOwner = owner.matches("\\d+:\\d+");
        String sh = "tail -n 200 " + RootShell.q(path) + " > " + RootShell.q(prev) + " 2>/dev/null; "
                + ": > " + RootShell.q(path) + "; "
                + (okOwner ? "chown " + owner + " " + RootShell.q(path) + " " + RootShell.q(prev) + "; " : "")
                + "echo done";
        RootShell.Res r = RootShell.exec(sh, 25);
        if (!r.out.contains("done")) return "清空失败：" + r.out;
        return "已清空 " + shortPath(path) + "\n清空前最后 200 行存在：\n" + prev
                + "\n\n点「刷新」看最新内容。";
    }

    private static String shortPath(String p) {
        if (p == null) return "";
        String s = p.replace("/data/data/" + PluginRepo.PKG + "/files/runtime/rootfs", "…");
        if (s.length() > 48) s = "…" + s.substring(s.length() - 47);
        return s;
    }
}
