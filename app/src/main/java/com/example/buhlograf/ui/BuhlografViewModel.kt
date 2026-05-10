package com.example.buhlograf.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.buhlograf.domain.AlcoholCategory
import com.example.buhlograf.domain.AlcoholProduct
import com.example.buhlograf.domain.AnalyticsService
import com.example.buhlograf.domain.AuthProvider
import com.example.buhlograf.domain.AuthService
import com.example.buhlograf.domain.BuildDashboardUseCase
import com.example.buhlograf.domain.BuildUserIdUseCase
import com.example.buhlograf.domain.CalendarDay
import com.example.buhlograf.domain.CalendarUiState
import com.example.buhlograf.domain.CatalogLoadState
import com.example.buhlograf.domain.CatalogRepository
import com.example.buhlograf.domain.CloudSyncService
import com.example.buhlograf.domain.DailyStatsRepository
import com.example.buhlograf.domain.DrinkDashboard
import com.example.buhlograf.domain.DrinkRepository
import com.example.buhlograf.domain.FriendProgress
import com.example.buhlograf.domain.FriendsRepository
import com.example.buhlograf.domain.ProductSource
import com.example.buhlograf.domain.ProductSuggestion
import com.example.buhlograf.domain.ProductTag
import com.example.buhlograf.domain.PublicIdGenerator
import com.example.buhlograf.domain.RemoteConfigService
import com.example.buhlograf.domain.RemoteConfigState
import com.example.buhlograf.domain.UserProfile
import com.example.buhlograf.domain.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class ProductVisibilityFilter(val title: String) {
    Active("Активные"),
    Hidden("Скрытые"),
    All("Все")
}

data class BuhlografUiState(
    val session: UserSession? = null,
    val dashboard: DrinkDashboard,
    val selectedTab: AppTab = AppTab.Dashboard,
    val addDrinkDialogVisible: Boolean = false,
    val loginMessage: String? = null,
    val vkMemeVisible: Boolean = false,
    val friendInput: String = "",
    val friendMessage: String? = null,
    val aboutDialogVisible: Boolean = false,
    val cloudProfile: UserProfile? = null,
    val remoteConfig: RemoteConfigState = RemoteConfigState(),
    val products: List<AlcoholProduct> = emptyList(),
    val productSuggestions: List<ProductSuggestion> = emptyList(),
    val catalogLoadState: CatalogLoadState = CatalogLoadState.Loading,
    val catalogMessage: String? = null,
    val adminProductDialogVisible: Boolean = false,
    val moderationSuggestion: ProductSuggestion? = null,
    val editingProduct: AlcoholProduct? = null,
    val selectedTag: String? = null,
    val productVisibilityFilter: ProductVisibilityFilter = ProductVisibilityFilter.Active,
    val calendar: CalendarUiState? = null
) {
    val catalogReady: Boolean
        get() = products.isNotEmpty() && catalogLoadState !is CatalogLoadState.Loading
}

enum class AppTab(val title: String) {
    Dashboard("График"),
    Catalog("Ассортимент"),
    Friends("Друзья"),
    Account("Аккаунт")
}

