package com.zfdang.touchhelper.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {
    private val mText: MutableLiveData<String> = MutableLiveData()
    val appPermission: MutableLiveData<Boolean> = MutableLiveData()
    val accessibilityPermission: MutableLiveData<Boolean> = MutableLiveData()
    val powerOptimization: MutableLiveData<Boolean> = MutableLiveData()
    val text: LiveData<String>
        get() = mText

}