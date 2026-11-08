package com.asson.dshtool;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * dsh-purge 插件的一键注入 / 清洗。
 *
 * 铁律：只动 {@link #PKG} 这一个包名的私有目录。DSH_HOME 不在这个包名下就拒绝执行，
 * 避免换机器时把别的 App 或系统目录写坏。
 *
 * 注入通道有两套，本类只负责「本地」那套（dsh-purge override + AGENTS.md）：
 *   - 有网关：网关自己的 SLOT / system 是主通道，本插件是叠加层
 *   - 无网关（新手机走官方账号）：本地这套就是唯一通道，所以必须能独立装起来
 */
public final class PluginRepo {

    /**
     * 只认这一个包名。
     *
     * 不是 final：启动时会用 Repo.detectDshPkg() 探一遍，谁家把 DSH GO 换名重签了，
     * 这里就跟着换 —— 否则范围校验（inScope）会把所有写操作全拒掉。
     * 探不到就保持默认值，行为跟以前一致。
     */
    public static String PKG = "io.github.ccch1mneyyy.dsh_app";

    /**
     * 启动时探到的全部候选包名（含 PKG 自己）。DSH GO 换名重装、或者机器上同时装了
     * 两个 fork 时，作用域校验认这个集合 —— 只要 DSH_HOME 落在其中任意一个的私有目录里就放行。
     * 空集合时退化成只认 PKG。
     */
    public static final java.util.List<String> PKGS = new java.util.ArrayList<String>();
    public static final String NAME = "dsh-purge";
    public static final String ASSET = "plugin-payload.tar";
    /** 本 App 自己打的备份后缀，和用户手动的 .bak-MR-* 区分开，清洗时只认自己的。 */
    public static final String BAK_TAG = ".bak-DSHGO-";
    /**
     * 插件补丁表适配的宿主版本线。dsh-purge 1.1.46+rc5 的补丁表是按 0.2.1-rc.5 抽的，
     * 官方便携包 README 写的是 0.2.2-rc.1 —— 都落在 0.2.x 这条线上。
     * 宿主换大版本时，补丁会大面积不命中（插件会静默跳过，不会崩，但也等于没生效）。
     */
    public static final String HOST_LINE = "0.2";

    private static final String[] PROMPTS = {"AGENTS.md", "prompt-inject.md", "personaPrefix.txt"};

    private PluginRepo() {}

    // ---------------------------------------------------------------- 范围

    /**
     * DSH_HOME 是否落在 DSH GO 的私有目录里。
     *
     * 认的是「候选包名集合」而不是单个写死的名字 —— DSH GO 换名重装、或者用户机器上
     * 同时有两个 fork 时，只要 DSH_HOME 在其中之一下面就算在范围内。
     * 目的不变：挡住把内容写进别的 App 或系统目录。
     */
    public static boolean inScope(DshPaths dp) {
        String h = dp.dshHome();
        if (h == null) return false;
        java.util.List<String> scope = PKGS.isEmpty()
                ? java.util.Collections.singletonList(PKG) : PKGS;
        for (String p : scope) {
            if (p == null || p.isEmpty()) continue;
            if (h.startsWith("/data/data/" + p + "/") || h.startsWith("/data/user/0/" + p + "/")) {
                return true;
            }
        }
        return false;
    }

    private static String pluginDir(DshPaths dp) { return dp.dshHome() + "/plugins/" + NAME; }
    private static String nmDir(DshPaths dp) { return dp.profileDir() + "/node_modules/" + NAME; }
    private static String pkgJson(DshPaths dp) { return dp.profileDir() + "/package.json"; }
    private static String stateDir(DshPaths dp) { return dp.dshHome() + "/" + NAME; }

    // ---------------------------------------------------------------- 网关脚本

    /**
     * 把内置的 gw-up.sh / gw-down.sh 部署到网关目录。
     * 这两个脚本以前只存在于设备 rootfs 里，换手机就没了 —— 现在跟着 APK 走。
     */
    public static String deployGw(Context ctx, DshPaths dp) {
        String gw = dp.gwDir();
        if (!RootShell.exists(gw)) {
            return "网关目录不存在：\n" + gw
                    + "\n\n先在 dsh go 里启动过一次网关，或在「手动指定」里把网关目录填对。";
        }
        String stamp = Repo.stamp();
        String owner = RootShell.exec("stat -c '%u:%g' " + RootShell.q(gw), 10).text();
        boolean okOwner = owner.matches("\\d+:\\d+");

        StringBuilder log = new StringBuilder();
        for (String name : new String[]{"gw-up.sh", "gw-down.sh"}) {
            File tmp = new File(ctx.getCacheDir(), name);
            try {
                InputStream in = ctx.getAssets().open(name);
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                // 去 CR：脚本是从 Windows 仓库里带出来的，一旦是 CRLF，
                // 设备上 sh 会逐行报 "\r not found"，再报 "elif unexpected" —— 网关直接起不来。
                // 这里在写盘前统一抹掉，别指望仓库里永远是 LF。
                byte[] raw = bo.toByteArray();
                java.io.ByteArrayOutputStream clean = new java.io.ByteArrayOutputStream(raw.length);
                for (int i = 0; i < raw.length; i++) {
                    if (raw[i] != '\r') clean.write(raw[i]);
                }
                OutputStream os = new FileOutputStream(tmp);
                os.write(clean.toByteArray());
                os.close();
            } catch (Exception e) {
                log.append(name).append("  读取内置脚本失败：").append(e).append('\n');
                continue;
            }
            String dst = gw + "/" + name;
            String bakNote = "";
            if (RootShell.exists(dst)) {
                String bak = dst + BAK_TAG + stamp;
                if (RootShell.exec("cp -f " + RootShell.q(dst) + " " + RootShell.q(bak), 20).ok) {
                    bakNote = "（原文件备份 " + bak + "）";
                }
            }
            RootShell.Res r = RootShell.exec(
                    "cp -f " + RootShell.q(tmp.getAbsolutePath()) + " " + RootShell.q(dst)
                            + " && chmod 755 " + RootShell.q(dst)
                            + (okOwner ? " && chown " + owner + " " + RootShell.q(dst) : ""), 30);
            log.append(name).append(r.ok ? "  已部署" + bakNote : "  失败：" + r.out).append('\n');
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }

        // 语法自检：脚本是 bash 写的，但设备上用 sh 跑，必须过 sh -n
        String rf = dp.rootfs();
        if (rf != null && !rf.isEmpty()) {
            String gwInRoot = gw.startsWith(rf) ? gw.substring(rf.length()) : "/root/workspace/gw";
            RootShell.Res syn = RootShell.exec("chroot " + RootShell.q(rf)
                    + " /usr/bin/env -i PATH=/usr/bin:/bin /bin/sh -c "
                    + RootShell.q("sh -n " + gwInRoot + "/gw-up.sh && sh -n " + gwInRoot + "/gw-down.sh && echo SYNTAX-OK"), 25);
            log.append(syn.out.contains("SYNTAX-OK") ? "语法自检  通过\n" : "语法自检  未通过：" + syn.out + "\n");
        }
        log.append("\n部署完不用重启网关，下次启动/重启就用新脚本。\n");
        return log.toString();
    }

    // ---------------------------------------------------------------- 宿主版本

    /** 每次注入记一行宿主版本，换机/升级后能看出什么时候变的。 */
    private static void recordHost(DshPaths dp) {
        String[] c = compat(dp);
        String line = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                .format(new java.util.Date())
                + "  宿主 " + c[0] + "（" + c[1] + "）\n";
        String dir = stateDir(dp);
        String f = dir + "/host.txt";
        String owner = RootShell.exec("stat -c '%u:%g' " + RootShell.q(dp.dshHome()), 10).text();
        boolean okOwner = owner.matches("\\d+:\\d+");
        RootShell.exec("mkdir -p " + RootShell.q(dir) + "; printf '%s' " + RootShell.q(line)
                + " >> " + RootShell.q(f)
                + (okOwner ? " && chown " + owner + " " + RootShell.q(f) : ""), 15);
    }

    /** 读最近几条宿主记录，给状态页/自检用。 */
    public static String hostHistory(DshPaths dp) {
        String t = RootShell.readText(stateDir(dp) + "/host.txt");
        if (t == null || t.trim().isEmpty()) return "";
        String[] lines = t.trim().split("\n");
        int from = Math.max(0, lines.length - 3);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < lines.length; i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    /** 读宿主 @deepseek-ai/dsh 的版本号。找不到返回空串。 */
    public static String hostVersion(DshPaths dp) {
        String rf = dp.rootfs();
        if (rf == null || rf.isEmpty()) return "";
        String[] cands = {
                "/opt/dsh/node_modules/@deepseek-ai/dsh/package.json",
                "/opt/dsh-go/node_modules/@deepseek-ai/dsh/package.json",
                "/usr/lib/node_modules/@deepseek-ai/dsh/package.json",
                "/usr/local/lib/node_modules/@deepseek-ai/dsh/package.json",
        };
        for (String c : cands) {
            String t = RootShell.readText(rf + c);
            if (t == null) continue;
            try {
                String v = new JSONObject(t).optString("version", "");
                if (!v.isEmpty()) return v;
            } catch (Exception ignored) {}
        }
        return "";
    }

    /** {版本, 结论, 等级}。等级 2=匹配 1=同版本线 0=不兼容。 */
    public static String[] compat(DshPaths dp) {
        String v = hostVersion(dp);
        if (v.isEmpty()) {
            return new String[]{"未探测到", "找不到宿主包，按不兼容处理", "0"};
        }
        String line = v.split("-")[0];              // 0.2.1-rc.4 → 0.2.1
        String[] p = line.split("\\.");
        String mm = p.length >= 2 ? p[0] + "." + p[1] : line;
        if (mm.equals(HOST_LINE)) {
            boolean stable = !v.contains("-");
            return new String[]{v,
                    stable ? "匹配" : "同版本线（" + HOST_LINE + ".x，rc 不同可能少命中几条补丁）",
                    stable ? "2" : "1"};
        }
        return new String[]{v,
                "与插件适配的 " + HOST_LINE + ".x 不同，建议只装提示词层", "0"};
    }

    // ---------------------------------------------------------------- 状态

    /**
     * 设置页状态卡用的三行摘要：{标签, 值, 好坏(1/0)}。
     * 不跑 node、不扫目录，纯文件存在性判断，秒回。
     */
    public static String[][] summary(DshPaths dp) {
        String[][] r = new String[4][];
        if (!RootShell.hasRoot()) {
            r[0] = new String[]{"插件", "需要 root", "0"};
            r[1] = new String[]{"注入", "需要 root", "0"};
            r[2] = new String[]{"宿主", "需要 root 才能读", "0"};
            r[3] = new String[]{"通道", "—", "2"};
            return r;
        }
        if (!inScope(dp)) {
            r[0] = new String[]{"插件", "路径不在 dsh go 目录内", "0"};
            r[1] = new String[]{"注入", "已禁止操作", "0"};
            r[2] = new String[]{"宿主", "—", "0"};
            r[3] = new String[]{"通道", "—", "0"};
            return r;
        }
        String sh =
                "D=" + RootShell.q(dp.dshHome()) + "; P=" + RootShell.q(dp.profileDir()) + "; " +
                "echo \"p=$([ -d $D/plugins/" + NAME + " ] && echo 1 || echo 0)\"; " +
                "echo \"n=$([ -d $P/node_modules/" + NAME + " ] && echo 1 || echo 0)\"; " +
                "echo \"g=$(ss -ltn 2>/dev/null | grep -c ':7863 ')\"";
        String out = RootShell.exec(sh, 20).out;
        boolean p = "1".equals(val(out, "p"));
        boolean n = "1".equals(val(out, "n"));
        boolean gw = !"0".equals(val(out, "g"));
        String ver = "";
        if (p) {
            String t = RootShell.readText(pluginDir(dp) + "/package.json");
            if (t != null) {
                try { ver = new JSONObject(t).optString("version", ""); } catch (Exception ignored) {}
            }
        }

        r[0] = new String[]{"插件", p ? "已装" + (ver.isEmpty() ? "" : " v" + ver) : "未装", p ? "1" : "0"};
        r[1] = new String[]{"注入", (p && n) ? "已就绪" : (p ? "缺 node_modules 副本" : "未注入"), (p && n) ? "1" : "0"};
        String[] c = compat(dp);
        String shortVerdict = "2".equals(c[2]) ? "匹配" : ("1".equals(c[2]) ? "同版本线" : "不兼容");
        r[2] = new String[]{"宿主", c[0].equals("未探测到") ? "未探测到" : c[0] + " · " + shortVerdict, c[2]};
        r[3] = new String[]{"通道", gw ? "网关层在线（7863）" : "本地直连（无网关）", "2"};
        return r;
    }

    public static String status(DshPaths dp) {
        StringBuilder sb = new StringBuilder();
        sb.append("目标包名  ").append(PKG).append('\n');
        sb.append("DSH_HOME  ").append(dp.dshHome()).append('\n');
        sb.append("profile   ").append(dp.profileName()).append('\n');

        boolean scope = inScope(dp);
        sb.append("范围检查  ").append(scope ? "通过（只在 dsh go 目录内操作）"
                : "不通过 —— DSH_HOME 不在 " + PKG + " 下，已禁止注入/清洗").append('\n');
        if (!scope) return sb.toString();

        boolean homeOk = RootShell.exists(dp.dshHome());
        boolean profOk = RootShell.exists(dp.profileDir());
        sb.append("DSH_HOME  ").append(homeOk ? "存在" : "不存在 —— 先在这台手机上打开一次 dsh go 让它初始化").append('\n');
        sb.append("profile   ").append(profOk ? "目录存在" : "目录不存在 —— 在下面把 profile 名改成实际值（移动端一般 web）").append('\n');
        if (!homeOk || !profOk) return sb.toString();

        String sh =
                "D=" + RootShell.q(dp.dshHome()) + "; P=" + RootShell.q(dp.profileDir()) + "; " +
                "echo \"plugin=$([ -d $D/plugins/" + NAME + " ] && echo 1 || echo 0)\"; " +
                "echo \"nm=$([ -d $P/node_modules/" + NAME + " ] && echo 1 || echo 0)\"; " +
                "echo \"agents=$(stat -c %s $D/AGENTS.md 2>/dev/null || echo -1)\"; " +
                "echo \"prompt=$(stat -c %s $D/prompt-inject.md 2>/dev/null || echo -1)\"; " +
                "echo \"persona=$(stat -c %s $D/personaPrefix.txt 2>/dev/null || echo -1)\"; " +
                "echo \"state=$(ls $D/" + NAME + "/*.json 2>/dev/null | wc -l)\"; " +
                "echo \"gw=$(ss -ltn 2>/dev/null | grep -c ':7863 ')\"; " +
                "echo \"node=$(ps -A -o ARGS 2>/dev/null | grep -c 'dsh/lib/bin.js')\"";
        String out = RootShell.exec(sh, 25).out;

        String plugin = val(out, "plugin"), nm = val(out, "nm");
        String agents = val(out, "agents"), prompt = val(out, "prompt"), persona = val(out, "persona");
        String state = val(out, "state"), gw = val(out, "gw"), node = val(out, "node");

        // 版本与登记状态直接从文件读，少一层 shell 转义
        String ver = "";
        boolean registered = false;
        String pkgText = RootShell.readText(pluginDir(dp) + "/package.json");
        if (pkgText != null) {
            try { ver = new JSONObject(pkgText).optString("version", ""); } catch (Exception ignored) {}
        }
        String profText = RootShell.readText(pkgJson(dp));
        if (profText != null) {
            try {
                JSONObject o = new JSONObject(profText);
                JSONObject deps = o.optJSONObject("dependencies");
                boolean inDep = deps != null && deps.has(NAME);
                boolean inBundle = false;
                JSONObject dsh = o.optJSONObject("dsh");
                JSONObject profile = dsh == null ? null : dsh.optJSONObject("profile");
                JSONArray bundles = profile == null ? null : profile.optJSONArray("bundles");
                if (bundles != null) {
                    for (int i = 0; i < bundles.length(); i++) {
                        if (NAME.equals(bundles.optString(i))) { inBundle = true; break; }
                    }
                }
                registered = inDep && inBundle;
                if (!registered) {
                    sb.append("注意      package.json 里 dependencies=").append(inDep)
                            .append(" / bundles=").append(inBundle).append("，需要重新注入\n");
                }
            } catch (Exception ignored) {}
        }

        sb.append('\n');
        sb.append("插件本体  ").append("1".equals(plugin) ? "已装" + (ver.isEmpty() ? "" : " v" + ver) : "未装").append('\n');
        sb.append("profile 内 ").append("1".equals(nm) ? "已就位" : "缺失（bundles 会加载失败）").append('\n');
        sb.append("package.json  ").append(registered ? "已登记 dsh-purge" : "未登记").append('\n');
        sb.append("状态目录  ").append(state).append(" 个文件\n");
        sb.append('\n');
        sb.append("AGENTS.md        ").append(bytes(agents)).append('\n');
        sb.append("prompt-inject.md ").append(bytes(prompt)).append('\n');
        sb.append("personaPrefix    ").append(bytes(persona)).append('\n');
        sb.append('\n');
        boolean hasGw = !"0".equals(gw);
        sb.append("注入通道  ").append(hasGw
                ? "网关层在线（7863）—— 以网关那份 system 为主，本插件叠加"
                : "无网关 —— 本地通道（dsh-purge override + AGENTS.md）是唯一注入来源").append('\n');
        String[] c = compat(dp);
        sb.append("宿主版本  ").append(c[0]).append(" —— ").append(c[1]).append('\n');
        String hist = hostHistory(dp);
        if (!hist.isEmpty()) {
            sb.append("最近注入  ").append(hist.replace("\n", "\n          ")).append('\n');
        }
        sb.append("dsh 进程  ").append("0".equals(node) ? "未运行（改完配置要重新打开 dsh go）" : "运行中").append('\n');
        return sb.toString();
    }

    // ---------------------------------------------------------------- 注入

    /**
     * @param overwrite true=用包里的提示词覆盖（先备份）；false=只补缺失的，已有的一律不动
     */
    public static String install(Context ctx, DshPaths dp, boolean overwrite) {
        if (!inScope(dp)) return "拒绝：DSH_HOME 不在 " + PKG + " 下\n" + dp.dshHome();
        if (!RootShell.exists(dp.dshHome())) {
            return "DSH_HOME 不存在：\n" + dp.dshHome()
                    + "\n\n先在这台手机上打开一次 dsh go，让它把目录初始化出来，再回来注入。";
        }
        if (!RootShell.exists(dp.profileDir())) {
            return "profile 目录不存在：\n" + dp.profileDir()
                    + "\n\n到设置页把 profile 名改成实际那个。";
        }

        StringBuilder log = new StringBuilder();
        String stamp = Repo.stamp();
        String stage = "/data/local/tmp/dshgo-plg-" + stamp;

        // 1. assets 里的 tar 落到缓存目录，再让 su 解到 stage
        File cache = new File(ctx.getCacheDir(), "payload.tar");
        try {
            InputStream in = openPayload(ctx);
            OutputStream os = new FileOutputStream(cache);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            os.close();
            in.close();
        } catch (Exception e) {
            return "读取内置插件包失败：" + e;
        }
        log.append("内置包    ").append(cache.length() / 1024).append(" KB\n");

        String prep =
                "rm -rf " + RootShell.q(stage) + "; mkdir -p " + RootShell.q(stage) + " && " +
                "tar -xf " + RootShell.q(cache.getAbsolutePath()) + " -C " + RootShell.q(stage) + " && " +
                "test -f " + RootShell.q(stage + "/plugins/" + NAME + "/package.json") + " && echo OK";
        RootShell.Res r = RootShell.exec(prep, 60);
        if (!r.out.contains("OK")) {
            RootShell.exec("rm -rf " + RootShell.q(stage), 10);
            return "解包失败：\n" + r.out;
        }
        log.append("解包      ").append(stage).append("  OK\n");

        // 2. 插件本体：plugins/ 和 profile 的 node_modules/ 各放一份（跳过 pnpm install）
        //    两个必须同时满足，否则 dsh 直接跳过这个 bundle：
        //    ① cp -rL 解引用 —— pnpm/proot 留下的符号链接指到 /data/user/0/... 绝对路径，
        //       在 proot 里解析不到，existsSync 就是 false；
        //    ② chown 成 DSH_HOME 的属主 —— root 拷进去的话 dsh（u0_a419）读不到 package.json，
        //       报 "cannot resolve profile bundle"，然后静默跳过，界面上什么都看不出来。
        String owner = RootShell.exec("stat -c '%u:%g' " + RootShell.q(dp.dshHome()), 10).text();
        if (!owner.matches("\\d+:\\d+")) owner = "";
        String chown = owner.isEmpty() ? "" : "chown -R " + owner + " ";
        String copy =
                "D=" + RootShell.q(dp.dshHome()) + "; P=" + RootShell.q(dp.profileDir()) + "; S=" + RootShell.q(stage) + "; " +
                "mkdir -p $D/plugins $P/node_modules $D/" + NAME + "; " +
                "rm -rf $D/plugins/" + NAME + " && cp -rL $S/plugins/" + NAME + " $D/plugins/ && " +
                "chmod -R u+rwX $D/plugins/" + NAME + " && " + chown + "$D/plugins/" + NAME + " && " +
                "rm -rf $P/node_modules/" + NAME + " && cp -rL $S/plugins/" + NAME + " $P/node_modules/ && " +
                "chmod -R u+rwX $P/node_modules/" + NAME + " && " + chown + "$P/node_modules/" + NAME + " && " +
                "cp -f $S/state/*.json $D/" + NAME + "/ 2>/dev/null; " + chown + "$D/" + NAME + " 2>/dev/null; " +
                "echo done";
        RootShell.Res c = RootShell.exec(copy, 180);
        if (!c.out.contains("done")) {
            RootShell.exec("rm -rf " + RootShell.q(stage), 10);
            return "复制插件失败：\n" + c.out;
        }
        log.append("插件本体  plugins/ 与 node_modules/ 各一份  OK")
                .append(owner.isEmpty() ? "（未能确定属主，跳过 chown）" : "（属主 " + owner + "）").append('\n');

        // 3. 提示词
        for (String f : PROMPTS) {
            String dst = dp.dshHome() + "/" + f;
            String src = stage + "/dsh/" + f;
            if (!RootShell.exists(src)) continue;
            boolean exist = RootShell.exists(dst);
            if (exist && !overwrite) {
                log.append("提示词    ").append(f).append(" 已存在，保留原样\n");
                continue;
            }
            if (exist) {
                String bak = backupOwn(dst, stamp);
                log.append("提示词    ").append(f).append(" 备份 ").append(bak == null ? "失败" : shortPath(bak)).append('\n');
            }
            boolean ok = RootShell.exec("cp -f " + RootShell.q(src) + " " + RootShell.q(dst)
                    + (owner.isEmpty() ? "" : " && chown " + owner + " " + RootShell.q(dst)), 30).ok;
            log.append("提示词    ").append(f).append(ok ? " 已写入" : " 写入失败").append('\n');
        }

        // 4. package.json 合并（只补 dsh-purge 一项，不动别人的）
        log.append(mergePackage(dp, true));

        RootShell.exec("rm -rf " + RootShell.q(stage), 15);
        cache.delete();
        recordHost(dp);
        log.append("\n完成。重新打开 dsh go（或重启 dsh 进程）后生效。\n");
        return log.toString();
    }

    /**
     * 只装提示词层：写 AGENTS.md / prompt-inject.md / personaPrefix.txt，
     * 不装插件、不改 package.json。宿主版本和插件补丁表对不上时的安全路径。
     */
    public static String installPrompts(Context ctx, DshPaths dp, boolean overwrite) {
        if (!inScope(dp)) return "拒绝：DSH_HOME 不在 " + PKG + " 下\n" + dp.dshHome();
        if (!RootShell.exists(dp.dshHome())) return "DSH_HOME 不存在，先打开一次 dsh go";

        String stamp = Repo.stamp();
        String stage = "/data/local/tmp/dshgo-plg-" + stamp;
        File cache = new File(ctx.getCacheDir(), "payload.tar");
        try {
            InputStream in = openPayload(ctx);
            OutputStream os = new FileOutputStream(cache);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            os.close();
            in.close();
        } catch (Exception e) {
            return "读取内置包失败：" + e;
        }
        RootShell.Res r = RootShell.exec(
                "rm -rf " + RootShell.q(stage) + "; mkdir -p " + RootShell.q(stage) + " && tar -xf "
                        + RootShell.q(cache.getAbsolutePath()) + " -C " + RootShell.q(stage)
                        + " && test -d " + RootShell.q(stage + "/dsh") + " && echo OK", 60);
        if (!r.out.contains("OK")) {
            RootShell.exec("rm -rf " + RootShell.q(stage), 10);
            return "解包失败：\n" + r.out;
        }
        // 包里只带插件、不带提示词：这不是错误，直接说清楚
        if (!RootShell.exists(stage + "/dsh")) {
            RootShell.exec("rm -rf " + RootShell.q(stage), 10);
            return "这个安装包里不带提示词层。\n\n"
                    + "插件和提示词是分开的：插件用「一键注入」装，提示词在提示词页自己存方案或导入。";
        }

        StringBuilder log = new StringBuilder();
        String owner = RootShell.exec("stat -c '%u:%g' " + RootShell.q(dp.dshHome()), 10).text();
        if (!owner.matches("\\d+:\\d+")) owner = "";

        for (String f : PROMPTS) {
            String src = stage + "/dsh/" + f;
            String dst = dp.dshHome() + "/" + f;
            if (!RootShell.exists(src)) continue;
            boolean exist = RootShell.exists(dst);
            if (exist && !overwrite) {
                log.append("提示词    ").append(f).append(" 已存在，保留原样\n");
                continue;
            }
            if (exist) {
                String bak = backupOwn(dst, stamp);
                log.append("提示词    ").append(f).append(" 备份 ").append(bak == null ? "失败" : shortPath(bak)).append('\n');
            }
            boolean ok = RootShell.exec("cp -f " + RootShell.q(src) + " " + RootShell.q(dst)
                    + (owner.isEmpty() ? "" : " && chown " + owner + " " + RootShell.q(dst)), 30).ok;
            log.append("提示词    ").append(f).append(ok ? " 已写入" : " 写入失败").append('\n');
        }

        RootShell.exec("rm -rf " + RootShell.q(stage), 15);
        cache.delete();
        recordHost(dp);
        log.append("\n只装了提示词层，插件和 package.json 没动。重新打开 dsh go 后生效。\n");
        return log.toString();
    }

    // ---------------------------------------------------------------- 清洗（打补丁）

    /**
     * 一键清洗 = 让 dsh-purge 按当前宿主版本自动打补丁（`--apply`）。
     *
     * 插件里 86 条补丁各自带匹配式，能匹配当前宿主文件的才打，匹配不上的跳过 ——
     * 所以「宿主版本不同」不会崩，只是命中条数少。这里把结果按「已清洗 / 已是最新 / 跳过」分类汇总。
     */
    public static String applyPatches(DshPaths dp) {
        if (!inScope(dp)) return "拒绝：DSH_HOME 不在 " + PKG + " 下\n" + dp.dshHome();
        if (!RootShell.exists(pluginDir(dp))) {
            return "插件没装，先点「一键注入」。";
        }
        String out = Repo.dshPurgeCli(dp, "--apply", 240);
        if (out.trim().isEmpty()) return "命令没有任何输出，可能是 node 不在 PATH 里。";
        return summarize("清洗", out);
    }

    /** 还原补丁：把打过的补丁全部回滚。 */
    public static String revertPatches(DshPaths dp) {
        if (!inScope(dp)) return "拒绝：DSH_HOME 不在 " + PKG + " 下\n" + dp.dshHome();
        if (!RootShell.exists(pluginDir(dp))) return "插件没装。";
        String out = Repo.dshPurgeCli(dp, "--revert", 240);
        if (out.trim().isEmpty()) return "命令没有任何输出。";
        return summarize("还原", out);
    }

    /** 把 CLI 的长输出压成「摘要 + 明细」。 */
    private static String summarize(String what, String out) {
        int applied = 0, already = 0, skipped = 0, na = 0, missing = 0, failed = 0;
        java.util.List<String> rows = new java.util.ArrayList<>();
        String shimLine = null, overrideLine = null, errLine = null;

        for (String line : out.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.contains("— applied")) applied++;
            else if (t.contains("— already")) already++;
            else if (t.contains("— skipped")) skipped++;
            else if (t.contains("— na")) na++;
            else if (t.contains("— missing_file")) missing++;
            // 只认真错误。CLI 还会打「✗ skipped patch #N」这种汇总行，
            // 拿 ✗ 开头判失败会把 skipped/na 重复算一遍（之前就错算成「失败 12」）。
            else if (t.contains("— error")) failed++;

            if (t.contains("[#")) rows.add(t);                       // 每条补丁的结果行
            if (t.startsWith("shim") && t.contains("/")) shimLine = t;
            if (t.contains("prompt-inject.md")) overrideLine = t;
            if (t.startsWith("[ERROR]")) errLine = t;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(what).append("结果\n");
        sb.append("  已打补丁     ").append(applied).append(" 条\n");
        if (already > 0) sb.append("  本来就是     ").append(already).append(" 条\n");
        int miss = skipped + na + missing + failed;
        if (miss > 0) {
            sb.append("  没打上       ").append(miss).append(" 条");
            java.util.List<String> why = new java.util.ArrayList<>();
            if (skipped > 0) why.add("条件跳过 " + skipped);
            if (na > 0) why.add("手机端不适用 " + na);
            if (missing > 0) why.add("缺文件 " + missing);
            if (failed > 0) why.add("失败 " + failed);
            sb.append("（").append(android.text.TextUtils.join(" · ", why)).append("）\n");
        }
        if (errLine != null) sb.append("\n").append(errLine).append('\n');
        sb.append('\n');

        // 没打上的排前面，已打上的排后面 —— 用户关心的是「缺了什么」，不是「成了什么」
        if (miss > 0) {
            sb.append("—— 没打上的 ").append(miss).append(" 条 ——\n");
            for (String r : rows) {
                if (!r.contains("— applied") && !r.contains("— already")) sb.append(r).append('\n');
            }
            sb.append("\n—— 已打上的 ").append(applied).append(" 条 ——\n");
        }
        for (String r : rows) {
            if (r.contains("— applied") || r.contains("— already")) sb.append(r).append('\n');
        }
        if (shimLine != null) sb.append('\n').append(shimLine).append('\n');
        if (overrideLine != null) sb.append(overrideLine).append('\n');
        sb.append("\n重启 dsh go 生效。");
        return sb.toString();
    }

    // ---------------------------------------------------------------- 卸载

    /** 卸插件 + 从 package.json 摘掉 dsh-purge + 还原本 App 打过的提示词备份。 */
    public static String clean(DshPaths dp) {
        if (!inScope(dp)) return "拒绝：DSH_HOME 不在 " + PKG + " 下\n" + dp.dshHome();

        StringBuilder log = new StringBuilder();
        String sh =
                "D=" + RootShell.q(dp.dshHome()) + "; P=" + RootShell.q(dp.profileDir()) + "; " +
                "if [ -d $D/plugins/" + NAME + " ]; then rm -rf $D/plugins/" + NAME + " && echo plugin_removed; else echo plugin_absent; fi; " +
                "if [ -d $P/node_modules/" + NAME + " ]; then rm -rf $P/node_modules/" + NAME + " && echo nm_removed; else echo nm_absent; fi; " +
                "rm -rf $D/" + NAME + "; echo state_cleared";
        String out = RootShell.exec(sh, 60).out;
        log.append(out.contains("plugin_removed") ? "插件本体  已删除\n" : "插件本体  本来就没有\n");
        log.append(out.contains("nm_removed") ? "node_modules  已删除\n" : "node_modules  本来就没有\n");

        log.append(mergePackage(dp, false));

        // 提示词：只还原本 App 打的备份，用户自己的 .bak-MR-* 一根手指都不碰
        log.append(restorePrompts(dp));

        log.append("\n完成。重新打开 dsh go 后生效。\n");
        return log.toString();
    }

    /** 只还原本 App 打过的 .bak-DSHGO-* 备份；没有就保留原文件。 */
    public static String restorePrompts(DshPaths dp) {
        if (!inScope(dp)) return "拒绝：DSH_HOME 不在 " + PKG + " 下\n";
        StringBuilder log = new StringBuilder();
        String dir = dp.dshHome();
        for (String f : PROMPTS) {
            String dst = dir + "/" + f;
            String newest = RootShell.exec(
                    "ls -1t " + RootShell.q(dir) + "/ 2>/dev/null | grep -F "
                            + RootShell.q(f + BAK_TAG) + " | head -1", 15).text();
            if (newest.isEmpty()) {
                log.append("提示词    ").append(f)
                        .append(RootShell.exists(dst) ? " 无本 App 备份，保留原文件" : " 不存在").append('\n');
                continue;
            }
            boolean ok = RootShell.exec("cp -f " + RootShell.q(dir + "/" + newest)
                    + " " + RootShell.q(dst), 30).ok;
            log.append("提示词    ").append(f).append(ok ? " 已还原 " + newest : " 还原失败").append('\n');
        }
        return log.toString();
    }

    // ---------------------------------------------------------------- package.json

    /**
     * @param add true=加 dsh-purge；false=摘掉
     */
    private static String mergePackage(DshPaths dp, boolean add) {
        String path = pkgJson(dp);
        String text = RootShell.readText(path);
        if (text == null || text.trim().isEmpty()) {
            return "package.json  读不到，跳过合并\n";
        }
        try {
            JSONObject o = new JSONObject(text);

            JSONObject deps = o.optJSONObject("dependencies");
            if (deps == null) { deps = new JSONObject(); o.put("dependencies", deps); }
            boolean depChanged;
            if (add) {
                depChanged = !deps.has(NAME);
                if (depChanged) deps.put(NAME, "file:../../plugins/" + NAME);
            } else {
                depChanged = deps.has(NAME);
                deps.remove(NAME);
            }

            JSONObject dsh = o.optJSONObject("dsh");
            if (dsh == null && add) { dsh = new JSONObject(); o.put("dsh", dsh); }
            JSONObject profile = dsh == null ? null : dsh.optJSONObject("profile");
            if (profile == null && add) { profile = new JSONObject(); dsh.put("profile", profile); }
            JSONArray bundles = profile == null ? null : profile.optJSONArray("bundles");
            if (bundles == null && add) { bundles = new JSONArray(); profile.put("bundles", bundles); }

            boolean bundleChanged = false;
            if (bundles != null) {
                boolean has = false;
                for (int i = 0; i < bundles.length(); i++) {
                    if (NAME.equals(bundles.optString(i))) { has = true; break; }
                }
                if (add && !has) { bundles.put(NAME); bundleChanged = true; }
                if (!add && has) {
                    JSONArray nb = new JSONArray();
                    for (int i = 0; i < bundles.length(); i++) {
                        String s = bundles.optString(i);
                        if (!NAME.equals(s)) nb.put(s);
                    }
                    profile.put("bundles", nb);
                    bundleChanged = true;
                }
            }

            if (!depChanged && !bundleChanged) {
                return "package.json  " + (add ? "已登记 dsh-purge，无需改动" : "本就没有 dsh-purge") + "\n";
            }

            backupOwn(path, Repo.stamp());
            RootShell.Res w = RootShell.writeText(path, o.toString(2));
            return "package.json  " + (w.ok ? (add ? "已登记 dsh-purge（dependencies + bundles）" : "已摘除 dsh-purge") : "写入失败：" + w.out) + "\n";
        } catch (Exception e) {
            return "package.json  解析失败（可能不是合法 JSON，已跳过）：" + e + "\n";
        }
    }

    // ---------------------------------------------------------------- 从 zip 导入

    /**
     * 从本地 zip 导入插件（便携包 / npm 包 / 直接压的插件目录都认）。
     * 只替换插件本体并登记 package.json，不动提示词 —— 要换提示词用「注入·覆盖」。
     */
    public static String importZip(Context ctx, DshPaths dp, android.net.Uri uri) {
        if (!inScope(dp)) return "拒绝：DSH_HOME 不在 " + PKG + " 下\n" + dp.dshHome();
        if (!RootShell.exists(dp.dshHome())) return "DSH_HOME 不存在，先打开一次 dsh go";
        if (!RootShell.exists(dp.profileDir())) return "profile 目录不存在，去设置页改 profile 名";

        File zip = new File(ctx.getCacheDir(), "import.zip");
        try {
            InputStream in = ctx.getContentResolver().openInputStream(uri);
            if (in == null) return "打不开这个文件";
            OutputStream os = new FileOutputStream(zip);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            os.close();
            in.close();
        } catch (Exception e) {
            return "读取 zip 失败：" + e;
        }

        StringBuilder log = new StringBuilder();
        File out = new File(ctx.getCacheDir(), "import-plg");
        String ver;
        try {
            java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip);
            String root = findPluginRoot(zf);
            if (root == null) {
                zf.close();
                return "这个 zip 里没有 dsh-purge 插件\n（按 package.json 里的 name 字段找，认 payload/plugins/dsh-purge、plugins/dsh-purge、dsh-purge 这几种布局）";
            }
            ver = readVersion(zf, root);
            deleteRec(out);
            out.mkdirs();
            int count = extract(zf, root, out);
            zf.close();
            log.append("zip       ").append(zip.length() / 1024).append(" KB，解出 ").append(count).append(" 个文件\n");
            log.append("插件版本  ").append(ver.isEmpty() ? "未知" : ver).append('\n');
        } catch (Exception e) {
            return "解析 zip 失败：" + e;
        }

        String owner = RootShell.exec("stat -c '%u:%g' " + RootShell.q(dp.dshHome()), 10).text();
        if (!owner.matches("\\d+:\\d+")) owner = "";
        String chown = owner.isEmpty() ? "" : "chown -R " + owner + " ";
        String src = RootShell.q(out.getAbsolutePath());
        String sh =
                "D=" + RootShell.q(dp.dshHome()) + "; P=" + RootShell.q(dp.profileDir()) + "; S=" + src + "; " +
                "mkdir -p $D/plugins $P/node_modules; " +
                "rm -rf $D/plugins/" + NAME + " && cp -rL $S $D/plugins/" + NAME + " && " +
                "chmod -R u+rwX $D/plugins/" + NAME + " && " + chown + "$D/plugins/" + NAME + " && " +
                "rm -rf $P/node_modules/" + NAME + " && cp -rL $S $P/node_modules/" + NAME + " && " +
                "chmod -R u+rwX $P/node_modules/" + NAME + " && " + chown + "$P/node_modules/" + NAME + " && " +
                "echo done";
        RootShell.Res r = RootShell.exec(sh, 180);
        if (!r.out.contains("done")) return log + "复制失败：\n" + r.out;
        log.append("插件本体  已替换").append(owner.isEmpty() ? "（跳过 chown）" : "（属主 " + owner + "）").append('\n');
        log.append(mergePackage(dp, true));
        recordHost(dp);
        log.append("\n完成。重新打开 dsh go 后生效。\n");
        return log.toString();
    }

    /** 找插件根：按 package.json 的 name 字段认，取层级最浅的那个。 */
    private static String findPluginRoot(java.util.zip.ZipFile zf) throws Exception {
        String best = null;
        java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            java.util.zip.ZipEntry e = en.nextElement();
            String name = e.getName().replace('\\', '/');
            if (!name.endsWith("package.json")) continue;
            String body = readEntry(zf, e, 4096);
            if (body == null || !body.contains("\"" + NAME + "\"")) continue;
            try {
                if (!NAME.equals(new JSONObject(body).optString("name", ""))) continue;
            } catch (Exception ignored) {
                continue;
            }
            String root = name.substring(0, name.length() - "package.json".length());
            if (best == null || root.length() < best.length()) best = root;
        }
        return best;
    }

    private static String readVersion(java.util.zip.ZipFile zf, String root) {
        // 注意：有的 zip 用反斜杠做分隔符（Windows 打包的便携包就是），
        // zf.getEntry("a/b/package.json") 会取不到，必须按归一化后的名字遍历比对。
        try {
            String want = root + "package.json";
            java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                java.util.zip.ZipEntry e = en.nextElement();
                if (!e.getName().replace('\\', '/').equals(want)) continue;
                String body = readEntry(zf, e, 4096);
                return body == null ? "" : new JSONObject(body).optString("version", "");
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String readEntry(java.util.zip.ZipFile zf, java.util.zip.ZipEntry e, int limit) {
        try {
            InputStream in = zf.getInputStream(e);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n, total = 0;
            while ((n = in.read(buf)) > 0 && total < limit) {
                bo.write(buf, 0, Math.min(n, limit - total));
                total += n;
            }
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Exception ex) {
            return null;
        }
    }

    private static int extract(java.util.zip.ZipFile zf, String root, File dst) throws Exception {
        int count = 0;
        java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            java.util.zip.ZipEntry e = en.nextElement();
            String name = e.getName().replace('\\', '/');
            if (!name.startsWith(root)) continue;
            String rel = name.substring(root.length());
            if (rel.isEmpty()) continue;
            File f = new File(dst, rel);
            if (name.endsWith("/")) { f.mkdirs(); continue; }
            f.getParentFile().mkdirs();
            InputStream in = zf.getInputStream(e);
            OutputStream os = new FileOutputStream(f);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            os.close();
            in.close();
            count++;
        }
        return count;
    }

    private static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRec(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    // ---------------------------------------------------------------- 工具

    /** 读内置包。aapt2 在 Windows 上会把子目录分隔符写成反斜杠（assets/plugin\payload.tar），
     *  所以主路径失败时退一步扫 assets 根目录找 .tar。 */
    private static InputStream openPayload(Context ctx) throws Exception {
        try {
            return ctx.getAssets().open(ASSET);
        } catch (Exception e) {
            String[] names = ctx.getAssets().list("");
            if (names != null) {
                for (String n : names) {
                    if (n != null && n.endsWith(".tar")) return ctx.getAssets().open(n);
                }
            }
            throw e;
        }
    }

    /** 备份成 <文件>.bak-DSHGO-<时间>。刻意不用 RootShell.backup（那会生成 .bak-MR-），
     *  好让清洗时能精确只认自己打的备份，不误碰用户手动的 .bak-MR-*。 */
    private static String backupOwn(String path, String stamp) {
        String bak = path + BAK_TAG + stamp;
        boolean ok = RootShell.exec("cp -f " + RootShell.q(path) + " " + RootShell.q(bak), 20).ok;
        return ok ? bak : null;
    }

    private static String val(String out, String key) {
        for (String line : out.split("\n")) {
            int i = line.indexOf('=');
            if (i > 0 && line.substring(0, i).trim().equals(key)) return line.substring(i + 1).trim();
        }
        return "";
    }

    private static String bytes(String s) {
        try {
            long n = Long.parseLong(s);
            return n < 0 ? "缺失" : n + " B";
        } catch (Exception e) {
            return "缺失";
        }
    }

    private static String shortPath(String p) {
        if (p == null) return "";
        int i = p.lastIndexOf('/');
        return i > 0 ? p.substring(i + 1) : p;
    }
}
