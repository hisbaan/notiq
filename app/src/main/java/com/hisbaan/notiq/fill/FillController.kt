package com.hisbaan.notiq.fill

import com.hisbaan.notiq.NotiqApp
import com.hisbaan.notiq.core.Code
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The code waiting to be filled, if any. [FillService] only listens to the screen while there's
 * one: from when a code arrives until it's filled, dismissed, or [OFFER_MS] passes.
 */
class FillController(private val app: NotiqApp) {
    data class Offer(val code: Code)

    private val _offer = MutableStateFlow<Offer?>(null)
    val offer: StateFlow<Offer?> = _offer

    private var expiry: Job? = null

    fun offer(code: Code) {
        _offer.value = Offer(code)
        expiry?.cancel()
        expiry = app.scope.launch {
            delay(OFFER_MS)
            end("expired")
        }
    }

    /** Filled, dismissed or expired: stop watching the screen. */
    fun end(reason: String) {
        if (_offer.value == null) return
        expiry?.cancel()
        _offer.value = null
        if (FillService.connected) app.diagnostics.event("Fill: $reason")
    }

    private companion object {
        /** Codes usually live 5-10 minutes. */
        const val OFFER_MS = 5 * 60_000L
    }
}
