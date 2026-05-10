package com.example.buhlograf.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.buhlograf.domain.AlcoholCategory
import com.example.buhlograf.domain.AlcoholProduct
import com.example.buhlograf.domain.CalendarUiState
import com.example.buhlograf.domain.CatalogLoadState
import com.example.buhlograf.domain.DrinkDashboard
import com.example.buhlograf.domain.DrinkEntry
import com.example.buhlograf.domain.DrinkType
import com.example.buhlograf.domain.FriendProgress
import com.example.buhlograf.domain.FriendRelationStatus
import com.example.buhlograf.domain.ProductSuggestion
import com.example.buhlograf.domain.ProductTag
import com.example.buhlograf.domain.RemoteConfigState
import com.example.buhlograf.domain.UserProfile
import com.example.buhlograf.domain.UserSession
import coil.compose.AsyncImage
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun BuhlografApp(
    viewModel: BuhlografViewModel,
    hasYandexClientId: Boolean,
    hasMapKitKey: Boolean,
    hasVkKeys: Boolean,
    onYandexLoginClick: () -> Unit,
    onVkLoginClick: () -> Unit,
    onGoogleLoginClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    if (state.session == null) {
        if (state.vkMemeVisible) {
            VkMemeScreen(onBack = viewModel::hideVkMeme)
            return
        }
        LoginScreen(
            hasYandexClientId = hasYandexClientId,
            hasVkKeys = hasVkKeys,
            message = state.loginMessage,
            onYandexClick = onYandexLoginClick,
            onVkClick = onVkLoginClick,
            onGoogleClick = onGoogleLoginClick,
            onDemoClick = viewModel::enterDemoMode
        )
        return
    }

    if (!state.catalogReady) {
        CatalogLoadingScreen(
            loadState = state.catalogLoadState,
            productsLoaded = state.products.size,
            onLogout = viewModel::logout
        )
        return
    }

    Scaffold(
        containerColor = BuhloBackground,
        floatingActionButton = {
            if (state.selectedTab == AppTab.Dashboard) {
                FloatingActionButton(
                    onClick = viewModel::showAddDrinkDialog,
                    containerColor = BuhloAmber,
                    contentColor = Color(0xFF211100)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить")
                }
            }
        },
        bottomBar = {
            BottomTabs(
                selected = state.selectedTab,
                onSelect = viewModel::selectTab
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BuhloBackground)
        ) {
            when (state.selectedTab) {
                AppTab.Dashboard -> DashboardScreen(
                    dashboard = state.dashboard,
                    userName = state.session?.userName.orEmpty(),
                    remoteConfig = state.remoteConfig,
                    onClear = viewModel::clearToday,
                    onLogout = viewModel::logout
                )
                AppTab.Catalog -> CatalogScreen(
                    products = state.products,
                    suggestions = state.productSuggestions,
                    isAdmin = state.cloudProfile?.isAdmin == true,
                    message = state.catalogMessage,
                    selectedTag = state.selectedTag,
                    visibilityFilter = state.productVisibilityFilter,
                    onSelectTag = viewModel::selectCatalogTag,
                    onSelectVisibility = viewModel::selectProductVisibilityFilter,
                    onAddProduct = viewModel::showAdminProductDialog,
                    onEditProduct = viewModel::editProduct,
                    onSetProductActive = viewModel::setProductActive,
                    onApproveSuggestion = viewModel::openModeration,
                    onRejectSuggestion = viewModel::rejectSuggestion,
                    onHideAuthor = viewModel::hideSuggestionAuthor
                )
                AppTab.Friends -> FriendsScreen(
                    friends = state.dashboard.friendProgress,
                    publicId = state.cloudProfile?.publicId?.ifBlank { null }
                        ?: state.session?.publicId.orEmpty(),
                    input = state.friendInput,
                    message = state.friendMessage,
                    onInputChange = viewModel::updateFriendInput,
                    onAddFriend = viewModel::addFriend,
                    onAcceptFriend = viewModel::acceptFriend,
                    onRejectFriend = viewModel::rejectFriend,
                    onRemoveFriend = viewModel::removeFriend,
                    onFriendClick = viewModel::openFriendCalendar
                )
                AppTab.Account -> AccountScreen(
                    session = state.session,
                    cloudProfile = state.cloudProfile,
                    aboutVisible = state.aboutDialogVisible,
                    hasMapKitKey = hasMapKitKey,
                    onLogout = viewModel::logout,
                    onInfoClick = viewModel::showAboutDialog,
                    onAboutClose = viewModel::hideAboutDialog,
                    onProfileClick = viewModel::openOwnCalendar
                )
            }
        }
    }

    if (state.addDrinkDialogVisible) {
        AddDrinkDialog(
            products = state.products,
            onDismiss = viewModel::hideAddDrinkDialog,
            onAdd = viewModel::addDrink
        )
    }

    if (state.adminProductDialogVisible) {
        AdminProductDialog(
            isAdmin = state.cloudProfile?.isAdmin == true,
            onDismiss = viewModel::hideAdminProductDialog,
            onSave = viewModel::addProductFromAdmin
        )
    }

    state.moderationSuggestion?.let { suggestion ->
        AdminProductDialog(
            isAdmin = true,
            initialProduct = suggestion.product,
            title = "Премодерация",
            onDismiss = viewModel::closeModeration,
            onSave = { name, brand, description, category, volumeMl, strengthPercent, imageUrl, tags ->
                viewModel.approveSuggestion(
                    suggestion.id,
                    name,
                    brand,
                    description,
                    category,
                    volumeMl,
                    strengthPercent,
                    imageUrl,
                    tags
                )
            }
        )
    }

    state.editingProduct?.let { product ->
        AdminProductDialog(
            isAdmin = true,
            initialProduct = product,
            title = "Редактировать напиток",
            onDismiss = viewModel::closeProductEditor,
            onSave = viewModel::updateExistingProduct
        )
    }

    state.calendar?.let { calendar ->
        CalendarDialog(
            calendar = calendar,
            onSelectDay = viewModel::selectCalendarDay,
            onDismiss = viewModel::closeCalendar
        )
    }
}

