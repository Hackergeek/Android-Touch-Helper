package com.zfdang.touchhelper.ui.about

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.zfdang.touchhelper.R

class AboutFragment : Fragment() {
    companion object {
        private const val TAG = "AboutFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_about, container, false)
        val tvVersion = root.findViewById<TextView>(R.id.textView_version)
        var versionName = "unknown"
        var versionCode = 0
        val pm = requireActivity().packageManager
        try {
            val pi = pm.getPackageInfo(requireActivity().packageName, 0)
            versionName = pi.versionName
            versionCode = pi.longVersionCode.toInt()
            // generate about_content with version from manifest
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "showInfoDialog: " + Log.getStackTraceString(e))
        }
        val version = getString(R.string.app_version, versionName, versionCode)
        tvVersion.text = version
        return root
    }
}