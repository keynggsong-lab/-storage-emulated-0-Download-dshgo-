package com.asson.dshtool;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * 设置页。
 * 顺序按「最常用的在最上面」排：插件 → 路径 → 诊断。
 * 路径默认自动识别，手动输入折起来，不占屏幕也不吓人。
 */
public final class SettingsPage {

    private SettingsPage() {}

    public static View build(final Activity act, final DshPaths dp, final Runnable onSaved) {
        ScrollView sv = new ScrollView(act);
        final LinearLayout root = Ui.page(act);
        sv.addView(root);

        // ================= 1. 插件（置顶） =================
        root.addView(Ui.text(act, "dsh-purge 插件", 20, Ui.TEXT));
        root.addView(Ui.text(act, "内置插件包，只写进这台机器的 DSH GO 目录", 11, Ui.MUTED));
        Ui.space(act, root, 8);

        final LinearLayout statusBox = Ui.card(act);
        root.addView(statusBox, Ui.lpW());
        Ui.space(act, root, 8);

        final TextView out = Ui.output(act, "点上面的按钮，结果会显示在这里");
        out.setTextColor(Ui.MUTED);

        Button inject = Ui.primaryButton(act, "一键注入（装插件）", null);
        root.addView(Ui.buttonRow(act, inject), Ui.lpW());

        Button clean = Ui.button(act, "一键清洗（按版本打补丁）", null);
        Button more = Ui.button(act, "更多操作", null);
        root.addView(Ui.buttonRow(act, clean, more), Ui.lpW());

        // 更多操作（默认折叠）
        final LinearLayout moreBox = Ui.v(act);
        moreBox.setVisibility(View.GONE);
        Button force = Ui.button(act, "注入·覆盖", null);
        Button zip = Ui.button(act, "从 zip 导入", null);
        moreBox.addView(Ui.buttonRow(act, force, zip));
        Button promptsOnly = Ui.button(act, "只装提示词", null);
        Button restore = Ui.button(act, "还原提示词", null);
        moreBox.addView(Ui.buttonRow(act, promptsOnly, restore));
        Button revert = Ui.button(act, "还原补丁", null);
        Button uninstall = Ui.button(act, "卸载插件", null);
        moreBox.addView(Ui.buttonRow(act, revert, uninstall));
        Button deployGw = Ui.button(act, "部署网关脚本", null);
        Button selfTest = Ui.button(act, "插件自检", null);
        moreBox.addView(Ui.buttonRow(act, deployGw, selfTest));
        root.addView(moreBox, Ui.lpW());

        Ui.space(act, root, 6);
        // 固定高度，不传 lpW —— 传了会覆盖 scrollBox 自己设的高度，又变成撑满全屏
        root.addView(Ui.text(act, "输出（框内可上下滑）", 11, Ui.MUTED), Ui.lpW());
        root.addView(Ui.scrollBox(act, out, 150));

        // 诊断排在结果框下面：先看「装没装上」，出问题再点这三个小的
        root.addView(Ui.text(act, "诊断（出问题才用）", 11, Ui.MUTED), Ui.lpW());
        final Button diag = Ui.button(act, "诊断", null);
        final Button purgeStatus = Ui.button(act, "插件详情", null);
        final Button copyDiag = Ui.button(act, "复制", null);
        root.addView(Ui.packRow(act, diag, purgeStatus, copyDiag), Ui.lpW());

        // ================= 2. 路径（自动识别，默认收起） =================
        Ui.space(act, root, 18);

        final LinearLayout pathSection = Ui.v(act);
        pathSection.setVisibility(View.GONE);

        final TextView pathArrow = Ui.text(act, "▸", 12, Ui.PRIMARY);
        pathArrow.setPadding(0, 0, Ui.dp(act, 6), 0);
        final LinearLayout pathHead = Ui.h(act);
        pathHead.addView(pathArrow);
        pathHead.addView(Ui.text(act, "路径与 profile", 14, Ui.TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView pathSummary = Ui.text(act, "自动识别", 11, Ui.MUTED);
        pathHead.addView(pathSummary);
        pathHead.setClickable(true);
        root.addView(pathHead, Ui.lpW());

        final LinearLayout pathBox = Ui.card(act);
        pathSection.addView(pathBox, Ui.lpW());
        Ui.space(act, pathSection, 8);

        Button redetect = Ui.button(act, "重新探测", null);
        Button manual = Ui.button(act, "手动指定", null);
        pathSection.addView(Ui.buttonRow(act, redetect, manual), Ui.lpW());

        // DSH GO 换名重装、或者机器上有两个 fork 时，这里手动选它认哪个
        final Button switchPkg = Ui.button(act, "切换 DSH GO 包名", null);
        pathSection.addView(Ui.buttonRow(act, switchPkg), Ui.lpW());

        // 一台机器上 profile 常有好几份（web / headless / dsh-app-safe），选错就读别人的配置
        final Button switchProf = Ui.button(act, "切换 profile", null);
        pathSection.addView(Ui.buttonRow(act, switchProf), Ui.lpW());

        // 手动指定（默认折叠）
        final LinearLayout manualBox = Ui.v(act);
        manualBox.setVisibility(View.GONE);
        final EditText wb2 = field(act, manualBox, "网关（上游）目录", dp.wb2apiDir());
        final EditText guard = field(act, manualBox, "兜底目录", dp.guardDir());
        final EditText gw = field(act, manualBox, "gw 目录", dp.gwDir());
        final EditText home = field(act, manualBox, "DSH_HOME", dp.dshHome());
        final EditText prof = field(act, manualBox, "profile 名（一般 web）", dp.profileName());
        Button savePath = Ui.button(act, "保存路径", null);
        manualBox.addView(Ui.buttonRow(act, savePath));
        pathSection.addView(manualBox, Ui.lpW());
        root.addView(pathSection, Ui.lpW());

        pathHead.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean show = pathSection.getVisibility() != View.VISIBLE;
                pathSection.setVisibility(show ? View.VISIBLE : View.GONE);
                pathArrow.setText(show ? "▾" : "▸");
            }
        });

