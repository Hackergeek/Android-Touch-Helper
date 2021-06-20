package com.zfdang.touchhelper

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.zfdang.TouchHelperApp
import com.zfdang.touchhelper.bean.PackagePositionDescription
import com.zfdang.touchhelper.bean.PackageWidgetDescription
import java.util.*

object Settings {
    private const val TAG = "Settings"
    private const val preferenceName = "TouchHelper_Config"
    private lateinit var mPreference: SharedPreferences
    private var mEditor: SharedPreferences.Editor? = null
    private var mJson: Gson? = null

    @SuppressLint("CommitPrefEdits")
    private fun initSettings() {
        mPreference =
            TouchHelperApp.appContext.getSharedPreferences(preferenceName, Activity.MODE_PRIVATE)
        mEditor = mPreference.edit()
        mJson = Gson()

        // init all settings from SharedPreferences
        bSkipAdNotification = mPreference.getBoolean(SKIP_AD_NOTIFICATION, true)

        // initial duration of skip ad process
        iSkipAdDuration = mPreference.getInt(SKIP_AD_DURATION, 4)

        // find all system packages, and set them as default value for whitelist
        val packageManager: PackageManager =
            TouchHelperApp.appContext.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val pkgSystems: MutableSet<String> = HashSet()
        for (e in resolveInfoList) {
            if (e.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == ApplicationInfo.FLAG_SYSTEM) {
                pkgSystems.add(e.activityInfo.packageName)
            }
        }

        // init whitelist of packages
        // https://stackoverflow.com/questions/10720028/android-sharedpreferences-not-saving
        // Note that you must not modify the set instance returned by this call. The consistency of the stored data is not guaranteed if you do, nor is your ability to modify the instance at all.
        setWhiteListPackages = HashSet(mPreference.getStringSet(WHITELIST_PACKAGE, pkgSystems))

        // init key words
        var json = mPreference.getString(KEY_WORDS_LIST, "[\"跳过\"]")
        listKeyWords = if (json != null) {
            val type = object : TypeToken<ArrayList<String?>?>() {}.type
            mJson!!.fromJson(json, type)
        } else {
            ArrayList()
        }

        // load activity widgets
        json = mPreference.getString(PACKAGE_WIDGETS, null)
        mapPackageWidgets = if (json != null) {
            val type =
                object : TypeToken<TreeMap<String?, Set<PackageWidgetDescription?>?>?>() {}.type
            mJson!!.fromJson(json, type)
        } else {
            TreeMap()
        }

        // load activity positions
        json = mPreference.getString(PACKAGE_POSITIONS, null)
        mapPackagePositions = if (json != null) {
            val type = object : TypeToken<TreeMap<String?, PackagePositionDescription?>?>() {}.type
            mJson!!.fromJson(json, type)
        } else {
            TreeMap()
        }
    }

    private var bSkipAdNotification = false
    var isSkipAdNotification: Boolean
        get() = bSkipAdNotification
        set(bSkipAdNotification) {
            if (this.bSkipAdNotification != bSkipAdNotification) {
                this.bSkipAdNotification = bSkipAdNotification
                mEditor!!.putBoolean(SKIP_AD_NOTIFICATION, this.bSkipAdNotification)
                mEditor!!.apply()
            }
        }
    private var iSkipAdDuration = 0
    var skipAdDuration: Int
        get() = iSkipAdDuration
        set(iSkipAdDuration) {
            if (this.iSkipAdDuration != iSkipAdDuration) {
                this.iSkipAdDuration = iSkipAdDuration
                mEditor!!.putInt(SKIP_AD_DURATION, this.iSkipAdDuration)
                mEditor!!.apply()
            }
        }
    private var setWhiteListPackages: MutableSet<String?>? = null

    // https://stackoverflow.com/questions/10720028/android-sharedpreferences-not-saving
    var whitelistPackages: Set<String?>?
        get() = setWhiteListPackages
        set(packages) {
            setWhiteListPackages!!.clear()
            setWhiteListPackages!!.addAll(packages!!)
            // https://stackoverflow.com/questions/10720028/android-sharedpreferences-not-saving
            mEditor!!.putStringSet(WHITELIST_PACKAGE, HashSet(setWhiteListPackages))
            mEditor!!.apply()
        }
    private var listKeyWords: ArrayList<String>? = null
    val keyWordList: List<String>?
        get() = listKeyWords
    val keyWordsAsString: String
        get() = java.lang.String.join(" ", listKeyWords!!)

    fun setKeyWordList(text: String) {
        val keys = text.split(" ").toTypedArray()
        listKeyWords!!.clear()
        listKeyWords!!.addAll(listOf(*keys))
        val json = mJson!!.toJson(listKeyWords)
        mEditor!!.putString(KEY_WORDS_LIST, json)
        mEditor!!.apply()
    }

    private var mapPackageWidgets: MutableMap<String?, MutableSet<PackageWidgetDescription?>?>? =
        null

    //        Log.d(TAG, json);
    var packageWidgets: MutableMap<String?, MutableSet<PackageWidgetDescription?>?>?
        get() = mapPackageWidgets
        set(map) {
            mapPackageWidgets = map
            val json = mJson!!.toJson(mapPackageWidgets)
            //        Log.d(TAG, json);
            mEditor!!.putString(PACKAGE_WIDGETS, json)
            mEditor!!.apply()
        }
    val packageWidgetsInString: String
        get() = mJson!!.toJson(mapPackageWidgets)

    fun setPackageWidgetsInString(value: String?): Boolean {
        if (value != null) {
            try {
                val type =
                    object : TypeToken<TreeMap<String?, Set<PackageWidgetDescription?>?>?>() {}.type
                mapPackageWidgets = mJson!!.fromJson(value, type)
                mEditor!!.putString(PACKAGE_WIDGETS, value)
                mEditor!!.apply()
            } catch (e: JsonSyntaxException) {
                Log.d(TAG, Utilities.getTraceStackInString(e))
                return false
            }
        }
        return false
    }

    private var mapPackagePositions: MutableMap<String?, PackagePositionDescription?>? = null

    //        Log.d(TAG, json);
    var packagePositions: MutableMap<String?, PackagePositionDescription?>?
        get() = mapPackagePositions
        set(map) {
            mapPackagePositions = map
            val json = mJson!!.toJson(mapPackagePositions)
            //        Log.d(TAG, json);
            mEditor!!.putString(PACKAGE_POSITIONS, json)
            mEditor!!.apply()
        }

    // notification on skip ads?
    private const val SKIP_AD_NOTIFICATION = "SKIP_AD_NOTIFICATION"

    // duration of skip ad process
    private const val SKIP_AD_DURATION = "SKIP_AD_DURATION"

    // whitelist of packages
    private const val WHITELIST_PACKAGE = "WHITELIST_PACKAGE"

    // list of key words
    private const val KEY_WORDS_LIST = "KEY_WORDS_LIST"

    // map of key activity widgets
    private const val PACKAGE_WIDGETS = "PACKAGE_WIDGETS"

    // map of key package positions
    private const val PACKAGE_POSITIONS = "PACKAGE_POSITIONS"

    init {
        initSettings()
    }
}