package com.close.hook.ads.ui.activity

import android.os.Bundle
import androidx.activity.addCallback
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.close.hook.ads.R
import com.close.hook.ads.databinding.ActivityCustomHookBinding
import com.close.hook.ads.ui.fragment.hook.CustomHookLogFragment
import com.close.hook.ads.ui.fragment.hook.CustomHookManagerFragment
import com.close.hook.ads.util.INavContainer
import com.close.hook.ads.util.OnBackPressContainer
import com.close.hook.ads.util.OnBackPressListener
import androidx.core.content.ContextCompat
import com.gjr.glassbutton.GlassNavBar
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior

class CustomHookActivity : BaseActivity(), OnBackPressContainer, INavContainer {

    companion object {
        private const val MANAGER_FRAGMENT_INDEX = 0
        private const val LOG_FRAGMENT_INDEX = 1
    }

    private val binding by lazy { ActivityCustomHookBinding.inflate(layoutInflater) }
    private val targetPackageName by lazy { intent.getStringExtra("packageName") }

    private val viewPager by lazy { binding.viewPager }
    val bottomNavigationView by lazy { binding.bottomNavigationHook }
    private val hideBottomViewOnScrollBehavior by lazy { HideBottomViewOnScrollBehavior<GlassNavBar>() }

    override var backController: OnBackPressListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupViewPager()
        setupBottomNavigation()
        setupCustomizedBackPress()
    }

    private fun setupViewPager() {
        viewPager.apply {
            adapter = ViewPagerAdapter(this@CustomHookActivity, targetPackageName)
            isUserInputEnabled = false
            offscreenPageLimit = 2

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    bottomNavigationView.setSelected(position)
                }
            })
        }
    }

    private fun setupBottomNavigation() {
        // 玻璃导航栏：添加两项 Hook / Log
        bottomNavigationView.addItem(ContextCompat.getDrawable(this, R.drawable.ic_hook_manager), "Hook")
        bottomNavigationView.addItem(ContextCompat.getDrawable(this, R.drawable.ic_log), "Log")
        (bottomNavigationView.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior = hideBottomViewOnScrollBehavior
        bottomNavigationView.setOnItemSelectedListener { index ->
            viewPager.currentItem = index
            true
        }
    }

    private fun setupCustomizedBackPress() {
        onBackPressedDispatcher.addCallback(this, true) {
            if (backController?.onBackPressed() == true) {
                return@addCallback
            }

            if (viewPager.currentItem != MANAGER_FRAGMENT_INDEX) {
                viewPager.currentItem = MANAGER_FRAGMENT_INDEX
            } else {
                finish()
            }
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

    private class ViewPagerAdapter(
        activity: FragmentActivity,
        private val packageName: String?
    ) : FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment = when (position) {
            MANAGER_FRAGMENT_INDEX -> CustomHookManagerFragment.newInstance(packageName)
            LOG_FRAGMENT_INDEX -> CustomHookLogFragment.newInstance(packageName)
            else -> throw IllegalStateException("Invalid position $position")
        }
    }
}
