package com.raisethebar.app

import android.app.Application
import com.raisethebar.app.data.AppClock
import com.raisethebar.app.data.Notifier
import com.raisethebar.app.data.Repo
import com.raisethebar.app.data.Seeder
import com.raisethebar.app.data.evaluateDays
import com.raisethebar.app.db.AppDatabase
import com.raisethebar.app.notify.AndroidNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RaiseTheBarApp : Application() {
    val repo: Repo by lazy { Repo(AppDatabase.build(this), AppClock.system(), notifier()) }

    private val startupLock = Mutex()
    private val readyFlow = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = readyFlow.asStateFlow()

    /** Runs on every app start and resume: seed bundled data, then the midnight timeout and check-in rebuild. */
    suspend fun startup() =
        startupLock.withLock {
            Seeder(repo.db, repo.time).seedIfNeeded()
            repo.clearCaches()
            repo.evaluateDays()
            readyFlow.value = true
            repo.changed()
        }

    private fun notifier(): Notifier = AndroidNotifier(this)
}