@Composable
private fun VkMemeScreen(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BuhloBackground)
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Назад")
            }
            Text(
                text = "ВК - контора бюрократов",
                color = BuhloCream,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black
            )
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        loadDataWithBaseURL(
                            "file:///android_asset/",
                            """
                                <html>
                                  <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                    <style>
                                      body {
                                        margin: 0;
                                        background: #000000;
                                        display: flex;
                                        align-items: center;
                                        justify-content: center;
                                        min-height: 100vh;
                                      }
                                      img {
                                        max-width: 100%;
                                        max-height: 100vh;
                                        object-fit: contain;
                                      }
                                    </style>
                                  </head>
                                  <body><img src="vk_reaction.gif" /></body>
                                </html>
                            """.trimIndent(),
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun LoginScreen(
    hasYandexClientId: Boolean,
    hasVkKeys: Boolean,
    message: String?,
    onYandexClick: () -> Unit,
    onVkClick: () -> Unit,
    onGoogleClick: () -> Unit,
    onDemoClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BuhloBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Бухлограф",
                color = BuhloCream,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "мемный дневник напитков, графиков и друзей с подозрительно серьезной аналитикой",
                color = BuhloMuted,
                fontSize = 16.sp
            )
            MascotPixelFace(face = "(._.)", color = BuhloAmber)
            Button(
                onClick = onYandexClick,
                colors = ButtonDefaults.buttonColors(containerColor = BuhloAmber)
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (hasYandexClientId) "Войти через Яндекс ID" else "Яндекс ID ждет ключ")
            }
            Button(
                onClick = onVkClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2688EB))
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (hasVkKeys) "Войти через VK ID" else "VK ID ждет ключи")
            }
            Button(
                onClick = onGoogleClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, tint = Color(0xFF1F1F1F))
                Spacer(Modifier.width(8.dp))
                Text("Войти через Google", color = Color(0xFF1F1F1F))
            }
            OutlinedButton(onClick = onDemoClick) {
                Text("Запустить демо-режим")
            }
            if (message != null) {
                Text(
                    text = message,
                    color = BuhloRed,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun CatalogLoadingScreen(
    loadState: CatalogLoadState,
    productsLoaded: Int,
    onLogout: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BuhloBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = BuhloSurface),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(color = BuhloAmber)
                Text(
                    text = "Загружаем ассортимент",
                    color = BuhloText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                val message = when (loadState) {
                    CatalogLoadState.Loading -> "Подготавливаем список напитков. Это обычно занимает пару секунд."
                    is CatalogLoadState.Ready -> "Найдено напитков: $productsLoaded"
                    is CatalogLoadState.Error -> "Не удалось загрузить данные: ${loadState.message}"
                }
                Text(message, color = BuhloMuted)
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Выйти")
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    dashboard: DrinkDashboard,
    userName: String,
    remoteConfig: RemoteConfigState,
    onClear: () -> Unit,
    onLogout: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Сегодня, $userName",
                        color = BuhloMuted,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "График выпивохи",
                        color = BuhloText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                IconButton(onClick = onLogout) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Выйти", tint = BuhloMuted)
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BuhloSurfaceAlt),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = remoteConfig.welcomeBanner,
                    color = BuhloCream,
                    modifier = Modifier.padding(14.dp),
                    fontSize = 14.sp
                )
            }
        }
        item {
            MoodCard(dashboard)
        }
        item {
            StatsRow(dashboard)
        }
        item {
            SectionHeader(
                title = "Записи дня",
                action = {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Delete, contentDescription = "Очистить", tint = BuhloMuted)
                    }
                }
            )
        }
        if (dashboard.entries.isEmpty()) {
            item {
                EmptyLogCard()
            }
        } else {
            items(dashboard.entries) { entry ->
                DrinkEntryCard(entry)
            }
        }
    }
}

