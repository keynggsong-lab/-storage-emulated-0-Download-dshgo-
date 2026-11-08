package com.asson.dshtool;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

/**
 * 提示词页。
 *
 * 上半部分：提示词方案（方案 1/2/3）—— 存快照 + 一键替换，这是「随时换回某一份」的入口。
 * 下半部分：单个文件，默认折叠。九个文件全展开时一屏塞不下，找东西全靠滑，
 * 所以标题行只留 名字 + 大小 + 箭头，点一下才展开路径和四个动作（同时只开一个）。
 */
public final class PromptPage {

    private PromptPage() {}

    public static View build(final Activity act, final DshPaths dp) {
        ScrollView sv = new ScrollView(act);
        final LinearLayout root = Ui.page(act);
        sv.addView(root);

        LinearLayout head = Ui.h(act);
        head.addView(Ui.text(act, "提示词与配置", 20, Ui.TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button backupAll = Ui.button(act, "全部备份", null);
        head.addView(backupAll);
        root.addView(head, Ui.lpW());

        final TextView tip = Ui.text(act,
                "点标题展开；方案可改名，也能查看里面存了什么", 11, Ui.MUTED);
        tip.setPadding(0, Ui.dp(act, 4), 0, 0);
        root.addView(tip, Ui.lpW());

        // ------------------------------------------------ 方案区
        root.addView(Ui.sectionTitle(act, "提示词方案 · 一键本地替换"), Ui.lpW());
        TextView note = Ui.text(act,
                "存为方案 = 存一份当前提示词；一键替换 = 写回全部层，写前自动备份。\n"
                        + "只存提示词，不动网关和模型配置。",
                11, Ui.MUTED);
        root.addView(note, Ui.lpW());
        Ui.space(act, root, 6);

        final LinearLayout slotBox = Ui.v(act);
        root.addView(slotBox, Ui.lpW());

        // ------------------------------------------------ 文件区
        root.addView(Ui.sectionTitle(act, "单个文件 · 点标题展开"), Ui.lpW());
        final LinearLayout list = Ui.v(act);
        root.addView(list, Ui.lpW());

        // 手风琴状态：方案槽和文件卡共用一套，开一个就把上一个收起来，整页任何时候只有一块展开
        final LinearLayout[] openBody = new LinearLayout[1];
        final TextView[] openArrow = new TextView[1];

        // 重新加载入口。用数组自引用，是因为 reload 要传进下面的渲染函数，
        // 而渲染函数里点了动作之后要重新读一遍文件 —— 不能只重画旧数据。
        final Runnable[] self = new Runnable[1];
        final Runnable load = new Runnable() {
            @Override public void run() {
                new Thread(new Runnable() {
                    @Override public void run() {
                        final List<String[]> files = Repo.allFiles(dp);
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                renderSlots(act, dp, slotBox, tip, self[0], openBody, openArrow);
                                renderFiles(act, dp, list, files, self[0], tip, openBody, openArrow);
                            }
                        });
                    }
                }).start();
            }
        };
        self[0] = load;
        load.run();


        backupAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                say(tip, Ui.MUTED, "备份全部…");
                new Thread(new Runnable() {
                    @Override public void run() {
                        final List<String[]> files = Repo.allFiles(dp);
                        final String stamp = Repo.stamp();
                        int ok = 0, skip = 0;
                        String last = "";
                        for (String[] f : files) {
                            if (parseLong(f[2], -1) < 0) { skip++; continue; }
                            String bak = RootShell.backup(f[1], stamp);
                            if (bak != null) { ok++; last = bak; } else skip++;
                        }
                        final int o = ok, s = skip;
                        final String l = last;
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                say(tip, o > 0 ? Ui.OK : Ui.BAD,
                                        "已备份 " + o + " 个文件" + (s > 0 ? "，跳过 " + s + " 个（文件不存在）" : "")
                                                + "，后缀 .bak-MR-" + stamp
                                                + (l.isEmpty() ? "" : "\n最后一份的完整路径：\n" + l));
                            }
                        });
                    }
                }).start();
            }
        });
        return sv;
    }

    // ---------------------------------------------------------------- 方案槽

    /**
     * 提示行的防串台计数器。
     * 存方案 / 导入 / 一键替换都是异步的，每个 su 调用要排队，慢的那次可能后回来，
     * 把先完成那次的提示覆盖掉 —— 表现就是「我点了一键替换，提示却说已存」。
     * 每次动作先领一个号，回来时号不是最新的就闭嘴。
     */
    private static int tipSeq = 0;

    private static void sayIf(TextView tip, int seq, int color, String text) {
        if (seq == tipSeq) say(tip, color, text);
    }

    /** 「导入文件」的说明。放在弹窗里，选文件之前先看明白。 */
    private static final String IMPORT_HELP =
            "两种都行：\n\n"
                    + "① 单个文件（.md / .txt）\n"
                    + "   当成 prompt-inject.md，写进这个方案的全部提示词层。\n"
                    + "   只换主提示词，选一个就够。\n\n"
                    + "② 打包成 zip（一次带几个）\n"
                    + "   zip 里的文件名要对上，按名字自动归位：\n"
                    + "     prompt-inject.md  → 所有提示词层\n"
                    + "     AGENTS.md         → 工作区指令\n"
                    + "     personaPrefix.txt → 人格前缀\n"
                    + "   放子目录也行，只认文件名；其他名字会忽略并告诉你。\n\n"
                    + "导入只是存进方案，还没动设备上的文件；要生效再点「一键替换」。";

    private static void renderSlots(final Activity act, final DshPaths dp, final LinearLayout box,
                                    final TextView tip, final Runnable reload,
                                    final LinearLayout[] openBody, final TextView[] openArrow) {
        box.removeAllViews();
        final int[] slots = PromptPreset.slots(act);
        if (slots.length == 0) {
            LinearLayout empty = Ui.card(act);
            empty.addView(Ui.text(act, "还没有方案", 14, Ui.TEXT), Ui.lpW());
            Ui.space(act, empty, 4);
            empty.addView(Ui.text(act,
                    "方案是存在手机上的一份提示词快照，App 本身不带任何内容。\n"
                            + "点下面「增加方案」，再把当前这几层存进去；手上已有成稿也可以直接导入。",
                    11, Ui.MUTED), Ui.lpW());
            box.addView(empty, Ui.lpW());
            Ui.space(act, box, 6);
        }
        for (int s : slots) {
            final int slot = s;
            String[] info = PromptPreset.info(act, slot);
            final boolean saved = "1".equals(info[0]);

            LinearLayout card = Ui.card(act);
            box.addView(card, Ui.lpW());
            Ui.space(act, box, 6);

            // ---- 标题行：默认折叠，跟下面的文件卡一样只占一行
            final LinearLayout hd = Ui.h(act);
            final TextView arrow = Ui.text(act, "▸", 12, Ui.PRIMARY);
            arrow.setPadding(0, 0, Ui.dp(act, 6), 0);
            hd.addView(arrow);
            // 名字用 wrap_content，让铅笔紧跟名字后面；中间插一根弹簧把状态推到最右。
            // 名字如果吃满权重，铅笔会被顶到状态文字旁边，就不是「名字旁边」了。
            hd.addView(Ui.text(act, PromptPreset.name(act, slot), 14, Ui.TEXT));

            // 名字旁边的小铅笔：改名走这里，不单独占一行按钮。
            // 它是独立可点视图，点它只弹改名框，不会连带把整张卡展开。
            final TextView pencil = Ui.text(act, "✎", 16, Ui.PRIMARY);
            pencil.setPadding(Ui.dp(act, 8), Ui.dp(act, 2), Ui.dp(act, 8), Ui.dp(act, 2));
            pencil.setClickable(true);
            hd.addView(pencil);

            View spring = new View(act);
            hd.addView(spring, new LinearLayout.LayoutParams(0, 1, 1f));

            hd.addView(Ui.text(act,
                    saved ? (info[1] + " 个 · " + info[2]) : "空",
                    11, saved ? Ui.OK : Ui.MUTED));

            // 尾部一个删除图标：点它弹确认，确定才删，取消什么都不做
            final TextView trash = Ui.text(act, "🗑", 14, Ui.MUTED);
            trash.setPadding(Ui.dp(act, 10), Ui.dp(act, 2), Ui.dp(act, 2), Ui.dp(act, 2));
            trash.setClickable(true);
            hd.addView(trash);
            int vp = Ui.dp(act, 2);
            hd.setPadding(0, vp, 0, vp);
            hd.setClickable(true);
            card.addView(hd, Ui.lpW());

            final LinearLayout body = Ui.v(act);
            body.setVisibility(View.GONE);
            Ui.space(act, body, 4);
            card.addView(body, Ui.lpW());

            Button save = Ui.button(act, "存为方案", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    final int seq = ++tipSeq;
                    say(tip, Ui.MUTED, "正在存方案 " + slot + "…");
                    new Thread(new Runnable() {
                        @Override public void run() {
                            final String r = PromptPreset.save(act, slot, dp);
                            act.runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    if (r == null) {
                                        sayIf(tip, seq, Ui.BAD, "方案 " + slot + " 保存失败：建目录被拒");
                                        return;
                                    }
                                    String[] p = r.split("\\|");
                                    sayIf(tip, seq, Ui.OK, "方案 " + slot + " 已存 " + p[0] + " 个文件"
                                            + (p.length > 1 && !"0".equals(p[1]) ? "（" + p[1] + " 个文件当前不存在，跳过）" : "")
                                            + "\n存在 App 私有目录，不影响设备上的原文件。");
                                    reload.run();
                                }
                            });
                        }
                    }).start();
                }
            });

            Button imp = Ui.button(act, "导入文件", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (!(act instanceof MainActivity)) {
                        say(tip, Ui.BAD, "当前宿主不支持选文件");
                        return;
                    }
                    // 先讲清楚「导几个文件、叫什么名字」，再开选文件器。
                    // 之前直接弹系统选择器，用户根本不知道要挑什么、要挑几个。
                    Ui.dialog(act)
                            .setTitle("导入文件 · 怎么导")
                            .setMessage(IMPORT_HELP)
                            .setPositiveButton("选文件", new android.content.DialogInterface.OnClickListener() {
                                @Override public void onClick(android.content.DialogInterface dd, int w) {
                                    pickAndImport(act, dp, slot, tip, reload);
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                }
            });

            Button apply = Ui.primaryButton(act, "一键替换", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    final int seq = ++tipSeq;
                    say(tip, Ui.MUTED, "正在用方案 " + slot + " 替换…");
                    new Thread(new Runnable() {
                        @Override public void run() {
                            final List<String> log = PromptPreset.apply(act, slot);
                            act.runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    showResult(act, "一键替换 · 方案 " + slot, join(log));
                                    sayIf(tip, seq, Ui.OK, "方案 " + slot + " 已写回，详情见弹窗");
                                    reload.run();
                                }
                            });
                        }
                    }).start();
                }
            });
            Ui.enable(apply, saved);

            Button peek = Ui.button(act, "查看内容", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    viewSlot(act, slot, tip);
                }
            });
            Ui.enable(peek, saved);

            body.addView(Ui.buttonRow(act, save, imp, peek, apply), Ui.lpW());

            trash.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Ui.dialog(act)
                            .setTitle("删除「" + PromptPreset.name(act, slot) + "」")
                            .setMessage("只删 App 里存的这份快照，设备上的提示词文件一个都不动。")
                            .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                                @Override public void onClick(android.content.DialogInterface d, int w) {
                                    if (PromptPreset.removeSlot(act, slot)) {
                                        say(tip, Ui.OK, "已删除方案「" + PromptPreset.name(act, slot) + "」");
                                    } else {
                                        say(tip, Ui.BAD, "删除失败：目录不可写");
                                    }
                                    reload.run();
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                }
            });

            pencil.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    renameSlot(act, slot, tip, reload);
                }
            });

            hd.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean show = body.getVisibility() != View.VISIBLE;
                    if (openBody[0] != null && openBody[0] != body) {
                        openBody[0].setVisibility(View.GONE);
                        if (openArrow[0] != null) openArrow[0].setText("▸");
                    }
                    body.setVisibility(show ? View.VISIBLE : View.GONE);
                    arrow.setText(show ? "▾" : "▸");
                    openBody[0] = show ? body : null;
                    openArrow[0] = show ? arrow : null;
                }
            });
        }

        // 想存几套存几套。App 本身不带方案，这一步是「从零开始」的入口。
        Button add = Ui.button(act, "＋ 增加方案", new View.OnClickListener() {
            @Override public void onClick(View v) {
                int slot = PromptPreset.addSlot(act);
                if (slot < 0) {
                    say(tip, Ui.BAD, "建方案目录失败");
                    return;
                }
                say(tip, Ui.OK, "已增加方案 " + slot + "，给它起个名字（取消就用默认名）");
                reload.run();
                renameSlot(act, slot, tip, reload);
            }
        });
        box.addView(Ui.buttonRow(act, add), Ui.lpW());
        box.addView(Ui.text(act,
                "方案想存几套就存几套；删掉只删这份快照，不动设备上的提示词文件。",
                11, Ui.MUTED), Ui.lpW());
    }

    /** 改槽名。名字存在方案目录的 name.txt，最多 16 字。 */
    private static void renameSlot(final Activity act, final int slot, final TextView tip,
                                   final Runnable reload) {
        final EditText et = Ui.edit(act, "给这个方案起个名，比如「日常版」");
        et.setText(PromptPreset.name(act, slot));
        et.setSingleLine(true);
        int p = Ui.dp(act, 12);
        et.setPadding(p, p, p, p);

        boolean fresh = "0".equals(PromptPreset.info(act, slot)[0]);
        Ui.dialog(act)
                .setTitle(fresh ? ("给方案 " + slot + " 起个名字") : ("方案 " + slot + " 改名字"))
                .setView(et)
                .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        String n = et.getText().toString().trim();
                        if (n.isEmpty()) n = "方案 " + slot;
                        if (PromptPreset.setName(act, slot, n)) {
                            say(tip, Ui.OK, "方案 " + slot + " 已改名为「" + PromptPreset.name(act, slot) + "」");
                            reload.run();
                        } else {
                            say(tip, Ui.BAD, "改名失败：目录不可写");
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 查看方案里存了什么：先列文件清单，点哪个就把哪个整篇展开。
     *
     * 清单只给「层名 + 字节数」；详情顶上只给一句「当前已应用 / 当前未应用」，
     * 下面就是全文 —— 不列路径、不列 md5、不截前 N 行。
     */
    private static void viewSlot(final Activity act, final int slot, final TextView tip) {
        final int seq = ++tipSeq;
        say(tip, Ui.MUTED, "读取方案 " + slot + " 的内容…");
        new Thread(new Runnable() {
            @Override public void run() {
                final List<PromptPreset.Item> items = PromptPreset.load(act, slot);
                final java.util.Map<String, String> cur = PromptPreset.currentMd5(pathsOf(items));
                act.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (items.isEmpty()) {
                            Ui.dialog(act)
                                    .setTitle("方案 " + slot + " 还是空的")
                                    .setMessage("这个方案还没存过内容。\n"
                                            + "先点「存为方案」把当前提示词存进来，或者「导入文件」。")
                                    .setPositiveButton("知道了", null)
                                    .show();
                            return;
                        }
                        sayIf(tip, seq, Ui.OK, "已读取 " + items.size() + " 个文件");
                        showSlotList(act, slot, items, cur);
                    }
                });
            }
        }).start();
    }

    private static List<String> pathsOf(List<PromptPreset.Item> items) {
        List<String> l = new java.util.ArrayList<>();
        for (PromptPreset.Item it : items) l.add(it.path);
        return l;
    }

    /** 方案里这份内容跟设备上现在的是不是同一份。 */
    private static boolean applied(PromptPreset.Item it, java.util.Map<String, String> cur) {
        String now = cur.get(it.path);
        return now != null && !"MISSING".equals(now)
                && now.equalsIgnoreCase(PromptPreset.md5(it.body));
    }

    /**
     * 方案内容清单：一行一个文件，右边直接写「已应用 / 未应用」。
     * 行可点，点进去只给原文。只用一个按钮：详情里「返回」回清单，清单里「关闭」退出去。
     *
     * 关键在于「先关旧窗再开新窗」：以前是把详情直接叠在清单上面，
     * 关掉详情后新清单又叠上去，窗口越摞越多，用户要连点好几次才关得掉。
     */
    private static void showSlotList(final Activity act, final int slot,
                                     final List<PromptPreset.Item> items,
                                     final java.util.Map<String, String> cur) {
        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];
        final PromptPreset.Item[] pending = new PromptPreset.Item[1];

        LinearLayout box = Ui.v(act);
        int pad = Ui.dp(act, 6);
        box.setPadding(pad, 0, pad, 0);

        for (int i = 0; i < items.size(); i++) {
            final PromptPreset.Item it = items.get(i);
            boolean on = applied(it, cur);

            LinearLayout row = Ui.h(act);
            row.addView(Ui.text(act, it.label, 13, Ui.TEXT),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(Ui.text(act, on ? "已应用" : "未应用", 12, on ? Ui.OK : Ui.MUTED));
            int vp = Ui.dp(act, 11);
            row.setPadding(0, vp, 0, vp);
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    pending[0] = it;
                    if (holder[0] != null) holder[0].dismiss();   // 关掉清单，等它彻底消失再开详情
                }
            });
            box.addView(row, Ui.lpW());
            if (i < items.size() - 1) Ui.divider(act, box);
        }

        android.app.AlertDialog dlg = Ui.dialog(act)
                .setTitle("方案 " + slot + " · " + items.size() + " 个文件")
                .setView(box)
                .setPositiveButton("关闭", null)
                .create();
        dlg.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface d) {
                holder[0] = null;
                PromptPreset.Item next = pending[0];
                pending[0] = null;
                if (next != null) showSlotFile(act, slot, items, cur, next);
            }
        });
        holder[0] = dlg;
        dlg.show();
    }

    /**
     * 单个文件的原文。里面只有内容本身 —— 不放路径、不放状态（清单里已经写了）。
     * 只有一个「返回」：按它关掉这一层，回到清单；清单不动。
     */
    private static void showSlotFile(final Activity act, final int slot,
                                     final List<PromptPreset.Item> items,
                                     final java.util.Map<String, String> cur,
                                     final PromptPreset.Item it) {
        TextView body = Ui.output(act, it.body == null ? "" : it.body);

        LinearLayout box = Ui.v(act);
        int pad = Ui.dp(act, 4);
        box.setPadding(pad, 0, pad, 0);
        // 不包 lpW()：那会覆盖 scrollBox 自己定的固定高度，框又变撑满整屏
        box.addView(Ui.scrollBox(act, body, 460, false));

        android.app.AlertDialog dlg = Ui.dialog(act)
                .setTitle(it.label)
                .setView(box)
                .setPositiveButton("返回", null)
                .create();
        dlg.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface d) {
                showSlotList(act, slot, items, cur);
            }
        });
        dlg.show();
    }

    private static int bytes(String s) {
        try {
            return s.getBytes("UTF-8").length;
        } catch (Exception e) {
            return s.length();
        }
    }

    // ---------------------------------------------------------------- 工作区 AGENTS.md

    /**
     * 工作区 AGENTS.md：路径每个用户都不一样，且要先建过工作区才有，所以不写死 —— 用户加几条认几条。
     * 版面压到最紧：标题行右边直接放「扫描 / 添加」两个小按钮，下面一张卡只列路径。
     */
    private static void addWorkspaceCard(final Activity act, final DshPaths dp,
                                         final LinearLayout root, final Runnable reload) {
        Ui.space(act, root, 6);

        LinearLayout head = Ui.h(act);
        head.addView(Ui.text(act, "工作区 AGENTS.md", 13, Ui.PRIMARY),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button scan = Ui.button(act, "扫描", null);
        Button add = Ui.button(act, "选文件", null);
        head.addView(scan);
        head.addView(add);
        root.addView(head, Ui.lpW());

        root.addView(Ui.text(act,
                "全局那份在 DSH_HOME，任何工作区都生效；这里加的是工作区那份，只在那一个工作区生效。",
                11, Ui.MUTED), Ui.lpW());
        Ui.space(act, root, 6);

        final LinearLayout box = Ui.card(act);
        root.addView(box, Ui.lpW());

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = new Runnable() {
            @Override public void run() {
                box.removeAllViews();
                java.util.List<String> list = dp.workspaceAgents();
                if (list.isEmpty()) {
                    box.addView(Ui.text(act,
                            "还没加。工作区路径各不相同，先在 DSH GO 里建过工作区再来加。",
                            11, Ui.MUTED), Ui.lpW());
                    return;
                }
                for (final String p : list) {
                    LinearLayout row = Ui.h(act);
                    row.addView(Ui.mono(act, p, 11),
                            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    TextView del = Ui.text(act, "🗑", 13, Ui.MUTED);
                    del.setPadding(Ui.dp(act, 8), Ui.dp(act, 1), Ui.dp(act, 1), Ui.dp(act, 1));
                    del.setClickable(true);
                    del.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            dp.removeWorkspaceAgents(p);
                            refresh[0].run();
                            reload.run();
                        }
                    });
                    row.addView(del);
                    box.addView(row, Ui.lpW());
                }
            }
        };
        refresh[0].run();

        scan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(final View v) {
                Ui.enable((Button) v, false);
                new Thread(new Runnable() {
                    @Override public void run() {
                        final java.util.List<String> found = Repo.detectWorkspaceAgents();
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                Ui.enable((Button) v, true);
                                final java.util.List<String> pool = new java.util.ArrayList<>();
                                for (String f : found) {
                                    if (!dp.workspaceAgents().contains(f)) pool.add(f);
                                }
                                if (pool.isEmpty()) {
                                    Ui.dialog(act)
                                            .setTitle("没扫到别的 AGENTS.md")
                                            .setMessage("共享存储里没找到还没加过的 AGENTS.md。\n"
                                                    + "先在 DSH GO 里建好工作区再来扫，或者用「添加」填路径。")
                                            .setPositiveButton("知道了", null)
                                            .show();
                                    return;
                                }
                                Ui.dialog(act)
                                        .setTitle("选择要加的 AGENTS.md")
                                        .setItems(pool.toArray(new String[0]),
                                                new android.content.DialogInterface.OnClickListener() {
                                                    @Override public void onClick(android.content.DialogInterface d, int which) {
                                                        dp.addWorkspaceAgents(pool.get(which));
                                                        refresh[0].run();
                                                        reload.run();
                                                    }
                                                })
                                        .setNegativeButton("取消", null)
                                        .show();
                            }
                        });
                    }
                }).start();
            }
        });

        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!(act instanceof MainActivity)) return;
                ((MainActivity) act).pickFile(new MainActivity.FilePick() {
                    @Override public void onPicked(Uri uri) {
                        final String path = realPath(act, uri);
                        if (path == null) {
                            Ui.dialog(act)
                                    .setTitle("拿不到真实路径")
                                    .setMessage("这个 App 只给了一个临时句柄，读不到文件在共享存储里的实际位置。\n\n"
                                            + "换「MT 管理器」再选一次，或者用「扫描」自动找。")
                                    .setPositiveButton("知道了", null)
                                    .show();
                            return;
                        }
                        new Thread(new Runnable() {
                            @Override public void run() {
                                final boolean ok = RootShell.exists(path);
                                act.runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        if (!ok) {
                                            android.widget.Toast.makeText(act, "这个路径下没有文件：" + path,
                                                    android.widget.Toast.LENGTH_LONG).show();
                                            return;
                                        }
                                        dp.addWorkspaceAgents(path);
                                        android.widget.Toast.makeText(act, "已加入：" + path,
                                                android.widget.Toast.LENGTH_SHORT).show();
                                        refresh[0].run();
                                        reload.run();
                                    }
                                });
                            }
                        }).start();
                    }
                });
            }
        });
    }

    /**
     * 把选中的 URI 反解成共享存储里的真实路径。
     * file:// 直接取；content:// 先问 _data 列（MT / 各家管理器大多给），问不到返回 null。
     */
    private static String realPath(Activity act, Uri uri) {
        if (uri == null) return null;
        if ("file".equals(uri.getScheme())) return uri.getPath();
        android.database.Cursor c = null;
        try {
            c = act.getContentResolver().query(uri, new String[]{"_data"}, null, null, null);
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex("_data");
                if (i >= 0) {
                    String p = c.getString(i);
                    if (p != null && !p.isEmpty()) return p;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) try { c.close(); } catch (Exception ignored) { }
        }
        return null;
    }

    // ---------------------------------------------------------------- 文件卡

    private static void renderFiles(final Activity act, final DshPaths dp, final LinearLayout list,
                                    List<String[]> files, final Runnable reload, final TextView tip,
                                    final LinearLayout[] openBody, final TextView[] openArrow) {
        list.removeAllViews();

        String lastGroup = null;
        boolean wsAdded = false;
        for (final String[] f : files) {
            final long size = parseLong(f[2], -1);
            final boolean ok = size >= 0;

            String group = f.length > 5 ? f[5] : "";
            if (group != null && !group.isEmpty() && !group.equals(lastGroup)) {
                // 「本地注入」这一组收尾时，把工作区 AGENTS.md 的管理块插在它下面、
                // 紧跟同组内容，不甩到页面最底下
                if (!wsAdded && DshPaths.G_LOCAL.equals(lastGroup)) {
                    addWorkspaceCard(act, dp, list, reload);
                    wsAdded = true;
                }
                lastGroup = group;
                list.addView(Ui.sectionTitle(act, group), Ui.lpW());
            }

            LinearLayout card = Ui.card(act);
            list.addView(card, Ui.lpW());
            Ui.space(act, list, 6);

            // ---- 标题行（点击展开/收起）
            final LinearLayout hd = Ui.h(act);
            final TextView arrow = Ui.text(act, "▸", 12, Ui.PRIMARY);
            arrow.setPadding(0, 0, Ui.dp(act, 6), 0);
            hd.addView(arrow);
            hd.addView(Ui.text(act, f[0], 14, Ui.TEXT),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            hd.addView(Ui.text(act, ok ? size + " B" : "未生成", 12, ok ? Ui.OK : Ui.MUTED));
            int vp = Ui.dp(act, 2);
            hd.setPadding(0, vp, 0, vp);
            hd.setClickable(true);
            card.addView(hd, Ui.lpW());

            // ---- 展开体
            final LinearLayout body = Ui.v(act);
            body.setVisibility(View.GONE);
            body.addView(Ui.mono(act, shortPath(f[1]), 11), Ui.lpW());
            if (f.length > 4 && !f[4].isEmpty()) {
                TextView hint = Ui.text(act, f[4], 11, Ui.MUTED);
                hint.setPadding(0, Ui.dp(act, 3), 0, 0);
                body.addView(hint, Ui.lpW());
            }
            Ui.space(act, body, 6);

            Button edit = Ui.button(act, "编辑", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    openEditor(act, f[1], f[0], f.length > 4 ? f[4] : "", reload, tip);
                }
            });
            Ui.enable(edit, ok);

            Button repl = Ui.button(act, "替换", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (!(act instanceof MainActivity)) {
                        say(tip, Ui.BAD, "当前宿主不支持选文件");
                        return;
                    }
                    ((MainActivity) act).pickFile(new MainActivity.FilePick() {
                        @Override public void onPicked(Uri uri) {
                            final String body = readUri(act, uri);
                            if (body == null) { say(tip, Ui.BAD, "读取所选文件失败"); return; }
                            say(tip, Ui.MUTED, "替换中…");
                            new Thread(new Runnable() {
                                @Override public void run() {
                                    final String bak = RootShell.backup(f[1], Repo.stamp());
                                    final RootShell.Res r = RootShell.writeText(f[1], body);
                                    act.runOnUiThread(new Runnable() {
                                        @Override public void run() {
                                            if (r.ok) {
                                                say(tip, Ui.OK, "已替换 " + f[0] + "（" + body.length()
                                                        + " 字符）\n备份在：\n" + bak
                                                        + "\n原文件已被覆盖，想退回就点「还原」。");
                                            } else {
                                                say(tip, Ui.BAD, "替换失败：" + r.out);
                                            }
                                            reload.run();
                                        }
                                    });
                                }
                            }).start();
                        }
                    });
                }
            });
            Ui.enable(repl, ok);

            Button bak = Ui.button(act, "备份", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    say(tip, Ui.MUTED, "备份中…");
                    new Thread(new Runnable() {
                        @Override public void run() {
                            final String stamp = Repo.stamp();
                            final String b = RootShell.backup(f[1], stamp);
                            act.runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    if (b == null) {
                                        say(tip, Ui.BAD, f[0] + " 备份失败：文件可能不可读，或目录没写权限");
                                    } else {
                                        say(tip, Ui.OK, f[0] + " 已备份，原文件没动\n备份完整路径：\n" + b
                                                + "\n想退回就点「还原」，会取最新一份备份。");
                                    }
                                    reload.run();
                                }
                            });
                        }
                    }).start();
                }
            });
            Ui.enable(bak, ok);

            Button restore = Ui.button(act, "还原", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    say(tip, Ui.MUTED, "还原中…");
                    new Thread(new Runnable() {
                        @Override public void run() {
                            List<String> baks = RootShell.listBackups(f[1]);
                            if (baks.isEmpty()) {
                                act.runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        say(tip, Ui.BAD, f[0] + " 没有备份可还原");
                                    }
                                });
                                return;
                            }
                            int i = f[1].lastIndexOf('/');
                            String dir = i > 0 ? f[1].substring(0, i) : "/";
                            final String from = baks.get(0);
                            final boolean r = RootShell.copy(dir + "/" + from, f[1]);
                            act.runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    say(tip, r ? Ui.OK : Ui.BAD,
                                            r ? (f[0] + " 已还原自 " + from) : (f[0] + " 还原失败"));
                                    reload.run();
                                }
                            });
                        }
                    }).start();
                }
            });
            Ui.enable(restore, ok);

            body.addView(Ui.buttonRow(act, edit, repl, bak, restore), Ui.lpW());
            card.addView(body, Ui.lpW());

            hd.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean show = body.getVisibility() != View.VISIBLE;
                    if (openBody[0] != null && openBody[0] != body) {
                        openBody[0].setVisibility(View.GONE);
                        if (openArrow[0] != null) openArrow[0].setText("▸");
                    }
                    body.setVisibility(show ? View.VISIBLE : View.GONE);
                    arrow.setText(show ? "▾" : "▸");
                    openBody[0] = show ? body : null;
                    openArrow[0] = show ? arrow : null;
                }
            });
        }

        // 兜底：整页只有本地注入一组时，也把这块挂上
        if (!wsAdded) addWorkspaceCard(act, dp, list, reload);
    }

    /** 选文件 → 读字节 → 交给 PromptPreset 判单文件还是 zip。 */
    private static void pickAndImport(final Activity act, final DshPaths dp, final int slot,
                                      final TextView tip, final Runnable reload) {
        final int seq = ++tipSeq;
        ((MainActivity) act).pickFile(new MainActivity.FilePick() {
            @Override public void onPicked(Uri uri) {
                final byte[] data = readUriBytes(act, uri);
                if (data == null) { sayIf(tip, seq, Ui.BAD, "读取所选文件失败"); return; }
                say(tip, Ui.MUTED, "导入中…（" + Math.max(1, data.length / 1024) + " KB）");
                new Thread(new Runnable() {
                    @Override public void run() {
                        final List<String> log = PromptPreset.importAny(act, slot, dp, data);
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                showResult(act, "导入到方案 " + slot, join(log));
                                sayIf(tip, seq, Ui.OK, "已导入到方案 " + slot + "，点「一键替换」才写回设备");
                                reload.run();
                            }
                        });
                    }
                }).start();
            }
        });
    }

    // ---------------------------------------------------------------- 编辑

    /**
     * 编辑器。走系统原生弹窗（Ui.dialog），不再自绘黑底全屏 ——
     * 原生弹窗自带标题、圆角、右下角文字按钮，跟 Android 16 系统里弹出来的一致。
     * 三个动作换成 setPositiveButton / setNeutralButton / setNegativeButton，
     * 其中「对比备份」点完不能关窗，所以 show() 之后把它的监听重新绑一次（原生按钮默认点了就 dismiss）。
     */
    private static void openEditor(final Activity act, final String path, final String label,
                                   final String hint, final Runnable onSaved, final TextView tip) {
        int p = Ui.dp(act, 12);

        LinearLayout col = Ui.v(act);
        col.setPadding(p, p / 2, p, 0);
        col.addView(Ui.mono(act, path, 11), Ui.lpW());
        if (hint != null && !hint.isEmpty()) {
            col.addView(Ui.text(act, hint, 11, Ui.MUTED), Ui.lpW());
        }
        Ui.space(act, col, 6);

        final EditText et = Ui.edit(act, "");
        et.setSingleLine(false);
        et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        et.setPadding(p, p, p, p);

        ScrollView sv = Ui.innerScroll(act);
        sv.addView(et, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // 弹窗里没有 weight 可用，直接给固定高度；460dp 大约能看 25 行，够改一段
        col.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 460)));

        final TextView msg = Ui.text(act, "", 11, Ui.MUTED);
        msg.setPadding(0, Ui.dp(act, 6), 0, 0);
        col.addView(msg, Ui.lpW());

        final android.app.AlertDialog d = Ui.dialog(act)
                .setTitle(label)
                .setView(col)
                .setPositiveButton("保存", null)
                .setNeutralButton("对比备份", null)
                .setNegativeButton("关闭", null)
                .create();
        d.show();

        if (d.getWindow() != null) {
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String body = et.getText().toString();
                msg.setTextColor(Ui.MUTED);
                msg.setText("保存中…");
                new Thread(new Runnable() {
                    @Override public void run() {
                        final String bak = RootShell.backup(path, Repo.stamp());
                        final RootShell.Res r = RootShell.writeText(path, body);
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (r.ok) {
                                    msg.setTextColor(Ui.OK);
                                    msg.setText("已保存（" + body.length() + " 字符）· 备份 " + shortName(bak));
                                    say(tip, Ui.OK, label + " 已保存，备份 " + shortName(bak));
                                    onSaved.run();
                                    d.dismiss();
                                } else {
                                    msg.setTextColor(Ui.BAD);
                                    msg.setText("失败：" + r.out.trim());
                                }
                            }
                        });
                    }
                }).start();
            }
        });
        // 对比不关窗：原生按钮点了会 dismiss，这里换掉监听
        d.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showDiff(act, path, et.getText().toString(), label, tip);
            }
        });
        d.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });

        new Thread(new Runnable() {
            @Override public void run() {
                final String text = RootShell.readText(path);
                act.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (text != null) et.setText(text);
                        else msg.setText("读不到文件");
                    }
                });
            }
        }).start();
    }

    // ---------------------------------------------------------------- 对比

    /** 拿最新一份备份跟当前内容比，说清楚「哪里变了」。 */
    private static void showDiff(final Activity act, String path, String cur, String label,
                                 final TextView tip) {
        List<String> baks = RootShell.listBackups(path);
        if (baks.isEmpty()) {
            say(tip, Ui.BAD, label + " 还没有备份，没得比。先点「备份」存一份。");
            return;
        }
        int i = path.lastIndexOf('/');
        String dir = i > 0 ? path.substring(0, i) : "/";
        String bakName = baks.get(0);
        String old = RootShell.readText(dir + "/" + bakName);
        if (old == null) {
            say(tip, Ui.BAD, "读不到备份文件：" + dir + "/" + bakName);
            return;
        }
        final String body = simpleDiff(old, cur, bakName);

        ScrollView sv = new ScrollView(act);
        TextView tv = Ui.mono(act, body, 11);
        tv.setTextColor(Ui.OUT_TEXT);
        tv.setTextIsSelectable(true);
        int p = Ui.dp(act, 12);
        tv.setPadding(p, p, p, p);
        sv.addView(tv);

        Ui.dialog(act)
                .setTitle(label + " · 与备份的差异")
                .setView(sv)
                .setPositiveButton("知道了", null)
                .show();
    }

    /**
     * 简化 diff：先剥掉相同的头尾，中间那段就是改动区。
     * 不做完整 LCS —— 提示词动辄上千行，LCS 表会吃掉几 MB；头尾剥离对「追加/中间插入」
     * 这类最常见改法足够准，而且不会像按行号硬比那样整段误报。
     */
    private static String simpleDiff(String oldText, String curText, String bakName) {
        String[] a = oldText.split("\n", -1);
        String[] b = curText.split("\n", -1);

        int min = Math.min(a.length, b.length);
        int pre = 0;
        while (pre < min && a[pre].equals(b[pre])) pre++;
        int suf = 0;
        while (suf < min - pre && a[a.length - 1 - suf].equals(b[b.length - 1 - suf])) suf++;

        int aEnd = a.length - suf, bEnd = b.length - suf;
        if (pre >= aEnd && pre >= bEnd) {
            return "和备份「" + bakName + "」完全一样，没有改动。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("对比对象：").append(bakName).append('\n');
        sb.append("备份 ").append(a.length).append(" 行 → 当前 ").append(b.length)
                .append(" 行（").append(b.length - a.length >= 0 ? "+" : "")
                .append(b.length - a.length).append("）\n");
        sb.append("前 ").append(pre).append(" 行相同");
        if (suf > 0) sb.append("，后 ").append(suf).append(" 行相同");
        sb.append("\n\n");

        int showA = Math.min(aEnd - pre, 20);
        int showB = Math.min(bEnd - pre, 20);
        if (aEnd > pre) {
            sb.append("备份里第 ").append(pre + 1).append("-").append(aEnd).append(" 行：\n");
            for (int k = 0; k < showA; k++) {
                sb.append("- ").append(clip(a[pre + k])).append('\n');
            }
            if (aEnd - pre > showA) sb.append("…还有 ").append(aEnd - pre - showA).append(" 行\n");
            sb.append('\n');
        }
        if (bEnd > pre) {
            sb.append("当前第 ").append(pre + 1).append("-").append(bEnd).append(" 行：\n");
            for (int k = 0; k < showB; k++) {
                sb.append("+ ").append(clip(b[pre + k])).append('\n');
            }
            if (bEnd - pre > showB) sb.append("…还有 ").append(bEnd - pre - showB).append(" 行\n");
        }
        sb.append("\n「-」是备份里的，「+」是现在的。关掉这个窗口点「保存」才会写盘。");
        return sb.toString();
    }

    private static String clip(String s) {
        if (s == null) return "";
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    // ---------------------------------------------------------------- 工具

    /** 多行结果弹窗：框内滚动，行首 ✓/✗ 上色，跟注入结果一个样式。 */
    private static void showResult(Activity act, String title, String body) {
        TextView tv = Ui.output(act, "");
        tv.setText(Ui.colorize(body));
        // 注意：这里不能再 setTextIsSelectable(true) —— 那会把触摸从 ScrollView 手里抢走，
        // 结果就是「框里滑不动、只能滚整页」。scrollBox 内部已经强制设成 false。
        // framed=false：弹窗走原生样式，内容别再自绘描边。
        ScrollView sv = Ui.scrollBox(act, tv, 240, false);

        Ui.dialog(act)
                .setTitle(title)
                .setView(sv)
                .setPositiveButton("知道了", null)
                .show();
    }

    private static String join(List<String> ls) {
        StringBuilder sb = new StringBuilder();
        for (String s : ls) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(s);
        }
        return sb.toString();
    }

    private static void say(TextView tip, int color, String text) {
        tip.setTextColor(color);
        tip.setText(text);
    }

    private static String readUri(Activity act, Uri uri) {
        byte[] b = readUriBytes(act, uri);
        if (b == null) return null;
        try {
            return new String(b, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    /** 按字节读，zip 要原样拿才能解压，不能先转字符串。 */
    private static byte[] readUriBytes(Activity act, Uri uri) {
        try {
            InputStream in = act.getContentResolver().openInputStream(uri);
            if (in == null) return null;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** 只显示文件名，不铺一整行路径。 */
    private static String shortName(String p) {
        if (p == null) return "";
        int i = p.lastIndexOf('/');
        return i > 0 ? p.substring(i + 1) : p;
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
