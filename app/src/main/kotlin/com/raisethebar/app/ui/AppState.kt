package com.raisethebar.app.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import com.raisethebar.app.data.Repo
import com.raisethebar.engine.InputRejected
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val LocalRepo = staticCompositionLocalOf<Repo> { error("No Repo provided") }

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** Loads data for a screen and reloads it whenever any write changes the database. Null until the first load. */
@Composable
fun <T> rememberLoad(vararg keys: Any?, block: suspend Repo.() -> T): T? {
    val repo = LocalRepo.current
    val version by repo.changes.collectAsState()
    val all = arrayOf<Any?>(repo, version, *keys)
    val state = produceState<T?>(null, *all) { value = repo.block() }
    return state.value
}

/** Runs writes off the composition; rejected inputs show a message instead of crashing. */
class Actions(
    val repo: Repo,
    private val scope: CoroutineScope,
    private val snackbar: SnackbarHostState,
) {
    fun run(then: () -> Unit = {}, block: suspend Repo.() -> Unit) {
        scope.launch {
            try {
                repo.block()
                then()
            } catch (e: InputRejected) {
                snackbar.showSnackbar(friendly(e))
            }
        }
    }

    fun message(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    companion object {
        private val LABELS =
            mapOf(
                "weight_kg" to "Weight",
                "reps" to "Reps",
                "reps_left" to "Reps left",
                "seconds" to "Hold time",
                "pain" to "Pain",
                "body_weight_kg" to "Body weight",
                "height_cm" to "Height",
                "waist_cm" to "Waist",
                "age" to "Age",
                "minutes" to "Minutes",
            )

        fun friendly(e: InputRejected): String = "${LABELS[e.field] ?: e.field.replace('_', ' ')} must be ${e.allowed}"
    }
}

@Composable
fun rememberActions(): Actions {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    return remember(repo, scope, snackbar) { Actions(repo, scope, snackbar) }
}
