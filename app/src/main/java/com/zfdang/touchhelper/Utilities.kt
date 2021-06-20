package com.zfdang.touchhelper

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.zfdang.TouchHelperApp
import java.io.PrintWriter
import java.io.StringWriter

object Utilities {
    const val TAG = "Utilities"
    fun printNodeStack(node: AccessibilityNodeInfo?) {
        var node = node
        Log.d(TAG, "Show Node information: ")
        var indent = ""
        while (node != null) {
            Log.d(
                TAG,
                indent + "class = " + node.className + " id = " + node.windowId + " label = " + node.text
            )
            node = node.parent
            indent += "  "
        }
    }

    fun getTraceStackInString(e: Throwable?): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        e?.printStackTrace(pw)
        return sw.toString()
    }

    fun toast(cs: CharSequence?) {
        Toast.makeText(TouchHelperApp.appContext, cs, Toast.LENGTH_SHORT).show()
    }

    fun describeAccessibilityNode(e: AccessibilityNodeInfo?): String {
        if (e == null) {
            return "null"
        }
        var result = "AccessibilityNode"
        result += " Classname=" + e.className.toString()
        val rect = Rect()
        e.getBoundsInScreen(rect)
        result += String.format(
            " Position=[%d, %d, %d, %d]",
            rect.left,
            rect.right,
            rect.top,
            rect.bottom
        )
        val id: CharSequence? = e.viewIdResourceName
        if (id != null) {
            result += " ResourceId=$id"
        }
        val description = e.contentDescription
        if (description != null) {
            result += " Description=$description"
        }
        val text = e.text
        if (text != null) {
            result += " Text=$text"
        }
        return result
    }
}