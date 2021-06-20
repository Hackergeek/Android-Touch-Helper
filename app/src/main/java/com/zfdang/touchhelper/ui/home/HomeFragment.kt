package com.zfdang.touchhelper.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.zfdang.touchhelper.R
import com.zfdang.touchhelper.service.TouchHelperService

class HomeFragment : Fragment() {
    companion object {
        private const val TAG = "HomeFragment"
    }

    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_home, container, false)
        val drawableYes = ContextCompat.getDrawable(requireContext(), R.drawable.ic_right)
        val drawableNo = ContextCompat.getDrawable(requireContext(), R.drawable.ic_wrong)

        // set observers for widget
        val imageAccessibilityPermission =
            root.findViewById<ImageView>(R.id.image_accessibility_permission)
        homeViewModel.accessibilityPermission.observe(viewLifecycleOwner, { aBoolean ->
            if (aBoolean) {
                imageAccessibilityPermission.setImageDrawable(drawableYes)
            } else {
                imageAccessibilityPermission.setImageDrawable(drawableNo)
            }
        })
        val imagePowerPermission = root.findViewById<ImageView>(R.id.image_power_permission)
        homeViewModel.powerOptimization.observe(viewLifecycleOwner, { aBoolean ->
            if (aBoolean) {
                imagePowerPermission.setImageDrawable(drawableYes)
            } else {
                imagePowerPermission.setImageDrawable(drawableNo)
            }
        })


        // set listener for buttons
        val btAccessibilityPermission =
            root.findViewById<ImageButton>(R.id.button_accessibility_permission)
        btAccessibilityPermission.setOnClickListener {
            val accessibilityIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            accessibilityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(accessibilityIntent)
        }
        val btPowerPermission = root.findViewById<ImageButton>(R.id.button_power_permission)
        btPowerPermission.setOnClickListener {
            //  打开电池优化的界面，让用户设置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent()
                // open battery optimization setting page
                intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                startActivity(intent)
            }
        }
        ignoreBatteryOptimization(requireActivity())

        // get the service status
        checkServiceStatus()
        return root
    }

    /**
     * 忽略电池优化
     */
    @SuppressLint("BatteryLife")
    private fun ignoreBatteryOptimization(activity: Activity) {
        val powerManager =
            activity.getSystemService(AppCompatActivity.POWER_SERVICE) as PowerManager
        val hasIgnored = powerManager.isIgnoringBatteryOptimizations(activity.packageName)
        //  判断当前APP是否有加入电池优化的白名单，如果没有，弹出加入电池优化的白名单的设置对话框。
        if (!hasIgnored) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:" + activity.packageName)
            startActivity(intent)
        }
    }

    override fun onResume() {
        checkServiceStatus()
        super.onResume()
    }

    private fun checkServiceStatus() {
        // detect the app storage permission
        val storagePermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val liveData = homeViewModel.appPermission
        liveData.value = storagePermission

        // detect the accessibility permission
        val accessibility = homeViewModel.accessibilityPermission
        accessibility.value = TouchHelperService.serviceImpl != null

        // detect power optimization
        val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        val hasIgnored = pm.isIgnoringBatteryOptimizations(requireContext().packageName)
        val power = homeViewModel.powerOptimization
        power.value = hasIgnored
    }
}