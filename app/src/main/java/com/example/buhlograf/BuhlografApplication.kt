package com.example.buhlograf

import android.app.Application
import com.example.buhlograf.data.AppMetricaAnalyticsService
import com.example.buhlograf.data.InMemoryDrinkRepository
import com.example.buhlograf.data.LocalFriendsRepository
import com.example.buhlograf.data.SecureAuthService
import com.example.buhlograf.domain.AnalyticsService
import com.example.buhlograf.domain.AuthService
import com.example.buhlograf.domain.BuildDashboardUseCase
import com.example.buhlograf.domain.CalculateMascotMoodUseCase
import com.example.buhlograf.domain.DrinkRepository
import com.example.buhlograf.domain.FriendsRepository
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import com.yandex.mapkit.MapKitFactory
import com.vk.id.VKID

class BuhlografApplication : Application() {
    lateinit var analyticsService: AnalyticsService
        private set
    lateinit var authService: AuthService
        private set
    lateinit var drinkRepository: DrinkRepository
        private set
    lateinit var friendsRepository: FriendsRepository
        private set
    lateinit var buildDashboardUseCase: BuildDashboardUseCase
        private set

    val hasYandexClientId: Boolean
        get() = BuildConfig.YANDEX_CLIENT_ID.isNotBlank()

    val hasMapKitKey: Boolean
        get() = BuildConfig.YANDEX_MAPKIT_API_KEY.isNotBlank()

    val hasVkKeys: Boolean
        get() = BuildConfig.VK_APP_ID.isNotBlank() && BuildConfig.VK_CLIENT_SECRET.isNotBlank()

    override fun onCreate() {
        super.onCreate()
        initializeAppMetrica()
        initializeMapKit()
        initializeVkId()

        analyticsService = AppMetricaAnalyticsService(
            enabled = BuildConfig.APPMETRICA_API_KEY.isNotBlank()
        )
        authService = SecureAuthService(this)
        drinkRepository = InMemoryDrinkRepository()
        friendsRepository = LocalFriendsRepository()
        buildDashboardUseCase = BuildDashboardUseCase(
            drinkRepository = drinkRepository,
            friendsRepository = friendsRepository,
            calculateMascotMood = CalculateMascotMoodUseCase()
        )
    }

    private fun initializeAppMetrica() {
        val apiKey = BuildConfig.APPMETRICA_API_KEY
        if (apiKey.isBlank()) return

        val config = AppMetricaConfig.newConfigBuilder(apiKey).build()
        AppMetrica.activate(this, config)
        AppMetrica.enableActivityAutoTracking(this)
    }

    private fun initializeMapKit() {
        val apiKey = BuildConfig.YANDEX_MAPKIT_API_KEY
        if (apiKey.isBlank()) return

        MapKitFactory.setApiKey(apiKey)
        MapKitFactory.initialize(this)
    }

    private fun initializeVkId() {
        if (!hasVkKeys) return

        VKID.init(this)
    }
}
