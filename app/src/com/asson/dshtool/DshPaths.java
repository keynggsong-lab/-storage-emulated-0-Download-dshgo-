package com.asson.dshtool;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * 路径全部可配置。安卓的 dsh go 跑在 proot 里，
 * /root/.dsh 从 Android 侧不可见，要填 rootfs 下的真实路径。
 * profile 名在移动端是 web（桌面端才是 desktop）。
 */
public final class DshPaths {

    private final SharedPreferences sp;

    public DshPaths(Context ctx) {
        this.sp = ctx.getSharedPreferences("dsh_paths", Context.MODE_PRIVATE);
    }

    /** 上游（网关第一层）的目录。pref key 沿用旧名，免得已装用户升级后丢配置。 */
    public String upstreamDir() { return sp.getString("wb2api", "/data/adb/wb2api"); }
    public void setUpstreamDir(String v) { sp.edit().putString("wb2api", v).apply(); }

    /**
     * 上游程序的文件名（目录里那个可执行文件）。各家不一样，默认 wb2api；
     * 留空时由 Repo 在目录里自动挑第一个没有扩展名的可执行文件。
     */
    public String upstreamBin() { return sp.getString("upstream_bin", ""); }
    public void setUpstreamBin(String v) { sp.edit().putString("upstream_bin", v).apply(); }

    public String guardDir() { return sp.getString("guard", "/data/adb/wb2api-guard"); }
    public void setGuardDir(String v) { sp.edit().putString("guard", v).apply(); }

    /** 网关预处理层在 proot 的 /root/workspace/gw，Android 侧即 dshHome 的上一级 + workspace/gw。 */
    public String gwDir() {
        String v = sp.getString("gw", "");
        if (v != null && !v.isEmpty()) return v;
        String home = dshHome();
        int i = home.lastIndexOf('/');
        String root = i > 0 ? home.substring(0, i) : home;
        return root + "/workspace/gw";
    }
    public void setGwDir(String v) { sp.edit().putString("gw", v).apply(); }

    public String dshHome() { return sp.getString("dshhome", "/root/.dsh"); }
    public void setDshHome(String v) { sp.edit().putString("dshhome", v).apply(); }

    /**
     * 用户手选的 DSH GO 包名。空 = 没选过，启动时按探测结果自动定。
     * DSH GO 换名重装后这个值会失效，启动检查发现它不在已装列表里就自动重置并重新探测。
     */
    public String pkgOverride() { return sp.getString("pkg", ""); }
    public void setPkgOverride(String v) { sp.edit().putString("pkg", v).apply(); }

    /**
     * rootfs 根目录。dshHome 形如 &lt;rootfs&gt;/root/.dsh，砍掉尾部即可。
     * 网关脚本必须在 chroot 进 rootfs 之后执行 —— node 是 Linux ELF，
     * 依赖 proot/chroot 提供的 /lib/ld-linux-aarch64.so.1，Android 侧直接跑会报
     * "No such file or directory"。
     */
    public String rootfs() {
        String h = dshHome();
        int i = h.indexOf("/root/.dsh");
        return i > 0 ? h.substring(0, i) : "";
    }

    /** 网关目录在 rootfs 内的路径（去掉 rootfs 前缀），给 chroot 后的命令用。 */
    public String gwDirInRoot() {
        String rf = rootfs();
        String g = gwDir();
        if (rf != null && !rf.isEmpty() && g.startsWith(rf)) {
            return g.substring(rf.length());
        }
        return "/root/workspace/gw";
    }

    /** profile 目录在 rootfs 内的路径，给 chroot 后的命令用。 */
    public String profileDirInRoot() {
        String rf = rootfs();
        String p = profileDir();
        if (rf != null && !rf.isEmpty() && p.startsWith(rf)) {
            return p.substring(rf.length());
        }
        return "/root/.dsh/profiles/" + profileName();
    }

    /** DSH_HOME 在 rootfs 内的路径。 */
    public String dshHomeInRoot() {
        String rf = rootfs();
        String h = dshHome();
        if (rf != null && !rf.isEmpty() && h.startsWith(rf)) {
            return h.substring(rf.length());
        }
        return "/root/.dsh";
    }

    /**
     * 更新清单地址。默认走 gh-proxy 反代 GitHub raw —— 国内直连 raw 基本拉不动，
     * 这个反代能通；换成自己的服务器就改这一处。
     */
    public String updateUrl() {
        String v = sp.getString("update_url", "");
        if (v != null && !v.isEmpty()) return v;
        return "https://gh-proxy.com/https://raw.githubusercontent.com/keynggsong-lab/"
                + "-storage-emulated-0-Download-dshgo-/main/update.json";
    }
    public void setUpdateUrl(String v) { sp.edit().putString("update_url", v).apply(); }

    /** 启动时自动查一次更新。关掉就只在设置页手动查。 */
    public boolean updateAuto() { return sp.getBoolean("update_auto", true); }
    public void setUpdateAuto(boolean v) { sp.edit().putBoolean("update_auto", v).apply(); }

    public String profileName() { return sp.getString("profile", "web"); }
    public void setProfileName(String v) { sp.edit().putString("profile", v).apply(); }