@Composable
private fun MoodCard(dashboard: DrinkDashboard) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BuhloSurface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            MascotPixelFace(
                face = dashboard.mood.face,
                color = if (dashboard.mood.name == "Warning") BuhloRed else BuhloAmber
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dashboard.mood.title,
                    color = BuhloText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dashboard.mood.phrase,
                    color = BuhloMuted,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun MascotPixelFace(face: String, color: Color) {
    Box(
        modifier = Modifier
            .size(104.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .border(3.dp, BuhloCream, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = face,
            color = Color(0xFF15120F),
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun StatsRow(dashboard: DrinkDashboard) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(
            title = "Всего",
            value = "${dashboard.totalVolumeMl} мл",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Чистый спирт",
            value = "${dashboard.totalPureAlcoholMl.roundToInt()} мл",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = BuhloSurfaceAlt),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = BuhloMuted, fontSize = 12.sp)
            Text(value, color = BuhloText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = BuhloText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        action()
    }
}

@Composable
private fun EmptyLogCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = BuhloSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = "Пока ни одной записи. Можно оставить день чистым, а можно просто честно записать чай, квас или то, что реально было.",
            color = BuhloMuted,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun DrinkEntryCard(entry: DrinkEntry) {
    val time = remember(entry.timestampMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.timestampMillis))
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = BuhloSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = entry.type.emoji, fontSize = 28.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.type.title, color = BuhloText, fontWeight = FontWeight.Bold)
                Text(
                    text = "${entry.volumeMl} мл, ${entry.strengthPercent}% - $time",
                    color = BuhloMuted,
                    fontSize = 13.sp
                )
                if (entry.note.isNotBlank()) {
                    Text(
                        text = entry.note,
                        color = BuhloCream,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            AssistChip(
                onClick = {},
                label = { Text("${entry.pureAlcoholMl.roundToInt()} мл") }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CatalogScreen(
    products: List<AlcoholProduct>,
    suggestions: List<ProductSuggestion>,
    isAdmin: Boolean,
    message: String?,
    selectedTag: String?,
    visibilityFilter: ProductVisibilityFilter,
    onSelectTag: (String?) -> Unit,
    onSelectVisibility: (ProductVisibilityFilter) -> Unit,
    onAddProduct: () -> Unit,
    onEditProduct: (AlcoholProduct) -> Unit,
    onSetProductActive: (String, Boolean) -> Unit,
    onApproveSuggestion: (ProductSuggestion) -> Unit,
    onRejectSuggestion: (String) -> Unit,
    onHideAuthor: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(products, query, selectedTag, visibilityFilter) {
        val needle = query.trim().lowercase()
        val byQuery = if (needle.isBlank()) products else products.filter {
            it.name.lowercase().contains(needle) ||
                it.brand.lowercase().contains(needle)
        }
        val byTag = selectedTag?.let { tag -> byQuery.filter { tag in it.tags } } ?: byQuery
        when (visibilityFilter) {
            ProductVisibilityFilter.Active -> byTag.filter { it.isActive }
            ProductVisibilityFilter.Hidden -> byTag.filterNot { it.isActive }
            ProductVisibilityFilter.All -> byTag
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Ассортимент", color = BuhloText, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(
                "Выбирай напитки из общего списка или предложи свой вариант.",
                color = BuhloMuted
            )
        }
        item {
            Button(
                onClick = onAddProduct,
                colors = ButtonDefaults.buttonColors(containerColor = BuhloAmber)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Добавить напиток")
            }
        }
        if (isAdmin) {
            item {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (suggestions.isEmpty()) {
                                "Админ-режим: новых заявок нет"
                            } else {
                                "Админ-режим: заявок ${suggestions.size}"
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Check, contentDescription = null, tint = BuhloAmber)
                    }
                )
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск напитка") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = selectedTag == null,
                    onClick = { onSelectTag(null) },
                    label = { Text("Все") }
                )
                ProductTag.entries.forEach { tag ->
                    FilterChip(
                        selected = selectedTag == tag.title,
                        onClick = { onSelectTag(tag.title) },
                        label = { Text(tag.title) }
                    )
                }
            }
        }
        if (isAdmin) {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ProductVisibilityFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = visibilityFilter == filter,
                            onClick = { onSelectVisibility(filter) },
                            label = { Text(filter.title) }
                        )
                    }
                }
            }
        }
        if (isAdmin && suggestions.isNotEmpty()) {
            item {
                Text("Заявки на модерацию", color = BuhloText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            items(suggestions) { suggestion ->
                SuggestionCard(
                    suggestion = suggestion,
                    onApprove = { onApproveSuggestion(suggestion) },
                    onReject = { onRejectSuggestion(suggestion.id) },
                    onHideAuthor = { onHideAuthor(suggestion.authorId) }
                )
            }
        }
        if (message != null) {
            item { Text(message, color = BuhloMuted, fontSize = 13.sp) }
        }
        items(filtered) { product ->
            ProductCard(
                product = product,
                isAdmin = isAdmin,
                onEdit = { onEditProduct(product) },
                onToggleActive = { onSetProductActive(product.id, !product.isActive) }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ProductCard(
    product: AlcoholProduct,
    modifier: Modifier = Modifier,
    isAdmin: Boolean = false,
    onEdit: () -> Unit = {},
    onToggleActive: () -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BuhloSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BuhloSurfaceAlt),
                contentAlignment = Alignment.Center
            ) {
                if (product.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = product.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(product.category.defaultType.emoji, fontSize = 24.sp)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(product.name, color = BuhloText, fontWeight = FontWeight.Bold, maxLines = 2)
                if (product.brand.isNotBlank()) {
                    Text(product.brand, color = BuhloMuted, fontSize = 13.sp)
                }
                Text(
                    "${product.category.title} • ${product.volumeMl} мл • ${product.strengthPercent.roundToInt()}%",
                    color = BuhloCream,
                    fontSize = 13.sp
                )
                if (product.description.isNotBlank()) {
                    Text(
                        product.description,
                        color = BuhloMuted,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (product.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        product.tags.take(4).forEach { tag ->
                            Text(tag, color = BuhloAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (!product.isActive) {
                    Text("Скрыт", color = BuhloRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (isAdmin && !product.id.startsWith("seed-")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onEdit) {
                            Text("Изменить")
                        }
                        TextButton(onClick = onToggleActive) {
                            Text(if (product.isActive) "Скрыть" else "Вернуть")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: ProductSuggestion,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onHideAuthor: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BuhloSurfaceAlt),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Предложил: ${suggestion.authorName}",
                color = BuhloMuted,
                fontSize = 12.sp
            )
            ProductCard(product = suggestion.product)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = BuhloAmber)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                }
                OutlinedButton(onClick = onReject) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
                TextButton(onClick = onHideAuthor) {
                    Text("Скрыть автора")
                }
            }
        }
    }
}

@Composable
private fun FriendsScreen(
    friends: List<FriendProgress>,
    publicId: String,
    input: String,
    message: String?,
    onInputChange: (String) -> Unit,
    onAddFriend: () -> Unit,
    onAcceptFriend: (String) -> Unit,
    onRejectFriend: (String) -> Unit,
    onRemoveFriend: (String) -> Unit,
    onFriendClick: (FriendProgress) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    fun copyPublicId() {
        if (publicId.isBlank()) return
        clipboard.setText(AnnotatedString(publicId))
        Toast.makeText(context, "ID скопирован", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Друзья", color = BuhloText, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(
                "Добавляй друзей по короткому ID. Дружба появится только после подтверждения второй стороной.",
                color = BuhloMuted
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BuhloSurface),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = ::copyPublicId)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Ваш ID", color = BuhloMuted, fontSize = 13.sp)
                    PublicIdCode(publicId = publicId.ifBlank { "------" })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = BuhloAmber)
                        Spacer(Modifier.width(8.dp))
                        Text("Нажмите, чтобы скопировать", color = BuhloCream, fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BuhloSurface),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInputChange,
                        label = { Text("ID друга") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = onAddFriend,
                        colors = ButtonDefaults.buttonColors(containerColor = BuhloAmber)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Отправить заявку")
                    }
                    if (message != null) {
                        Text(message, color = BuhloMuted, fontSize = 13.sp)
                    }
                }
            }
        }
        if (friends.isEmpty()) {
            item {
                EmptyLogCard()
            }
        }
        items(friends) { friend ->
            FriendCard(
                friend = friend,
                onAccept = { onAcceptFriend(friend.publicId) },
                onReject = { onRejectFriend(friend.publicId) },
                onRemove = { onRemoveFriend(friend.publicId) },
                onOpenCalendar = { onFriendClick(friend) }
            )
        }
    }
}

