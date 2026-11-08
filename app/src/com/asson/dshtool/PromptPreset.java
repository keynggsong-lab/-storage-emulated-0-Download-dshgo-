package com.asson.dshtool;

import android.app.Activity;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 提示词方案槽。
 *
 * 存的是一份「快照」：把当前各层的提示词原样抓下来放进 App 私有目录，
 * 之后随时可以一键写回。文件放 App 私有目录而不是 dsh 目录 ——
 * 一是不往 dsh 的目录里塞额外东西，二是快照本身不需要 root 就能读。
 *
 * 方案数量不固定：全新安装时一个都没有，用户点「增加方案」自己建，想存几套存几套。
 * 槽号从 1 往上加，删掉的号不回收（避免老目录被复用接错内容）。
 *
 * 只收提示词类文件。config.json / trigger.txt / warm.json / cordis.patch.yml
 * 一律不进方案：那些是网关运行配置，一键替换把 baseURL 退回旧值会直接把网关弄断。
 */
public final class PromptPreset {

    private static final String MANIFEST = "manifest.txt";
    private static final String META = "meta.txt";
    private static final String NAME = "name.txt";

    private PromptPreset() {}

    /** 哪些文件算「提示词」，进方案。工作区那份是用户自加的，按前缀认。 */
    public static boolean inScope(String label) {
        return "AGENTS.md".equals(label)
                || (label != null && label.startsWith("工作区 AGENTS.md"))
                || "personaPrefix".equals(label)
                || "dsh-purge override".equals(label)
                || (label != null && label.startsWith("网关 system"))
                || "兜底副本".equals(label);
    }

    private static File root(Activity a) {
        return new File(a.getFilesDir(), "presets");
    }

    private static File dir(Activity a, int slot) {
        return new File(root(a), "p" + slot);
    }

    /** 现有方案槽号，升序。全新安装是空数组 —— App 本身不带任何方案内容。 */
    public static int[] slots(Activity a) {
        File[] fs = root(a).listFiles();
        List<Integer> ns = new ArrayList<>();
        if (fs != null) {
            for (File f : fs) {
                if (f == null || !f.isDirectory()) continue;
                String n = f.getName();
                if (n == null || !n.startsWith("p") || n.length() < 2) continue;
                try {
                    ns.add(Integer.parseInt(n.substring(1)));
                } catch (Exception ignored) {
                }
            }
        }
        java.util.Collections.sort(ns);
        int[] out = new int[ns.size()];
        for (int i = 0; i < out.length; i++) out[i] = ns.get(i);
        return out;
    }

    /** 增加一个空方案，返回槽号；建不出来返回 -1。 */
    public static int addSlot(Activity a) {
        int max = 0;
        for (int s : slots(a)) if (s > max) max = s;
        int slot = max + 1;
        File d = dir(a, slot);
        if (!d.exists() && !d.mkdirs()) return -1;
        return slot;
    }

    /** 删掉一个方案，连带里面存的快照。 */
    public static boolean removeSlot(Activity a, int slot) {
        return wipe(dir(a, slot));
    }

    private static boolean wipe(File f) {
        if (f == null || !f.exists()) return false;
        File[] cs = f.listFiles();
        if (cs != null) {
            for (File c : cs) wipe(c);
        }
        return f.delete();
    }

    /** {是否已保存, 文件数, 保存时间} */
    public static String[] info(Activity a, int slot) {
        File m = new File(dir(a, slot), META);
        if (!m.exists()) return new String[]{"0", "0", ""};
        try {
            List<String> ls = lines(m);
            if (ls.size() < 2) return new String[]{"0", "0", ""};
            return new String[]{"1", ls.get(1).trim(), ls.get(0).trim()};
        } catch (Exception e) {
            return new String[]{"0", "0", ""};
        }
    }

