package com.close.hook.ads.ui.fragment.block

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.close.hook.ads.R
import com.close.hook.ads.databinding.FragmentBlockPagerBinding
import com.close.hook.ads.ui.fragment.base.BaseFragment
import com.google.android.material.tabs.TabLayoutMediator

class BlockPagerFragment : BaseFragment<FragmentBlockPagerBinding>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> BlockListFragment()
                else -> SubscriptionRuleFragment()
            }
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = getString(
                if (position == 0) R.string.tab_local_rules else R.string.tab_subscription_rules
            )
        }.attach()
    }
}
