package com.studio72f.eigen

import android.app.Application

/**
 * Process start, before any screen or service. 72F AI Share may start Eigen only to run its Gemma model for another
 * 72F app, with no activity at all, so what Eigen serves is registered here.
 */
class EigenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.studio72f.eigen.ai.GemmaModels.register()
        com.studio72f.eigen.ai.EigenAssistant.init(this)
    }
}
