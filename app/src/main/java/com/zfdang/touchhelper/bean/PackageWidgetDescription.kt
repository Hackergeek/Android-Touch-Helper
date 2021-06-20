package com.zfdang.touchhelper.bean

import android.graphics.Rect
import java.util.*

class PackageWidgetDescription {
    var packageName: String
    var activityName: String
    var className: String
    var idName: String
    var description: String
    var text: String
    var position: Rect
    var clickable: Boolean
    var onlyClick: Boolean

    constructor() {
        packageName = ""
        activityName = ""
        className = ""
        idName = ""
        description = ""
        text = ""
        position = Rect()
        clickable = false
        onlyClick = false
    }

    constructor(
        packageName: String,
        activityName: String,
        className: String,
        idName: String,
        description: String,
        text: String,
        position: Rect,
        clickable: Boolean,
        onlyClick: Boolean
    ) {
        this.packageName = packageName
        this.activityName = activityName
        this.className = className
        this.idName = idName
        this.description = description
        this.text = text
        this.position = position
        this.clickable = clickable
        this.onlyClick = onlyClick
    }

    constructor(widgetDescription: PackageWidgetDescription) {
        packageName = widgetDescription.packageName
        activityName = widgetDescription.activityName
        className = widgetDescription.className
        idName = widgetDescription.idName
        description = widgetDescription.description
        text = widgetDescription.text
        position = Rect(widgetDescription.position)
        clickable = widgetDescription.clickable
        onlyClick = widgetDescription.onlyClick
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this === other) return true
        if (other !is PackageWidgetDescription) return false
        return position == other.position
    }

    override fun hashCode(): Int {
        return Objects.hash(position)
    }
}