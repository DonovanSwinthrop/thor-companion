package com.pokedaisy.app

import android.app.Application

/** Process start: counts the app open (for CrashReports' one-time ask) and applies the crash-report choice. */
class PokeDaisyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The NEW tags' week starts with the first launch of a build that has the feature.
        com.pokedaisy.app.companion.NewFeatures.init(Prefs(this))
        val prefs = Prefs(this)
        prefs.appOpenCount = (prefs.appOpenCount + 1).coerceAtMost(1000)
        CrashReports.apply(prefs.crashReportsEnabled)
        ScreenScraperClient.enabled = FeatureFlags.screenScraper(this)
        // the test walker reads the RUN tab's state from files/run-state.json
        if (BuildConfig.DEBUG) com.pokedaisy.app.companion.RrOverlay.debugDir = filesDir
    }
}