class BuhlografViewModel(
    private val authService: AuthService,
    private val drinkRepository: DrinkRepository,
    private val friendsRepository: FriendsRepository,
    private val catalogRepository: CatalogRepository,
    private val dailyStatsRepository: DailyStatsRepository,
    private val cloudSyncService: CloudSyncService,
    private val remoteConfigService: RemoteConfigService,
    private val buildDashboard: BuildDashboardUseCase,
    private val analyticsService: AnalyticsService,
    private val buildUserId: BuildUserIdUseCase = BuildUserIdUseCase()
) : ViewModel() {
    private val dayFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val _state = MutableStateFlow(
        BuhlografUiState(
            session = authService.getSavedSession(),
            dashboard = buildDashboard(),
            products = catalogRepository.getProducts(),
            productSuggestions = catalogRepository.getSuggestions(),
            catalogLoadState = catalogRepository.getLoadState()
        )
    )
    val state: StateFlow<BuhlografUiState> = _state

    init {
        analyticsService.trackEvent(
            name = "screen_viewed",
            params = mapOf("screen_name" to "dashboard")
        )
        authService.getSavedSession()?.let { session ->
            bindCloudSession(session)
            startCatalogListening()
        }
        remoteConfigService.fetch { config ->
            _state.update { it.copy(remoteConfig = config) }
        }
    }

    fun enterDemoMode() {
        val session = authService.createDemoSession()
        authService.saveSession(session)
        analyticsService.trackEvent(
            name = "user_logged_in",
            params = mapOf("provider" to session.provider.analyticsName)
        )
        _state.update { it.copy(session = session, loginMessage = null) }
        bindCloudSession(session)
        startCatalogListening()
    }

    fun onYandexLoginSuccess(
        token: String,
        userName: String,
        photoUrl: String? = null,
        email: String? = null,
        firebaseUid: String? = null
    ) {
        val normalizedEmail = email?.trim()?.lowercase()
        val userId = buildUserId(AuthProvider.Yandex, normalizedEmail ?: firebaseUid ?: token)
        val session = UserSession(
            token = token,
            userName = userName.ifBlank { "Пользователь Яндекса" },
            provider = AuthProvider.Yandex,
            userId = userId,
            publicId = PublicIdGenerator.fromUserId(userId),
            photoUrl = photoUrl,
            email = normalizedEmail
        )
        saveLoggedSession(session)
    }

    fun onYandexLoginError(message: String) {
        analyticsService.trackError("Yandex login failed: $message")
        _state.update { it.copy(loginMessage = message) }
    }

    fun onVkLoginSuccess(token: String, userName: String, photoUrl: String?, firebaseUid: String? = null) {
        val userId = firebaseUid ?: buildUserId(AuthProvider.VK, token)
        val session = UserSession(
            token = token,
            userName = userName.ifBlank { "Пользователь VK" },
            provider = AuthProvider.VK,
            userId = userId,
            publicId = PublicIdGenerator.fromUserId(userId),
            photoUrl = photoUrl
        )
        saveLoggedSession(session)
    }

    fun onVkLoginError(message: String) {
        analyticsService.trackError("VK login failed: $message")
        _state.update { it.copy(loginMessage = message) }
    }

    fun showVkMeme() {
        _state.update { it.copy(vkMemeVisible = true, loginMessage = null) }
    }

    fun hideVkMeme() {
        _state.update { it.copy(vkMemeVisible = false) }
    }

    fun onGoogleLoginSuccess(
        token: String,
        userName: String,
        photoUrl: String?,
        email: String? = null,
        firebaseUid: String? = null
    ) {
        val userId = firebaseUid ?: buildUserId(AuthProvider.Google, token)
        val session = UserSession(
            token = token,
            userName = userName.ifBlank { "Пользователь Google" },
            provider = AuthProvider.Google,
            userId = userId,
            publicId = PublicIdGenerator.fromUserId(userId),
            photoUrl = photoUrl,
            email = email
        )
        saveLoggedSession(session)
    }

    fun onGoogleLoginError(message: String) {
        analyticsService.trackError("Google login failed: $message")
        _state.update { it.copy(loginMessage = message) }
    }

    fun logout() {
        authService.clearSession()
        cloudSyncService.unbind()
        _state.update {
            it.copy(
                session = null,
                cloudProfile = null,
                selectedTab = AppTab.Dashboard,
                calendar = null
            )
        }
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

    fun hideAddDrinkDialog() {
        _state.update { it.copy(addDrinkDialogVisible = false) }
    }

    fun addDrink(product: AlcoholProduct, volumeMl: Int, strengthPercent: Double, note: String) {
        val entry = drinkRepository.addEntry(
            product = product,
            volumeMl = volumeMl.coerceAtLeast(1),
            strengthPercent = strengthPercent.coerceIn(0.0, 96.0),
            note = note
        )
        analyticsService.trackEvent(
            name = "drink_added",
            params = mapOf(
                "product_id" to entry.productId,
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

    fun updateFriendInput(value: String) {
        _state.update {
            it.copy(
                friendInput = PublicIdGenerator.normalize(value),
                friendMessage = null
            )
        }
    }

    fun addFriend() {
        val id = PublicIdGenerator.normalize(_state.value.friendInput)
        val ownPublicId = _state.value.cloudProfile?.publicId?.ifBlank { null }
            ?: _state.value.session?.publicId.orEmpty()
        if (id == ownPublicId) {
            _state.update { it.copy(friendMessage = "Это ваш ID. Себя добавить нельзя.") }
            return
        }
        val friend = friendsRepository.addFriendById(id)
        if (friend == null) {
            _state.update { it.copy(friendMessage = "Введите ID друга из 6 букв/цифр.") }
            return
        }
        analyticsService.trackEvent(
            name = "friend_added",
            params = mapOf("friend_public_id" to friend.publicId)
        )
        _state.update {
            it.copy(
                dashboard = buildDashboard(),
                friendInput = "",
                friendMessage = "Заявка ${friend.publicId} отправлена."
            )
        }
    }

    fun acceptFriend(publicId: String) {
        friendsRepository.acceptFriend(publicId)
        analyticsService.trackEvent(
            name = "friend_request_accepted",
            params = mapOf("friend_public_id" to publicId)
        )
    }

    fun rejectFriend(publicId: String) {
        friendsRepository.rejectFriend(publicId)
        analyticsService.trackEvent(
            name = "friend_request_rejected",
            params = mapOf("friend_public_id" to publicId)
        )
    }

    fun removeFriend(publicId: String) {
        friendsRepository.removeFriend(publicId)
        analyticsService.trackEvent(
            name = "friend_removed",
            params = mapOf("friend_public_id" to publicId)
        )
        _state.update { it.copy(dashboard = buildDashboard()) }
    }

    fun showAboutDialog() {
        _state.update { it.copy(aboutDialogVisible = true) }
    }

    fun hideAboutDialog() {
        _state.update { it.copy(aboutDialogVisible = false) }
    }

    fun showAdminProductDialog() {
        _state.update { it.copy(adminProductDialogVisible = true, catalogMessage = null) }
    }

    fun hideAdminProductDialog() {
        _state.update { it.copy(adminProductDialogVisible = false) }
    }

    fun editProduct(product: AlcoholProduct) {
        _state.update { it.copy(editingProduct = product, catalogMessage = null) }
    }

    fun closeProductEditor() {
        _state.update { it.copy(editingProduct = null) }
    }

    fun selectCatalogTag(tag: String?) {
        _state.update { it.copy(selectedTag = tag) }
    }

    fun selectProductVisibilityFilter(filter: ProductVisibilityFilter) {
        _state.update { it.copy(productVisibilityFilter = filter) }
    }

    fun addProductFromAdmin(
        name: String,
        brand: String,
        description: String,
        category: AlcoholCategory,
        volumeMl: Int,
        strengthPercent: Double,
        imageUrl: String,
        tags: List<String>
    ) {
        val state = _state.value
        val session = state.session ?: return
        val isAdmin = state.cloudProfile?.isAdmin == true
        if (name.isBlank()) {
            _state.update { it.copy(catalogMessage = "Название напитка обязательно.") }
            return
        }
        val now = System.currentTimeMillis()
        val product = AlcoholProduct(
            id = "product-$now",
            barcode = "",
            name = name.trim(),
            brand = brand.trim(),
            description = description.trim(),
            category = category,
            volumeMl = volumeMl.coerceAtLeast(1),
            strengthPercent = strengthPercent.coerceIn(0.0, 96.0),
            imageUrl = imageUrl.trim(),
            source = if (isAdmin) ProductSource.Admin else ProductSource.UserSuggested,
            isVerified = isAdmin,
            tags = tags.ifEmpty { ProductTag.defaultFor(category) },
            createdBy = session.userId,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        catalogRepository.submitProduct(
            product = product,
            createdBy = session.userId,
            authorName = session.userName,
            isAdmin = isAdmin
        ) { saved, errorMessage ->
            _state.update {
                it.copy(
                    adminProductDialogVisible = !saved,
                    catalogMessage = if (saved) {
                        if (isAdmin) "Напиток добавлен." else "Заявка отправлена на модерацию."
                    } else {
                        "Не удалось сохранить напиток: ${errorMessage ?: "проверьте правила Firebase"}."
                    }
                )
            }
            refreshCatalog()
        }
        if (!isAdmin) {
            _state.update { it.copy(catalogMessage = "Отправляем заявку на модерацию...") }
        }
    }

    fun updateExistingProduct(
        name: String,
        brand: String,
        description: String,
        category: AlcoholCategory,
        volumeMl: Int,
        strengthPercent: Double,
        imageUrl: String,
        tags: List<String>
    ) {
        val session = _state.value.session ?: return
        val source = _state.value.editingProduct ?: return
        val isAdmin = _state.value.cloudProfile?.isAdmin == true
        if (!isAdmin) return
        val updated = source.copy(
            barcode = "",
            name = name.trim(),
            brand = brand.trim(),
            description = description.trim(),
            category = category,
            volumeMl = volumeMl.coerceAtLeast(1),
            strengthPercent = strengthPercent.coerceIn(0.0, 96.0),
            imageUrl = imageUrl.trim(),
            tags = tags.ifEmpty { ProductTag.defaultFor(category) },
            updatedBy = session.userId,
            updatedAtMillis = System.currentTimeMillis()
        )
        val saved = catalogRepository.updateProduct(updated, isAdmin)
        _state.update {
            it.copy(
                editingProduct = if (saved) null else source,
                catalogMessage = if (saved) "Напиток обновлен." else "Не удалось обновить напиток."
            )
        }
        refreshCatalog()
    }

    fun setProductActive(productId: String, isActive: Boolean) {
        val session = _state.value.session ?: return
        val isAdmin = _state.value.cloudProfile?.isAdmin == true
        if (!isAdmin) return
        val saved = catalogRepository.setProductActive(productId, isActive, session.userId, isAdmin)
        _state.update {
            it.copy(
                catalogMessage = if (saved) {
                    if (isActive) "Напиток возвращен в ассортимент." else "Напиток скрыт из ассортимента."
                } else {
                    "Не удалось изменить напиток."
                }
            )
        }
        refreshCatalog()
    }

    fun openModeration(suggestion: ProductSuggestion) {
        _state.update { it.copy(moderationSuggestion = suggestion, catalogMessage = null) }
    }

    fun closeModeration() {
        _state.update { it.copy(moderationSuggestion = null) }
    }

    fun approveSuggestion(
        suggestionId: String,
        name: String,
        brand: String,
        description: String,
        category: AlcoholCategory,
        volumeMl: Int,
        strengthPercent: Double,
        imageUrl: String,
        tags: List<String>
    ) {
        val session = _state.value.session ?: return
        val isAdmin = _state.value.cloudProfile?.isAdmin == true
        if (!isAdmin) return
        val suggestion = _state.value.moderationSuggestion ?: return
        val product = suggestion.product.copy(
            barcode = "",
            name = name.trim(),
            brand = brand.trim(),
            description = description.trim(),
            category = category,
            volumeMl = volumeMl.coerceAtLeast(1),
            strengthPercent = strengthPercent.coerceIn(0.0, 96.0),
            imageUrl = imageUrl.trim(),
            tags = tags.ifEmpty { ProductTag.defaultFor(category) },
            source = ProductSource.Admin,
            isVerified = true
        )
        catalogRepository.approveSuggestion(suggestionId, product, session.userId)
        _state.update {
            it.copy(
                moderationSuggestion = null,
                catalogMessage = "Заявка одобрена."
            )
        }
        refreshCatalog()
    }

    fun rejectSuggestion(suggestionId: String) {
        val session = _state.value.session ?: return
        catalogRepository.rejectSuggestion(suggestionId, session.userId)
        _state.update { it.copy(catalogMessage = "Заявка отклонена.") }
        refreshCatalog()
    }

    fun hideSuggestionAuthor(authorId: String) {
        catalogRepository.hideSuggestionAuthor(authorId)
        _state.update { it.copy(catalogMessage = "Автор скрыт из списка заявок.") }
        refreshCatalog()
    }

    fun openOwnCalendar() {
        val state = _state.value
        val publicId = state.cloudProfile?.publicId?.ifBlank { null } ?: state.session?.publicId.orEmpty()
        val name = state.cloudProfile?.name?.ifBlank { null } ?: state.session?.userName ?: "Аккаунт"
        openCalendar(publicId, name)
    }

    fun openFriendCalendar(friend: FriendProgress) {
        openCalendar(friend.publicId, friend.name)
    }

    fun selectCalendarDay(dayKey: String) {
        val calendar = _state.value.calendar ?: return
        _state.update {
            it.copy(
                calendar = buildCalendarState(
                    ownerPublicId = calendar.ownerPublicId,
                    ownerName = calendar.ownerName,
                    selectedDayKey = dayKey
                )
            )
        }
    }

    fun closeCalendar() {
        _state.update { it.copy(calendar = null) }
    }

    private fun saveLoggedSession(session: UserSession) {
        _state.update { it.copy(loginMessage = "Проверяем профиль по почте...") }
        cloudSyncService.resolveSessionByEmail(session) { resolvedSession ->
            authService.saveSession(resolvedSession)
            analyticsService.trackEvent(
                name = "user_logged_in",
                params = mapOf("provider" to resolvedSession.provider.analyticsName)
            )
            _state.update { it.copy(session = resolvedSession, loginMessage = null) }
            bindCloudSession(resolvedSession)
            startCatalogListening()
        }
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

    private fun refreshCatalog() {
        _state.update {
            it.copy(
                products = catalogRepository.getProducts(),
                productSuggestions = catalogRepository.getSuggestions(),
                catalogLoadState = catalogRepository.getLoadState()
            )
        }
    }

    private fun startCatalogListening() {
        catalogRepository.startListening {
            refreshCatalog()
        }
        catalogRepository.startSuggestionsListening {
            refreshCatalog()
        }
        refreshCatalog()
    }

    private fun bindCloudSession(session: UserSession) {
        cloudSyncService.bindSession(
            session = session,
            onDataChanged = {
                _state.update { current ->
                    val calendar = current.calendar
                    current.copy(
                        dashboard = buildDashboard(),
                        calendar = calendar?.let {
                            buildCalendarState(it.ownerPublicId, it.ownerName, it.selectedDayKey)
                        }
                    )
                }
            },
            onProfileChanged = { profile ->
                _state.update { it.copy(cloudProfile = profile) }
            }
        )
    }

    private fun openCalendar(ownerPublicId: String, ownerName: String) {
        if (ownerPublicId.isBlank()) return
        val today = LocalDate.now().format(dayFormatter)
        _state.update {
            it.copy(calendar = buildCalendarState(ownerPublicId, ownerName, today))
        }
    }

    private fun buildCalendarState(
        ownerPublicId: String,
        ownerName: String,
        selectedDayKey: String
    ): CalendarUiState {
        val stats = dailyStatsRepository.getDailyStats(ownerPublicId)
        val statsByDay = stats.associateBy { it.dayKey }
        val selectedDate = runCatching { LocalDate.parse(selectedDayKey, dayFormatter) }
            .getOrDefault(LocalDate.now())
        val month = YearMonth.from(selectedDate)
        val days = (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            val key = date.format(dayFormatter)
            val stat = statsByDay[key]
            CalendarDay(
                dayKey = key,
                dayOfMonth = day,
                hasEntries = stat != null && stat.entriesCount > 0,
                pureAlcoholMl = stat?.totalPureAlcoholMl ?: 0.0
            )
        }
        return CalendarUiState(
            ownerPublicId = ownerPublicId,
            ownerName = ownerName,
            selectedDayKey = selectedDayKey,
            monthDays = days,
            selectedDayStats = dailyStatsRepository.getDayStats(ownerPublicId, selectedDayKey),
            selectedDayEntries = dailyStatsRepository.getDayEntries(ownerPublicId, selectedDayKey),
            isLoading = false
        )
    }
}

class BuhlografViewModelFactory(
    private val authService: AuthService,
    private val drinkRepository: DrinkRepository,
    private val friendsRepository: FriendsRepository,
    private val catalogRepository: CatalogRepository,
    private val dailyStatsRepository: DailyStatsRepository,
    private val cloudSyncService: CloudSyncService,
    private val remoteConfigService: RemoteConfigService,
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
                catalogRepository = catalogRepository,
                dailyStatsRepository = dailyStatsRepository,
                cloudSyncService = cloudSyncService,
                remoteConfigService = remoteConfigService,
                buildDashboard = buildDashboard,
                analyticsService = analyticsService
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