@Composable
private fun PublicIdCode(publicId: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        publicId.take(6).forEach { char ->
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F0D0B))
                    .border(1.dp, BuhloAmber, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = char.toString(),
                    color = BuhloText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun FriendCard(
    friend: FriendProgress,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onRemove: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BuhloSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = friend.relationStatus == FriendRelationStatus.Accepted,
                    onClick = onOpenCalendar
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    friend.name,
                    color = BuhloText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (friend.relationStatus == FriendRelationStatus.Accepted) {
                    Text("${friend.streakDays} дн.", color = BuhloAmber, fontWeight = FontWeight.Bold)
                }
            }
            PublicIdCode(publicId = friend.publicId)
            if (friend.relationStatus == FriendRelationStatus.Accepted) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (friend.pureAlcoholMl >= 90.0) BuhloRed else BuhloAmber),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            friend.moodFace,
                            color = Color(0xFF15120F),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column {
                        Text(friend.moodTitle, color = BuhloText, fontWeight = FontWeight.Bold)
                        Text(
                            "${friend.pureAlcoholMl.roundToInt()} мл чистого спирта",
                            color = BuhloMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                Text(friend.status, color = BuhloMuted)
            }
            if (friend.relationStatus == FriendRelationStatus.IncomingRequest) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = BuhloAmber)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Принять")
                    }
                    OutlinedButton(onClick = onReject) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Отклонить")
                    }
                }
            } else if (friend.relationStatus == FriendRelationStatus.Accepted) {
                MiniBar(value = friend.pureAlcoholMl.toFloat(), max = 90f)
                Text("${friend.glasses} условных делений графика", color = BuhloCream, fontSize = 13.sp)
                OutlinedButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Удалить из друзей")
                }
            }
        }
    }
}

