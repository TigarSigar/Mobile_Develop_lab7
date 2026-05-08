<div align="center">

# МИНИСТЕРСТВО НАУКИ И ВЫСШЕГО ОБРАЗОВАНИЯ  
# РОССИЙСКОЙ ФЕДЕРАЦИИ

## ФЕДЕРАЛЬНОЕ ГОСУДАРСТВЕННОЕ БЮДЖЕТНОЕ  
## ОБРАЗОВАТЕЛЬНОЕ УЧРЕЖДЕНИЕ  
## ВЫСШЕГО ОБРАЗОВАНИЯ  
## «КЕМЕРОВСКИЙ ГОСУДАРСТВЕННЫЙ УНИВЕРСИТЕТ»

### Институт цифры

<br><br>

# ОТЧЁТ  
# О ВЫПОЛНЕНИИ ЛАБОРАТОРНОЙ РАБОТЫ №6

<br>

### по дисциплине: Разработка мобильных приложений  
### тема: Сервисы Google, VK, Yandex. Реализация приложения **Бухлограф**

<br><br>

**Студент:** 3 курса, группы ФИТ-231  
**Кононенко Егор Сергеевич**

**Направление подготовки:**  
02.03.02 — Фундаментальная информатика и информационные технологии

<br><br><br>

**Руководитель:**  
асс. Киселёв К. Е.

<br><br><br><br>

**Кемерово 2026**

</div>

---

# Лабораторная работа №6  
## Сервисы Google, VK, Yandex. Реализация приложения «Бухлограф»

## Цель работы

Научиться подключать сторонние сервисы к Android-приложению, использовать внешние SDK и при этом не связывать пользовательский интерфейс напрямую с конкретными поставщиками сервисов.

В рамках работы были изучены и применены:

- AppMetrica для аналитики;
- Yandex ID для авторизации;
- VK ID SDK как дополнительный провайдер авторизации;
- Google Sign-In как дополнительный вход через Google-аккаунт;
- Yandex MapKit для отображения карты;
- `EncryptedSharedPreferences` для безопасного хранения токенов;
- фасады `AnalyticsService` и `AuthService`, скрывающие внешние SDK от ViewModel.

---

## Краткая теоретическая часть

В Android-разработке внешние сервисы используются для задач, которые сложно или невыгодно реализовывать самостоятельно: авторизация, аналитика, карты, push-уведомления, платежи и другие инфраструктурные функции.

При прямом использовании SDK во ViewModel или UI приложение получает сильную связность: бизнес-логика начинает зависеть от конкретной библиотеки. Чтобы избежать этого, используется подход фасадов и интерфейсов. В данной работе SDK AppMetrica, Yandex ID и VK ID вынесены за собственные сервисы и обработчики, а ViewModel работает с внутренними моделями приложения.

---

## Постановка задания

По заданию лабораторной работы требовалось:

- подключить AppMetrica;
- хранить API-ключи в `local.properties` и передавать их через `BuildConfig`;
- инициализировать SDK в классе `Application`;
- создать `AnalyticsService`;
- реализовать `AppMetricaAnalyticsService` и `FakeAnalyticsService`;
- написать unit-тесты ViewModel на события аналитики;
- добавить авторизацию через Yandex ID и VK ID;
- скрыть SDK авторизации за внутренней логикой приложения;
- сохранять токен и данные пользователя в `EncryptedSharedPreferences`;
- логировать событие `user_logged_in` с параметром `provider`;
- добавить экран «О нас» с картой и маршрутом до офиса.

В качестве практической реализации было создано приложение **«Бухлограф»**.

---

## Описание приложения «Бухлограф»

**Бухлограф** — это мемный дневник напитков с графиками, друзьями и пиксельным маскотом.

Пользователь отмечает, что и сколько он выпил за день: тип напитка, объём, крепость и комментарий. На основе записей приложение считает общий объём, условный объём чистого спирта и меняет настроение маскота.

Состояния маскота:

- пустой дневник — маскот грустит, потому что графику нечего рисовать;
- умеренная запись — культурный режим;
- активный вечер — маскот оживает;
- перебор — включается режим совести с предупреждением.

Приложение не поощряет чрезмерное употребление, а работает как сатирический дневник и демонстрация интеграции сторонних сервисов.

---

## Используемые технологии

- **Kotlin** — основной язык разработки;
- **Jetpack Compose** — пользовательский интерфейс;
- **AppMetrica SDK** — аналитика событий;
- **Yandex ID LoginSDK** — авторизация через Яндекс;
- **VK ID SDK** — авторизация через VK;
- **Google Play Services Auth** — вход через Google;
- **Yandex MapKit** — карта на экране «О нас»;
- **EncryptedSharedPreferences** — безопасное хранение токенов;
- **JUnit** — unit-тесты ViewModel.

---

# Реализация лабораторной работы

## 1. Инициализация SDK в Application

В проекте создан собственный класс `BuhlografApplication`, который зарегистрирован в `AndroidManifest.xml`.

В нём выполняется инициализация внешних сервисов:

- AppMetrica;
- Yandex MapKit;
- VK ID;
- локальных репозиториев;
- сервисов авторизации и аналитики.

