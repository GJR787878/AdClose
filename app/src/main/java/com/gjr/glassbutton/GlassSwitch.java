package com.gjr.glassbutton;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;

import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * 玻璃拟态开关（与玻璃按钮/导航同风格）。
 *
 * 视觉：
 *   - 关闭态：半透明深灰玻璃轨道（25% 白），浅玻璃白拇指
 *   - 开启态：玻璃蓝（0xFF0A84FF）轨道，纯白拇指
 *
 * 用法：直接在 preference widget 布局或普通布局里替换 MaterialSwitch / Switch。
 */
public class GlassSwitch extends MaterialSwitch {

    public GlassSwitch(Context context) {
        this(context, null);
    }

    public GlassSwitch(Context context, AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.materialSwitchStyle);
    }

    public GlassSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        applyGlass();
    }

    private void applyGlass() {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        // 轨道：开 = 玻璃蓝；关 = 25% 白（玻璃深灰底）
        int[] trackColors = new int[]{
                GlassButtonStyle.COLOR_ACCENT,
                0x40FFFFFF
        };
        // 拇指：开 = 纯白；关 = 浅玻璃白
        int[] thumbColors = new int[]{
                0xFFFFFFFF,
                0xFFEDEDED
        };
        setTrackTintList(new ColorStateList(states, trackColors));
        setThumbTintList(new ColorStateList(states, thumbColors));
    }
}
