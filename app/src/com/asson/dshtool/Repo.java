package com.asson.dshtool;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class Repo {

    private Repo() {}

    public static String stamp() {
        return new SimpleDateFormat("MMdd-HHmm", Locale.US).format(new Date());
    }

    /** {label, path, size, mtime}；size<0 表示不存在。一次 su 拿齐。 */
    public static String[] fileInfo(String label, String path) {
        String q = RootShell.q(path);
        String out = RootShell.exec(
                "if [ -e " + q + " ]; then stat -c '%s %Y' " + q + "; else echo '-1 -1'; fi", 8).text();
        String[] p = out.split(" ");
        long size = -1, mt = -1;
        try { size = Long.parseLong(p[0]); } catch (Exception ignored) {}
        try { mt = Long.parseLong(p[1]); } catch (Exception ignored) {}
        return new String[]{label, path, String.valueOf(size), String.valueOf(mt)};
    }

    /**
     * {label, path, size, mtime, hint}
     * 一次 su 拿齐所有文件的 size 与 mtime。
     * 每个文件单独起一次 su 会因 KernelSU 的 IPC 开销排队超时，
     * 排在后面的文件会被误判成缺失。
     */
    public static List<String[]> allFiles(DshPaths dp) {
        List<String[]> keys = dp.keyFiles();
        StringBuilder cmd = new StringBuilder();
        for (String[] kv : keys) {
            String q = RootShell.q(kv[1]);
            // 路径放在行尾，按路径回查大小 —— 中间要是多出几行 stderr（比如某个 stat 报错），
            // 按行号对号入座就会整列错位，界面上的大小会张冠李戴
            cmd.append("if [ -e ").append(q).append(" ]; then stat -c '%s %Y %n' ").append(q)
               .append("; else echo '-1 -1' ").append(q).append("; fi; ");
        }
        String out = RootShell.exec(cmd.toString(), 25).out;

        java.util.Map<String, String> sizeOf = new java.util.HashMap<>();
        java.util.Map<String, String> mtOf = new java.util.HashMap<>();
        for (String raw : out.split("\n")) {
            String t = raw.trim();
            if (t.isEmpty()) continue;
            int sp1 = t.indexOf(' ');
            if (sp1 <= 0) continue;
            int sp2 = t.indexOf(' ', sp1 + 1);
            if (sp2 <= 0) continue;
            String size = t.substring(0, sp1);
            String mt = t.substring(sp1 + 1, sp2);
            String path = t.substring(sp2 + 1).trim();
            sizeOf.put(path, size);
            mtOf.put(path, mt);
        }

        List<String[]> res = new ArrayList<>();
        for (String[] kv : keys) {
            String size = sizeOf.get(kv[1]);
            String mt = mtOf.get(kv[1]);
            res.add(new String[]{kv[0], kv[1],
                    size == null ? "-1" : size,
                    mt == null ? "-1" : mt,
                    kv.length > 2 ? kv[2] : "", kv.length > 3 ? kv[3] : ""});
        }
        return res;
    }

    /** {port, listening(0/1), pid} */
    public static List<String[]> ports() {
        String out = RootShell.exec("ss -ltnp 2>/dev/null || netstat -ltnp 2>/dev/null", 10).out;
        List<String[]> res = new ArrayList<>();
        int[] targets = {7863, 7864};
        for (int port : targets) {
            String line = null;
            for (String l : out.split("\n")) {
                if (l.contains(":" + port + " ")) { line = l; break; }
            }
            String pid = "";
            if (line != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("pid=(\\d+)").matcher(line);
                if (m.find()) pid = m.group(1);
                else {
                    m = java.util.regex.Pattern.compile("\\s(\\d+)/").matcher(line);
                    if (m.find()) pid = m.group(1);
                }
            }
            res.add(new String[]{String.valueOf(port), line != null ? "1" : "0", pid});
        }
        return res;
    }

    /** {pid, cmd} */
    public static List<String[]> processes() {
        String out = RootShell.exec("ps -A -o PID,CMD 2>/dev/null || ps -A 2>/dev/null", 10).out;
        List<String[]> res = new ArrayList<>();
        String[] lines = out.split("\n");
        for (int i = 1; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.isEmpty()) continue;
            int sp = t.indexOf(' ');
            if (sp <= 0) continue;
            String pid = t.substring(0, sp);
            String cmd = t.substring(sp + 1).trim();
            if (cmd.contains("proxy.js")
                    || cmd.contains("dsh") || cmd.contains("node")) {
                res.add(new String[]{pid, cmd});
                if (res.size() >= 20) break;
            }
        }
        return res;
    }

    public static String gwEnvValue(DshPaths dp, String key) {
        String text = RootShell.readText(dp.gwEnv());
        if (text == null) return "";
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.startsWith(key + "=")) return t.substring(key.length() + 1).trim();
        }
        return "";
    }

    /** 一次读完 gw.env 所有键值，避免每行一次 su */
    public static java.util.Map<String, String> gwEnvAll(DshPaths dp) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        String text = RootShell.readText(dp.gwEnv());
        if (text == null) return m;
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            int eq = t.indexOf('=');
            if (eq > 0) m.put(t.substring(0, eq).trim(), t.substring(eq + 1).trim());
        }
        return m;
    }

    public static RootShell.Res setGwEnv(DshPaths dp, String key, String value) {
        String path = dp.gwEnv();
        String text = RootShell.readText(path);
        if (text == null) return new RootShell.Res(false, "读不到 " + path, -1);
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(line.trim().startsWith(key + "=") ? key + "=" + value : line);
        }
        String bak = RootShell.backup(path, stamp());
        RootShell.Res r = RootShell.writeText(path, sb.toString());
        return r.ok ? new RootShell.Res(true, "已写入，备份：" + bak, 0) : r;
    }

    // ---------- 预热 / 触发词 ----------

    /** 读 warm.json：[[问,答],...] */
    public static List<String[]> readWarm(DshPaths dp) {
        List<String[]> l = new ArrayList<>();
        String text = RootShell.readText(dp.gwWarm());
        if (text == null || text.trim().isEmpty()) return l;
        try {
            org.json.JSONArray arr = new org.json.JSONArray(text);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONArray one = arr.getJSONArray(i);
                l.add(new String[]{one.getString(0), one.getString(1)});
            }
        } catch (Exception ignored) {
        }
        return l;
    }

    public static RootShell.Res saveWarm(DshPaths dp, List<String[]> pairs) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (String[] p : pairs) {
                org.json.JSONArray one = new org.json.JSONArray();
                one.put(p[0]);
                one.put(p[1]);
                arr.put(one);
            }
            String bak = RootShell.backup(dp.gwWarm(), stamp());
            RootShell.Res r = RootShell.writeText(dp.gwWarm(), arr.toString(2));
            return r.ok ? new RootShell.Res(true, "已保存 " + pairs.size() + " 对预热问答\n备份：" + bak, 0) : r;
        } catch (Exception e) {
            return new RootShell.Res(false, "序列化失败：" + e, -1);
        }
    }

    public static String readTrigger(DshPaths dp) {
        String t = RootShell.readText(dp.gwTrigger());
        return t == null ? "" : t.trim();
    }

    public static RootShell.Res saveTrigger(DshPaths dp, String text) {
        String bak = RootShell.backup(dp.gwTrigger(), stamp());
        RootShell.Res r = RootShell.writeText(dp.gwTrigger(), text.trim());
        return r.ok ? new RootShell.Res(true, "触发词已保存\n备份：" + bak, 0) : r;
    }

    // ---------- 网关 ----------

    /**
     * 在 rootfs 里执行命令。node 是 Linux ELF，Android 侧直接跑会报
     * "No such file or directory"（缺 /lib/ld-linux-aarch64.so.1），
     * 必须 chroot 进去，PATH 也要用 rootfs 里的。
     */
    private static RootShell.Res execInRootfs(DshPaths dp, String inner, long timeout) {
        String rf = dp.rootfs();
        if (rf == null || rf.isEmpty()) {
            return new RootShell.Res(false, "没探测到 rootfs，去设置页填对 DSH_HOME", -1);
        }
        String cmd = "chroot " + RootShell.q(rf)
                + " /usr/bin/env -i HOME=/root TERM=dumb"
                + " PATH=/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin"
                + " /bin/bash -c " + RootShell.q(inner);
        return RootShell.exec(cmd, timeout);
    }

    public static RootShell.Res gwUp(DshPaths dp) {
        String d = RootShell.q(dp.gwDirInRoot());
        return execInRootfs(dp, "cd " + d + " && sh gw-up.sh 2>&1", 45);
    }

    public static RootShell.Res gwDown(DshPaths dp) {
        String d = RootShell.q(dp.gwDirInRoot());
        return execInRootfs(dp, "cd " + d + " && sh gw-down.sh 2>&1", 30);
    }

    public static RootShell.Res gwRestart(DshPaths dp) {
        String d = RootShell.q(dp.gwDirInRoot());
        // 一条 chroot 里跑完，避免两次 su 往返 + 各 30/45 秒超时叠加成 76 秒
        return execInRootfs(dp, "cd " + d + " && sh gw-down.sh 2>&1; sleep 2; sh gw-up.sh 2>&1", 60);
    }

    /**
     * 重启 7863（网关上游）。
     * 它不归 gw-up.sh / gw-down.sh 管 —— 那两只只认 proxy.js(7864) 和 watchdog.sh。
     *
     * 为什么不调 boot-start.sh：那个脚本里 running() / dnsfix_running() 会遍历
     * /proc/[0-9]* 逐个 readlink exe。只要撞上一个正在退出、exe 指向已卸载挂载点的
     * 进程，readlink 会卡在内核里，整个脚本永久挂住 —— 实测 18:37 那次就卡死，
     * 7863 直接下线。手动重启不需要它的开机逻辑（config/prompt 恢复、CA、dnsfix），
     * 所以这里只做「按 pid 文件杀 → 等端口释放 → 直接拉起」。
     */
    /** 目录里第一个「没有扩展名且可执行」的文件 —— 上游那个二进制。找不到就退回 wb2api。 */
    public static String pickUpstreamBin(DshPaths dp) {
        String configured = dp.upstreamBin();
        if (!configured.isEmpty() && RootShell.exists(dp.upstreamDir() + "/" + configured)) {
            return configured;
        }
        String ls = RootShell.exec("ls -1 " + RootShell.q(dp.upstreamDir()) + " 2>/dev/null", 10).out;
        for (String raw : ls.split("\n")) {
            String n = raw.trim();
            if (n.isEmpty() || n.contains(".")) continue;
            if ("1".equals(RootShell.exec("[ -x " + RootShell.q(dp.upstreamDir() + "/" + n)
                    + " ] && echo 1", 8).text())) {
                return n;
            }
        }
        return "wb2api";
    }

    public static RootShell.Res upstreamRestart(DshPaths dp) {
        String bin = pickUpstreamBin(dp);
        String cmd =
                "D=" + RootShell.q(dp.upstreamDir()) + "; B=" + RootShell.q(bin)
                        + "; P=$D/pid; PORT=7863; " +
                "up(){ ss -ltn 2>/dev/null | grep -q \":$PORT \"; }; " +
                // 1) 杀：pid 文件 + pgrep 精确名（pgrep 是 C 实现，不会像 shell readlink 那样卡死）
                //    pid 文件先验 comm，防止文件里是别的进程（比如 dsh）时误杀
                "if [ -f $P ]; then K=$(cat $P 2>/dev/null); " +
                "[ -n \"$K\" ] && [ \"$(cat /proc/$K/comm 2>/dev/null)\" = \"$B\" ] && kill $K 2>/dev/null; fi; " +
                "for p in $(pgrep -x \"$B\" 2>/dev/null); do kill $p 2>/dev/null; done; " +
                // 2) 等端口释放，最多 8 秒，不行再 -9
                "n=0; while up && [ $n -lt 8 ]; do n=$((n+1)); sleep 1; done; " +
                "if up; then for p in $(pgrep -x \"$B\" 2>/dev/null); do kill -9 $p 2>/dev/null; done; sleep 2; fi; " +
                "if up; then echo \"7863 还在监听，杀不掉（pid 文件可能过期）\"; exit 1; fi; " +
                // 3) 拉起：环境变量照 boot-start.sh 那套，Go 不认 Android 的证书目录布局
                "cd $D || exit 1; " +
                "SSL_CERT_FILE=$D/ca-bundle.pem SSL_CERT_DIR=/system/etc/security/cacerts " +
                "nohup setsid ./\"$B\" >> $D/run.log 2>&1 < /dev/null & " +
                // 4) 等健康，最多 20 秒
                "n=0; while [ $n -lt 20 ]; do sleep 1; up && break; n=$((n+1)); done; " +
                // 5) 回写真实 pid（setsid 会 fork，$! 不是上游的 pid；必须按端口过滤取，
                //    直接 head -1 会拿到别的监听进程 —— 曾经写进去的是 dsh 的 node pid，
                //    下次重启就会把 dsh 自己杀掉）
                "NEW=$(ss -ltnp 2>/dev/null | grep \":$PORT \" | sed -n 's/.*pid=\\([0-9]*\\).*/\\1/p' | head -1); " +
                "[ -n \"$NEW\" ] && echo $NEW > $P; " +
                "if up; then echo \"7863 已重启 pid=$NEW（$B）\"; else echo \"7863 启动失败，看 run.log\"; exit 1; fi";
        return RootShell.exec(cmd, 60);
    }

    public static RootShell.Res killPort(int port) {
        return RootShell.exec("fuser -k " + port + "/tcp 2>/dev/null || "
                + "(ss -ltnp 2>/dev/null | grep ':" + port + " ' | sed -n 's/.*pid=\\([0-9]*\\).*/\\1/p' | xargs -r kill -9)", 15);
    }

    // ---------- 探测 ----------

    /**
     * 找出所有像 DSH GO 的包名。
     *
     * 判据是「私有目录下真有 rootfs」，不是只看名字 —— 只看名字会把别的 dsh 相关 App 认错。
     * 返回全部候选：DSH GO 换名重装、或者机器上装了多个 fork 时，作用域校验要认一整个集合。
     * 一个都没探到就退化成「名字带 dsh」，再没有就空列表（调用方保持默认值）。
     *
     * 一次 su 走完（每个候选单独起 su 会慢到几秒）。
     */
    public static List<String> detectDshPkgs() {
        String inner =
                "for p in $(pm list packages 2>/dev/null | sed 's/^package://'); do "
                        + "  case \"$p\" in *[Dd][Ss][Hh]*) ;; *) continue ;; esac; "
                        + "  [ \"$p\" = com.asson.dshtool ] && continue; "
                        + "  d=/data/data/$p/files; "
                        + "  if [ -d $d/runtime/rootfs ] || [ -d $d/rootfs ] "
                        + "     || [ -d /data/user/0/$p/files/runtime/rootfs ]; then echo $p; fi; "
                        + "done";
        List<String> out = uniqLines(RootShell.exec(inner, 30).out);
        if (out.isEmpty()) {
            out = uniqLines(RootShell.exec(
                    "pm list packages 2>/dev/null | sed 's/^package://' "
                            + "| grep -i dsh | grep -v '^com.asson.dshtool$'", 20).out);
        }
        return out;
    }

    /** 单个包名的快捷方式：多个候选时取第一个。 */
    public static String detectDshPkg() {
        List<String> l = detectDshPkgs();
        return l.isEmpty() ? "" : l.get(0);
    }

    public static List<String> detectDshHome() {
        // 只用精确 glob，不扫全盘：find /data/data 在真机上动辄几十秒，会撞超时。
        String cmd =
                "ls -d /data/data/*/files/runtime/rootfs/root/.dsh 2>/dev/null; " +
                        "ls -d /data/data/*/files/rootfs/root/.dsh 2>/dev/null; " +
                        "ls -d /data/local/tmp/*/rootfs/root/.dsh 2>/dev/null; " +
                        "ls -d /root/.dsh 2>/dev/null; " +
                        "ls -d /sdcard/.dsh 2>/dev/null";
        return uniqLines(RootShell.exec(cmd, 20).out);
    }

    /** 在指定包名目录下找 DSH_HOME。换包名（DSH GO 改名重装）之后用它重新对齐路径。 */
    public static String detectDshHomeFor(String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        String out = RootShell.exec(
                "for h in /data/data/" + pkg + "/files/runtime/rootfs/root/.dsh "
                        + "/data/user/0/" + pkg + "/files/runtime/rootfs/root/.dsh "
                        + "/data/data/" + pkg + "/files/rootfs/root/.dsh; do "
                        + "[ -d \"$h\" ] && echo \"$h\" && break; done", 15).text();
        if (out.isEmpty()) return null;
        return out.split("\\n")[0].trim();
    }

    /**
     * 扫共享存储里的 AGENTS.md，给「添加工作区 AGENTS.md」当候选。
     * 限深 4：/sdcard 底下动辄几万个文件，深翻会卡住。
     */
    public static List<String> detectWorkspaceAgents() {
        String cmd = "find /sdcard -maxdepth 4 -name AGENTS.md 2>/dev/null | head -30";
        return uniqLines(RootShell.exec(cmd, 30).out);
    }

    public static List<String> detectGwDir() {
        String cmd =
                "for d in /data/adb/gw /data/adb/dshgw /data/local/tmp/gw /root/gw; do " +
                        "[ -f \"$d/proxy.js\" ] && echo \"$d\"; done; " +
                        "find /data/adb /data/local/tmp -maxdepth 3 -name proxy.js 2>/dev/null | head -8";
        List<String> raw = uniqLines(RootShell.exec(cmd, 35).out);
        List<String> dirs = new ArrayList<>();
        for (String r : raw) {
            int i = r.lastIndexOf('/');
            dirs.add(i > 0 ? r.substring(0, i) : r);
        }
        return dedupe(dirs);
    }

    public static List<String> detectUpstreamDir() {
        String cmd =
                "for d in /data/adb/wb2api /data/adb/wb2api-guard; do " +
                        "[ -f \"$d/config.json\" ] && echo \"$d\"; done; " +
                        "find /data/adb -maxdepth 3 -name config.json 2>/dev/null | head -8";
        List<String> raw = uniqLines(RootShell.exec(cmd, 35).out);
        List<String> dirs = new ArrayList<>();
        for (String r : raw) {
            int i = r.lastIndexOf('/');
            dirs.add(i > 0 ? r.substring(0, i) : r);
        }
        return dedupe(dirs);
    }

    /** 在 profiles/ 下找带 cordis.patch.yml 的目录名。 */
    /** profiles/ 下所有 profile 名，给设置页的「切换 profile」用。 */
    public static List<String> detectProfiles(DshPaths dp) {
        return uniqLines(RootShell.exec(
                "ls -1 " + RootShell.q(dp.dshHome() + "/profiles") + " 2>/dev/null", 10).out);
    }

    /**
     * 在 profiles/ 下挑出「真正在用的那个」。
     *
     * 一台机器上常有好几个 profile（web / headless / dsh-app-safe …）。
     * 以前取「第一个带 cordis.patch.yml 的」，字母序一排就会挑到 dsh-app-safe ——
     * 界面读到的全是那份 217 字节的占位补丁，而不是用户实际在改的 web。
     * 现在按手机端常用顺序优先。
     */
    public static String detectProfile(DshPaths dp) {
        String dir = dp.dshHome() + "/profiles";
        List<String> names = uniqLines(RootShell.exec("ls -1 " + RootShell.q(dir) + " 2>/dev/null", 10).out);
        String[] prefer = {"web", "headless", "desktop"};
        for (String p : prefer) {
            if (names.contains(p) && RootShell.exists(dir + "/" + p + "/cordis.patch.yml")) return p;
        }
        for (String name : names) {
            if (RootShell.exists(dir + "/" + name + "/cordis.patch.yml")) return name;
        }
        return names.isEmpty() ? "" : names.get(0);
    }

    /**
     * 跑 dsh-purge 自带的 CLI。必须在 chroot 里跑 —— node 是 Linux ELF，
     * Android 侧没有；而且插件要靠 DSH_BASE 找宿主（@deepseek-ai/*），
     * 不设就是「插件根未找到 / 补丁 0/86」。
     *
     * @param args --status / --apply / --revert / --uninstall
     */
    public static String dshPurgeCli(DshPaths dp, String args, long timeoutSec) {
        String d = RootShell.q(dp.profileDirInRoot());
        String inner =
                "for b in /opt/dsh/node_modules/@deepseek-ai /opt/dsh-go/node_modules/@deepseek-ai "
                        + "/usr/lib/node_modules/@deepseek-ai /usr/local/lib/node_modules/@deepseek-ai; do "
                        + "[ -d \"$b\" ] && export DSH_BASE=\"$b\" && break; done; "
                        + "export DSH_HOME=" + RootShell.q(dp.dshHomeInRoot()) + "; "
                        + "cd " + d + " 2>/dev/null || { echo 'profile 目录不存在'; exit 1; }; "
                        + "node node_modules/dsh-purge/bin/dsh-purge.js " + args + " 2>&1";
        return execInRootfs(dp, inner, timeoutSec).out;
    }

    public static String dshPurgeStatus(DshPaths dp) {
        String out = dshPurgeCli(dp, "--status", 60);
        String[] lines = out.split("\n");
        StringBuilder sb = new StringBuilder();
        int n = Math.min(lines.length, 32);
        for (int i = 0; i < n; i++) sb.append(lines[i]).append('\n');
        return sb.toString();
    }

    // ---------------------------------------------------------------- dsh go 本体

    /**
     * dsh go 这个 App 的引擎状态。
     * @return {进程数, 3080 是否监听}
     */
    public static String[] dshGoStatus() {
        String out = RootShell.exec(
                "c=$(ps -A -o ARGS 2>/dev/null | grep 'dsh/lib/bin.js' | grep -vc grep); "
                        + "p=$(ss -ltn 2>/dev/null | grep -c ':3080 '); "
                        + "echo \"$c|$p\"", 15).text();
        String[] parts = out.trim().split("\\|");
        String procs = parts.length > 0 ? parts[0].trim() : "0";
        String port = parts.length > 1 ? parts[1].trim() : "0";
        return new String[]{procs, port};
    }

    // ---------------------------------------------------------------- 模型网关

    /**
     * 读 dsh 当前指向的模型网关。配置在 profile 的 cordis.patch.yml：
     *   - id: llm-pi-ai
     *     config.providers.custom-webapi.baseURL: http://127.0.0.1:7864/v1
     *
     * @return {baseURL, provider 名, api 类型, apiKeyEnv}；读不到返回空数组
     */
    public static String[] modelGateway(DshPaths dp) {
        String p = dp.profilePatch();
        String text = RootShell.readText(p);
        if (text == null) return new String[0];
        String url = "", provider = "", api = "", keyEnv = "";
        String[] lines = text.split("\n");
        int urlLine = -1;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (urlLine < 0 && t.startsWith("baseURL:")) {
                url = t.substring("baseURL:".length()).trim();
                urlLine = i;
            }
        }
        if (urlLine < 0) return new String[0];
        // 往上找 provider 名（缩进比 baseURL 少一级、以冒号结尾）
        for (int i = urlLine; i >= 0 && i > urlLine - 8; i--) {
            String t = lines[i].trim();
            if (t.endsWith(":") && !t.startsWith("baseURL") && !t.startsWith("config")
                    && !t.startsWith("providers") && t.indexOf(' ') < 0) {
                provider = t.substring(0, t.length() - 1);
                break;
            }
        }
        for (int i = urlLine; i < lines.length && i < urlLine + 12; i++) {
            String t = lines[i].trim();
            if (t.startsWith("api:")) api = t.substring(4).trim();
            if (t.startsWith("apiKeyEnv:")) keyEnv = t.substring("apiKeyEnv:".length()).trim();
        }
        return new String[]{url, provider, api, keyEnv};
    }

    /** 网关地址通不通。返回 {http 码, 说明}。 */
    public static String[] gatewayReach(String url) {
        if (url == null || url.isEmpty()) return new String[]{"", "没有配置"};
        String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        String code = RootShell.exec("curl -s -o /dev/null -m 4 -w '%{http_code}' "
                + RootShell.q(base + "/models") + " 2>/dev/null", 12).text();
        if (code.isEmpty() || "000".equals(code)) {
            return new String[]{"000", "连不上"};
        }
        if ("401".equals(code) || "403".equals(code)) {
            return new String[]{code, "通了但要鉴权"};
        }
        return new String[]{code, "可连"};
    }

    /** 改模型网关地址。先备份 cordis.patch.yml。 */
    public static String setGatewayUrl(DshPaths dp, String url) {
        String u = url == null ? "" : url.trim();
        if (u.isEmpty()) return "地址不能为空";
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            return "地址要以 http:// 或 https:// 开头";
        }
        String p = dp.profilePatch();
        String text = RootShell.readText(p);
        if (text == null) return "读不到 " + p;
        if (!text.contains("baseURL:")) return p + " 里没有 baseURL，可能不是 provider 配置文件";

        String bak = RootShell.backup(p, "DSHGO-" + stamp());
        String newText = text.replaceAll("(?m)^(\\s*baseURL:\\s*).*$",
                "$1" + java.util.regex.Matcher.quoteReplacement(u));
        RootShell.Res r = RootShell.writeText(p, newText);
        if (!r.ok) return "写入失败：" + r.out;

        String owner = RootShell.exec("stat -c '%u:%g' " + RootShell.q(p), 10).text();
        if (owner.matches("\\d+:\\d+")) {
            RootShell.exec("chown " + owner + " " + RootShell.q(p), 10);
        }
        String[] reach = gatewayReach(u);
        return "已改到 " + u + "\n连通性：" + reach[1] + (reach[0].isEmpty() ? "" : "（HTTP " + reach[0] + "）")
                + "\n备份：" + bak
                + "\n\n重启 dsh go 后生效。";
    }

    private static List<String> uniqLines(String out) {
        List<String> l = new ArrayList<>();
        for (String s : out.split("\n")) {
            String t = s.trim();
            if (!t.isEmpty() && !l.contains(t)) l.add(t);
        }
        return l;
    }

    private static List<String> dedupe(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) if (!out.contains(s)) out.add(s);
        return out;
    }

    // ---------------------------------------------------------------- 还原档位表

    /**
     * 署名与交流群不走字面量。
     *
     * 明文常量会被编译进 dex 的字符串池，任何人 strings 一遍就拿到了，
     * 拿去改个名字重新签个名又是一个包。这里改成整数档位表（放在 Ui，混在配色常量里），
     * 运行时才拼回文字：字符串池里没有原样的作者名和群号。
     * 想改也得先读懂这段换算，光扫字符串扫不出来。
     */
    public static String authorOf() { return rampText(Ui.TINT_COOL); }

    public static String groupOf() { return rampText(Ui.TINT_WARM); }

    /** 档位差累加回码位，再翻一层定值。偏移和掩码改了，Ui 里的表也得跟着重算。 */
    private static String rampText(int[] ramp) {
        StringBuilder sb = new StringBuilder(ramp.length);
        int acc = 0;
        for (int v : ramp) {
            acc = (acc + v - 1000) & 0xFF;
            sb.append((char) (acc ^ 0x3C));
        }
        return sb.toString();
    }
}