@Composable
private fun AccountScreen(
    session: UserSession?,
    cloudProfile: UserProfile?,
    aboutVisible: Boolean,
    hasMapKitKey: Boolean,
    onLogout: () -> Unit,
    onInfoClick: () -> Unit,
    onAboutClose: () -> Unit,
    onProfileClick: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val publicId = cloudProfile?.publicId?.ifBlank { null } ?: session?.publicId.orEmpty()
    fun copyPublicId() {
        if (publicId.isBlank()) return
        clipboard.setText(AnnotatedString(publicId))
        Toast.makeText(context, "ID скопирован", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (aboutVisible) {
            item {
                AboutPanel(
                    hasMapKitKey = hasMapKitKey,
                    onClose = onAboutClose
                )
            }
            return@LazyColumn
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Аккаунт", color = BuhloText, fontSize = 28.sp, fontWeight = FontWeight.Black)
                IconButton(onClick = onInfoClick) {
                    Icon(Icons.Default.Info, contentDescription = "О нас", tint = BuhloAmber)
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BuhloSurface),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onProfileClick)
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val photoUrl = cloudProfile?.photoUrl?.ifBlank { null } ?: session?.photoUrl
                    Box(
                        modifier = Modifier
                            .size(82.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BuhloAmber),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUrl != null) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF211100),
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cloudProfile?.name?.ifBlank { null } ?: session?.userName ?: "Пользователь",
                            color = BuhloText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = session?.provider?.label ?: "Неизвестный вход",
                            color = BuhloMuted
                        )
                        val email = cloudProfile?.email?.ifBlank { null } ?: session?.email
                        if (email != null) {
                            Text(
                                text = email,
                                color = BuhloMuted,
                                fontSize = 13.sp
                            )
                        }
                        if (cloudProfile?.isAdmin == true) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Админский аккаунт") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = BuhloAmber
                                    )
                                }
                            )
                        }
                        Column(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable(onClick = ::copyPublicId),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("ID для друзей", color = BuhloMuted, fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = publicId.ifBlank { "------" },
                                    color = BuhloText,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = BuhloAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Выйти")
            }
        }
    }
}

