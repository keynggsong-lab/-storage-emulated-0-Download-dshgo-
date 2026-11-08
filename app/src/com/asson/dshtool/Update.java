package com.asson.dshtool;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * 云更新：拉清单 → 比版本 → 下载 → 校验 sha256 → root 静默安装。
 *
 * 只用 HttpURLConnection，不引第三方库。失败一律抛异常，由界面给一句人话。
 * 清单格式（update.json）：
 *   { "versionCode": int, "versionName": str, "url": str, "sha256": str, "size": int, "notes": str }
 */
public final class Update {

    public static final class Info {
        public int code;
        public String name = "", url = "", sha = "", notes = "";
        public long size;
    }

    private Update() {}

    public static int versionCode(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    public static String versionName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    public static Info fetch(String manifest) throws Exception {
        String body = get(manifest, 20);
        JSONObject o = new JSONObject(body);
        Info i = new Info();
        i.code = o.optInt("versionCode", 0);
        i.name = o.optString("versionName", "");
        i.url = o.optString("url", "");
        i.sha = o.optString("sha256", "");
        i.notes = o.optString("notes", "");
        i.size = o.optLong("size", 0);
        if (i.code <= 0 || i.url.isEmpty()) throw new Exception("清单内容不完整");
        return i;
    }

    private static String get(String url, int timeoutSec) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeoutSec * 1000);
        c.setReadTimeout(timeoutSec * 1000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "DshGoTool-updater");
        int code = c.getResponseCode();
        if (code / 100 != 2) throw new Exception("HTTP " + code);
        InputStream in = c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        c.disconnect();
        return new String(bo.toByteArray(), "UTF-8");
    }

    /** 下到 cache 目录，校验 sha256；不匹配就删掉并抛异常。 */
    public static File download(Context c, Info info) throws Exception {
        File dir = new File(c.getCacheDir(), "update");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("缓存目录建不出来");
        File out = new File(dir, "DshGoTool-" + info.name + ".apk");
        HttpURLConnection conn = (HttpURLConnection) new URL(info.url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "DshGoTool-updater");
        int code = conn.getResponseCode();
        if (code / 100 != 2) throw new Exception("下载失败 HTTP " + code);
        InputStream in = conn.getInputStream();
        FileOutputStream fo = new FileOutputStream(out);
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
        fo.close();
        in.close();
        conn.disconnect();
        if (info.sha != null && !info.sha.isEmpty()) {
            if (!sha256(out).equalsIgnoreCase(info.sha)) {
                out.delete();
                throw new Exception("校验不通过（sha256 不一致），已丢弃");
            }
        }
        return out;
    }

    public static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        InputStream in = new java.io.FileInputStream(f);
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        in.close();
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** root 静默安装。返回 "OK" 表示成功，其它值是 shell 回显，交给界面展示。 */
    public static String install(File apk) {
        RootShell.Res r = RootShell.exec("pm install -r " + RootShell.q(apk.getAbsolutePath()), 180);
        String out = r.out == null ? "" : r.out.trim();
        if (out.contains("Success")) return "OK";
        return out.isEmpty() ? "安装失败（没有回显）" : out;
    }
}
