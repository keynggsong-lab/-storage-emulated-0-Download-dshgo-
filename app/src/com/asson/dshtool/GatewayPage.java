package com.asson.dshtool;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 网关页：端口 / 启停 / 11 个开关 / 预热提示词 / 触发词。
 * 改任何一项都会先备份再写，写完提示是否立刻重启网关。
 */
public final class GatewayPage {

    // key, 默认值, 类型, 说明, 生效条件
    private static final String[][] SWITCHES = {
            {"GW_NO_WARM", "0", "onoff", "预热注入（0=开 1=关）", "仅命中触发词时"},
            {"GW_NO_REFUSE", "0", "onoff", "拒绝文本处理（0=检测 1=直通）", ""},
            {"GW_MAX_RESAMPLE", "3", "num", "输出被掐时的重采次数", "仅被掐断时"},
            {"GW_MAX_REFRAME", "3", "num", "输入被拦时的换问法次数", "仅上游拦截时"},
            {"GW_MAX_CONT", "8", "num", "正文被掐断后的续写次数", "仅被掐断时"},
            {"GW_TARGET_LEN", "300", "num", "续写目标字数", "仅续写时"},
            {"GW_REASON_FALLBACK", "1", "onoff", "正文被掐时把思考补发", "正文为空时"},
            {"GW_MIRROR_REASON", "1", "onoff", "思考镜像，另一条补发路径", "正文为空时"},
            {"GW_HEAD_BUF", "400", "num", "首段缓冲字数，0=完全直通", "拒绝检测关闭时无意义"},
            {"GW_HEAD_CUT_MAX", "80", "num", "首段切分上限", "同上"},
            {"GW_RESHAPE", "0", "onoff", "请求改写（实测无效，建议关）", ""},
    };