```kotlin
class BuhlografApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeAppMetrica()
        initializeMapKit()
        initializeVkId()

        analyticsService = AppMetricaAnalyticsService(
            enabled = BuildConfig.APPMETRICA_API_KEY.isNotBlank()
        )
        authService = SecureAuthService(this)
    }
}
```

Это соответствует требованию лабораторной: SDK не инициализируются в Activity, а запускаются на уровне приложения.

---

## 2. Хранение ключей через local.properties

Ключи не внесены напрямую в исходный код. Они читаются из `local.properties` и передаются в приложение через `BuildConfig`.

```kotlin
buildConfigField("String", "YANDEX_CLIENT_ID", buildConfigString(yandexClientId))
buildConfigField("String", "APPMETRICA_API_KEY", buildConfigString(localValue("APPMETRICA_API_KEY")))
buildConfigField("String", "YANDEX_MAPKIT_API_KEY", buildConfigString(localValue("YANDEX_MAPKIT_API_KEY")))
buildConfigField("String", "VK_APP_ID", buildConfigString(vkAppId))
buildConfigField("String", "VK_CLIENT_SECRET", buildConfigString(vkClientSecret))
```

Файл `local.properties` находится в `.gitignore`, поэтому секреты не публикуются в репозиторий.

---

## 3. Аналитика через AppMetrica

Для аналитики создан интерфейс:

```kotlin
interface AnalyticsService {
    fun trackEvent(name: String, params: Map<String, Any> = emptyMap())
    fun trackError(message: String, error: Throwable? = null)
}
```

Реальная реализация скрывает работу с SDK AppMetrica:

```kotlin
class AppMetricaAnalyticsService(
    private val enabled: Boolean
) : AnalyticsService {
    override fun trackEvent(name: String, params: Map<String, Any>) {
        if (enabled) {
            AppMetrica.reportEvent(name, params)
        } else {
            Log.d("BuhlografAnalytics", "event=$name params=$params")
        }
    }
}
```

Если ключ AppMetrica отсутствует, события выводятся в Logcat. Это позволяет тестировать приложение без падений.

---

## 4. FakeAnalyticsService и unit-тесты

Для тестирования ViewModel создан `FakeAnalyticsService`.

```kotlin
class FakeAnalyticsService : AnalyticsService {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()

    override fun trackEvent(name: String, params: Map<String, Any>) {
        events += name to params
    }
}
```

Unit-тесты проверяют:

- событие `drink_added` при добавлении записи;
- событие `screen_viewed` при открытии вкладки друзей;
- событие `user_logged_in` при входе в демо-режим.

```kotlin
assertTrue(
    fixture.analytics.events.any { (name, params) ->
        name == "drink_added" &&
            params["type"] == "beer" &&
            params["volume_ml"] == 500
    }
)
```

---

## 5. Авторизация через Yandex ID

Для Yandex ID подключен официальный LoginSDK:

```kotlin
implementation("com.yandex.android:authsdk:3.1.3")
```

В `MainActivity` создаётся SDK и launcher:

```kotlin
val yandexSdk = YandexAuthSdk.create(YandexAuthOptions(this))

val yandexLoginLauncher = rememberLauncherForActivityResult(sdk.contract) { result ->
    when (result) {
        is YandexAuthResult.Success -> viewModel.onYandexLoginSuccess(...)
        is YandexAuthResult.Failure -> viewModel.onYandexLoginError(...)
        YandexAuthResult.Cancelled -> viewModel.onYandexLoginError(...)
    }
}
```

После успешного входа создаётся внутренняя модель `UserSession`, а ViewModel не зависит от классов SDK.

---

## 6. Авторизация через VK ID

В проект подключен VK ID SDK:

```kotlin
implementation("com.vk.id:vkid:2.6.0")
```

Также добавлены репозитории VK SDK в `settings.gradle.kts`.

В приложении есть отдельная кнопка «Войти через VK ID». При нажатии вызывается:

```kotlin
VKID.instance.authorize(
    lifecycleOwner = this,
    callback = object : VKIDAuthCallback {
        override fun onAuth(accessToken: AccessToken) {
            viewModel.onVkLoginSuccess(...)
        }

        override fun onFail(fail: VKIDAuthFail) {
            viewModel.onVkLoginError(fail.description)
        }
    }
)
```

Примечание по тестированию: в учебной среде VK ID вход может завершаться ошибкой на стороне VK ID/кабинета разработчика. Аналогичная проблема наблюдалась у других студентов. При этом интеграция SDK, ключи, manifest placeholders и обработка результата в коде реализованы.

---

## 7. Вход через Google

Дополнительно добавлен вход через Google-аккаунт:

```kotlin
implementation("com.google.android.gms:play-services-auth:21.4.0")
```

Google Sign-In используется как третий провайдер авторизации:

```kotlin
GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
    .requestEmail()
    .requestProfile()
    .build()
```

После успешного входа пользователь также сохраняется в общей модели `UserSession`, а событие отправляется в AppMetrica с параметром:

```text
provider = google
```

---

## 8. Безопасное хранение токена

