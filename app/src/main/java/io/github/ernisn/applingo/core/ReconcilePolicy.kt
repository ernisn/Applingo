package io.github.ernisn.applingo.core

/** What to do when Applingo's recorded choice and the system's per-app locales disagree. */
sealed interface ReconcileAction {
    /** Nothing to do. */
    data object None : ReconcileAction

    /** The system lost or trimmed the choice (e.g. after an app update): write [locales] again. */
    data class Repair(val locales: List<String>) : ReconcileAction

    /** The locales were changed outside Applingo (system settings, the app itself): record [locales]. */
    data class Adopt(val locales: List<String>) : ReconcileAction
}

object ReconcilePolicy {

    /**
     * Decides how to bring [desired] (Applingo's record, empty = none) and [actual] (the system's
     * current per-app locales, empty = follows the system) back in line. Both must be canonical tags.
     *
     * Only changes that look like the system discarding our choice are repaired: everything cleared,
     * or entries removed while the rest kept their order (what `removeUnsupportedAppLocales` does).
     * Any other difference is treated as a deliberate change elsewhere and followed instead of fought.
     */
    fun decide(desired: List<String>, actual: List<String>): ReconcileAction = when {
        actual == desired -> ReconcileAction.None
        desired.isEmpty() -> ReconcileAction.Adopt(actual)
        actual.isEmpty() || isOrderedSubset(actual, desired) -> ReconcileAction.Repair(desired)
        else -> ReconcileAction.Adopt(actual)
    }

    private fun isOrderedSubset(candidate: List<String>, of: List<String>): Boolean {
        var i = 0
        for (tag in of) {
            if (i < candidate.size && candidate[i] == tag) i++
        }
        return i == candidate.size
    }
}
