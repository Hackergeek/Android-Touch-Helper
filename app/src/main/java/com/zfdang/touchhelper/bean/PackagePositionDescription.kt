package com.zfdang.touchhelper.bean

class PackagePositionDescription {
    var packageName: String
    var activityName: String
    var x: Int
    var y: Int
    var delay: Int
    var period: Int
    var number: Int

    constructor() {
        packageName = ""
        activityName = ""
        x = 0
        y = 0
        delay = 0
        period = 0
        number = 0
    }

    constructor(
        packageName: String,
        activityName: String,
        x: Int,
        y: Int,
        delay: Int,
        period: Int,
        number: Int
    ) {
        this.packageName = packageName
        this.activityName = activityName
        this.x = x
        this.y = y
        this.delay = delay
        this.period = period
        this.number = number
    }

    constructor(positionDescription: PackagePositionDescription) {
        packageName = positionDescription.packageName
        activityName = positionDescription.activityName
        x = positionDescription.x
        y = positionDescription.y
        delay = positionDescription.delay
        period = positionDescription.period
        number = positionDescription.number
    }
}