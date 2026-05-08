package com.example.buhlograf

import com.example.buhlograf.data.FakeAnalyticsService
import com.example.buhlograf.data.InMemoryDrinkRepository
import com.example.buhlograf.data.LocalFriendsRepository
import com.example.buhlograf.domain.AuthService
import com.example.buhlograf.domain.BuildDashboardUseCase
import com.example.buhlograf.domain.CalculateMascotMoodUseCase
import com.example.buhlograf.domain.DrinkType
import com.example.buhlograf.domain.UserSession
import com.example.buhlograf.ui.AppTab
import com.example.buhlograf.ui.BuhlografViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuhlografViewModelTest {
    @Test
    fun addDrinkTracksAnalyticsEvent() {
        val fixture = Fixture()

        fixture.viewModel.addDrink(
            type = DrinkType.Beer,
            volumeMl = 500,
            strengthPercent = 5.0,
            note = "после пары"
        )

        assertTrue(
            fixture.analytics.events.any { (name, params) ->
                name == "drink_added" &&
                    params["type"] == "beer" &&
                    params["volume_ml"] == 500
            }
        )
    }

    @Test
    fun openingFriendsTracksScreenViewedEvent() {
        val fixture = Fixture()

        fixture.viewModel.selectTab(AppTab.Friends)

        assertTrue(
            fixture.analytics.events.any { (name, params) ->
                name == "screen_viewed" && params["screen_name"] == "friends"
            }
        )
    }

    @Test
    fun demoLoginStoresSessionAndTracksProvider() {
        val fixture = Fixture()

        fixture.viewModel.enterDemoMode()

        assertEquals("demo", fixture.auth.session?.provider?.analyticsName)
        assertTrue(
            fixture.analytics.events.any { (name, params) ->
                name == "user_logged_in" && params["provider"] == "demo"
            }
        )
    }

    private class Fixture {
        val analytics = FakeAnalyticsService()
        val auth = TestAuthService()
        val drinkRepository = InMemoryDrinkRepository()
        private val buildDashboard = BuildDashboardUseCase(
            drinkRepository = drinkRepository,
            friendsRepository = LocalFriendsRepository(),
            calculateMascotMood = CalculateMascotMoodUseCase()
        )
        val viewModel = BuhlografViewModel(
            authService = auth,
            drinkRepository = drinkRepository,
            friendsRepository = LocalFriendsRepository(),
            buildDashboard = buildDashboard,
            analyticsService = analytics
        )
    }

    private class TestAuthService : AuthService {
        var session: UserSession? = null

        override fun getSavedSession(): UserSession? = session

        override fun saveSession(session: UserSession) {
            this.session = session
        }

        override fun clearSession() {
            session = null
        }

        override fun createDemoSession(): UserSession =
            UserSession(
                token = "demo",
                userName = "test",
                provider = com.example.buhlograf.domain.AuthProvider.Demo,
                userId = "DEMO-TEST"
            )
    }
}