    /** 常用开关：真正会动的四个。其余 7 个收进「高级」，免得一屏 11 行吓人。 */
    private static final java.util.Set<String> COMMON =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "GW_NO_REFUSE", "GW_MAX_RESAMPLE", "GW_MAX_CONT", "GW_TARGET_LEN"));
    private static boolean advOpen = false;

    private GatewayPage() {}
    public static View build(final Activity act, final DshPaths dp) {
        ScrollView sv = new ScrollView(act);
        final LinearLayout root = Ui.page(act);
        sv.addView(root);

        root.addView(Ui.text(act, "本地网关", 20, Ui.TEXT));
        Ui.space(act, root, 8);

        // 没装网关的机器（官方账号直连）这一页全是空的：先给一句结论，别让人对着空壳乱点
        final TextView gwNote = Ui.text(act, "", 11, Ui.MUTED);
        gwNote.setVisibility(View.GONE);
        root.addView(gwNote, Ui.lpW());
        new Thread(new Runnable() {
            @Override public void run() {
                final boolean has = RootShell.exists(dp.gwEnv());
                act.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (has) return;
                        gwNote.setText("这台机器没装本地网关（官方账号直连就不需要）。下面是端口和开关的占位，"
                                + "只有装了网关才有内容。");
                        gwNote.setVisibility(View.VISIBLE);
                    }
                });
            }
        }).start();
        Ui.space(act, root, 8);

        // ---------- 端口 + 启停 ----------
        final LinearLayout portBox = Ui.card(act);
        root.addView(portBox, Ui.lpW());
        Ui.space(act, root, 8);

        final Button up = Ui.button(act, "启动", null);
        final Button down = Ui.button(act, "停止", null);
        final Button restart = Ui.button(act, "重启", null);
        root.addView(Ui.buttonRow(act, up, down, restart), Ui.lpW());
        root.addView(Ui.text(act, "这一页管本地网关，可选。官方账号直连不需要它。", 11, Ui.MUTED), Ui.lpW());
        Ui.space(act, root, 14);

        // ---------- 模型网关（dsh 把模型请求发去哪） ----------
        Ui.space(act, root, 14);
        root.addView(Ui.text(act, "模型网关", 14, Ui.TEXT), Ui.lpW());
        root.addView(Ui.text(act,
                "DSH GO 把模型请求发到这个地址。走本地网关还是直连，看这里。",
                11, Ui.MUTED), Ui.lpW());
        Ui.space(act, root, 6);
        final LinearLayout modelBox = Ui.card(act);
        root.addView(modelBox, Ui.lpW());
        Ui.space(act, root, 14);

        // out 在后面才声明，这里先用数组占位，等 out 出来再填实现
        final Runnable[] refreshModel = new Runnable[1];

        // ---------- 开关 ----------
        root.addView(Ui.text(act, "开关", 14, Ui.TEXT), Ui.lpW());
        root.addView(Ui.text(act, "点右侧按钮切换，改完会问你要不要立刻重启网关", 11, Ui.MUTED), Ui.lpW());
        Ui.space(act, root, 6);
        final LinearLayout switchBox = Ui.v(act);
        root.addView(switchBox, Ui.lpW());
        Ui.space(act, root, 14);

        // ---------- 预热提示词 ----------
        root.addView(Ui.text(act, "预热提示词", 14, Ui.TEXT), Ui.lpW());
        root.addView(Ui.text(act, "命中触发词时，proxy 把这几对问答塞进上下文", 11, Ui.MUTED), Ui.lpW());
        Ui.space(act, root, 6);
        final LinearLayout warmBox = Ui.v(act);
        root.addView(warmBox, Ui.lpW());
        final List<EditText[]> warmEdits = new ArrayList<>();
        Button addPair = Ui.button(act, "添加一对", null);
        Button saveWarm = Ui.button(act, "保存预热", null);
        root.addView(Ui.buttonRow(act, addPair, saveWarm), Ui.lpW());
        Ui.space(act, root, 14);

        // ---------- 触发词 ----------
        root.addView(Ui.text(act, "触发词", 14, Ui.TEXT), Ui.lpW());
        root.addView(Ui.text(act, "用 | 分隔。命中任意一个才注入上面的预热", 11, Ui.MUTED), Ui.lpW());
        Ui.space(act, root, 6);
        final EditText trigEdit = Ui.edit(act, "免杀|绕过AV|提权|…");
        trigEdit.setMinLines(4);
        trigEdit.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        root.addView(trigEdit, Ui.lpW());
        Button saveTrig = Ui.button(act, "保存触发词", null);
        root.addView(saveTrig, Ui.lpBtnW(act));
        Ui.space(act, root, 14);

        // ---------- 输出 ----------
        root.addView(Ui.text(act, "输出（框内可上下滑）", 12, Ui.MUTED), Ui.lpW());
        final TextView out = Ui.output(act, "—");
        root.addView(Ui.scrollBox(act, out, 200));

        // out 有了，补上模型网关的刷新实现
        refreshModel[0] = new Runnable() {
            @Override public void run() {
                new Thread(new Runnable() {
                    @Override public void run() {
                        final String[] gw = Repo.modelGateway(dp);
                        final String[] reach = gw.length > 0
                                ? Repo.gatewayReach(gw[0]) : new String[]{"", "没配置"};
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                fillModel(act, modelBox, gw, reach, dp, out, refreshModel[0]);
                            }
                        });
                    }
                }).start();
            }
        };

        // ---------- 刷新 ----------
        // 7863 是 wb2api，有独立守护链，不归 gw 脚本管；按钮放在端口卡里，跟它管的端口挨着
        final Button wb2Restart = Ui.button(act, "重启 7863", null);

        final Runnable refreshPorts = new Runnable() {
            @Override public void run() {
                new Thread(new Runnable() {
                    @Override public void run() {
                        final List<String[]> ports = Repo.ports();
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                portBox.removeAllViews();
                                for (String[] p : ports) {
                                    boolean l = "1".equals(p[1]);
                                    LinearLayout row = Ui.h(act);
                                    row.addView(Ui.mono(act, p[0], 13));
                                    String name = "7863".equals(p[0]) ? "上游" : "代理";
                                    row.addView(Ui.text(act, "  " + name, 11, Ui.MUTED),
                                            new LinearLayout.LayoutParams(0,
                                                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                                    String txt = l ? ("监听中 pid=" + (p[2].isEmpty() ? "?" : p[2])) : "未监听";
                                    row.addView(Ui.text(act, txt, 12, l ? Ui.OK : Ui.BAD));
                                    portBox.addView(row, Ui.lpW());
                                }
                                Ui.divider(act, portBox);
                                Ui.space(act, portBox, 6);
                                portBox.addView(Ui.buttonRow(act, wb2Restart), Ui.lpW());
                                portBox.addView(Ui.text(act,
                                        "两个端口各管各的：上面的按钮只动 7864，重启 7863 会断一次上游连接。",
                                        10, Ui.MUTED), Ui.lpW());
                            }
                        });
                    }
                }).start();
            }
        };

        renderSwitches(act, switchBox, dp, out);