    /** 把当前提示词存成这个方案。返回 "已存n|缺m"，失败返回 null。 */
    public static String save(Activity a, int slot, DshPaths dp) {
        File d = dir(a, slot);
        if (!d.exists() && !d.mkdirs()) return null;

        // 先清掉上次留下的 dat，否则文件数变少时会有残留被误读
        File[] old = d.listFiles();
        if (old != null) {
            for (File f : old) {
                if (f.getName().endsWith(".dat")) f.delete();
            }
        }

        StringBuilder man = new StringBuilder();
        int n = 0, miss = 0;
        for (String[] kv : dp.keyFiles()) {
            if (!inScope(kv[0])) continue;
            String body = RootShell.readText(kv[1]);
            if (body == null) { miss++; continue; }
            if (!write(new File(d, "f" + n + ".dat"), body)) { miss++; continue; }
            man.append(kv[0]).append('\t').append(kv[1]).append('\n');
            n++;
        }
        write(new File(d, MANIFEST), man.toString());
        write(new File(d, META), stamp() + "\n" + n + "\n");
        return n + "|" + miss;
    }

    /** 把方案写回各层。返回逐行结果，带 ✓ / ✗，直接给输出框上色。 */
    public static List<String> apply(Activity a, int slot) {
        List<String> log = new ArrayList<>();
        File d = dir(a, slot);
        File m = new File(d, MANIFEST);
        if (!m.exists()) {
            log.add("✗ 方案 " + slot + " 还没存过内容");
            return log;
        }
        List<String> man;
        try {
            man = lines(m);
        } catch (Exception e) {
            log.add("✗ 读方案清单失败");
            return log;
        }
        int ok = 0;
        for (int i = 0; i < man.size(); i++) {
            String line = man.get(i).trim();
            if (line.isEmpty()) continue;
            int t = line.indexOf('\t');
            if (t <= 0) continue;
            String label = line.substring(0, t);
            String path = line.substring(t + 1);
            String body = read(new File(d, "f" + i + ".dat"));
            if (body == null) {
                log.add("✗ " + label + "：方案里没有内容");
                continue;
            }
            String bak = RootShell.backup(path, Repo.stamp());
            RootShell.Res r = RootShell.writeText(path, body);
            if (r.ok) {
                ok++;
                log.add("✓ " + label + "（" + body.length() + " 字符）"
                        + (bak == null ? "" : " · 备份 " + shortName(bak)));
            } else {
                log.add("✗ " + label + " 写入失败：" + r.out.trim());
            }
        }
        if (log.isEmpty()) log.add("✗ 方案 " + slot + " 是空的");
        log.add("");
        log.add(ok > 0
                ? "写回 " + ok + " 层。每层写前都留了 .bak-MR-" + Repo.stamp() + " 备份，想退回点单文件的「还原」。"
                : "一层都没写进去，看上面的 ✗。");
        return log;
    }

    /**
     * 导入一个本地文件，铺到这个方案里所有 prompt-inject.md 层。
     * 方案还没存过就先自动存一份 —— 否则用户点「导入」只会看到「先存为方案」这种卡壳提示。
     */
    public static List<String> importBody(Activity a, int slot, DshPaths dp, String body) {
        List<String> log = new ArrayList<>();
        File d = dir(a, slot);
        File m = new File(d, MANIFEST);
        if (!m.exists()) {
            if (save(a, slot, dp) == null) {
                log.add("✗ 建方案目录失败");
                return log;
            }
        }
        List<String> man;
        try {
            man = lines(m);
        } catch (Exception e) {
            log.add("✗ 读方案清单失败");
            return log;
        }
        int hit = 0;
        for (int i = 0; i < man.size(); i++) {
            String line = man.get(i).trim();
            int t = line.indexOf('\t');
            if (t <= 0) continue;
            if (!line.substring(t + 1).endsWith("prompt-inject.md")) continue;
            if (write(new File(d, "f" + i + ".dat"), body)) hit++;
        }
        if (hit == 0) {
            log.add("✗ 这个方案里没有 prompt-inject.md 层");
            return log;
        }
        log.add("✓ 已导入到方案 " + slot + " 的 " + hit + " 个提示词层（" + body.length() + " 字符）");
        log.add("");
        log.add("这只是存进方案，还没动设备上的文件。要生效点「一键替换」。");
        return log;
    }

