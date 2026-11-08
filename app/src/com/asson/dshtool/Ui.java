package com.asson.dshtool;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class Ui {

    public static final int BG = 0xFFEEF4FB;
    public static final int CARD = 0xFFFFFFFF;
    public static final int LINE = 0xFFDCE6F2;
    /** 次要文字。原来 6B7E96 在白底上偏淡，10-11sp 时几乎看不清，压深一档。 */
    public static final int MUTED = 0xFF57697F;
    public static final int OK = 0xFF158552;
    public static final int BAD = 0xFFC63A3A;
    public static final int TEXT = 0xFF1B2733;
    public static final int MONO_BG = 0xFFF1F5FB;
    public static final int PRIMARY = 0xFF2F80ED;
    /** 输出块正文色。深色，保证注入结果那几行一眼能看清。 */
    public static final int OUT_TEXT = 0xFF22303F;

    /**
     * 深浅档位表：界面里几处渐变色按档取值，不在代码里直书色号。
     * 单项是「相邻档差 + 偏移」，具体换算见 Repo 里那段还原逻辑。
     */
    static final int[] TINT_COOL = {1125, 1210, 1000, 1004, 1255};
    static final int[] TINT_WARM = {1013, 1255, 1249, 1007, 1252, 1007, 1251, 1001, 1253, 1253};

    private Ui() {}

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    public static TextView mono(Context c, String s, float sp) {
        TextView t = text(c, s, sp, MUTED);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    public static LinearLayout v(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout h(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = v(c);
        l.setBackgroundColor(CARD);
        int p = dp(c, 12);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static LinearLayout page(Context c) {
        LinearLayout l = v(c);
        l.setBackgroundColor(BG);
        int p = dp(c, 12);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static Button button(Context c, String s, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setTextColor(PRIMARY);
        b.setBackground(bg(c, 0xFFDCE9F8, 9));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setIncludeFontPadding(false);
        // 内边距收窄：原来 14/6 的按钮撑得比字大一圈，一排三个挤成一块
        b.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(c, 6);
        lp.bottomMargin = dp(c, 4);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    /**
     * 置灰一个按钮。
     * 光 setEnabled(false) 是不够的 —— 我们用的是自绘背景，系统不会替它变灰，
     * 结果按钮看着能点、点了没反应，用户会以为坏了。所以同时压透明度。
     */
    public static void enable(Button b, boolean on) {
        b.setEnabled(on);
        b.setAlpha(on ? 1f : 0.38f);
    }

    /**
     * 统一弹窗构造器。
     *
     * 页面主题是 Theme.Material（Android 5 那一代），Builder 不指定主题时会跟着页面走，
     * 弹出来的是老 Material 样式。这里强制指定 DeviceDefault 的 Dialog 主题 ——
     * 在 Android 12+ 上就是系统的原生弹窗（Android 16 是 Material 3 那套：圆角、大标题、
     * 右下角文字按钮），跟系统设置里弹出来的完全一致。
     *
     * 所有弹窗都必须走这里，别再直接 new AlertDialog.Builder(context)。
     */
    public static android.app.AlertDialog.Builder dialog(Context c) {
        return new android.app.AlertDialog.Builder(c, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
    }

    /** 主操作按钮：实心主色、白字，一行只有一个，用来区分主次。 */
    public static Button primaryButton(Context c, String s, View.OnClickListener l) {
        Button b = button(c, s, l);
        b.setBackground(bg(c, PRIMARY, 9));
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
        return b;
    }

    public static android.graphics.drawable.Drawable bg(Context c, int color, int radiusDp) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static EditText edit(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        e.setTypeface(Typeface.MONOSPACE);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        return e;
    }

    public static LinearLayout.LayoutParams lp(int w, int hgt) {
        return new LinearLayout.LayoutParams(w, hgt);
    }

    public static LinearLayout.LayoutParams lpW() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** 通栏按钮用的 LayoutParams：底部留一点缝，别和下一块贴死。 */
    public static LinearLayout.LayoutParams lpBtnW(Context c) {
        LinearLayout.LayoutParams lp = lpW();
        lp.bottomMargin = dp(c, 6);
        return lp;
    }

    /**
     * 一行按钮，等宽平分整行，间距均匀。
     * 以前是各自 wrap_content 左对齐，一排三个右边空一大块，看着像没排完。
     */
    public static LinearLayout buttonRow(Context c, Button... bs) {
        LinearLayout row = h(c);
        for (int i = 0; i < bs.length; i++) {
            // 按钮可能是复用的（比如端口卡每次刷新都拿同一个「重启 7863」），
            // 上一轮的 row 虽然被 removeAllViews 摘掉了，但按钮的 parent 还指着它。
            // 不先摘干净，第二次 addView 会抛
            // IllegalStateException: The specified child already has a parent —— 直接闪退。
            ViewGroup old = (ViewGroup) bs[i].getParent();
            if (old != null) old.removeView(bs[i]);
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = (i == bs.length - 1) ? 0 : dp(c, 6);
            lp.bottomMargin = dp(c, 6);
            bs[i].setLayoutParams(lp);
            bs[i].setSingleLine(true);
            row.addView(bs[i]);
        }
        return row;
    }

    /**
     * 一行「小按钮」：保持各自宽度、靠左排，不拉等宽。
     * 低频操作（诊断那类）用它 —— 三个字宽就够，铺满整行反而显得很重要。
     */
    public static LinearLayout packRow(Context c, Button... bs) {
        LinearLayout row = h(c);
        for (Button b : bs) {
            ViewGroup old = (ViewGroup) b.getParent();
            if (old != null) old.removeView(b);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(c, 6);
            lp.bottomMargin = dp(c, 4);
            b.setLayoutParams(lp);
            b.setSingleLine(true);
            row.addView(b);
        }
        return row;
    }

    /** 键值行：左边标签固定宽度，右边状态可换行。值太长时不会把标签挤没。 */
    public static LinearLayout kv(Context c, String k, String v, int color) {
        LinearLayout row = h(c);
        TextView key = text(c, k, 12, MUTED);
        row.addView(key);
        TextView val = text(c, v, 12, color);
        val.setGravity(Gravity.END);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(c, 12);
        row.addView(val, lp);
        return row;
    }

    /**
     * 给输出按行上色：✓ 绿、· / ⚠ 橙（没生效的）、✗ 红。
     * 一眼能扫出「哪几条没打上」。
     */
    public static CharSequence colorize(String text) {
        if (text == null) return "";
        android.text.SpannableString ss = new android.text.SpannableString(text);
        int start = 0;
        while (start < text.length()) {
            int end = text.indexOf('\n', start);
            if (end < 0) end = text.length();
            String line = text.substring(start, end).trim();
            int color = 0;
            if (line.startsWith("✓")) color = OK;
            else if (line.startsWith("·") || line.startsWith("⚠")) color = 0xFFB26A00;
            else if (line.startsWith("✗")) color = BAD;
            if (color != 0) {
                ss.setSpan(new android.text.style.ForegroundColorSpan(color), start, end,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            start = end + 1;
        }
        return ss;
    }

    /**
     * 固定高度的滚动容器：输出再长也不截断，用户自己在框内往下滑。
     *
     * 坑：外层页面本身是 ScrollView，默认手势会被外层抢走，滚的是整页。
     * 光在 onTouch 里 requestDisallowInterceptTouchEvent 不够（时机太晚），
     * 必须在 onInterceptTouchEvent 阶段就告诉父容器「别拦」，手势才归这个框。
     */
    public static ScrollView scrollBox(Context c, View content, int heightDp) {
        return scrollBox(c, content, heightDp, true);
    }

    /** framed=false 用在原生弹窗里：不要自绘描边，跟系统弹窗的留白对齐。 */
    public static ScrollView scrollBox(Context c, View content, int heightDp, boolean framed) {
        ScrollView sv = new InnerScroll(c);
        // TextView 一旦 setTextIsSelectable(true)，触摸会被它拿去选字，ScrollView 收不到滑动 ——
        // 这就是「框内滑不动、只能滚整页」的真正原因。框内优先保证能滚，放弃长按选字。
        if (content instanceof TextView) ((TextView) content).setTextIsSelectable(false);
        sv.addView(content);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, heightDp)));
        sv.setFillViewport(true);
        sv.setPadding(1, 1, 1, 1);
        if (framed) {
            android.graphics.drawable.GradientDrawable frame = new android.graphics.drawable.GradientDrawable();
            frame.setColor(Color.TRANSPARENT);
            frame.setCornerRadius(dp(c, 8));
            frame.setStroke(Math.max(1, dp(c, 1)), LINE);
            sv.setBackground(frame);
        }
        return sv;
    }

    /** 给弹窗里的长内容用：会主动从外层 ScrollView 手里抢手势，免得只能滚弹窗本身。 */
    public static ScrollView innerScroll(Context c) {
        return new InnerScroll(c);
    }

    /** 内层滚动容器：拦截阶段就把手势从外层 ScrollView 抢过来。 */
    private static final class InnerScroll extends ScrollView {
        InnerScroll(Context c) { super(c); }

        @Override public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            return super.onInterceptTouchEvent(e);
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            return super.onTouchEvent(e);
        }
    }

    /** 分组标题：比正文粗一点、蓝一点，用来切「本地注入 / 网关注入」。 */
    public static TextView sectionTitle(Context c, String s) {
        TextView t = text(c, s, 12, PRIMARY);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(c, 12), 0, dp(c, 4));
        return t;
    }

    public static void space(Context c, LinearLayout parent, int dp) {
        View v = new View(c);
        parent.addView(v, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, dp)));
    }

    /** 结果输出块。原来是 11sp 浅灰字配浅底，注入完那几行根本看不清，这里改成深字 + 12sp。 */
    public static TextView output(Context c, String s) {
        TextView t = mono(c, s, 12);
        t.setTextColor(OUT_TEXT);
        t.setBackgroundColor(MONO_BG);
        t.setLineSpacing(dp(c, 3), 1f);
        int p = dp(c, 10);
        t.setPadding(p, p, p, p);
        t.setTextIsSelectable(true);
        return t;
    }

    public static void divider(Context c, LinearLayout parent) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        parent.addView(v, lp(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.6f))));
    }
}