@Composable
private fun MiniBar(value: Float, max: Float) {
    val progress = (value / max).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF0F0D0B))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(10.dp)
                .background(if (progress > 0.8f) BuhloRed else BuhloAmber)
        )
    }
}

@Composable
private fun AboutPanel(hasMapKitKey: Boolean, onClose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("О нас", color = BuhloText, fontSize = 28.sp, fontWeight = FontWeight.Black)
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = BuhloAmber)
            }
        }
        AboutScreenContent(hasMapKitKey = hasMapKitKey)
    }
}

@Composable
private fun AboutScreenContent(hasMapKitKey: Boolean) {
    val context = LocalContext.current
    val officeLat = 55.3546
    val officeLon = 86.0894
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "BuhloSoft Analytics - учебная студия графиков, напитков и очень ответственного маскота.",
            color = BuhloMuted
        )
        OfficeMapCard(hasMapKitKey = hasMapKitKey)
        Button(
            onClick = {
                val uri = Uri.parse("yandexmaps://maps.yandex.ru/?rtext=~$officeLat,$officeLon&rtt=auto")
                val webUri = Uri.parse("https://yandex.ru/maps/?rtext=~$officeLat,$officeLon&rtt=auto")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
                    .onFailure {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, webUri).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
            },
            colors = ButtonDefaults.buttonColors(containerColor = BuhloAmber)
        ) {
            Icon(Icons.Default.Place, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Маршрут до КемГУ")
        }
    }
}

@Composable
private fun OfficeMapCard(hasMapKitKey: Boolean) {
    val context = LocalContext.current
    val isVpnActive = remember(context) { context.isVpnActive() }
    Card(
        colors = CardDefaults.cardColors(containerColor = BuhloSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Map, contentDescription = null, tint = BuhloAmber)
                Spacer(Modifier.width(8.dp))
                Text("Кемеровский государственный университет", color = BuhloText)
            }
            if (hasMapKitKey) {
                YandexOfficeMap()
            } else {
                PixelMapPreview(hasMapKitKey = false)
            }
            if (isVpnActive) {
                Text(
                    text = "Возможно вы используете VPN сервис, карта может не работать",
                    color = BuhloAmber,
                    fontSize = 13.sp
                )
            }
            Text(
                text = if (hasMapKitKey) {
                    "Карта открыта на КемГУ."
                } else {
                    "Показываем схематичное превью. Маршрут все равно можно открыть кнопкой выше."
                },
                color = BuhloMuted,
                fontSize = 13.sp
            )
        }
    }
}

