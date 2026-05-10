package com.example.buhlograf

import android.app.Application
import com.example.buhlograf.data.AppMetricaAnalyticsService
import com.example.buhlograf.data.FirebaseBuhlografRepository
import com.example.buhlograf.data.FirebaseRemoteConfigService
import com.example.buhlograf.data.FcmTokenStore
import com.example.buhlograf.data.NoOpCloudSyncService
import com.example.buhlograf.data.InMemoryDrinkRepository
import com.example.buhlograf.data.LocalCatalogRepository
import com.example.buhlograf.data.LocalDailyStatsRepository
import com.example.buhlograf.data.LocalFriendsRepository
import com.example.buhlograf.data.SecureAuthService
import com.example.buhlograf.data.StaticRemoteConfigService
import com.example.buhlograf.domain.AnalyticsService
import com.example.buhlograf.domain.AuthService
import com.example.buhlograf.domain.BuildDashboardUseCase
import com.example.buhlograf.domain.CalculateMascotMoodUseCase
import com.example.buhlograf.domain.CatalogRepository
import com.example.buhlograf.domain.CloudSyncService
import com.example.buhlograf.domain.DailyStatsRepository
import com.example.buhlograf.domain.DrinkRepository
import com.example.buhlograf.domain.FriendsRepository
import com.example.buhlograf.domain.RemoteConfigService
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import com.yandex.mapkit.MapKitFactory
import com.vk.id.VKID
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig

class BuhlografApplication : Application() {
    lateinit var analyticsService: AnalyticsService
        private set
    lateinit var authService: AuthService
        private set
    lateinit var drinkRepository: DrinkRepository
        private set
    lateinit var friendsRepository: FriendsRepository
        private set
    lateinit var catalogRepository: CatalogRepository
        private set
    lateinit var dailyStatsRepository: DailyStatsRepository
        private set
    lateinit var cloudSyncService: CloudSyncService
        private set
    lateinit var remoteConfigService: RemoteConfigService
        private set
    lateinit var buildDashboardUseCase: BuildDashboardUseCase
        private set

    val hasYandexClientId: Boolean
        get() = BuildConfig.YANDEX_CLIENT_ID.isNotBlank()

    val hasMapKitKey: Boolean
        get() = BuildConfig.YANDEX_MAPKIT_API_KEY.isNotBlank()

    val hasVkKeys: Boolean
        get() = BuildConfig.VK_APP_ID.isNotBlank() && BuildConfig.VK_CLIENT_SECRET.isNotBlank()

    val hasFirebaseConfig: Boolean
        get() = BuildConfig.HAS_GOOGLE_SERVICES_JSON

    override fun onCreate() {
        super.onCreate()
        initializeAppMetrica()
        initializeMapKit()
        initializeVkId()

        analyticsService = AppMetricaAnalyticsService(
            enabled = BuildConfig.APPMETRICA_API_KEY.isNotBlank()
        )
        authService = SecureAuthService(this)
        FcmTokenStore.load(this)
        if (hasFirebaseConfig) {
            FirebaseApp.initializeApp(this)
            val realtimeDatabase = if (BuildConfig.FIREBASE_DATABASE_URL.isNotBlank()) {
                FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL)
            } else {
                FirebaseDatabase.getInstance()
            }
            val firebaseRepository = FirebaseBuhlografRepository(
                firestore = FirebaseFirestore.getInstance(),
                realtimeDatabase = realtimeDatabase
            )
            drinkRepository = firebaseRepository
            friendsRepository = firebaseRepository
            catalogRepository = firebaseRepository
            dailyStatsRepository = firebaseRepository
            cloudSyncService = firebaseRepository
            remoteConfigService = FirebaseRemoteConfigService(FirebaseRemoteConfig.getInstance())
        } else {
            val localDrinkRepository = InMemoryDrinkRepository()
            val localCatalogRepository = LocalCatalogRepository()
            drinkRepository = localDrinkRepository
            friendsRepository = LocalFriendsRepository()
            catalogRepository = localCatalogRepository
            dailyStatsRepository = LocalDailyStatsRepository(localDrinkRepository)
            cloudSyncService = NoOpCloudSyncService()
            remoteConfigService = StaticRemoteConfigService()
        }
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
