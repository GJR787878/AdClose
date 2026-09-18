package com.close.hook.ads.ui.activity

import android.os.Bundle
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.close.hook.ads.R
import com.close.hook.ads.preference.PrefManager
import com.close.hook.ads.ui.fragment.app.AppsPagerFragment
import com.close.hook.ads.ui.fragment.block.BlockPagerFragment
import com.close.hook.ads.ui.fragment.home.HomeFragment
import com.close.hook.ads.ui.fragment.request.RequestFragment
import com.close.hook.ads.ui.fragment.settings.SettingsFragment
import com.close.hook.ads.util.INavContainer
import com.close.hook.ads.util.OnBackPressContainer
import com.close.hook.ads.util.OnBackPressListener
import androidx.core.content.ContextCompat
import com.gjr.glassbutton.GlassNavBar
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior

class MainActivity : BaseActivity(), OnBackPressContainer, INavContainer {

    override var backController: OnBackPressListener? = null
    
    private lateinit var viewPager2: ViewPager2
    private lateinit var bottomNavigationView: GlassNavBar
    private lateinit var hideBottomViewOnScrollBehavior: HideBottomViewOnScrollBehavior<GlassNavBar>

    private val fragmentSuppliers: List<() -> Fragment> = listOf(
        ::AppsPagerFragment,
        ::RequestFragment,
        ::HomeFragment,
        ::BlockPagerFragment,
        ::SettingsFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupViewPagerAndBottomNavigation()
    }

    private fun setupViewPagerAndBottomNavigation() {
        viewPager2 = findViewById(R.id.view_pager)
        bottomNavigationView = findViewById(R.id.bottom_navigation)

        // 玻璃导航栏：按 fragment 顺序添加图标 + 文字项
        val navIcons = listOf(
            R.drawable.ic_apps, R.drawable.ic_requests, R.drawable.ic_home_outline,
            R.drawable.ic_block, R.drawable.ic_setting
        )
        val navLabels = listOf(
            R.string.bottom_item_1, R.string.bottom_item_2, R.string.bottom_item_5,
            R.string.bottom_item_4, R.string.bottom_item_3
        )
        for (i in navIcons.indices) {
            bottomNavigationView.addItem(ContextCompat.getDrawable(this, navIcons[i]), getString(navLabels[i]))
        }

        val adapter = BottomFragmentStateAdapter(supportFragmentManager, lifecycle, fragmentSuppliers)
        viewPager2.adapter = adapter
        viewPager2.isUserInputEnabled = false
        viewPager2.offscreenPageLimit = fragmentSuppliers.size

        bottomNavigationView.setOnItemSelectedListener { index ->
            viewPager2.setCurrentItem(index, false)
            true
        }

        hideBottomViewOnScrollBehavior = HideBottomViewOnScrollBehavior()
        (bottomNavigationView.layoutParams as CoordinatorLayout.LayoutParams).behavior = hideBottomViewOnScrollBehavior

        viewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                bottomNavigationView.setSelected(position)
            }
        })
        viewPager2.setCurrentItem(PrefManager.defaultPage, false)
    }

    override fun onBackPressed() {
        if (backController?.onBackPressed() == true) {
            return
        }

        if (viewPager2.currentItem != 0) {
            viewPager2.setCurrentItem(0, true)
        } else {
            super.onBackPressed()
        }
    }

    override fun showNavigation() {
        if (hideBottomViewOnScrollBehavior.isScrolledDown) {
            hideBottomViewOnScrollBehavior.slideUp(bottomNavigationView)
        }
    }

    override fun hideNavigation() {
        if (hideBottomViewOnScrollBehavior.isScrolledUp) {
            hideBottomViewOnScrollBehavior.slideDown(bottomNavigationView)
        }
    }

    fun getBottomNavigationView(): GlassNavBar {
        return bottomNavigationView
    }

    private class BottomFragmentStateAdapter(
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle,
        private val fragmentSuppliers: List<() -> Fragment>
    ) : FragmentStateAdapter(fragmentManager, lifecycle) {

        override fun createFragment(position: Int): Fragment {
            return fragmentSuppliers[position]()
        }

        override fun getItemCount(): Int {
            return fragmentSuppliers.size
        }
    }
}
