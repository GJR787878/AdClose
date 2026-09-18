package com.gjr.glassbutton;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.CompoundButton;

/**
 * 玻璃拟态胶囊开关（ToggleButton 形态，非滑轨）。
 *
 * 视觉与 GlassCapsuleButton 一致：玻璃胶囊背景 + 选中态玻璃蓝高亮。
 * checked = 选中（开启），文字显示 ON；未 checked 显示 OFF。
 * 继承 CompoundButton，可直接作为 SwitchPreferenceCompat 的 widget 使用。
 */
public class GlassToggleButton extends CompoundButton {

    private GlassButtonDrawable mGlassDrawable;

    public GlassToggleButton(Context context) {
        this(context, null);
    }

    public GlassToggleButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GlassToggleButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setAllCaps(false);
        setTextSize(13);
        float density = getResources().getDisplayMetrics().density;
        setPadding(Math.round(20 * density), Math.round(10 * density),
                Math.round(20 * density), Math.round(10 * density));
        applyGlass();
    }

    private void applyGlass() {
        float density = getResources().getDisplayMetrics().density;
        boolean checked = isChecked();
        float radius = 22f * density;
        float borderPx = checked ? 2f : 1f;
        mGlassDrawable = new GlassButtonDrawable(radius, borderPx, checked);
        setBackground(mGlassDrawable);
        setTextColor(checked ? GlassButtonStyle.COLOR_ACCENT : GlassButtonStyle.COLOR_WHITE);
        setText(checked ? "ON" : "OFF");
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        applyGlass();
    }
}
