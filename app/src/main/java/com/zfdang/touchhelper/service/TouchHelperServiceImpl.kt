package com.zfdang.touchhelper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.View.OnFocusChangeListener
import android.view.View.OnTouchListener
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import com.zfdang.touchhelper.*
import com.zfdang.touchhelper.bean.PackagePositionDescription
import com.zfdang.touchhelper.bean.PackageWidgetDescription
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashSet
import kotlin.math.roundToInt

class TouchHelperServiceImpl(private val service: AccessibilityService) {
    private lateinit var mSetting: Settings

    // broadcast receiver handler
    private var installReceiver: TouchHelperServiceReceiver? = null
    lateinit var receiverHandler: Handler
    private var executorService: ScheduledExecutorService? = null
    private var futureExpireSkipAdProcess: ScheduledFuture<*>? = null
    private var methodByActivityPosition = false
    private var methodByActivityWidget = false
    private var methodByButtonKeyWord = false
    private lateinit var packageManager: PackageManager
    private var currentPackageName: String? = null
    private var currentActivityName: String? = null
    private var packageName: String? = null
    private lateinit var pkgLaunchers: HashSet<String>
    private var pkgWhiteList: Set<String?>? = null
    private var keyWordList: List<String?>? = null
    private var mapPackagePositions: MutableMap<String?, PackagePositionDescription?>? = null
    private var mapPackageWidgets: MutableMap<String?, MutableSet<PackageWidgetDescription?>?>? =
        null
    private var setWidgets: MutableSet<PackageWidgetDescription?>? = null
    private var packagePositionDescription: PackagePositionDescription? = null
    private val toastLock = ReentrantLock()
    fun onServiceConnected() {
        try {
            // the following codes are not necessary
//            // set accessibility configuration
//            AccessibilityServiceInfo asi = service.getServiceInfo();
//
//            // If you only want this service to work with specific applications, set their
//            // package names here. Otherwise, when the service is activated, it will listen
//            // to events from all applications.
//
//            // Set the type of feedback your service will provide.
//            asi.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
//
//            // Default services are invoked only if no package-specific ones are present
//            // for the type of AccessibilityEvent generated. This service *is*
////            asi.packageNames = new String[] {"com.example.android.myFirstApp", "com.example.android.mySecondApp"};
//
//            // application-specific, so the flag isn't necessary. If this was a
//            // general-purpose service, it would be worth considering setting the
//            // DEFAULT flag.
//            asi.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
//                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
//                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
//                    | AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES
//                    | AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT;
//            asi.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
//            asi.notificationTimeout = 50;
//            service.setServiceInfo(asi);

            // initialize parameters
            currentPackageName = "Initial PackageName"
            currentActivityName = "Initial ClassName"
            packageName = service.packageName

            // read settings from sharedPreferences
            mSetting = Settings
            // key words
            keyWordList = mSetting.keyWordList
            //            Log.d(TAG, keyWordList.toString());

            // whitelist of packages
            pkgWhiteList = mSetting.whitelistPackages

            // load pre-defined widgets or positions
            mapPackageWidgets = mSetting.packageWidgets
            mapPackagePositions = mSetting.packagePositions

            // collect all installed packages
            packageManager = service.packageManager
            updatePackage()

            // install receiver and handler for broadcasting events
            installReceiverAndHandler()

            // create future task
            executorService = Executors.newSingleThreadScheduledExecutor()
            futureExpireSkipAdProcess =
                executorService?.schedule({ }, 0, TimeUnit.MILLISECONDS)
        } catch (e: Throwable) {
            Log.e(TAG, Utilities.getTraceStackInString(e))
        }
    }

    fun onInterrupt() {
        stopSkipAdProcess()
    }

    private fun installReceiverAndHandler() {
        // install broadcast receiver for package add / remove
        installReceiver = TouchHelperServiceReceiver()
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED)
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        intentFilter.addDataScheme("package")
        service.registerReceiver(installReceiver, intentFilter)

