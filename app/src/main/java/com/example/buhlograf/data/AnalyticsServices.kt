package com.example.buhlograf.data

import android.util.Log
import com.example.buhlograf.domain.AnalyticsService
import io.appmetrica.analytics.AppMetrica

class AppMetricaAnalyticsService(
    private val enabled: Boolean
) : AnalyticsService {
    override fun trackEvent(name: String, params: Map<String, Any>) {
        if (enabled) {
            AppMetrica.reportEvent(name, params)
        } else {
            Log.d(TAG, "event=$name params=$params")
        }
    }

    override fun trackError(message: String, error: Throwable?) {
        if (enabled) {
            AppMetrica.reportError(message, error)
        } else {
            Log.d(TAG, "error=$message cause=${error?.message}")
        }
    }

    private companion object {
        const val TAG = "BuhlografAnalytics"
    }
}

class FakeAnalyticsService : AnalyticsService {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    val errors = mutableListOf<String>()

    override fun trackEvent(name: String, params: Map<String, Any>) {
        events += name to params
    }

    override fun trackError(message: String, error: Throwable?) {
        errors += message
    }
}
