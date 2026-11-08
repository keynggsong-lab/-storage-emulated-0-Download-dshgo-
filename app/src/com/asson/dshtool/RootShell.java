package com.asson.dshtool;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 所有落盘与进程操作都经 su。每次调用单独起进程，不常驻 shell。 */
public final class RootShell {

    public static final class Res {
        public final boolean ok;
        public final String out;
        public final int code;

        Res(boolean ok, String out, int code) {
            this.ok = ok;
            this.out = out;
            this.code = code;
        }

        public String text() { return out.trim(); }
    }

    private RootShell() {}

    public static synchronized Res exec(String cmd, long timeoutSec) {
        // App 在自己的 mount namespace 里，/data/data 只挂了自己的目录。
        // -M 切到全局 namespace，才能看到别的 App 的私有目录。
        Res r = execRaw(cmd, timeoutSec, true);
        if (!r.ok && (r.out.contains("unknown option") || r.out.contains("invalid option")
                || r.out.contains("Usage"))) {
            r = execRaw(cmd, timeoutSec, false);
        }
        return r;
    }

    private static Res execRaw(String cmd, long timeoutSec, boolean mountMaster) {
        Process p = null;
        try {
            java.util.List<String> argv = new java.util.ArrayList<>();
            argv.add("su");
            if (mountMaster) argv.add("-M");
            argv.add("-c");
            argv.add(cmd);
            p = new ProcessBuilder(argv).redirectErrorStream(true).start();

            // 超时看门狗：必须独立线程。
            // 以前是「先读完 stdout 再 waitFor(timeout)」，但 read() 会一直阻塞到子进程
            // 关闭 stdout —— su 弹授权框没人点、或脚本卡死时 read 永不返回，
            // waitFor 那句永远执行不到，超时形同虚设，UI 会永久卡在「执行中」。
            final Process target = p;
            Thread watchdog = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        if (!target.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                            target.destroyForcibly();
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
            watchdog.setDaemon(true);
            watchdog.start();

            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            char[] buf = new char[4096];
            int n;
            while ((n = br.read(buf)) > 0) sb.append(buf, 0, n);
            br.close();

            // read 返回 EOF 后进程可能还没被回收，用 waitFor 确认，避免 isAlive 误判超时
            boolean finished = p.waitFor(2, TimeUnit.SECONDS);
            int code = finished ? p.exitValue() : -1;
            if (!finished) {
                p.destroyForcibly();
                sb.append("\n[timeout ").append(timeoutSec).append("s]");
            }
            return new Res(code == 0, sb.toString(), code);
        } catch (Exception e) {
            if (p != null) p.destroyForcibly();
            return new Res(false, String.valueOf(e), -1);
        }
    }

    public static Res exec(String cmd) { return exec(cmd, 25); }

    public static boolean hasRoot() { return "0".equals(exec("id -u", 8).text()); }

    public static String q(String s) { return "'" + s.replace("'", "'\\''") + "'"; }

    public static boolean exists(String path) {
        return "1".equals(exec("[ -e " + q(path) + " ] && echo 1 || echo 0", 8).text());
    }

    public static String readText(String path) {
        Res r = exec("cat " + q(path), 25);
        return r.ok ? r.out : null;
    }

    /** 用 base64 传输，避开引号、反引号、$ 在 shell 里的转义。 */
    public static Res writeText(String path, String text) {
        String b64 = Base64.encodeToString(text.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        return exec("printf '%s' '" + b64 + "' | base64 -d > " + q(path), 40);
    }

    public static boolean copy(String from, String to) {
        return exec("cp -f " + q(from) + " " + q(to), 20).ok;
    }

    public static String backup(String path, String stamp) {
        String bak = path + ".bak-MR-" + stamp;
        return copy(path, bak) ? bak : null;
    }

    public static List<String> listBackups(String path) {
        int i = path.lastIndexOf('/');
        if (i <= 0) return Collections.emptyList();
        String parent = path.substring(0, i);
        String name = path.substring(i + 1);
        List<String> out = new ArrayList<>();
        for (String line : exec("ls -1 " + q(parent) + "/ 2>/dev/null", 12).out.split("\n")) {
            String t = line.trim();
            if (t.startsWith(name + ".bak-")) out.add(t);
        }
        Collections.sort(out, Collections.reverseOrder());
        return out;
    }

    public static String tail(String path, int lines) {
        return exec("tail -n " + lines + " " + q(path) + " 2>/dev/null", 15).out;
    }

    public static String q2(String s) { return q(s); }
}