        // install handler to handle broadcast messages
        receiverHandler = Handler { msg ->
            when (msg.what) {
                TouchHelperService.ACTION_REFRESH_KEYWORDS -> keyWordList =
                    mSetting.keyWordList
                TouchHelperService.ACTION_REFRESH_PACKAGE -> {
                    pkgWhiteList = mSetting.whitelistPackages
                    //                        Log.d(TAG, pkgWhiteList.toString());
                    updatePackage()
                }
                TouchHelperService.ACTION_REFRESH_CUSTOMIZED_ACTIVITY -> {
                    mapPackageWidgets = mSetting.packageWidgets
                    mapPackagePositions = mSetting.packagePositions
                }
                TouchHelperService.ACTION_STOP_SERVICE ->
                    service.disableSelf()
                TouchHelperService.ACTION_ACTIVITY_CUSTOMIZATION -> showActivityCustomizationDialog()
            }
            true
        }
    }

    // events sequence after clicking one the app icon on home
    //    TYPE_VIEW_CLICKED - net.oneplus.launcher - android.widget.TextView
    //    TYPE_WINDOWS_CHANGED - null - null
    //    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - android.widget.FrameLayout
    //    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - android.widget.FrameLayout
    //    TYPE_WINDOW_STATE_CHANGED - tv.danmaku.bili - android.widget.FrameLayout
    //    TYPE_WINDOW_CONTENT_CHANGED - net.oneplus.launcher - android.widget.FrameLayout
    //    TYPE_WINDOW_CONTENT_CHANGED - net.oneplus.launcher - android.widget.FrameLayout
    //    TYPE_WINDOW_CONTENT_CHANGED - net.oneplus.launcher - android.widget.FrameLayout
    //    TYPE_WINDOW_CONTENT_CHANGED - net.oneplus.launcher - android.widget.FrameLayout
    //    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - android.widget.FrameLayout
    //    TYPE_WINDOW_STATE_CHANGED - tv.danmaku.bili - tv.danmaku.bili.ui.splash.SplashActivity
    //    TYPE_WINDOWS_CHANGED - null - null
    //    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - android.widget.FrameLayout
    //    TYPE_WINDOW_STATE_CHANGED - tv.danmaku.bili - tv.danmaku.bili.MainActivityV2
    //    TYPE_WINDOWS_CHANGED - null - null
    //    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - android.view.ViewGroup
    //    TYPE_VIEW_SCROLLED - tv.danmaku.bili - android.widget.HorizontalScrollView
    //    TYPE_WINDOWS_CHANGED - null - null
    //    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - android.view.ViewGroup
    //    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - android.widget.ImageView
    //    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - androidx.viewpager.widget.ViewPager
    //    TYPE_WINDOWS_CHANGED - null - null
    //    TYPE_NOTIFICATION_STATE_CHANGED - tv.danmaku.bili - android.widget.Toast$TN
    //    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - androidx.viewpager.widget.ViewPager
    //    TYPE_VIEW_SCROLLED - tv.danmaku.bili - androidx.recyclerview.widget.RecyclerView
    //    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - android.widget.ImageView
    //    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - androidx.viewpager.widget.ViewPager
    //    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - androidx.recyclerview.widget.RecyclerView
    //    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - androidx.recyclerview.widget.RecyclerView
    //    TYPE_WINDOW_CONTENT_CHANGED - com.android.systemui - android.widget.FrameLayout
    // 思路描述：
    // 1. TYPE_WINDOW_STATE_CHANGED, 判断packageName和activityName
    // 2. TYPE_WINDOW_CONTENT_CHANGED, 尝试两种方法去跳过广告；如果重复次数超出预设，停止尝试
    fun onAccessibilityEvent(event: AccessibilityEvent) {
//        Log.d(TAG, AccessibilityEvent.eventTypeToString(event.getEventType()) + " - " + event.getPackageName() + " - " + event.getClassName() + "; ");
//        Log.d(TAG, "    currentPackageName = " + currentPackageName + "  currentActivityName = " + currentActivityName);
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    val tempPkgName = event.packageName
                    val tempClassName = event.className
                    if (tempPkgName == null || tempClassName == null) {
//                        currentPackageName = "initial package";
//                        currentActivityName = "initial activity";
                        return
                    }
                    val pkgName = tempPkgName.toString()
                    val actName = tempClassName.toString()
                    val isActivity =
                        !actName.startsWith("android.widget.") && !actName.startsWith("android.view.")
                    if (currentPackageName == pkgName) {
                        // current package, is it an activity?
                        if (isActivity) {
                            // yes, it's an activity
                            if (currentActivityName != actName) {
                                // new activity in the package, this means this activity is not the first activity any more
                                // stop skip ad process
                                // there are some cases that ad-activity is not the first activity in the package
//                                stopSkipAdProcess();
                                currentActivityName = actName
                                return
                            } else {
                                // same package, same activity, but not the first activity any longer
                                // do nothing here
                            }
                        }
                    } else {
                        // new package, is it a activity?
                        if (isActivity) {
                            // yes, it's an activity
                            // since it's an activity in another package, it must be a new activity, save them
                            currentPackageName = pkgName
                            currentActivityName = actName

                            // stop current skip ad process if it exists
                            stopSkipAdProcess()
                            if (pkgLaunchers!!.contains(pkgName)) {
                                // if the package is in our list, start skip ads process
                                startSkipAdProcess()
                            }
                        }
                    }

                    // now to take different methods to skip ads
                    if (methodByActivityPosition) {
//                        Log.d(TAG, "method by position in STATE_CHANGED");
                        packagePositionDescription = mapPackagePositions!![currentPackageName]
                        if (packagePositionDescription != null) {
                            // try multiple times to click the position to skip ads
                            showToast("正在根据位置跳过广告...")
                            executorService!!.scheduleAtFixedRate(
                                object : Runnable {
                                    var num = 0
                                    override fun run() {
                                        if (num < packagePositionDescription!!.number) {
                                            if (currentActivityName == packagePositionDescription!!.activityName) {
                                                // current activity is null, or current activity is the target activity
//                                            Log.d(TAG, "Find skip-ad by position, simulate click now! ");
                                                click(
                                                    packagePositionDescription!!.x,
                                                    packagePositionDescription!!.y,
                                                    0,
                                                    40
                                                )
                                            }
                                            num++
                                        } else {
                                            throw RuntimeException()
                                        }
                                    }
                                },
                                packagePositionDescription!!.delay.toLong(),
                                packagePositionDescription!!.period.toLong(),
                                TimeUnit.MILLISECONDS
                            )
                        } else {
                            // no customized positions for this activity
                            methodByActivityPosition = false
                        }
                    }
                    if (methodByActivityWidget) {
//                        Log.d(TAG, "method by widget in STATE_CHANGED");
                        setWidgets = mapPackageWidgets!![currentPackageName]
                        if (setWidgets != null) {
//                            Log.d(TAG, "Find skip-ad by widget, simulate click ");
                            findSkipButtonByWidget(service.rootInActiveWindow, setWidgets!!)
                        } else {
                            // no customized widget for this activity
                            methodByActivityWidget = false
                        }
                    }
                    if (methodByButtonKeyWord) {
//                        Log.d(TAG, "method by keywords in STATE_CHANGED");
                        findSkipButtonByTextOrDescription(service.rootInActiveWindow)
                    }
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    if (event.packageName != currentPackageName) {
                        // do nothing if package name is new
                        return
                    }
                    if (methodByActivityWidget && setWidgets != null) {
//                        Log.d(TAG, "method by widget in CONTENT_CHANGED");
                        findSkipButtonByWidget(event.source, setWidgets!!)
                    }
                    if (methodByButtonKeyWord) {
//                        Log.d(TAG, "method by keywords in CONTENT_CHANGED");
                        findSkipButtonByTextOrDescription(event.source)
                    }
                }
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                }
                AccessibilityEvent.TYPE_ANNOUNCEMENT -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_ASSIST_READING_CONTEXT -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_GESTURE_DETECTION_END -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_GESTURE_DETECTION_START -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_VIEW_HOVER_EXIT -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY -> {
                    TODO()
                }
                AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                    TODO()
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, Utilities.getTraceStackInString(e))
        }
    }

    fun onUnbind(intent: Intent?) {
        try {
            service.unregisterReceiver(installReceiver)
        } catch (e: Throwable) {
            Log.e(TAG, Utilities.getTraceStackInString(e))
        }
    }
    /**
     * 自动查找启动广告的“跳过”的控件, 这个方法目前没被使用，因为有些控件不设text, 而description里包含了关键字
     */
    //    private void findSkipButtonByText(AccessibilityNodeInfo nodeInfo) {
    //        if (nodeInfo == null) return;
    //        for (int n = 0; n < keyWordList.size(); n++) {
    //            String keyword = keyWordList.get(n);
    //            List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText(keyword);
    //            if (!list.isEmpty()) {
    //                for (AccessibilityNodeInfo e : list) {
    ////                    Log.d(TAG, "Find skip-ad by keywords " + e.toString() + " label size = ");
    ////                    Utilities.printNodeStack(e);
    //                    // add more validation about the node: 找到的按钮，不能比关键字的长度超出太多
    //                    String label = e.getText().toString();
    //                    if(label != null && label.length() <= keyword.length() + 4){
    ////                        Log.d(TAG, "label = " + label + " keyword = " + keyword);
    //                        ShowToastInIntentService("正在根据关键字跳过广告...");
    //
    //                        if (!e.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
    //                            if (!e.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
    //                                Rect rect = new Rect();
    //                                e.getBoundsInScreen(rect);
    //                                click(rect.centerX(), rect.centerY(), 0, 20);
    //                            }
    //                        }
    //                    }
    //
    //                    e.recycle();
    //                }
    //                b_method_by_button_keyword = false;
    //                return;
    //            }
    //
    //        }
    //        nodeInfo.recycle();
    //    }
    /**
     * 查找并点击包含keyword控件，目标包括Text和Description
     *
     */
    private fun findSkipButtonByTextOrDescription(root: AccessibilityNodeInfo) {
        var listA = ArrayList<AccessibilityNodeInfo?>()
        var listB = ArrayList<AccessibilityNodeInfo?>()
        listA.add(root)

//        showAllChildren(root);
        var total = listA.size
        var index = 0
        while (index < total) {
            val node = listA[index++]
            if (node != null) {
                val description = node.contentDescription
                val text = node.text

                // try to find keyword
                for (keyword in keyWordList!!) {
                    var isFind = false
                    // text or description contains keyword, but not too long （<= length + 6）
                    if (text != null && text.toString().length <= keyword!!.length + 6 && text.toString()
                            .contains(
                                keyword
                            )
                    ) {
                        isFind = true
                    } else if (description != null && description.toString().length <= keyword!!.length + 6 && description.toString()
                            .contains(
                                keyword
                            )
                    ) {
                        isFind = true
                    }
                    if (isFind) {
                        showToast("正在根据关键字跳过广告...")
                        //                        Log.d(TAG, Utilities.describeAccessibilityNode(node));
//                        Log.d(TAG, "keyword = " + keyword);
                        if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            if (!node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                val rect = Rect()
                                node.getBoundsInScreen(rect)
                                click(rect.centerX(), rect.centerY(), 0, 20)
                            }
                        }
                        break
                    }
                }
                for (n in 0 until node.childCount) {
                    listB.add(node.getChild(n))
                }
                node.recycle()
            }

            // reach the end of listA
            if (index == total) {
                listA = listB
                listB = ArrayList()
                index = 0
                total = listA.size
            }
        }
    }

    /**
     * 查找并点击由 ActivityWidgetDescription 定义的控件
     */
    private fun findSkipButtonByWidget(
        root: AccessibilityNodeInfo,
        set: Set<PackageWidgetDescription?>
    ) {
        var a = 0
        var b = 1
        var listA = ArrayList<AccessibilityNodeInfo?>()
        var listB = ArrayList<AccessibilityNodeInfo?>()
        listA.add(root)
        while (a < b) {
            val node = listA[a++]
            if (node != null) {
                val temRect = Rect()
                node.getBoundsInScreen(temRect)
                val cId: CharSequence? = node.viewIdResourceName
                val cDescribe = node.contentDescription
                val cText = node.text
                for (e in set) {
                    var isFind = false
                    if (temRect == e!!.position) {
                        isFind = true
                    } else if (cId != null && !e.idName.isEmpty() && cId.toString() == e.idName) {
                        isFind = true
                    } else if (cDescribe != null && !e.description.isEmpty() && cDescribe.toString()
                            .contains(
                                e.description
                            )
                    ) {
                        isFind = true
                    } else if (cText != null && !e.text.isEmpty() && cText.toString()
                            .contains(e.text)
                    ) {
                        isFind = true
                    }
                    if (isFind) {
//                        Log.d(TAG, "Find skip-ad by Widget " + e.toString());
                        showToast("正在根据控件跳过广告...")
                        if (e.onlyClick) {
                            click(temRect.centerX(), temRect.centerY(), 0, 20)
                        } else {
                            if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                if (!node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                    click(temRect.centerX(), temRect.centerY(), 0, 20)
                                }
                            }
                        }
                        setWidgets = null
                        return
                    }
                }
                for (n in 0 until node.childCount) {
                    listB.add(node.getChild(n))
                }
                node.recycle()
            }
            if (a == b) {
                a = 0
                b = listB.size
                listA = listB
                listB = ArrayList()
            }
        }
    }

    private fun showAllChildren(root: AccessibilityNodeInfo) {
        val roots = ArrayList<AccessibilityNodeInfo>()
        roots.add(root)
        val nodeList = ArrayList<AccessibilityNodeInfo>()
        findAllNode(roots, nodeList, "")
    }

    /**
     * 查找所有的控件
     */
    private fun findAllNode(
        roots: List<AccessibilityNodeInfo>,
        list: MutableList<AccessibilityNodeInfo>,
        indent: String
    ) {
        val childrenList = ArrayList<AccessibilityNodeInfo>()
        for (e in roots) {
            if (e == null) continue
            list.add(e)
            //            Log.d(TAG, indent + Utilities.describeAccessibilityNode(e));
            for (n in 0 until e.childCount) {
                childrenList.add(e.getChild(n))
            }
        }
        if (!childrenList.isEmpty()) {
            findAllNode(childrenList, list, "$indent  ")
        }
    }

    /**
     * 模拟点击
     */
    private fun click(X: Int, Y: Int, start_time: Long, duration: Long): Boolean {
        val path = Path()
        path.moveTo(X.toFloat(), Y.toFloat())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val builder =
                GestureDescription.Builder()
                    .addStroke(StrokeDescription(path, start_time, duration))
            service.dispatchGesture(builder.build(), null, null)
        } else {
            false
        }
    }

    /**
     * start the skip-ad process
     */
    private fun startSkipAdProcess() {
//        Log.d(TAG, "Start Skip-ad process");
        methodByActivityPosition = true
        methodByActivityWidget = true
        methodByButtonKeyWord = true
        setWidgets = null
        packagePositionDescription = null

        // cancel all methods 4 seconds later
        if (!futureExpireSkipAdProcess!!.isCancelled && !futureExpireSkipAdProcess!!.isDone) {
            futureExpireSkipAdProcess!!.cancel(true)
        }
        futureExpireSkipAdProcess = executorService!!.schedule(
            { stopSkipAdProcessInner() },
            (mSetting!!.skipAdDuration * 1000).toLong(),
            TimeUnit.MILLISECONDS
        )
    }

    /**
     * stop the skip-ad process
     */
    private fun stopSkipAdProcess() {
        stopSkipAdProcessInner()
        if (!futureExpireSkipAdProcess!!.isCancelled && !futureExpireSkipAdProcess!!.isDone) {
            futureExpireSkipAdProcess!!.cancel(false)
        }
    }

    /**
     * stop the skip-ad process, without cancel scheduled task
     */
    private fun stopSkipAdProcessInner() {
        methodByActivityPosition = false
        methodByActivityWidget = false
        methodByButtonKeyWord = false
        setWidgets = null
        packagePositionDescription = null
        if (toastLock.isLocked) {
            toastLock.unlock()
        }
    }

    /**
     * find all packages while launched. also triggered when receive package add / remove events
     */
    private fun updatePackage() {
        pkgLaunchers = HashSet()
        val pkgTemps: MutableSet<String?> = HashSet()
        // find all launchers
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfoList =
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        for (resolveInfo in resolveInfoList) {
            pkgLaunchers.add(resolveInfo.activityInfo.packageName)
        }
        // ignore some hardcoded packages
        pkgTemps.add(packageName)
        pkgTemps.add("com.android.settings")
        if(!pkgWhiteList.isNullOrEmpty()) {
            pkgLaunchers.removeAll(pkgWhiteList!!)
        }
        pkgLaunchers.removeAll(pkgTemps)
    }

    // display activity customization dialog, and allow users to pick widget or positions
    @SuppressLint("ClickableViewAccessibility")
    private fun showActivityCustomizationDialog() {
        // show activity customization window
        val windowManager =
            service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val b = metrics.heightPixels > metrics.widthPixels
        val width = if (b) metrics.widthPixels else metrics.heightPixels
        val height = if (b) metrics.heightPixels else metrics.widthPixels
        val widgetDescription = PackageWidgetDescription()
        val positionDescription = PackagePositionDescription("", "", 0, 0, 500, 500, 6)
        val inflater = LayoutInflater.from(service)
        // activity customization view
        val viewCustomization = inflater.inflate(R.layout.layout_activity_customization, null)
        val tvPackageName = viewCustomization.findViewById<TextView>(R.id.tv_package_name)
        val tvActivityName = viewCustomization.findViewById<TextView>(R.id.tv_activity_name)
        val tvWidgetInfo = viewCustomization.findViewById<TextView>(R.id.tv_widget_info)
        val tvPositionInfo = viewCustomization.findViewById<TextView>(R.id.tv_position_info)
        val btShowOutline = viewCustomization.findViewById<Button>(R.id.button_show_outline)
        val btAddWidget = viewCustomization.findViewById<Button>(R.id.button_add_widget)
        val btShowTarget = viewCustomization.findViewById<Button>(R.id.button_show_target)
        val btAddPosition = viewCustomization.findViewById<Button>(R.id.button_add_position)
        val btQuit = viewCustomization.findViewById<Button>(R.id.button_quit)
        val viewTarget = inflater.inflate(R.layout.layout_accessibility_node_desc, null)
        val layoutOverlayOutline = viewTarget.findViewById<FrameLayout>(R.id.frame)
        val imageTarget = ImageView(service)
        imageTarget.setImageResource(R.drawable.ic_target)

        // define view positions
        val customizationParams: WindowManager.LayoutParams
        val outlineParams: WindowManager.LayoutParams
        val targetParams: WindowManager.LayoutParams
        customizationParams = WindowManager.LayoutParams()
        customizationParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        customizationParams.format = PixelFormat.TRANSPARENT
        customizationParams.gravity = Gravity.START or Gravity.TOP
        customizationParams.flags =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        customizationParams.width = width
        customizationParams.height = height / 5
        customizationParams.x = (metrics.widthPixels - customizationParams.width) / 2
        customizationParams.y = metrics.heightPixels - customizationParams.height
        customizationParams.alpha = 0.8f
        outlineParams = WindowManager.LayoutParams()
        outlineParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        outlineParams.format = PixelFormat.TRANSPARENT
        outlineParams.gravity = Gravity.START or Gravity.TOP
        outlineParams.width = metrics.widthPixels
        outlineParams.height = metrics.heightPixels
        outlineParams.flags =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        outlineParams.alpha = 0f
        targetParams = WindowManager.LayoutParams()
        targetParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        targetParams.format = PixelFormat.TRANSPARENT
        targetParams.flags =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        targetParams.gravity = Gravity.START or Gravity.TOP
        targetParams.height = width / 4
        targetParams.width = targetParams.height
        targetParams.x = (metrics.widthPixels - targetParams.width) / 2
        targetParams.y = (metrics.heightPixels - targetParams.height) / 2
        targetParams.alpha = 0f
        viewCustomization.setOnTouchListener(object : OnTouchListener {
            var x = 0
            var y = 0
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        x = Math.round(event.rawX)
                        y = Math.round(event.rawY)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        customizationParams.x = Math.round(customizationParams.x + (event.rawX - x))
                        customizationParams.y = Math.round(customizationParams.y + (event.rawY - y))
                        x = Math.round(event.rawX)
                        y = Math.round(event.rawY)
                        windowManager.updateViewLayout(viewCustomization, customizationParams)
                    }
                }
                return true
            }
        })
        imageTarget.setOnTouchListener(object : OnTouchListener {
            var x = 0
            var y = 0
            var width = targetParams.width / 2
            var height = targetParams.height / 2
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        btAddPosition.isEnabled = true
                        targetParams.alpha = 0.9f
                        windowManager.updateViewLayout(imageTarget, targetParams)
                        x = event.rawX.roundToInt()
                        y = event.rawY.roundToInt()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        targetParams.x = (targetParams.x + (event.rawX - x)).roundToInt()
                        targetParams.y = (targetParams.y + (event.rawY - y)).roundToInt()
                        x = event.rawX.roundToInt()
                        y = event.rawY.roundToInt()
                        windowManager.updateViewLayout(imageTarget, targetParams)
                        positionDescription.packageName = currentPackageName!!
                        positionDescription.activityName = currentActivityName!!
                        positionDescription.x = targetParams.x + width
                        positionDescription.y = targetParams.y + height
                        tvPackageName.text = positionDescription.packageName
                        tvActivityName.text = positionDescription.activityName
                        tvPositionInfo.text =
                            "X轴：" + positionDescription.x + "    " + "Y轴：" + positionDescription.y + "    " + "(其他参数默认)"
                    }
                    MotionEvent.ACTION_UP -> {
                        targetParams.alpha = 0.5f
                        windowManager.updateViewLayout(imageTarget, targetParams)
                    }
                }
                return true
            }
        })
        btShowOutline.setOnClickListener(View.OnClickListener { v ->
            val button = v as Button
            if (outlineParams.alpha == 0f) {
                val root = service.rootInActiveWindow ?: return@OnClickListener
                widgetDescription.packageName = currentPackageName!!
                widgetDescription.activityName = currentActivityName!!
                layoutOverlayOutline.removeAllViews()
                val roots = ArrayList<AccessibilityNodeInfo>()
                roots.add(root)
                val nodeList = ArrayList<AccessibilityNodeInfo>()
                findAllNode(roots, nodeList, "")
                nodeList.sortWith { a, b ->
                    val rectA = Rect()
                    val rectB = Rect()
                    a.getBoundsInScreen(rectA)
                    b.getBoundsInScreen(rectB)
                    rectB.width() * rectB.height() - rectA.width() * rectA.height()
                }
                for (e in nodeList) {
                    val temRect = Rect()
                    e.getBoundsInScreen(temRect)
                    val params = FrameLayout.LayoutParams(temRect.width(), temRect.height())
                    params.leftMargin = temRect.left
                    params.topMargin = temRect.top
                    val img = ImageView(service)
                    img.setBackgroundResource(R.drawable.node)
                    img.isFocusableInTouchMode = true
                    img.setOnClickListener { v -> v.requestFocus() }
                    img.onFocusChangeListener = OnFocusChangeListener { v, hasFocus ->
                        if (hasFocus) {
                            widgetDescription.position = temRect
                            widgetDescription.clickable = e.isClickable
                            widgetDescription.className = e.className.toString()
                            val cId: CharSequence? = e.viewIdResourceName
                            widgetDescription.idName = cId?.toString() ?: ""
                            val cDesc = e.contentDescription
                            widgetDescription.description = cDesc?.toString() ?: ""
                            val cText = e.text
                            widgetDescription.text = cText?.toString() ?: ""
                            btAddWidget.isEnabled = true
                            tvPackageName.text = widgetDescription.packageName
                            tvActivityName.text = widgetDescription.activityName
                            tvWidgetInfo.text =
                                "click:" + (if (e.isClickable) "true" else "false") + " " + "bonus:" + temRect.toShortString() + " " + "id:" + (cId?.toString()
                                    ?.substring(cId.toString().indexOf("id/") + 3)
                                    ?: "null") + " " + "desc:" + (cDesc?.toString()
                                    ?: "null") + " " + "text:" + (cText?.toString() ?: "null")
                            v.setBackgroundResource(R.drawable.node_focus)
                        } else {
                            v.setBackgroundResource(R.drawable.node)
                        }
                    }
                    layoutOverlayOutline.addView(img, params)
                }
                outlineParams.alpha = 0.5f
                outlineParams.flags =
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                windowManager.updateViewLayout(viewTarget, outlineParams)
                tvPackageName.text = widgetDescription.packageName
                tvActivityName.text = widgetDescription.activityName
                button.text = "隐藏布局"
            } else {
                outlineParams.alpha = 0f
                outlineParams.flags =
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                windowManager.updateViewLayout(viewTarget, outlineParams)
                btAddWidget.isEnabled = false
                button.text = "显示布局"
            }
        })
        btShowTarget.setOnClickListener { v ->
            val button = v as Button
            if (targetParams.alpha == 0f) {
                positionDescription.packageName = currentPackageName!!
                positionDescription.activityName = currentActivityName!!
                targetParams.alpha = 0.5f
                targetParams.flags =
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                windowManager.updateViewLayout(imageTarget, targetParams)
                tvPackageName.text = positionDescription.packageName
                tvActivityName.text = positionDescription.activityName
                button.text = "隐藏准心"
            } else {
                targetParams.alpha = 0f
                targetParams.flags =
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                windowManager.updateViewLayout(imageTarget, targetParams)
                btAddPosition.isEnabled = false
                button.text = "显示准心"
            }
        }
        btAddWidget.setOnClickListener {
            val temWidget = PackageWidgetDescription(widgetDescription)
            var set = mapPackageWidgets!![widgetDescription.packageName]
            if (set == null) {
                set = HashSet()
                set.add(temWidget)
                mapPackageWidgets!![widgetDescription.packageName] = set
            } else {
                set.add(temWidget)
            }
            btAddWidget.isEnabled = false
            tvPackageName.text = widgetDescription.packageName + " (以下控件数据已保存)"
            // save
            Settings.packageWidgets = mapPackageWidgets
        }
        btAddPosition.setOnClickListener {
            mapPackagePositions!![positionDescription.packageName] =
                PackagePositionDescription(positionDescription)
            btAddPosition.isEnabled = false
            tvPackageName.text = positionDescription.packageName + " (以下坐标数据已保存)"
            // save
            Settings.packagePositions = mapPackagePositions
        }
        btQuit.setOnClickListener {
            windowManager.removeViewImmediate(viewTarget)
            windowManager.removeViewImmediate(viewCustomization)
            windowManager.removeViewImmediate(imageTarget)
        }
        windowManager.addView(viewTarget, outlineParams)
        windowManager.addView(viewCustomization, customizationParams)
        windowManager.addView(imageTarget, targetParams)
    }

    private fun showToast(sText: String?) {
        val myContext: Context = service
        // show one toast in 5 seconds only
        if (mSetting!!.isSkipAdNotification && toastLock.tryLock()) {
            Handler(Looper.getMainLooper()).post {
                val toast = Toast.makeText(myContext, sText, Toast.LENGTH_SHORT)
                toast.show()
            }
        }
    }

    companion object {
        private const val TAG = "TouchHelperServiceImpl"
    }
}