final Runnable loadWarm = new Runnable() {
            @Override public void run() {
                new Thread(new Runnable() {
                    @Override public void run() {
                        final List<String[]> pairs = Repo.readWarm(dp);
                        final String trig = Repo.readTrigger(dp);
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                warmBox.removeAllViews();
                                warmEdits.clear();
                                for (int i = 0; i < pairs.size(); i++) {
                                    String[] p = pairs.get(i);
                                    LinearLayout card = Ui.card(act);
                                    card.addView(Ui.text(act, "第 " + (i + 1) + " 对", 11, Ui.MUTED));
                                    EditText q = Ui.edit(act, "问题");
                                    q.setText(p[0]);
                                    EditText a = Ui.edit(act, "回答");
                                    a.setText(p[1]);
                                    a.setMinLines(2);
                                    card.addView(q, Ui.lpW());
                                    card.addView(a, Ui.lpW());
                                    warmBox.addView(card, Ui.lpW());
                                    Ui.space(act, warmBox, 6);
                                    warmEdits.add(new EditText[]{q, a});
                                }
                                if (pairs.isEmpty()) {
                                    warmBox.addView(Ui.text(act, "（空，预热不注入任何内容）", 11, Ui.MUTED), Ui.lpW());
                                }
                                if (!trigEdit.getText().toString().isEmpty()) {
                                    // 已经编辑过就别覆盖
                                } else {
                                    trigEdit.setText(trig);
                                }
                            }
                        });
                    }
                }).start();
            }
        };

        // ---------- 事件 ----------
        View.OnClickListener action = new View.OnClickListener() {
            @Override public void onClick(final View v) {
                up.setEnabled(false);
                down.setEnabled(false);
                restart.setEnabled(false);
                out.setText("执行中…");
                new Thread(new Runnable() {
                    @Override public void run() {
                        final RootShell.Res r;
                        if (v == up) r = Repo.gwUp(dp);
                        else if (v == down) r = Repo.gwDown(dp);
                        else r = Repo.gwRestart(dp);
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                out.setText(r.out.isEmpty() ? "完成" : r.out);
                                up.setEnabled(true);
                                down.setEnabled(true);
                                restart.setEnabled(true);
                                refreshPorts.run();
                            }
                        });
                    }
                }).start();
            }
        };
        up.setOnClickListener(action);
        down.setOnClickListener(action);
        restart.setOnClickListener(action);

        wb2Restart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                wb2Restart.setEnabled(false);
                out.setText("重启 7863 中…");
                new Thread(new Runnable() {
                    @Override public void run() {
                        final RootShell.Res r = Repo.wb2Restart(dp);
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                out.setText(r.out.isEmpty() ? "完成" : r.out);
                                wb2Restart.setEnabled(true);
                                refreshPorts.run();
                            }
                        });
                    }
                }).start();
            }
        });

        addPair.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                LinearLayout card = Ui.card(act);
                card.addView(Ui.text(act, "新增", 11, Ui.MUTED));
                EditText q = Ui.edit(act, "问题");
                EditText a = Ui.edit(act, "回答");
                a.setMinLines(2);
                card.addView(q, Ui.lpW());
                card.addView(a, Ui.lpW());
                warmBox.addView(card, Ui.lpW());
                Ui.space(act, warmBox, 6);
                warmEdits.add(new EditText[]{q, a});
            }
        });

        saveWarm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final List<String[]> pairs = new ArrayList<>();
                for (EditText[] e : warmEdits) {
                    String q = e[0].getText().toString().trim();
                    String a = e[1].getText().toString().trim();
                    if (q.isEmpty() && a.isEmpty()) continue;
                    pairs.add(new String[]{q, a});
                }
                out.setText("保存中…");
                new Thread(new Runnable() {
                    @Override public void run() {
                        final RootShell.Res r = Repo.saveWarm(dp, pairs);
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                out.setText(r.out);
                                if (r.ok) askRestart(act, dp, out, refreshPorts);
                            }
                        });
                    }
                }).start();
            }
        });

        saveTrig.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String text = trigEdit.getText().toString();
                out.setText("保存中…");
                new Thread(new Runnable() {
                    @Override public void run() {
                        final RootShell.Res r = Repo.saveTrigger(dp, text);
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                out.setText(r.out);
                                if (r.ok) askRestart(act, dp, out, refreshPorts);
                            }
                        });
                    }
                }).start();
            }
        });

        refreshPorts.run();
        renderSwitches(act, switchBox, dp, out);
        loadWarm.run();
        refreshModel[0].run();
        return sv;
    }

    // ---------------------------------------------------------------- 模型网关

    private static void fillModel(final Activity act, LinearLayout card, String[] gw, String[] reach,
                                  final DshPaths dp, final TextView out, final Runnable rerender) {
        card.removeAllViews();
        String url = gw.length > 0 ? gw[0] : "";
        String prov = gw.length > 1 && !gw[1].isEmpty() ? gw[1] : "";
        boolean ok = "可连".equals(reach[1]) || "通了但要鉴权".equals(reach[1]);

        card.addView(Ui.text(act, "dsh 现在用的地址", 11, Ui.MUTED), Ui.lpW());
        card.addView(Ui.mono(act, url.isEmpty() ? "（读不到配置）" : url, 12), Ui.lpW());
        if (!prov.isEmpty()) {
            card.addView(Ui.text(act, "provider " + prov
                    + (gw.length > 2 && !gw[2].isEmpty() ? " · " + gw[2] : ""), 11, Ui.MUTED), Ui.lpW());
        }
        card.addView(Ui.text(act, "连通性 " + reach[1]
                + (reach[0].isEmpty() ? "" : "（HTTP " + reach[0] + "）"),
                12, ok ? Ui.OK : Ui.BAD), Ui.lpW());
        Ui.space(act, card, 8);

        Button detect = Ui.button(act, "识别网关", new View.OnClickListener() {
            @Override public void onClick(View v) {
                out.setText("识别中…");
                new Thread(new Runnable() {
                    @Override public void run() {
                        String[] g = Repo.modelGateway(dp);
                        String[] r = g.length > 0 ? Repo.gatewayReach(g[0]) : new String[]{"", "没配置"};
                        StringBuilder sb = new StringBuilder();
                        if (g.length == 0) {
                            sb.append("没读到网关配置。\n这台机器的 dsh 可能是官方账号直连，不需要配网关。\n\n配置文件：")
                                    .append(dp.profilePatch());
                        } else {
                            sb.append("模型网关\n  ").append(g[0]).append('\n');
                            if (!g[1].isEmpty()) sb.append("provider  ").append(g[1]).append('\n');
                            if (g.length > 2 && !g[2].isEmpty()) sb.append("api       ").append(g[2]).append('\n');
                            if (g.length > 3 && !g[3].isEmpty()) sb.append("密钥变量  ").append(g[3]).append('\n');
                            sb.append("连通性    ").append(r[1]);
                            if (!r[0].isEmpty()) sb.append("（HTTP ").append(r[0]).append("）");
                            sb.append("\n\n配置文件：\n").append(dp.profilePatch());
                        }
                        final String t = sb.toString();
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() { out.setText(t); }
                        });
                    }
                }).start();
            }
        });

        Button change = Ui.button(act, "更换网关", new View.OnClickListener() {
            @Override public void onClick(View v) {
                askModelGateway(act, dp, url, out, rerender);
            }
        });
        card.addView(Ui.buttonRow(act, detect, change), Ui.lpW());
    }

    private static void askModelGateway(final Activity act, final DshPaths dp, String cur,
                                        final TextView out, final Runnable rerender) {
        final EditText et = Ui.edit(act, "http://127.0.0.1:7864/v1");
        et.setText(cur == null ? "" : cur);
        et.setSingleLine(true);
        Ui.dialog(act)
                .setTitle("更换模型网关")
                .setMessage("dsh 把模型请求发到这个地址。\n"
                        + "本地网关：http://127.0.0.1:7864/v1\n"
                        + "官方直连：https://api.deepseek.com/v1\n\n"
                        + "改完要重启 dsh go。")
                .setView(et)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        writeGw(act, dp, et.getText().toString().trim(), out, rerender);
                    }
                })
                .setNeutralButton("用本地网关", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        writeGw(act, dp, "http://127.0.0.1:7864/v1", out, rerender);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static void writeGw(final Activity act, final DshPaths dp, final String url,
                                final TextView out, final Runnable rerender) {
        out.setText("写入中…");
        new Thread(new Runnable() {
            @Override public void run() {
                final String r = Repo.setGatewayUrl(dp, url);
                act.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        out.setText(r);
                        rerender.run();
                    }
                });
            }
        }).start();
    }

    /** 渲染 11 个开关。常用 4 个常显，其余收进「高级」。做成静态方法避免 Runnable 自引用。 */
    private static void renderSwitches(final Activity act, final LinearLayout box,
                                       final DshPaths dp, final TextView out) {
        new Thread(new Runnable() {
            @Override public void run() {
                final Map<String, String> env = Repo.gwEnvAll(dp);
                act.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        box.removeAllViews();
                        box.addView(Ui.sectionTitle(act, "常用"), Ui.lpW());
                        for (final String[] sw : SWITCHES) {
                            if (COMMON.contains(sw[0])) addSwitchRow(act, box, dp, out, sw, env);
                        }
                        if (advOpen) {
                            box.addView(Ui.sectionTitle(act, "高级（不确定就别动）"), Ui.lpW());
                            for (final String[] sw : SWITCHES) {
                                if (!COMMON.contains(sw[0])) addSwitchRow(act, box, dp, out, sw, env);
                            }
                        }
                        int hidden = 0;
                        for (String[] sw : SWITCHES) if (!COMMON.contains(sw[0])) hidden++;
                        Button toggle = Ui.button(act, advOpen ? "收起高级" : ("高级设置（" + hidden + " 项）"), null);
                        toggle.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                advOpen = !advOpen;
                                renderSwitches(act, box, dp, out);
                            }
                        });
                        box.addView(Ui.buttonRow(act, toggle), Ui.lpW());
                    }
                });
            }
        }).start();
    }

    private static void addSwitchRow(final Activity act, final LinearLayout box, final DshPaths dp,
                                     final TextView out, final String[] sw, Map<String, String> env) {
        final String key = sw[0], dflt = sw[1], type = sw[2];
        String cur = env.containsKey(key) ? env.get(key) : dflt;

        LinearLayout row = Ui.h(act);
        row.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 4));

        LinearLayout left = Ui.v(act);
        left.addView(Ui.mono(act, key, 11));
        String hint = sw[4].isEmpty() ? sw[3] : (sw[3] + " · " + sw[4]);
        left.addView(Ui.text(act, hint, 11, Ui.MUTED));
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        final TextView valView = Ui.text(act, cur, 12, Ui.TEXT);
        valView.setPadding(0, 0, Ui.dp(act, 8), 0);
        row.addView(valView);

        String label = "onoff".equals(type) ? ("1".equals(cur) ? "关" : "开") : "改";
        final Button b = Ui.button(act, label, null);
        b.setMinWidth(Ui.dp(act, 44));
        b.setPadding(Ui.dp(act, 10), Ui.dp(act, 4), Ui.dp(act, 10), Ui.dp(act, 4));
        row.addView(b);

        final Runnable after = new Runnable() {
            @Override public void run() { renderSwitches(act, box, dp, out); }
        };

        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if ("onoff".equals(type)) {
                    String now = valView.getText().toString();
                    applyEnv(dp, key, "1".equals(now) ? "0" : "1", out, after);
                } else {
                    askNumber(act, key, valView.getText().toString(), new NumberCb() {
                        @Override public void onValue(String v2) {
                            applyEnv(dp, key, v2, out, after);
                        }
                    });
                }
            }
        });

        box.addView(row, Ui.lpW());
        Ui.divider(act, box);
    }

    private interface NumberCb { void onValue(String v); }

    private static void askNumber(Activity act, String key, String cur, final NumberCb cb) {
        final EditText et = Ui.edit(act, "数字");
        et.setText(cur);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        Ui.dialog(act)
                .setTitle(key)
                .setView(et)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        cb.onValue(et.getText().toString().trim());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static void applyEnv(final DshPaths dp, final String key, final String val,
                                 final TextView out, final Runnable after) {
        if (val == null || val.isEmpty()) return;
        out.setText("写入 " + key + "=" + val + " …");
        new Thread(new Runnable() {
            @Override public void run() {
                final RootShell.Res r = Repo.setGwEnv(dp, key, val);
                out.post(new Runnable() {
                    @Override public void run() {
                        out.setText(r.out + "\n点「重启」后生效");
                        if (after != null) after.run();
                    }
                });
            }
        }).start();
    }

    private static void askRestart(final Activity act, final DshPaths dp,
                                   final TextView out, final Runnable refreshPorts) {
        Ui.dialog(act)
                .setTitle("已保存")
                .setMessage("现在重启网关让它生效？")
                .setPositiveButton("重启", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        out.setText("重启中…");
                        new Thread(new Runnable() {
                            @Override public void run() {
                                final RootShell.Res r = Repo.gwRestart(dp);
                                act.runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        out.setText(r.out);
                                        if (refreshPorts != null) refreshPorts.run();
                                    }
                                });
                            }
                        }).start();
                    }
                })
                .setNegativeButton("稍后", null)
                .show();
    }
}
