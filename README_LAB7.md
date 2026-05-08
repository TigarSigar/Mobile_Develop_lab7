<div align="center">

# ОТЧЁТ  
# О ВЫПОЛНЕНИИ ЛАБОРАТОРНОЙ РАБОТЫ №7

### по дисциплине: Разработка мобильных приложений  
### тема: Firebase, FCM, Remote Config и Cloud Firestore

**Студент:** Кононенко Егор Сергеевич  
**Приложение:** Бухлограф

</div>

---

## Цель работы

Освоить подключение Firebase к Android-приложению и реализовать:

- Firebase Cloud Messaging;
- обработку FCM-токена;
- push-уведомления;
- Firebase Remote Config;
- Cloud Firestore;
- хранение пользовательского профиля;
- хранение записей и друзей пользователя в облаке;
- realtime-подписку на данные профиля.

---

## Что реализовано в приложении

В приложение **Бухлограф** добавлена Firebase-инфраструктура:

- зависимости `firebase-bom`, `firebase-auth`, `firebase-firestore`, `firebase-messaging`, `firebase-analytics`, `firebase-config`;
- условное подключение `com.google.gms.google-services`, чтобы проект собирался до добавления `google-services.json`;
- `PushMessagingService`, расширяющий `FirebaseMessagingService`;
- обработка `onNewToken()` и сохранение FCM-токена;
- обработка `onMessageReceived()` и показ уведомления через `NotificationCompat`;
- запрос разрешения `POST_NOTIFICATIONS` на Android 13+;
- Firestore-репозиторий для записей и друзей;
- Remote Config сервис с параметрами `welcome_banner` и `experimental_friends_enabled`;
- отображение Remote Config баннера на главном экране;
- отображение профиля пользователя из Firestore во вкладке аккаунта.

---

## Структура Firestore

Используется следующая структура:

```text
users/{userId}
users/{userId}/drinks/{drinkId}
users/{userId}/friends/{friendId}
```

Документ пользователя содержит:

```kotlin
data class UserProfile(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val provider: String = "",
    val photoUrl: String = "",
    val fcmToken: String = "",
    val updatedAtMillis: Long = 0L
)
```

Записи пользователя сохраняются в подколлекции `drinks`, а ID друзей — в подколлекции `friends`.

---

## Firebase setup

Для завершения настройки нужно:

1. Открыть [Firebase Console](https://console.firebase.google.com/).
2. Создать проект, например `Buhlograf`.
3. Добавить Android-приложение:

```text
Android package name: com.example.buhlograf
SHA-1 / SHA-256: взять из debug keystore
```

Для текущего проекта уже использовался SHA-256:

```text
FC:7C:1B:F3:04:CB:36:38:AE:27:BC:2F:65:4D:95:51:5C:E9:67:75:BF:10:D0:AD:D0:C0:46:80:56:6B:FD:12
```

4. Скачать `google-services.json`.
5. Положить файл сюда:

```text
app/google-services.json
```

После этого Gradle автоматически применит плагин `com.google.gms.google-services`.

---

## Authentication

Для работы Firestore security rules нужно включить Firebase Authentication:

- Google provider;
- Anonymous provider.

Google provider нужен для входа через Google. Anonymous provider используется для Yandex ID и VK ID, так как эти провайдеры не являются стандартными Firebase-провайдерами без собственного backend/custom token.

После настройки Firebase нужно добавить Web Client ID в `local.properties`:

```properties
GOOGLE_WEB_CLIENT_ID=...
```

---

## Cloud Firestore

В коде реализован `FirebaseBuhlografRepository`, который одновременно работает как:

- `DrinkRepository`;
- `FriendsRepository`;
- `CloudSyncService`.

При входе пользователя вызывается:

```kotlin
cloudSyncService.bindSession(...)
```

После этого:

- профиль сохраняется в `users/{userId}`;
- записи сохраняются в `users/{userId}/drinks`;
- друзья сохраняются в `users/{userId}/friends`;
- изменения профиля, друзей и записей слушаются через `addSnapshotListener()`.

---

## Firestore rules

Правила безопасности вынесены в файл:

```text
firestore.rules
```

Их нужно скопировать в Firebase Console:

```js
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;

      match /drinks/{drinkId} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
      }

      match /friends/{friendId} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
      }
    }
  }
}
```

---

## Firebase Cloud Messaging

Реализован сервис:

```kotlin
class PushMessagingService : FirebaseMessagingService()
```

Он переопределяет:

- `onNewToken()`;
- `onMessageReceived()`.

В `onNewToken()` токен сохраняется локально и обновляется в Firestore:

```kotlin
app.cloudSyncService.updateFcmToken(session.userId, token)
```

В `onMessageReceived()` создаётся notification channel и показывается уведомление.

---

## Remote Config

Добавлен интерфейс:

```kotlin
interface RemoteConfigService {
    fun getState(): RemoteConfigState
    fun fetch(onUpdated: (RemoteConfigState) -> Unit)
}
```

Параметры:

```text
welcome_banner
experimental_friends_enabled
```

`welcome_banner` отображается на главном экране приложения.

---

## Улучшение авторизации

В рамках подготовки к 7-й лабораторной исправлены данные профиля:

- Yandex ID после входа дополнительно запрашивает `https://login.yandex.ru/info?format=json`;
- из Яндекса берутся имя, email и avatar id;
- Google Sign-In может работать через Firebase Auth при наличии `GOOGLE_WEB_CLIENT_ID`;
- VK ID сохраняет имя и фото, если SDK возвращает `userData`.

---

## Вывод

В ходе лабораторной работы приложение **Бухлограф** было расширено Firebase-инфраструктурой.

Были реализованы FCM, Remote Config и Cloud Firestore. Записи пользователя и список друзей теперь готовы храниться в Firebase, а профиль пользователя отображается из облачного документа. Архитектура осталась расширяемой: Firebase скрыт за интерфейсами `CloudSyncService` и `RemoteConfigService`, поэтому UI и ViewModel не зависят напрямую от конкретных SDK.