    /**
     * 导入：单文件或 zip 都走这里，按内容自动判别。
     * zip 的魔数是 PK\x03\x04，认字节不认扩展名 —— 用户把 zip 改名成 .txt 也照样能进。
     */
    public static List<String> importAny(Activity a, int slot, DshPaths dp, byte[] data) {
        if (data == null || data.length == 0) {
            List<String> log = new ArrayList<>();
            log.add("✗ 选的文件是空的");
            return log;
        }
        boolean zip = data.length > 4 && data[0] == 'P' && data[1] == 'K';
        if (zip) return importZip(a, slot, dp, data);
        String body;
        try {
            body = new String(data, "UTF-8");
        } catch (Exception e) {
            List<String> log = new ArrayList<>();
            log.add("✗ 按 UTF-8 读不出来");
            return log;
        }
        return importBody(a, slot, dp, body);
    }

    /**
     * 从 zip 导入。zip 里可以有多个文件，按**文件名**归位（放子目录也行，只认 basename）：
     *   prompt-inject.md  → 所有提示词层（dsh / wb2api / guard）
     *   AGENTS.md         → 工作区指令
     *   personaPrefix.txt → 人格前缀
     * 其他文件名忽略，并在结果里列出来，免得用户以为没导进去。
     */
    public static List<String> importZip(Activity a, int slot, DshPaths dp, byte[] data) {
        List<String> log = new ArrayList<>();
        File d = dir(a, slot);
        if (!new File(d, MANIFEST).exists() && save(a, slot, dp) == null) {
            log.add("✗ 建方案目录失败");
            return log;
        }
        List<String> man;
        try {
            man = lines(new File(d, MANIFEST));
        } catch (Exception e) {
            log.add("✗ 读方案清单失败");
            return log;
        }

        int cPrompt = 0, cAgents = 0, cPersona = 0, cFiles = 0;
        List<String> ignored = new ArrayList<>();
        java.util.zip.ZipInputStream zin = null;
        try {
            zin = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(data));
            java.util.zip.ZipEntry e;
            byte[] buf = new byte[16 * 1024];
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String base = baseName(e.getName());
                if (base.isEmpty() || base.startsWith(".")) continue;   // .DS_Store / __MACOSX 之类
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                int n;
                while ((n = zin.read(buf)) > 0) bo.write(buf, 0, n);
                String body = new String(bo.toByteArray(), "UTF-8");
                int hit = writeToLayers(d, man, base, body);
                if (hit == 0) {
                    ignored.add(base);
                    continue;
                }
                cFiles++;
                if (base.equalsIgnoreCase("prompt-inject.md")) cPrompt += hit;
                else if (base.equalsIgnoreCase("AGENTS.md")) cAgents += hit;
                else cPersona += hit;
            }
        } catch (Exception ex) {
            log.add("✗ 解压失败：" + ex.getMessage());
            return log;
        } finally {
            if (zin != null) try { zin.close(); } catch (Exception ignored2) {}
        }

        if (cFiles == 0) {
            log.add("✗ zip 里没有能认出来的文件名");
            log.add("");
            log.add("认这三个名字（放子目录也行）：");
            log.add("  prompt-inject.md  → 所有提示词层");
            log.add("  AGENTS.md         → 工作区指令");
            log.add("  personaPrefix.txt → 人格前缀");
            if (!ignored.isEmpty()) log.add("这次看到的文件：" + joinShort(ignored));
            return log;
        }
        if (cPrompt > 0) log.add("✓ prompt-inject.md → " + cPrompt + " 个提示词层");
        if (cAgents > 0) log.add("✓ AGENTS.md → " + cAgents + " 个文件");
        if (cPersona > 0) log.add("✓ personaPrefix.txt → " + cPersona + " 个文件");
        if (!ignored.isEmpty()) log.add("· 忽略 " + ignored.size() + " 个不认识的文件：" + joinShort(ignored));
        log.add("");
        log.add("已存进方案 " + slot + "，共 " + cFiles + " 个文件。点「一键替换」才写回设备。");
        return log;
    }

    /** 按 basename 把内容写进方案里对应的层，返回写了几层。 */
    private static int writeToLayers(File d, List<String> man, String base, String body) {
        int hit = 0;
        for (int i = 0; i < man.size(); i++) {
            String line = man.get(i).trim();
            int t = line.indexOf('\t');
            if (t <= 0) continue;
            String path = line.substring(t + 1);
            String low = path.toLowerCase();
            boolean match;
            if (base.equalsIgnoreCase("prompt-inject.md")) {
                match = low.endsWith("prompt-inject.md");
            } else if (base.equalsIgnoreCase("AGENTS.md")) {
                match = low.endsWith("/agents.md") || low.equals("agents.md");
            } else if (base.toLowerCase().startsWith("personaprefix")) {
                match = low.contains("personaprefix");
            } else {
                match = false;
            }
            if (!match) continue;
            if (write(new File(d, "f" + i + ".dat"), body)) hit++;
        }
        return hit;
    }

    private static String baseName(String path) {
        if (path == null) return "";
        String s = path.replace('\\', '/');
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    private static String joinShort(List<String> ls) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String s : ls) {
            if (n >= 5) { sb.append("…"); break; }
            if (sb.length() > 0) sb.append("、");
            sb.append(s);
            n++;
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ 名字
    public static String name(Activity a, int slot) {
        File f = new File(dir(a, slot), NAME);
        if (!f.exists()) return "方案 " + slot;
        String s = read(f);
        if (s == null) return "方案 " + slot;
        s = s.trim();
        return s.isEmpty() ? ("方案 " + slot) : s;
    }

    public static boolean setName(Activity a, int slot, String name) {
        File d = dir(a, slot);
        if (!d.exists() && !d.mkdirs()) return false;
        String s = name == null ? "" : name.trim();
        if (s.length() > 16) s = s.substring(0, 16);
        return write(new File(d, NAME), s);
    }

    // ------------------------------------------------------------ 查看内容

    /** 方案里的一个文件。 */
    public static final class Item {
        public String label = "";
        public String path = "";
        public String body = "";
    }

    /** 读方案里存的全部内容。方案没存过返回空表。 */
    public static List<Item> load(Activity a, int slot) {
        List<Item> out = new ArrayList<>();
        File d = dir(a, slot);
        File m = new File(d, MANIFEST);
        if (!m.exists()) return out;
        List<String> man;
        try {
            man = lines(m);
        } catch (Exception e) {
            return out;
        }
        for (int i = 0; i < man.size(); i++) {
            String line = man.get(i).trim();
            if (line.isEmpty()) continue;
            int t = line.indexOf('\t');
            if (t <= 0) continue;
            Item it = new Item();
            it.label = line.substring(0, t);
            it.path = line.substring(t + 1);
            String b = read(new File(d, "f" + i + ".dat"));
            it.body = b == null ? "" : b;
            out.add(it);
        }
        return out;
    }

    /**
     * 一次 su 拿齐各路径的 md5，用来判断「方案里这份跟设备上现在这份是不是同一个」。
     * 逐条读文件要起五次 su，慢一倍多；md5 一次就够，也省内存。
     */
    public static java.util.Map<String, String> currentMd5(List<String> paths) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        if (paths.isEmpty()) return map;
        StringBuilder cmd = new StringBuilder();
        for (String p : paths) {
            String q = RootShell.q(p);
            cmd.append("if [ -e ").append(q).append(" ]; then printf '%s ' ").append(q)
               .append("; md5sum ").append(q).append(" | cut -c1-32; else echo ").append(q)
               .append(" MISSING; fi; ");
        }
        String out = RootShell.exec(cmd.toString(), 30).out;
        for (String line : out.split("\n")) {
            String t = line.trim();
            int sp = t.lastIndexOf(' ');
            if (sp <= 0) continue;
            map.put(t.substring(0, sp), t.substring(sp + 1));
        }
        return map;
    }

    /** 字符串的 md5，跟设备上 md5sum 的口径一致（UTF-8 字节）。 */
    public static String md5(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------ 文件读写

    private static boolean write(File f, String text) {
        FileOutputStream o = null;
        try {
            o = new FileOutputStream(f);
            o.write(text.getBytes("UTF-8"));
            o.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (o != null) try { o.close(); } catch (Exception ignored) {}
        }
    }

    private static String read(File f) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
        }
    }

    private static List<String> lines(File f) throws Exception {
        List<String> out = new ArrayList<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        String s;
        while ((s = br.readLine()) != null) out.add(s);
        br.close();
        return out;
    }

    private static String stamp() {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date());
    }

    private static String shortName(String p) {
        if (p == null) return "";
        int i = p.lastIndexOf('/');
        return i > 0 ? p.substring(i + 1) : p;
    }
}
