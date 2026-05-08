package com.example.buhlograf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as BuhlografApplication

        setContent {
            BuhlografTheme {
                val viewModel: BuhlografViewModel = viewModel(
                    factory = BuhlografViewModelFactory(
                        authService = app.authService,
                        drinkRepository = app.drinkRepository,
                        friendsRepository = app.friendsRepository,
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
                                viewModel.onYandexLoginSuccess(
                                    token = result.token.value,
                                    userName = "Пользователь Яндекса"
                                )
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
                    GoogleSignIn.getClient(
                        this@MainActivity,
                        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .requestProfile()
                            .build()
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
                        viewModel.onGoogleLoginSuccess(
                            token = token,
                            userName = account.displayName ?: account.email ?: "Пользователь Google",
                            photoUrl = account.photoUrl?.toString()
                        )
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
                                        viewModel.onVkLoginSuccess(
                                            token = accessToken.token,
                                            userName = userName,
                                            photoUrl = user?.photo200 ?: user?.photo100 ?: user?.photo50
                                        )
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
}
