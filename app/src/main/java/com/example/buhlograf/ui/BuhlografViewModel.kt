package com.example.buhlograf.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.buhlograf.domain.AnalyticsService
import com.example.buhlograf.domain.AuthProvider
import com.example.buhlograf.domain.AuthService
import com.example.buhlograf.domain.BuildDashboardUseCase
import com.example.buhlograf.domain.BuildUserIdUseCase
import com.example.buhlograf.domain.DrinkDashboard
import com.example.buhlograf.domain.DrinkRepository
import com.example.buhlograf.domain.DrinkType
import com.example.buhlograf.domain.FriendsRepository
import com.example.buhlograf.domain.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class BuhlografUiState(
    val session: UserSession? = null,
    val dashboard: DrinkDashboard,
    val selectedTab: AppTab = AppTab.Dashboard,
    val addDrinkDialogVisible: Boolean = false,
    val loginMessage: String? = null,
    val friendInput: String = "",
    val friendMessage: String? = null,
    val aboutDialogVisible: Boolean = false
)

enum class AppTab(val title: String) {
    Dashboard("График"),
    Friends("Друзья"),
    Account("Аккаунт")
}

class BuhlografViewModel(
    private val authService: AuthService,
    private val drinkRepository: DrinkRepository,
    private val friendsRepository: FriendsRepository,
    private val buildDashboard: BuildDashboardUseCase,
    private val analyticsService: AnalyticsService,
    private val buildUserId: BuildUserIdUseCase = BuildUserIdUseCase()
) : ViewModel() {
    private val _state = MutableStateFlow(
        BuhlografUiState(
            session = authService.getSavedSession(),
            dashboard = buildDashboard()
        )
    )
    val state: StateFlow<BuhlografUiState> = _state

    init {
        analyticsService.trackEvent(
            name = "screen_viewed",
            params = mapOf("screen_name" to "dashboard")
        )
    }

    fun enterDemoMode() {
        val session = authService.createDemoSession()
        authService.saveSession(session)
        analyticsService.trackEvent(
            name = "user_logged_in",
            params = mapOf("provider" to session.provider.analyticsName)
        )
        _state.update { it.copy(session = session, loginMessage = null) }
    }

    fun onYandexLoginSuccess(token: String, userName: String) {
        val session = UserSession(
            token = token,
            userName = userName.ifBlank { "Пользователь Яндекса" },
            provider = AuthProvider.Yandex,
            userId = buildUserId(AuthProvider.Yandex, token)
        )
        authService.saveSession(session)
        analyticsService.trackEvent(
            name = "user_logged_in",
            params = mapOf("provider" to session.provider.analyticsName)
        )
        _state.update { it.copy(session = session, loginMessage = null) }
    }

    fun onYandexLoginError(message: String) {
        analyticsService.trackError("Yandex login failed: $message")
        _state.update { it.copy(loginMessage = message) }
    }

    fun onVkLoginSuccess(token: String, userName: String, photoUrl: String?) {
        val session = UserSession(
            token = token,
            userName = userName.ifBlank { "Пользователь VK" },
            provider = AuthProvider.VK,
            userId = buildUserId(AuthProvider.VK, token),
            photoUrl = photoUrl
        )
        authService.saveSession(session)
        analyticsService.trackEvent(
            name = "user_logged_in",
            params = mapOf("provider" to session.provider.analyticsName)
        )
        _state.update { it.copy(session = session, loginMessage = null) }
    }

    fun onVkLoginError(message: String) {
        analyticsService.trackError("VK login failed: $message")
        _state.update { it.copy(loginMessage = message) }
    }

    fun logout() {
        authService.clearSession()
        _state.update { it.copy(session = null) }
    }

    fun onGoogleLoginSuccess(token: String, userName: String, photoUrl: String?) {
        val session = UserSession(
            token = token,
            userName = userName.ifBlank { "Пользователь Google" },
            provider = AuthProvider.Google,
            userId = buildUserId(AuthProvider.Google, token),
            photoUrl = photoUrl
        )
        authService.saveSession(session)
        analyticsService.trackEvent(
            name = "user_logged_in",
            params = mapOf("provider" to session.provider.analyticsName)
        )
        _state.update { it.copy(session = session, loginMessage = null) }
    }

    fun onGoogleLoginError(message: String) {
        analyticsService.trackError("Google login failed: $message")
        _state.update { it.copy(loginMessage = message) }
    }

    fun selectTab(tab: AppTab) {
        analyticsService.trackEvent(
            name = "screen_viewed",
            params = mapOf("screen_name" to tab.name.lowercase())
        )
        _state.update { it.copy(selectedTab = tab) }
    }

    fun showAddDrinkDialog() {
        _state.update { it.copy(addDrinkDialogVisible = true) }
    }

    fun updateFriendInput(value: String) {
        _state.update { it.copy(friendInput = value, friendMessage = null) }
    }

    fun addFriend() {
        val id = _state.value.friendInput
        val friend = friendsRepository.addFriendById(id)
        if (friend == null) {
            _state.update { it.copy(friendMessage = "Введите ID друга.") }
            return
        }
        analyticsService.trackEvent(
            name = "friend_added",
            params = mapOf("friend_id" to friend.id)
        )
        _state.update {
            it.copy(
                dashboard = buildDashboard(),
                friendInput = "",
                friendMessage = "Друг ${friend.id} добавлен."
            )
        }
    }

    fun showAboutDialog() {
        _state.update { it.copy(aboutDialogVisible = true) }
    }

    fun hideAboutDialog() {
        _state.update { it.copy(aboutDialogVisible = false) }
    }

    fun hideAddDrinkDialog() {
        _state.update { it.copy(addDrinkDialogVisible = false) }
    }

    fun addDrink(type: DrinkType, volumeMl: Int, strengthPercent: Double, note: String) {
        val entry = drinkRepository.addEntry(
            type = type,
            volumeMl = volumeMl.coerceAtLeast(1),
            strengthPercent = strengthPercent.coerceIn(0.0, 96.0),
            note = note
        )
        analyticsService.trackEvent(
            name = "drink_added",
            params = mapOf(
                "type" to entry.type.name.lowercase(),
                "volume_ml" to entry.volumeMl,
                "strength_percent" to entry.strengthPercent
            )
        )
        refreshDashboard(addDrinkDialogVisible = false)
    }

    fun clearToday() {
        drinkRepository.clearToday()
        analyticsService.trackEvent(name = "day_cleared")
        refreshDashboard(addDrinkDialogVisible = false)
    }

    private fun refreshDashboard(addDrinkDialogVisible: Boolean) {
        val dashboard = buildDashboard()
        if (dashboard.mood.name == "Warning") {
            analyticsService.trackEvent(
                name = "limit_warning_shown",
                params = mapOf("pure_alcohol_ml" to dashboard.totalPureAlcoholMl)
            )
        }
        _state.update {
            it.copy(
                dashboard = dashboard,
                addDrinkDialogVisible = addDrinkDialogVisible
            )
        }
    }
}

class BuhlografViewModelFactory(
    private val authService: AuthService,
    private val drinkRepository: DrinkRepository,
    private val friendsRepository: FriendsRepository,
    private val buildDashboard: BuildDashboardUseCase,
    private val analyticsService: AnalyticsService
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BuhlografViewModel::class.java)) {
            return BuhlografViewModel(
                authService = authService,
                drinkRepository = drinkRepository,
                friendsRepository = friendsRepository,
                buildDashboard = buildDashboard,
                analyticsService = analyticsService
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
