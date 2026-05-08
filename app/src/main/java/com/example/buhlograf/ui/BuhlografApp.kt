package com.example.buhlograf.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.buhlograf.domain.DrinkDashboard
import com.example.buhlograf.domain.DrinkEntry
import com.example.buhlograf.domain.DrinkType
import com.example.buhlograf.domain.FriendProgress
import com.example.buhlograf.domain.UserSession
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
                    onClear = viewModel::clearToday,
                    onLogout = viewModel::logout
                )
                AppTab.Friends -> FriendsScreen(
                    friends = state.dashboard.friendProgress,
                    input = state.friendInput,
                    message = state.friendMessage,
                    onInputChange = viewModel::updateFriendInput,
                    onAddFriend = viewModel::addFriend
                )
                AppTab.Account -> AccountScreen(
                    session = state.session,
                    onLogout = viewModel::logout,
                    onInfoClick = viewModel::showAboutDialog
                )
            }
        }
    }

    if (state.aboutDialogVisible) {
        AboutDialog(
            hasMapKitKey = hasMapKitKey,
            onDismiss = viewModel::hideAboutDialog
        )
    }

    if (state.addDrinkDialogVisible) {
        AddDrinkDialog(
            onDismiss = viewModel::hideAddDrinkDialog,
            onAdd = viewModel::addDrink
        )
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
private fun DashboardScreen(
    dashboard: DrinkDashboard,
    userName: String,
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
private fun FriendsScreen(
    friends: List<FriendProgress>,
    input: String,
    message: String?,
    onInputChange: (String) -> Unit,
    onAddFriend: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Друзья", color = BuhloText, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(
                "Добавляй друзей по ID. В следующей лабораторной этот поиск переедет на Firebase.",
                color = BuhloMuted
            )
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
                        Text("Добавить друга")
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
            FriendCard(friend)
        }
    }
}

@Composable
private fun FriendCard(friend: FriendProgress) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BuhloSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(friend.name, color = BuhloText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${friend.streakDays} дн.", color = BuhloAmber, fontWeight = FontWeight.Bold)
            }
            Text("ID: ${friend.id}", color = BuhloCream, fontSize = 13.sp)
            Text(friend.status, color = BuhloMuted)
            MiniBar(value = friend.pureAlcoholMl.toFloat(), max = 90f)
            Text("${friend.glasses} условных делений графика", color = BuhloCream, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AccountScreen(
    session: UserSession?,
    onLogout: () -> Unit,
    onInfoClick: () -> Unit
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
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(82.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BuhloAmber),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF211100),
                            modifier = Modifier.size(46.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session?.userName ?: "Пользователь",
                            color = BuhloText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = session?.provider?.label ?: "Неизвестный вход",
                            color = BuhloMuted
                        )
                        Text(
                            text = "ID: ${session?.userId ?: "-"}",
                            color = BuhloCream,
                            fontSize = 13.sp
                        )
                        if (session?.photoUrl != null) {
                            Text(
                                text = "Фото профиля получено от провайдера",
                                color = BuhloMuted,
                                fontSize = 12.sp
                            )
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
private fun AboutDialog(hasMapKitKey: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        title = { Text("О нас") },
        text = {
            AboutScreenContent(hasMapKitKey = hasMapKitKey)
        },
        containerColor = BuhloSurface,
        titleContentColor = BuhloText,
        textContentColor = BuhloText
    )
}

@Composable
private fun AboutScreenContent(hasMapKitKey: Boolean) {
    val context = LocalContext.current
    val officeLat = 55.0302
    val officeLon = 82.9204
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "BuhloSoft Analytics - выдуманная студия, где графики важнее легенд, а маскот всегда просит не забывать воду.",
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
            Text("Маршрут до офиса")
        }
    }
}

@Composable
private fun OfficeMapCard(hasMapKitKey: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BuhloSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Map, contentDescription = null, tint = BuhloAmber)
                Spacer(Modifier.width(8.dp))
                Text("Офис: Новосибирск, Красный проспект, 1", color = BuhloText)
            }
            if (hasMapKitKey) {
                YandexOfficeMap()
            } else {
                PixelMapPreview(hasMapKitKey = false)
            }
            Text(
                text = if (hasMapKitKey) {
                    "Ключ MapKit найден. Это настоящая карта с маркером офиса."
                } else {
                    "Для настоящей карты нужен YANDEX_MAPKIT_API_KEY. Сейчас показано пиксельное превью."
                },
                color = BuhloMuted,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun YandexOfficeMap() {
    val officePoint = remember { Point(55.0302, 82.9204) }
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
    onDismiss: () -> Unit,
    onAdd: (DrinkType, Int, Double, String) -> Unit
) {
    var selectedType by remember { mutableStateOf(DrinkType.Beer) }
    var volume by remember { mutableIntStateOf(selectedType.defaultVolumeMl) }
    var strength by remember { mutableDoubleStateOf(selectedType.defaultStrength) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onAdd(selectedType, volume, strength, note) },
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DrinkType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                volume = type.defaultVolumeMl
                                strength = type.defaultStrength
                            },
                            label = { Text(type.title) }
                        )
                    }
                }
                Text("Объем: $volume мл")
                Slider(
                    value = volume.toFloat(),
                    onValueChange = { volume = it.roundToInt() },
                    valueRange = 50f..1000f,
                    steps = 18
                )
                Text("Крепость: ${strength.roundToInt()}%")
                Slider(
                    value = strength.toFloat(),
                    onValueChange = { strength = it.toDouble() },
                    valueRange = 0f..60f,
                    steps = 11
                )
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