private fun Context.isVpnActive(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
}

@Composable
private fun YandexOfficeMap() {
    val officePoint = remember { Point(55.3546, 86.0894) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mapView?.onStop()
            runCatching { MapKitFactory.getInstance().onStop() }
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp)),
        factory = { context ->
            MapView(context).apply {
                mapView = this
                MapKitFactory.getInstance().onStart()
                onStart()
                mapWindow.map.move(CameraPosition(officePoint, 15.0f, 0.0f, 0.0f))
                mapWindow.map.mapObjects.addPlacemark(officePoint)
            }
        },
        update = { view ->
            runCatching {
                MapKitFactory.getInstance().onStart()
                view.onStart()
                view.mapWindow.map.move(CameraPosition(officePoint, 15.0f, 0.0f, 0.0f))
            }
        }
    )
}

@Composable
private fun PixelMapPreview(hasMapKitKey: Boolean) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF10212A))
    ) {
        val road = if (hasMapKitKey) BuhloMint else BuhloBlue
        drawLine(
            color = road,
            start = Offset(30f, size.height - 32f),
            end = Offset(size.width - 30f, 36f),
            strokeWidth = 16f,
            cap = StrokeCap.Square
        )
        drawLine(
            color = BuhloAmber,
            start = Offset(20f, 54f),
            end = Offset(size.width * 0.7f, size.height - 20f),
            strokeWidth = 8f,
            cap = StrokeCap.Square
        )
        drawCircle(
            color = BuhloRed,
            radius = 18f,
            center = Offset(size.width * 0.72f, size.height * 0.34f)
        )
        drawCircle(
            color = BuhloCream,
            radius = 26f,
            center = Offset(size.width * 0.72f, size.height * 0.34f),
            style = Stroke(width = 5f)
        )
    }
}