Токен, имя пользователя, провайдер и ID пользователя сохраняются через `EncryptedSharedPreferences`.

```kotlin
EncryptedSharedPreferences.create(
    context,
    "secure_session",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

Токены не сохраняются в обычный `SharedPreferences`, не передаются через Intent и не выводятся в Logcat.

---

## 9. Вкладка аккаунта

В приложении реализована вкладка **«Аккаунт»**. Она показывает:

- имя пользователя;
- провайдера входа;
- ID пользователя;
- признак наличия фото профиля;
- кнопку выхода.

ID пользователя нужен для будущей системы друзей через Firebase.

```kotlin
data class UserSession(
    val token: String,
    val userName: String,
    val provider: AuthProvider,
    val userId: String,
    val photoUrl: String? = null
)
```

---

## 10. Система друзей

Вкладка **«Друзья»** больше не содержит заранее заданные мок-профили. Пользователь может добавить друга по ID.

Сейчас друзья сохраняются локально в `LocalFriendsRepository`, а в следующей лабораторной этот механизм будет перенесён на Firebase/Firestore.

```kotlin
class LocalFriendsRepository : FriendsRepository {
    private val friends = mutableListOf<FriendProgress>()

    override fun addFriendById(id: String): FriendProgress? {
        val normalized = id.trim().uppercase()
        if (normalized.isBlank()) return null

        val friend = FriendProgress(
            id = normalized,
            name = "Друг $normalized",
            status = "ожидает синхронизацию Firebase",
            pureAlcoholMl = 0.0,
            streakDays = 0
        )
        friends += friend
        return friend
    }
}
```

---

## 11. Экран «О нас» и карта

Раздел «О нас» перенесён из нижней навигации в маленькую кнопку `i` на вкладке аккаунта.

В разделе отображается:

- информация о выдуманной компании **BuhloSoft Analytics**;
- карта Yandex MapKit;
- маркер офиса;
- кнопка построения маршрута до офиса.

```kotlin
MapKitFactory.setApiKey(apiKey)
MapKitFactory.initialize(this)
```

Маршрут строится через интент Яндекс.Карт:

```kotlin
val uri = Uri.parse("yandexmaps://maps.yandex.ru/?rtext=~$officeLat,$officeLon&rtt=auto")
```

---

## 12. События аналитики

В AppMetrica логируются следующие события:

- `screen_viewed`;
- `drink_added`;
- `day_cleared`;
- `friend_added`;
- `limit_warning_shown`;
- `user_logged_in`.

Для события входа передаётся параметр `provider`:

- `yandex`;
- `vk`;
- `google`;
- `demo`.

---

# Контрольное задание для самопроверки

## Выполненные пункты

### 1. AppMetrica подключена — выполнено

SDK подключен, ключ передаётся через `BuildConfig`, инициализация выполняется в `Application`.

### 2. AnalyticsService создан — выполнено

Созданы `AnalyticsService`, `AppMetricaAnalyticsService` и `FakeAnalyticsService`.

### 3. Unit-тесты ViewModel — выполнено

Написаны unit-тесты, проверяющие вызовы аналитики через fake-сервис.

### 4. Yandex ID — выполнено

Добавлен вход через Yandex LoginSDK, обработаны успех, ошибка и отмена.

### 5. VK ID — выполнено частично с технической оговоркой

SDK подключён, кнопка входа, manifest placeholders и callback реализованы. При практическом тестировании вход VK ID может не проходить из-за ограничений/ошибок кабинета VK ID, что совпало с проблемой у других студентов.

### 6. Безопасное хранение токенов — выполнено

Данные сессии хранятся в `EncryptedSharedPreferences`.

### 7. Событие user_logged_in — выполнено

После входа через каждый провайдер отправляется событие `user_logged_in` с параметром `provider`.

### 8. Раздел «О нас» с картой — выполнено

Раздел доступен через кнопку `i`, содержит описание компании, карту и построение маршрута.

---

## Подготовка к лабораторной работе №7

В лабораторной работе №7 требуется подключить Firebase и Firebase Cloud Messaging.

В текущем приложении уже подготовлена логика, которую можно будет перенести на Firebase:

- у каждого пользователя есть `userId`;
- во вкладке друзей есть добавление по ID;
- локальный `FriendsRepository` можно заменить на Firestore-реализацию;
- в дальнейшем можно хранить друзей в коллекции `users/{userId}/friends`;
- Firebase также можно использовать для push-уведомлений о добавлении друга.

---

## Итог

В ходе лабораторной работы было разработано приложение **«Бухлограф»**, демонстрирующее подключение сторонних сервисов в Android.

В приложении реализованы:

- авторизация через Yandex ID;
- интеграция VK ID SDK;
- вход через Google;
- аналитика через AppMetrica;
- безопасное хранение пользовательской сессии;
- карта Yandex MapKit;
- вкладка аккаунта;
- система добавления друзей по ID;
- unit-тесты для проверки событий аналитики.

Работа показала, что внешние SDK лучше подключать через фасады и внутренние модели приложения. Такой подход уменьшает связанность кода, упрощает тестирование и позволяет заменять поставщиков сервисов без переписывания UI и ViewModel.

