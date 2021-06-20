package com.zfdang.touchhelper.ui.settings

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.preference.*
import com.zfdang.touchhelper.R
import com.zfdang.touchhelper.Settings
import com.zfdang.touchhelper.Utilities
import com.zfdang.touchhelper.bean.PackagePositionDescription
import com.zfdang.touchhelper.bean.PackageWidgetDescription
import com.zfdang.touchhelper.service.TouchHelperService
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination
import java.util.*


class SettingsFragment : PreferenceFragmentCompat() {
    companion object {
        private const val TAG = "SettingsFragment"
    }

    private var activityPositions: MultiSelectListPreference? = null
    private var activityWidgets: MultiSelectListPreference? = null
    private var mapActivityWidgets: MutableMap<String?, MutableSet<PackageWidgetDescription?>?>? =
        null
    private var mapActivityPositions: MutableMap<String?, PackagePositionDescription?>? = null
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.touch_helper_preference, rootKey)
        initPreferences()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        // get the height of BottomNavigationView
        val resourceId = resources.getIdentifier(
            "design_bottom_navigation_height",
            "dimen",
            requireActivity().packageName
        )
        var height = 147
        if (resourceId > 0) {
            height = resources.getDimensionPixelSize(resourceId)
        }

        // set bottom padding for the preference fragment, so that all parts could be shown properly
        view!!.setPadding(
            view.paddingLeft,
            view.paddingTop,
            view.paddingRight,
            view.paddingBottom + height
        )
        return view
    }

    private fun initPreferences() {
        val notification = findPreference<CheckBoxPreference>("skip_ad_notification")
        if (notification != null) {
            notification.isChecked = Settings.isSkipAdNotification
            notification.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val value = newValue as Boolean
                    Settings.isSkipAdNotification = value
                    true
                }
        }
        val duration = findPreference<SeekBarPreference>("skip_ad_duration")
        if (duration != null) {
            duration.max = 10
            duration.min = 1
            duration.updatesContinuously = true
            duration.value = Settings.skipAdDuration
            duration.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, _ ->
                    val value = duration.value + duration.min
                    Settings.skipAdDuration = value
                    true
                }
        }


        // key words to detect skip-ad button
        val textKeyWords = findPreference<EditTextPreference>("setting_key_words")
        if (textKeyWords != null) {
            textKeyWords.text = Settings.keyWordsAsString
            textKeyWords.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val text = newValue.toString()
                    Settings.setKeyWordList(text)

                    // notify accessibility to refresh packages
                    if (TouchHelperService.serviceImpl != null) {
                        TouchHelperService.serviceImpl!!.receiverHandler.sendEmptyMessage(
                            TouchHelperService.ACTION_REFRESH_KEYWORDS
                        )
                    }
                    true
                }
        }

        // select packages to be whitelisted
        val whitelist = findPreference<Preference>("setting_whitelist")
        if (whitelist != null) {
            whitelist.onPreferenceClickListener = object : Preference.OnPreferenceClickListener {
                override fun onPreferenceClick(preference: Preference): Boolean {
                    // find all packages
                    val list: MutableList<String> = ArrayList()
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    val resolveInfoLists =
                        requireActivity().packageManager.queryIntentActivities(
                            intent,
                            PackageManager.MATCH_ALL
                        )
                    for (e in resolveInfoLists) {
//                        Log.d(TAG, "launcher - " + e.activityInfo.packageName);
                        list.add(e.activityInfo.packageName)
                    }

                    // generate AppInformation for packages
                    val listApp = ArrayList<AppInformation>()
                    val pkgWhitelist = Settings.whitelistPackages
                    for (pkgName in list) {
                        try {
                            val info = requireActivity().packageManager.getApplicationInfo(
                                pkgName,
                                PackageManager.GET_META_DATA
                            )
                            val appInfo = AppInformation(
                                pkgName,
                                requireActivity().packageManager.getApplicationLabel(info)
                                    .toString(),
                                requireActivity().packageManager.getApplicationIcon(info)
                            )
                            appInfo.isChecked = pkgWhitelist!!.contains(pkgName)
                            listApp.add(appInfo)
                        } catch (e: PackageManager.NameNotFoundException) {
                            Log.e(TAG, Utilities.getTraceStackInString(e))
                        }
                    }

                    // sort apps
                    listApp.sort()

                    // listApp adapter
                    val baseAdapter: BaseAdapter = object : BaseAdapter() {
                        override fun getCount(): Int {
                            return listApp.size
                        }

                        override fun getItem(position: Int): Any {
                            return listApp[position]
                        }

                        override fun getItemId(position: Int): Long {
                            return position.toLong()
                        }

                        override fun getView(
                            position: Int,
                            convertView: View?,
                            parent: ViewGroup
                        ): View {
                            val holder: ViewHolder
                            var localConvertView = convertView
                            if (localConvertView == null) {
                                localConvertView =
                                    layoutInflater.inflate(
                                        R.layout.layout_package_information,
                                        null
                                    )
                                holder = ViewHolder(localConvertView)
                                localConvertView.tag = holder
                            } else {
                                holder = localConvertView.tag as ViewHolder
                            }
                            val app = listApp[position]
                            holder.textView.text = app.applicationName
                            holder.imageView.setImageDrawable(app.applicationIcon)
                            holder.checkBox.isChecked = app.isChecked
                            return localConvertView!!
                        }
                    }

                    // inflate the dialog view

                    val viewAppList = layoutInflater.inflate(R.layout.layout_select_packages, null)
                    val listView = viewAppList.findViewById<ListView>(R.id.listView)
                    listView.adapter = baseAdapter
                    listView.onItemClickListener =
                        OnItemClickListener { _, view, position, _ ->
                            val item = (view.tag as ViewHolder).checkBox
                            val app = listApp[position]
                            app.isChecked = !app.isChecked
                            item.isChecked = app.isChecked
                        }
                    val dialog = AlertDialog.Builder(context)
                        .setView(viewAppList)
                        .create()
                    val btCancel = viewAppList.findViewById<Button>(R.id.button_cancel)
                    btCancel?.setOnClickListener { dialog.dismiss() }
                    val btConfirm = viewAppList.findViewById<Button>(R.id.button_confirm)
                    btConfirm?.setOnClickListener { // save checked packages
                        val packageWhitelist: MutableSet<String?> = HashSet()
                        for (app in listApp) {
                            if (app.isChecked) {
                                packageWhitelist.add(app.packageName)
                            }
                        }
                        Settings.whitelistPackages = packageWhitelist

                        // notify accessibility to refresh packages
                        if (TouchHelperService.serviceImpl != null) {
                            TouchHelperService.serviceImpl!!.receiverHandler.sendEmptyMessage(
                                TouchHelperService.ACTION_REFRESH_PACKAGE
                            )
                        }
                        dialog.dismiss()
                    }

                    // show the dialog
                    dialog.show()
                    return true
                } // public boolean onPreferenceClick(Preference preference) {

                val outputFormat = HanyuPinyinOutputFormat()

                inner class AppInformation(
                    var packageName: String?,
                    var applicationName: String,
                    applicationIcon: Drawable
                ) : Comparable<Any?> {
                    var applicationNamePinyin: String? = null
                    var applicationIcon: Drawable
                    var isChecked: Boolean
                    override operator fun compareTo(other: Any?): Int {
                        if (other !is AppInformation) {
                            throw IllegalArgumentException()
                        }
                        return if (isChecked && !other.isChecked) {
                            -11
                        } else if (!isChecked && other.isChecked) {
                            1
                        } else {
                            //
                            applicationNamePinyin!!.compareTo(other.applicationNamePinyin!!)
                        }
                    }

                    init {
                        try {
                            applicationNamePinyin = PinyinHelper.toHanYuPinyinString(
                                applicationName,
                                outputFormat,
                                "",
                                true
                            )
                        } catch (badHanyuPinyinOutputFormatCombination: BadHanyuPinyinOutputFormatCombination) {
                            applicationNamePinyin = applicationName
                            Log.e(
                                TAG,
                                Utilities.getTraceStackInString(
                                    badHanyuPinyinOutputFormatCombination
                                )
                            )
                        }
                        this.applicationIcon = applicationIcon
                        isChecked = false
                    }
                } // class AppInformation

                inner class ViewHolder(v: View) {
                    var textView: TextView = v.findViewById(R.id.name)
                    var imageView: ImageView = v.findViewById(R.id.img)
                    var checkBox: CheckBox = v.findViewById(R.id.check)

                } // class ViewHolder {
            }
        }

        // let user to customize skip-ad button or position for package
        val activityCustomization = findPreference<Preference>("setting_activity_customization")
        if (activityCustomization != null) {
            activityCustomization.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    if (TouchHelperService.serviceImpl != null) {
                        TouchHelperService.serviceImpl!!.receiverHandler.sendEmptyMessage(
                            TouchHelperService.ACTION_ACTIVITY_CUSTOMIZATION
                        )
                    } else {
                        Toast.makeText(context, "开屏跳过服务未运行，请打开无障碍服务!", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
        }

        // manage saved activity widgets
        activityWidgets =
            findPreference<Preference>("setting_activity_widgets") as MultiSelectListPreference?
        mapActivityWidgets = Settings.packageWidgets
        updateMultiSelectListPreferenceEntries(activityWidgets, mapActivityWidgets!!.keys)
        activityWidgets!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                val results = newValue as HashSet<*>
                //                Log.d(TAG, "size " + results.size());

                // update activity widgets
                val keys: Set<String?> = HashSet(
                    mapActivityWidgets!!.keys
                )
                for (key in keys) {
                    if (!results.contains(key)) {
                        // this key is not selected to keep, remove the entry
                        mapActivityWidgets!!.remove(key)
                    }
                }
                Settings.packageWidgets = mapActivityWidgets

                // refresh MultiSelectListPreference
                updateMultiSelectListPreferenceEntries(activityWidgets, mapActivityWidgets!!.keys)

                // send message to accessibility service
                if (TouchHelperService.serviceImpl != null) {
                    TouchHelperService.serviceImpl!!.receiverHandler.sendEmptyMessage(
                        TouchHelperService.ACTION_REFRESH_CUSTOMIZED_ACTIVITY
                    )
                }
                true
            }


        // advanced method to manage "customized package widgets", by editing the raw setting
        val packageWidgetsAdvance =
            findPreference<Preference>("setting_activity_widgets_advanced")
        if (packageWidgetsAdvance != null) {
            packageWidgetsAdvance.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    val fragmentManager = requireActivity().supportFragmentManager
                    val newFragment = ManagePackageWidgetsDialogFragment()
                    newFragment.show(fragmentManager, "dialog")
                    true
                }
        }


        // manage saved activity positions
        activityPositions =
            findPreference<Preference>("setting_activity_positions") as MultiSelectListPreference?
        mapActivityPositions = Settings.packagePositions
        updateMultiSelectListPreferenceEntries(activityPositions, mapActivityPositions!!.keys)
        activityPositions!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                val results = newValue as HashSet<*>
                //                Log.d(TAG, "size " + results.size());

                // update activity widgets
                val keys: Set<String?> = HashSet(
                    mapActivityPositions!!.keys
                )
                for (key in keys) {
                    if (!results.contains(key)) {
                        // this key is not selected to keep, remove the entry
                        mapActivityPositions!!.remove(key)
                    }
                }
                Settings.packagePositions = mapActivityPositions

                // refresh MultiSelectListPreference
                updateMultiSelectListPreferenceEntries(
                    activityPositions,
                    mapActivityPositions!!.keys
                )

                // send message to accessibility service
                if (TouchHelperService.serviceImpl != null) {
                    TouchHelperService.serviceImpl!!.receiverHandler.sendEmptyMessage(
                        TouchHelperService.ACTION_REFRESH_CUSTOMIZED_ACTIVITY
                    )
                }
                true
            }
    }

    private fun updateMultiSelectListPreferenceEntries(
        preference: MultiSelectListPreference?,
        keys: Set<String?>?
    ) {
        if (preference == null || keys == null) return
        val entries = keys.toTypedArray<CharSequence?>()
        preference.entries = entries
        preference.entryValues = entries
        preference.values = keys
    }

    override fun onResume() {
        super.onResume()

        // these values might be changed by adding new widget or positions, update entries for these two multipeline
        mapActivityWidgets = Settings.packageWidgets
        updateMultiSelectListPreferenceEntries(activityWidgets, mapActivityWidgets!!.keys)
        mapActivityPositions = Settings.packagePositions
        updateMultiSelectListPreferenceEntries(activityPositions, mapActivityPositions!!.keys)
    }
}