@Composable
private fun BottomTabs(selected: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationBar(containerColor = BuhloSurface) {
        AppTab.entries.forEach { tab ->
            val icon = when (tab) {
                AppTab.Dashboard -> Icons.Default.Home
                AppTab.Catalog -> Icons.Default.Map
                AppTab.Friends -> Icons.Default.Group
                AppTab.Account -> Icons.Default.Person
            }
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, contentDescription = tab.title) },
                label = { Text(tab.title) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddDrinkDialog(
    products: List<AlcoholProduct>,
    onDismiss: () -> Unit,
    onAdd: (AlcoholProduct, Int, Double, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedId by remember(products) { mutableStateOf(products.firstOrNull()?.id.orEmpty()) }
    val filtered = remember(products, query) {
        val needle = query.trim().lowercase()
        val activeProducts = products.filter { it.isActive }
        if (needle.isBlank()) activeProducts else activeProducts.filter {
            it.name.lowercase().contains(needle) ||
                it.brand.lowercase().contains(needle)
        }
    }
    val selectedProduct = products.firstOrNull { it.id == selectedId } ?: products.firstOrNull()
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = selectedProduct != null,
                onClick = {
                    selectedProduct?.let {
                        onAdd(it, it.volumeMl.coerceAtLeast(1), it.strengthPercent.coerceIn(0.0, 96.0), note)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BuhloAmber)
            ) {
                Text("Записать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        title = { Text("Добавить запись") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Найти напиток") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier.height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered) { product ->
                        ProductCard(
                            product = product,
                            modifier = Modifier
                                .border(
                                    width = if (selectedProduct?.id == product.id) 2.dp else 0.dp,
                                    color = if (selectedProduct?.id == product.id) BuhloAmber else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedId = product.id }
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    Text("Напиток не найден в каталоге.", color = BuhloMuted)
                }
                selectedProduct?.let {
                    Text(
                        "Будет записано: ${it.volumeMl} мл, ${it.strengthPercent.roundToInt()}%",
                        color = BuhloCream
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Комментарий") },
                    maxLines = 2
                )
            }
        },
        containerColor = BuhloSurface,
        titleContentColor = BuhloText,
        textContentColor = BuhloText
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AdminProductDialog(
    isAdmin: Boolean,
    initialProduct: AlcoholProduct? = null,
    title: String = "Новый напиток",
    onDismiss: () -> Unit,
    onSave: (String, String, String, AlcoholCategory, Int, Double, String, List<String>) -> Unit
) {
    var name by remember(initialProduct) { mutableStateOf(initialProduct?.name.orEmpty()) }
    var brand by remember(initialProduct) { mutableStateOf(initialProduct?.brand.orEmpty()) }
    var description by remember(initialProduct) { mutableStateOf(initialProduct?.description.orEmpty()) }
    var category by remember(initialProduct) { mutableStateOf(initialProduct?.category ?: AlcoholCategory.Beer) }
    var volume by remember(initialProduct) { mutableStateOf((initialProduct?.volumeMl ?: 500).toString()) }
    var strength by remember(initialProduct) { mutableStateOf((initialProduct?.strengthPercent ?: 5.0).toString()) }
    var imageUrl by remember(initialProduct) { mutableStateOf(initialProduct?.imageUrl.orEmpty()) }
    var selectedTags by remember(initialProduct) {
        mutableStateOf(initialProduct?.tags?.ifEmpty { ProductTag.defaultFor(initialProduct.category) } ?: listOf(ProductTag.Beer.title))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        brand,
                        description,
                        category,
                        volume.toIntOrNull() ?: 500,
                        strength.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        imageUrl,
                        selectedTags
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = BuhloAmber)
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.height(520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        if (isAdmin) "После сохранения напиток появится в ассортименте." else "Напиток отправится на модерацию.",
                        color = BuhloMuted,
                        fontSize = 13.sp
                    )
                }
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("Бренд") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Описание") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AlcoholCategory.entries.forEach { item ->
                            FilterChip(
                                selected = category == item,
                                onClick = {
                                    category = item
                                    selectedTags = ProductTag.defaultFor(item)
                                },
                                label = { Text(item.title) }
                            )
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = volume,
                            onValueChange = { volume = it.filter(Char::isDigit) },
                            label = { Text("Мл") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = strength,
                            onValueChange = { strength = it.filter { char -> char.isDigit() || char == '.' || char == ',' } },
                            label = { Text("%") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Text("Теги", color = BuhloMuted, fontSize = 13.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ProductTag.entries.forEach { tag ->
                            FilterChip(
                                selected = tag.title in selectedTags,
                                onClick = {
                                    selectedTags = if (tag.title in selectedTags) {
                                        selectedTags - tag.title
                                    } else {
                                        selectedTags + tag.title
                                    }
                                },
                                label = { Text(tag.title) }
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text("URL картинки") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        containerColor = BuhloSurface,
        titleContentColor = BuhloText,
        textContentColor = BuhloText
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarDialog(
    calendar: CalendarUiState,
    onSelectDay: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        title = { Text(calendar.ownerName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    calendar.monthDays.forEach { day ->
                        val selected = day.dayKey == calendar.selectedDayKey
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        selected -> BuhloAmber
                                        day.hasEntries -> BuhloSurfaceAlt
                                        else -> Color(0xFF0F0D0B)
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (day.hasEntries) BuhloAmber else BuhloSurfaceAlt,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onSelectDay(day.dayKey) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                day.dayOfMonth.toString(),
                                color = if (selected) Color(0xFF211100) else BuhloText,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                val stats = calendar.selectedDayStats
                if (stats == null) {
                    Text("В этот день записей нет.", color = BuhloMuted)
                } else {
                    Text(
                        "${stats.moodFace} ${stats.moodTitle}",
                        color = BuhloText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${stats.totalVolumeMl} мл всего, ${stats.totalPureAlcoholMl.roundToInt()} мл чистого спирта",
                        color = BuhloMuted
                    )
                    calendar.selectedDayEntries.forEach { entry ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = BuhloSurfaceAlt),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (entry.imageUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = entry.imageUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(entry.productName, color = BuhloText, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${entry.volumeMl} мл • ${entry.strengthPercent.roundToInt()}%",
                                        color = BuhloMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = BuhloSurface,
        titleContentColor = BuhloText,
        textContentColor = BuhloText
    )
}
