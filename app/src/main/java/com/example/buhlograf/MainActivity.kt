package com.example.buhlograf

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import com.example.buhlograf.BuildConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.example.buhlograf.ui.BuhlografApp
import com.example.buhlograf.ui.BuhlografTheme
import com.example.buhlograf.ui.BuhlografViewModel
import com.example.buhlograf.ui.BuhlografViewModelFactory
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthSdk
import com.vk.id.AccessToken
import com.vk.id.VKID
import com.vk.id.VKIDAuthFail
import com.vk.id.auth.AuthCodeData
import com.vk.id.auth.VKIDAuthCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ensureNotificationPermission()

        val app = application as BuhlografApplication

        setContent {
            BuhlografTheme {
                val viewModel: BuhlografViewModel = viewModel(
                    factory = BuhlografViewModelFactory(
                        authService = app.authService,
                        drinkRepository = app.drinkRepository,
                        friendsRepository = app.friendsRepository,
                        cloudSyncService = app.cloudSyncService,
                        remoteConfigService = app.remoteConfigService,
                        buildDashboard = app.buildDashboardUseCase,
                        analyticsService = app.analyticsService
                    )
                )
                val yandexSdk = remember(app.hasYandexClientId) {
                    if (app.hasYandexClientId) {
                        YandexAuthSdk.create(YandexAuthOptions(this@MainActivity))
                    } else {
                        null
                    }
                }
                val yandexLoginLauncher = yandexSdk?.let { sdk ->
                    rememberLauncherForActivityResult(sdk.contract) { result ->
                        when (result) {
                            is YandexAuthResult.Success -> {
                                completeYandexLogin(result.token.value, viewModel)
                            }
                            is YandexAuthResult.Failure -> {
                                viewModel.onYandexLoginError(
                                    result.exception.message ?: "Ошибка входа через Яндекс ID"
                                )
                            }
                            YandexAuthResult.Cancelled -> {
                                viewModel.onYandexLoginError("Вход через Яндекс ID отменен.")
                            }
                        }
                    }
                }
                val googleSignInClient = remember {
                    val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestProfile()
                    if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
                        builder.requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    }
                    GoogleSignIn.getClient(
                        this@MainActivity,
                        builder.build()
                    )
                }
                val googleLoginLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    runCatching {
                        GoogleSignIn.getSignedInAccountFromIntent(result.data)
                            .getResult(ApiException::class.java)
                    }.onSuccess { account ->
                        val token = account.id ?: account.email ?: account.displayName ?: "google-user"
                        val idToken = account.idToken
                        if (BuildConfig.HAS_GOOGLE_SERVICES_JSON && idToken != null) {
                            FirebaseAuth.getInstance()
                                .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                                .addOnSuccessListener { auth ->
                                    viewModel.onGoogleLoginSuccess(
                                        token = idToken,
                                        userName = account.displayName ?: account.email ?: "Пользователь Google",
                                        photoUrl = account.photoUrl?.toString(),
                                        email = account.email,
                                        firebaseUid = auth.user?.uid
                                    )
                                }
                                .addOnFailureListener { error ->
                                    viewModel.onGoogleLoginError(
                                        error.localizedMessage ?: "Firebase Auth не принял Google токен"
                                    )
                                }
                        } else {
                            viewModel.onGoogleLoginSuccess(
                                token = token,
                                userName = account.displayName ?: account.email ?: "Пользователь Google",
                                photoUrl = account.photoUrl?.toString(),
                                email = account.email
                            )
                        }
                    }.onFailure { error ->
                        viewModel.onGoogleLoginError(
                            error.localizedMessage ?: "Ошибка входа через Google"
                        )
                    }
                }
                BuhlografApp(
                    viewModel = viewModel,
                    hasYandexClientId = app.hasYandexClientId,
                    hasMapKitKey = app.hasMapKitKey,
                    hasVkKeys = app.hasVkKeys,
                    onYandexLoginClick = {
                        val launcher = yandexLoginLauncher
                        if (launcher == null) {
                            viewModel.onYandexLoginError("Нужен YANDEX_CLIENT_ID в local.properties.")
                        } else {
                            launcher.launch(YandexAuthLoginOptions())
                        }
                    },
                    onVkLoginClick = {
                        if (!app.hasVkKeys) {
                            viewModel.onVkLoginError("Нужны VK_APP_ID и VK_CLIENT_SECRET в local.properties.")
                        } else {
                            VKID.instance.authorize(
                                lifecycleOwner = this@MainActivity,
                                callback = object : VKIDAuthCallback {
                                    override fun onAuth(accessToken: AccessToken) {
                                        val user = accessToken.userData
                                        val userName = listOfNotNull(
                                            user?.firstName,
                                            user?.lastName
                                        ).joinToString(" ").ifBlank { "Пользователь VK" }
                                        ensureFirebaseUid { uid ->
                                            viewModel.onVkLoginSuccess(
                                                token = accessToken.token,
                                                userName = userName,
                                                photoUrl = user?.photo200 ?: user?.photo100 ?: user?.photo50,
                                                firebaseUid = uid
                                            )
                                        }
                                    }

                                    override fun onAuthCode(
                                        data: AuthCodeData,
                                        isCompletion: Boolean
                                    ) = Unit

                                    override fun onFail(fail: VKIDAuthFail) {
                                        viewModel.onVkLoginError(fail.description)
                                    }
                                }
                            )
                        }
                    },
                    onGoogleLoginClick = {
                        googleLoginLauncher.launch(googleSignInClient.signInIntent)
                    }
                )
            }
        }
    }

    private fun completeYandexLogin(token: String, viewModel: BuhlografViewModel) {
        lifecycleScope.launch {
            val profile = withContext(Dispatchers.IO) { fetchYandexProfile(token) }
            ensureFirebaseUid { uid ->
                viewModel.onYandexLoginSuccess(
                    token = token,
                    userName = profile.name,
                    photoUrl = profile.photoUrl,
                    email = profile.email,
                    firebaseUid = uid
                )
            }
        }
    }

    private fun fetchYandexProfile(token: String): YandexProfile {
        return runCatching {
            val connection = (URL("https://login.yandex.ru/info?format=json").openConnection() as HttpURLConnection)
            connection.setRequestProperty("Authorization", "OAuth $token")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(json)
            val email = data.optString("default_email").ifBlank { null }
            val name = data.optString("real_name")
                .ifBlank { data.optString("display_name") }
                .ifBlank { email ?: "Пользователь Яндекса" }
            val avatarId = data.optString("default_avatar_id")
            val photoUrl = avatarId
                .takeIf { it.isNotBlank() && it != "0/0-0" }
                ?.let { "https://avatars.yandex.net/get-yapic/$it/islands-200" }
            YandexProfile(name = name, email = email, photoUrl = photoUrl)
        }.getOrElse {
            YandexProfile(name = "Пользователь Яндекса", email = null, photoUrl = null)
        }
    }

    private fun ensureFirebaseUid(onReady: (String?) -> Unit) {
        if (!BuildConfig.HAS_GOOGLE_SERVICES_JSON) {
            onReady(null)
            return
        }
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.uid?.let {
            onReady(it)
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { onReady(it.user?.uid) }
            .addOnFailureListener { onReady(null) }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(permission), 7001)
    }

    private data class YandexProfile(
        val name: String,
        val email: String?,
        val photoUrl: String?
    )
}