    /**
     * profile 是不是用户手动定的。手动定过就不再被自动探测覆盖 ——
     * 一台机器上 web / headless / dsh-app-safe 可能同时在，探测只能猜。
     */
    public boolean profileLocked() { return sp.getBoolean("profile_lock", false); }
    public void setProfileLocked(boolean v) { sp.edit().putBoolean("profile_lock", v).apply(); }

    /** 上游每请求读取，真正发给模型的 system。 */
    public String upstreamPrompt() { return upstreamDir() + "/prompt-inject.md"; }
    public String upstreamConfig() { return upstreamDir() + "/config.json"; }
    public String upstreamLog() { return upstreamDir() + "/run.log"; }

    /** guard 的兜底副本，网关挂了由 guard.sh / jiapo.sh 写回。 */
    public String guardPrompt() { return guardDir() + "/prompt-inject.md"; }
    public String guardStatus() { return guardDir() + "/status.json"; }
    public String guardLog() { return guardDir() + "/guard.log"; }

    /** dsh-purge 的 override，每轮 system 组装时现读。 */
    public String dshPrompt() { return dshHome() + "/prompt-inject.md"; }
    public String agents() { return dshHome() + "/AGENTS.md"; }
    /**
     * 工作区那份 AGENTS.md —— 在共享存储上（比如 /storage/emulated/0/.dsh/AGENTS.md），
     * 不在 rootfs 里。每个人的工作区路径都不一样，而且要先在 DSH GO 里建过工作区才有，
     * 所以这里不写死路径：由用户自己在设置页添加，想加几条加几条。
     */
    public List<String> workspaceAgents() {
        List<String> out = new ArrayList<>();
        String raw = sp.getString("ws_agents", "");
        for (String line : raw.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    public void addWorkspaceAgents(String path) {
        String p = path == null ? "" : path.trim();
        if (p.isEmpty()) return;
        List<String> cur = workspaceAgents();
        if (cur.contains(p)) return;
        cur.add(p);
        saveWorkspaceAgents(cur);
    }

    public void removeWorkspaceAgents(String path) {
        List<String> cur = workspaceAgents();
        if (cur.remove(path)) saveWorkspaceAgents(cur);
    }

    private void saveWorkspaceAgents(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(s);
        }
        sp.edit().putString("ws_agents", sb.toString()).apply();
    }
    public String persona() { return dshHome() + "/personaPrefix.txt"; }
    public String profilePatch() { return dshHome() + "/profiles/" + profileName() + "/cordis.patch.yml"; }
    public String profileDir() { return dshHome() + "/profiles/" + profileName(); }

    /** 网关预处理层（7864 上的 node proxy）。 */
    public String gwProxy() { return gwDir() + "/proxy.js"; }
    public String gwTrigger() { return gwDir() + "/trigger.txt"; }
    public String gwWarm() { return gwDir() + "/warm.json"; }
    public String gwEnv() { return gwDir() + "/gw.env"; }
    public String gwLog() { return gwDir() + "/gw.log"; }

    /**
     * {label, path, hint, group} —— hint 写清楚这份文件管什么、改完怎么生效。
     * 状态页与提示词页都直接显示，不让人猜。
     * 按注入层分组：本地注入（宿主自己组 system）/ 网关注入（网关代理与上游）。
     */
    public List<String[]> keyFiles() {
        List<String[]> l = new ArrayList<>();

        // ---- 本地注入 · dsh 侧 ----
        l.add(new String[]{"AGENTS.md", agents(),
                "全局指令，所有工作区都用这份。改完新开一个会话生效。", G_LOCAL});
        // 工作区那份是用户自己加进来的，路径各不相同；没加就一条也不列
        for (String wa : workspaceAgents()) {
            l.add(new String[]{"工作区 AGENTS.md", wa,
                    "只在这个工作区生效，换工作区就不带了。写这个工作区特有的规矩。", G_LOCAL});
        }
        l.add(new String[]{"personaPrefix", persona(),
                "人格前缀。有的版本已经并进 AGENTS.md，没有这个文件是正常的。", G_LOCAL});
        l.add(new String[]{"cordis.patch.yml", profilePatch(),
                "这个 profile 的设置：模型、插件开关。改完重启 DSH GO。", G_LOCAL});
        l.add(new String[]{"dsh-purge override", dshPrompt(),
                "插件写进去的系统提示词。没装插件就是空的。", G_LOCAL});

        // ---- 网关注入 · 网关侧 ----
        l.add(new String[]{"网关 system", upstreamPrompt(),
                "走网关时真正发给模型的那份。改完下一条消息生效，不用重启。", G_GW});
        l.add(new String[]{"兜底副本", guardPrompt(),
                "兜底副本。网关出故障时自动写回上面那份，平时不用管。", G_GW});
        l.add(new String[]{"网关 config", upstreamConfig(),
                "网关自己的配置。改完要重启网关才生效。", G_GW});
        l.add(new String[]{"trigger.txt", gwTrigger(),
                "触发词：命中才注入预热。改完重启网关。", G_GW});
        l.add(new String[]{"warm.json", gwWarm(),
                "预热问答：会话开头先垫几句。改完重启网关。", G_GW});
        return l;
    }

    public static final String G_LOCAL = "本地注入 · DSH GO 自己读";
    public static final String G_GW = "网关注入 · 装了网关才有";
}
