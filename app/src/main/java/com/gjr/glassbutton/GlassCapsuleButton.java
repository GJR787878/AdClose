package com.gjr.glassbutton;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.Button;

/**
 * 玻璃拟态胶囊按钮。
 *
 * 视觉效果（参考 iOS 26 Liquid Glass / 深色玻璃按钮）：
 *   - 半透明深色填充 + 顶部白色高光渐变 + 底部内阴影 + 上亮下暗渐变描边
 *   - 默认态：通透玻璃底、细描边、白色文字
 *   - 选中态（glassSelected=true）：填充加深、描边加粗加亮、蓝色文字（0xFF0A84FF）
 *   - 按压态：填充轻微提亮
 *
 * 用法一（XML，需 attrs.xml）：
 *   <com.gjr.glassbutton.GlassCapsuleButton
 *       android:layout_width="wrap_content"
 *       android:layout_height="wrap_content"
 *       android:text="按钮"
 *       app:glassSelected="false"
 *       app:glassCornerRadius="28dp" />
 *
 * 用法二（纯代码，零资源依赖）：
 *   GlassCapsuleButton btn = new GlassCapsuleButton(context);
 *   btn.setText("按钮");
 *   btn.setGlassSelected(true);
 */
public class GlassCapsuleButton extends Button {

    private boolean mGlassSelected = false;
    private float mCornerRadiusDp = 28f;
    private GlassButtonDrawable mGlassDrawable;

    public GlassCapsuleButton(Context context) {
        this(context, null);
    }

    public GlassCapsuleButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GlassCapsuleButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.GlassCapsuleButton);
            mGlassSelected = a.getBoolean(R.styleable.GlassCapsuleButton_glassSelected, false);
            float density = context.getResources().getDisplayMetrics().density;
            mCornerRadiusDp = a.getDimension(R.styleable.GlassCapsuleButton_glassCornerRadius,
                    Math.round(28f * density)) / density;
            a.recycle();
        }
        init();
    }

    private void init() {
        setAllCaps(false);
        setTextSize(14);
        float density = getResources().getDisplayMetrics().density;
        setPadding(Math.round(24 * density), Math.round(14 * density),
                Math.round(24 * density), Math.round(14 * density));
        applyGlassBackground();
    }

    private void applyGlassBackground() {
        float density = getResources().getDisplayMetrics().density;
        float cornerPx = mCornerRadiusDp * density;
        float borderPx = (mGlassSelected ? 2f : 1f) * density;
        mGlassDrawable = new GlassButtonDrawable(cornerPx, borderPx, mGlassSelected);
        setBackground(mGlassDrawable);
        setTextColor(mGlassSelected ? GlassButtonStyle.COLOR_ACCENT : GlassButtonStyle.COLOR_WHITE);
    }

    /**
     * 切换玻璃选中态：选中 = 蓝色文字 + 加粗加亮描边 + 加深填充。
     */
    public void setGlassSelected(boolean selected) {
        if (mGlassSelected != selected) {
            mGlassSelected = selected;
            applyGlassBackground();
        }
    }

    public boolean isGlassSelected() {
        return mGlassSelected;
    }

    /**
     * 设置圆角半径（dp）。
     */
    public void setGlassCornerRadius(float cornerRadiusDp) {
        mCornerRadiusDp = cornerRadiusDp;
        applyGlassBackground();
    }

    public float getGlassCornerRadius() {
        return mCornerRadiusDp;
    }
}