        // ================= 3. 诊断 =================
        Ui.space(act, root, 18);

        // ================= 行为 =================
        final Runnable[] refresh = new Runnable[1];
        refresh[0] = new Runnable() {
            @Override public void run() {
                new Thread(new Runnable() {
                    @Override public void run() {
                        final String[][] s = PluginRepo.summary(dp);
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                statusBox.removeAllViews();
                                for (String[] kv : s) {
                                    int color = "1".equals(kv[2]) ? Ui.OK
                                            : ("2".equals(kv[2]) ? Ui.TEXT : Ui.BAD);
                                    statusBox.addView(Ui.kv(act, kv[0], kv[1], color));
                                }
                            }
                        });
                    }
                }).start();
            }
        };

        final Runnable[] refreshPaths = new Runnable[1];
        refreshPaths[0] = new Runnable() {
            @Override public void run() {
                new Thread(new Runnable() {
                    @Override public void run() {
                        final boolean homeOk = RootShell.exists(dp.dshHome());
                        final boolean profOk = RootShell.exists(dp.profileDir());
                        final boolean gwOk = RootShell.exists(dp.gwDir());
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                pathSummary.setText(PluginRepo.PKG + " · " + dp.profileName());
                                pathBox.removeAllViews();
                                pathBox.addView(Ui.kv(act, "DSH GO 包名",
                                        PluginRepo.PKG + (PluginRepo.PKGS.size() > 1
                                                ? "（共 " + PluginRepo.PKGS.size() + " 个候选）" : ""),
                                        Ui.TEXT));
                                pathBox.addView(Ui.kv(act, "DSH_HOME", shortPath(dp.dshHome()),
                                        homeOk ? Ui.OK : Ui.BAD));
                                pathBox.addView(Ui.kv(act, "profile",
                                        dp.profileName() + (profOk ? "" : "（目录不存在）"),
                                        profOk ? Ui.OK : Ui.MUTED));
                                pathBox.addView(Ui.kv(act, "网关目录", shortPath(dp.gwDir()),
                                        gwOk ? Ui.OK : Ui.MUTED));
                            }
                        });
                    }
                }).start();
            }
        };

        refresh[0].run();
        refreshPaths[0].run();

        // 选 DSH GO 认哪个包名：换名重装、或者同时装了两个 fork 时用
        switchPkg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(final View v) {
                Ui.enable((Button) v, false);
                new Thread(new Runnable() {
                    @Override public void run() {
                        final java.util.List<String> found = Repo.detectDshPkgs();
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                Ui.enable((Button) v, true);
                                if (found.isEmpty()) {
                                    Ui.dialog(act)
                                            .setTitle("没探到 DSH GO")
                                            .setMessage("这台机器上没有找到私有目录里带 rootfs 的 DSH GO。\n\n"
                                                    + "先装好 DSH GO、打开跑一次，再回来点「重新探测」。\n\n"
                                                    + "现在认的包名：" + PluginRepo.PKG)
                                            .setPositiveButton("知道了", null)
                                            .show();
                                    return;
                                }
                                String[] items = new String[found.size()];
                                for (int i = 0; i < found.size(); i++) {
                                    items[i] = found.get(i)
                                            + (found.get(i).equals(PluginRepo.PKG) ? "（当前）" : "");
                                }
                                Ui.dialog(act)
                                        .setTitle("选 DSH GO 包名")
                                        .setItems(items, new android.content.DialogInterface.OnClickListener() {
                                            @Override public void onClick(android.content.DialogInterface d, int which) {
                                                String p = found.get(which);
                                                PluginRepo.PKG = p;
                                                PluginRepo.PKGS.clear();
                                                PluginRepo.PKGS.addAll(found);
                                                dp.setPkgOverride(p);
                                                // 包名一换，DSH_HOME 也得跟着换到新包名下
                                                String home = Repo.detectDshHomeFor(p);
                                                if (home != null) {
                                                    dp.setDshHome(home);
                                                    String prof = Repo.detectProfile(dp);
                                                    if (!prof.isEmpty()) dp.setProfileName(prof);
                                                }
                                                refreshPaths[0].run();
                                                refresh[0].run();
                                                onSaved.run();
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

        // 选 profile：选完锁定，启动时不再被自动探测覆盖
        switchProf.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(final View v) {
                Ui.enable((Button) v, false);
                new Thread(new Runnable() {
                    @Override public void run() {
                        final java.util.List<String> found = Repo.detectProfiles(dp);
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                Ui.enable((Button) v, true);
                                if (found.isEmpty()) {
                                    Ui.dialog(act)
                                            .setTitle("没探到 profile")
                                            .setMessage("DSH_HOME/profiles 下没找到任何 profile 目录。\n\n"
                                                    + "当前用的：" + dp.profileName())
                                            .setPositiveButton("知道了", null)
                                            .show();
                                    return;
                                }
                                String[] items = new String[found.size()];
                                for (int i = 0; i < found.size(); i++) {
                                    items[i] = found.get(i)
                                            + (found.get(i).equals(dp.profileName()) ? "（当前）" : "");
                                }
                                Ui.dialog(act)
                                        .setTitle("选 profile（手机一般用 web）")
                                        .setItems(items, new android.content.DialogInterface.OnClickListener() {
                                            @Override public void onClick(android.content.DialogInterface d, int which) {
                                                dp.setProfileName(found.get(which));
                                                dp.setProfileLocked(true);
                                                refreshPaths[0].run();
                                                refresh[0].run();
                                                onSaved.run();
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

        more.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean show = moreBox.getVisibility() != View.VISIBLE;
                moreBox.setVisibility(show ? View.VISIBLE : View.GONE);
                ((Button) v).setText(show ? "收起" : "更多操作");
            }
        });

        manual.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean show = manualBox.getVisibility() != View.VISIBLE;
                manualBox.setVisibility(show ? View.VISIBLE : View.GONE);
                ((Button) v).setText(show ? "收起" : "手动指定");
            }
        });

        inject.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!RootShell.hasRoot()) {
                    out.setText("没有 root，插件注入用不了。\n先在 root 管理器里给本 App 授权，再回来点。");
                    return;
                }
                final String[] c = PluginRepo.compat(dp);
                if ("0".equals(c[2])) {
                    Ui.dialog(act)
                            .setTitle("宿主版本不匹配")
                            .setMessage("这台机器宿主是 " + c[0] + "，" + c[1] + "。\n\n"
                                    + "「只装提示词」：安全。只写 AGENTS.md / prompt-inject.md，"
                                    + "插件不注册，注入照样生效。\n\n"
                                    + "「仍然完整注入」：装插件。宿主版本不同，补丁可能大面积不命中，"
                                    + "甚至影响 dsh 启动。")
                            .setPositiveButton("只装提示词", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) {
                                    run(act, out, "注入提示词中…", new Job() {
                                        @Override public String run() { return PluginRepo.installPrompts(act, dp, false); }
                                        @Override public void after() { refresh[0].run(); }
                                    });
                                }
                            })
                            .setNeutralButton("仍然完整注入", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) {
                                    run(act, out, "注入中…", new Job() {
                                        @Override public String run() { return PluginRepo.install(act, dp, false); }
                                        @Override public void after() { refresh[0].run(); }
                                    });
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    return;
                }
                run(act, out, "注入中…", new Job() {
                    @Override public String run() { return PluginRepo.install(act, dp, false); }
                    @Override public void after() { refresh[0].run(); }
                });
            }
        });

        promptsOnly.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!RootShell.hasRoot()) {
                    out.setText("没有 root，写不进 dsh 目录。先在 root 管理器里授权。");
                    return;
                }
                run(act, out, "注入提示词中…", new Job() {
                    @Override public String run() { return PluginRepo.installPrompts(act, dp, false); }
                    @Override public void after() { refresh[0].run(); }
                });
            }
        });

        deployGw.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                confirm(act, "部署网关脚本",
                        "把内置的 gw-up.sh / gw-down.sh 写到网关目录，原文件先备份。\n\n"
                                + "网关启停靠这两个脚本，缺了或坏了就启动不了。",
                        "部署", new Job() {
                            @Override public String run() { return PluginRepo.deployGw(act, dp); }
                            @Override public void after() { }
                        }, out);
            }
        });

        clean.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!RootShell.hasRoot()) {
                    out.setText("没有 root，清洗用不了。先在 root 管理器里授权。");
                    return;
                }
                confirm(act, "一键清洗",
                        "按当前宿主版本自动打补丁：能匹配的补丁打上，匹配不上的跳过。\n\n"
                                + "会先备份宿主文件，随时可以用「还原补丁」回滚。\n"
                                + "大概 10-30 秒，完成后要重启 dsh go。",
                        "开始清洗", new Job() {
                            @Override public String run() { return PluginRepo.applyPatches(dp); }
                            @Override public void after() { refresh[0].run(); }
                        }, out);
            }
        });

        revert.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                confirm(act, "还原补丁",
                        "把打过的补丁全部回滚到宿主原样（用插件自己的备份还原）。",
                        "还原", new Job() {
                            @Override public String run() { return PluginRepo.revertPatches(dp); }
                            @Override public void after() { refresh[0].run(); }
                        }, out);
            }
        });

        uninstall.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                confirm(act, "卸载插件",
                        "删掉插件本体和 profile 里的副本，并从 package.json 摘掉 dsh-purge。\n\n"
                                + "提示词只还原本 App 打过的备份，你自己的 .bak-MR-* 不会动。",
                        "卸载", new Job() {
                            @Override public String run() { return PluginRepo.clean(dp); }
                            @Override public void after() { refresh[0].run(); }
                        }, out);
            }
        });

        force.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                confirm(act, "覆盖注入",
                        "用内置包里的提示词覆盖当前 AGENTS.md / prompt-inject.md / personaPrefix.txt"
                                + "（先备份成 .bak-DSHGO-<时间>）。确定？",
                        "覆盖", new Job() {
                            @Override public String run() { return PluginRepo.install(act, dp, true); }
                            @Override public void after() { refresh[0].run(); }
                        }, out);
            }
        });

        zip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!(act instanceof MainActivity)) {
                    out.setText("当前宿主不支持选文件");
                    return;
                }
                ((MainActivity) act).pickFile(new MainActivity.FilePick() {
                    @Override public void onPicked(android.net.Uri uri) {
                        run(act, out, "导入中…（只换插件本体，提示词不动）", new Job() {
                            @Override public String run() { return PluginRepo.importZip(act, dp, uri); }
                            @Override public void after() { refresh[0].run(); }
                        });
                    }
                });
            }
        });

        restore.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                run(act, out, "还原中…", new Job() {
                    @Override public String run() { return PluginRepo.restorePrompts(dp); }
                    @Override public void after() { refresh[0].run(); }
                });
            }
        });

        selfTest.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                run(act, out, "自检中…", new Job() {
                    @Override public String run() { return PluginRepo.status(dp); }
                    @Override public void after() { }
                });
            }
        });

        redetect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                out.setText("探测中…");
                new Thread(new Runnable() {
                    @Override public void run() {
                        List<String> homes = Repo.detectDshHome();
                        String pick = null;
                        for (String h : homes) {
                            if (h.startsWith("/sdcard")) continue;
                            if (RootShell.exists(h + "/profiles")) { pick = h; break; }
                        }
                        if (pick == null) {
                            for (String h : homes) {
                                if (!h.startsWith("/sdcard")) { pick = h; break; }
                            }
                        }
                        StringBuilder sb = new StringBuilder();
                        if (pick == null) {
                            sb.append("没探测到 dsh go 的 .dsh 目录\n先打开一次 dsh go，让它把目录建出来");
                        } else {
                            dp.setDshHome(pick);
                            String p = Repo.detectProfile(dp);
                            if (!p.isEmpty()) dp.setProfileName(p);
                            sb.append("已切换：\n").append(pick);
                            sb.append("\nprofile：").append(dp.profileName());
                        }
                        final String t = sb.toString();
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                out.setText(t);
                                refresh[0].run();
                                refreshPaths[0].run();
                            }
                        });
                    }
                }).start();
            }
        });

        savePath.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dp.setWb2apiDir(wb2.getText().toString().trim());
                dp.setGuardDir(guard.getText().toString().trim());
                dp.setGwDir(gw.getText().toString().trim());
                dp.setDshHome(home.getText().toString().trim());
                dp.setProfileName(prof.getText().toString().trim());
                out.setText("路径已保存");
                refresh[0].run();
                refreshPaths[0].run();
                onSaved.run();
            }
        });

        diag.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                run(act, out, "诊断中…", new Job() {
                    @Override public String run() {
                        return buildDiag(dp);
                    }
                    @Override public void after() { }
                });
            }
        });

        // 诊断原文一键抄走，手机上截屏发给别人太费劲
        copyDiag.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                CharSequence t = out.getText();
                if (t == null || t.length() == 0) return;
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) act.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("diagnose", t));
                    android.widget.Toast.makeText(act, "诊断结果已复制", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });

        purgeStatus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                run(act, out, "读取中…", new Job() {
                    @Override public String run() {
                        String t = Repo.dshPurgeStatus(dp);
                        return t.isEmpty() ? "（无输出）" : t;
                    }
                    @Override public void after() { }
                });
            }
        });

        return sv;
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 诊断：先三行结论（root / 目录 / 插件），再附原始回显。
     * 结论句用 ✓ / ✗ / · 开头，Ui.colorize 会自动上色 —— 用户先看结论，要发人再抄原文。
     */
    private static String buildDiag(DshPaths dp) {
        String id = RootShell.exec("id", 10).out.trim();
        boolean rootOk = id.contains("uid=0");

        java.util.List<String[]> files = Repo.allFiles(dp);
        int have = 0;
        for (String[] f : files) if (!"-1".equals(f[2])) have++;
        boolean homeOk = RootShell.exists(dp.dshHome());

        boolean plugOk = RootShell.exists(dp.dshHome() + "/plugins/" + PluginRepo.NAME);
        String[] compat = PluginRepo.compat(dp);
        String ver = compat.length > 0 ? compat[0] : "";
        String level = compat.length > 2 ? compat[2] : "0";

        StringBuilder sb = new StringBuilder();
        sb.append(rootOk ? "✓ root：已授权"
                : "✗ root：没拿到 —— 到 KernelSU / Magisk 里授权后重进").append('\n');
        if (homeOk) {
            sb.append("✓ 目录：DSH_HOME 在 · 关键文件 ")
              .append(have).append('/').append(files.size()).append('\n');
        } else {
            sb.append("✗ 目录：DSH_HOME 不存在 —— 去上面「路径 · 手动指定」填").append('\n');
        }
        if (plugOk) {
            sb.append("✓ 插件：已装");
            if (!ver.isEmpty() && !"未探测到".equals(ver)) {
                sb.append(" · 宿主 ").append(ver)
                  .append("2".equals(level) ? "（匹配）"
                          : ("1".equals(level) ? "（同版本线）" : "（不匹配，建议只装提示词）"));
            }
        } else {
            sb.append("· 插件：还没装 —— 点上面「一键注入」");
        }
        sb.append('\n');

        sb.append("\n—— 原始回显 ——\n");
        sb.append("$ id\n").append(id.isEmpty() ? "(无输出)" : id).append('\n');
        sb.append("$ getenforce\n").append(RootShell.exec("getenforce", 8).out.trim()).append('\n');
        sb.append("$ ls -ld DSH_HOME\n")
          .append(RootShell.exec("ls -ld " + RootShell.q(dp.dshHome()) + " 2>&1", 10).out.trim()).append('\n');
        sb.append("$ ls profiles\n")
          .append(RootShell.exec("ls -1 " + RootShell.q(dp.dshHome() + "/profiles")
                  + " 2>&1 | head -10", 10).out.trim()).append('\n');
        sb.append("包名：").append(PluginRepo.PKG).append('\n');
        sb.append("宿主：").append(ver.isEmpty() ? "读不到" : ver).append('\n');
        return sb.toString();
    }

    private interface Job {
        String run();
        void after();
    }

    private static void run(final Activity act, final TextView out, final String busy, final Job job) {
        out.setTextColor(Ui.OUT_TEXT);
        out.setText(busy);
        new Thread(new Runnable() {
            @Override public void run() {
                final String t = job.run();
                act.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        out.setTextColor(Ui.OUT_TEXT);
                        out.setText(t == null || t.isEmpty() ? "完成" : Ui.colorize(t));
                        job.after();
                    }
                });
            }
        }).start();
    }

    private static void confirm(final Activity act, String title, String msg, String ok,
                                final Job job, final TextView out) {
        Ui.dialog(act)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(ok, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        run(act, out, "执行中…", job);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static String shortPath(String p) {
        if (p == null) return "";
        String s = p.replace("/data/data/" + PluginRepo.PKG + "/files/runtime/rootfs", "…");
        return s.length() > 46 ? "…" + s.substring(s.length() - 45) : s;
    }

    private static EditText field(Activity act, LinearLayout parent, String label, String value) {
        parent.addView(Ui.text(act, label, 11, Ui.MUTED), Ui.lpW());
        EditText e = Ui.edit(act, label);
        e.setText(value);
        e.setSingleLine(true);
        parent.addView(e, Ui.lpW());
        Ui.space(act, parent, 6);
        return e;
    }
}
