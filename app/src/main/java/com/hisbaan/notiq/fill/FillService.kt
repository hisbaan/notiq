package com.hisbaan.notiq.fill

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.hisbaan.notiq.NotiqApp
import com.hisbaan.notiq.core.Assessment
import com.hisbaan.notiq.core.CodeFieldDetector
import com.hisbaan.notiq.core.FillMode
import com.hisbaan.notiq.shizuku.ShizukuShell
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Fills codes into other apps' fields. The class name is permanent: enabling the service is tied
 * to it. Idle (no events at all) unless [FillController] has a code waiting; then it watches
 * focus and window changes, and for the focused field asks CodeFieldDetector and the fill mode
 * whether to show the chip, fill, or do nothing.
 */
class FillService : AccessibilityService() {
    private val app get() = application as NotiqApp
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var chip: FillChip
    private lateinit var filler: Filler
    private var watching: Job? = null

    /** The last thing logged for a field, so Diagnostics gets one line per field, not per event. */
    private var lastLogged: String? = null

    private val evaluate = Runnable { evaluateNow() }

    override fun onServiceConnected() {
        instance = this
        chip = FillChip(this, onFill = ::fillFromChip, onDismiss = { app.fill.end("chip dismissed") })
        filler = Filler(app)
        setListening(false)
        watching = app.scope.launch {
            app.fill.offer.collect { offer ->
                setListening(offer != null)
                lastLogged = null
                if (offer != null) evaluateSoon() else chip.hide()
            }
        }
        app.diagnostics.event("Fill service connected")
        app.refreshSetup()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        evaluateSoon()
    }

    override fun onInterrupt() {}

    private fun stop() {
        if (instance !== this) return
        instance = null
        watching?.cancel()
        handler.removeCallbacks(evaluate)
        chip.hide()
        app.diagnostics.event("Fill service disconnected")
        app.refreshSetup()
    }

    /** No events while nothing's waiting, so the service costs nothing the rest of the time. */
    private fun setListening(on: Boolean) {
        serviceInfo = serviceInfo.apply { eventTypes = if (on) EVENTS else 0 }
    }

    /** Events come in bursts (a screen change is dozens); look once things settle. */
    private fun evaluateSoon() {
        handler.removeCallbacks(evaluate)
        handler.postDelayed(evaluate, SETTLE_MS)
    }

    private fun evaluateNow() {
        val offer = app.fill.offer.value ?: return chip.hide()
        val mode = app.settings.settings.value?.fillMode ?: return
        val code = offer.code.code
        val look = look(code) ?: return chip.hide()
        val action = mode.decide(look.assessment.confidence)
        log("${look.field.packageName}: ${look.assessment.confidence} (${look.assessment.reason}) → $action\n${describe(look.field)}")
        when (action) {
            FillMode.Action.NOTHING -> chip.hide()
            FillMode.Action.CHIP -> chip.show(code, FieldReader.bounds(look.field), imeTop())
            FillMode.Action.FILL -> fill(look, code)
        }
    }

    private fun fillFromChip() {
        val offer = app.fill.offer.value ?: return chip.hide()
        val look = look(offer.code.code) ?: return chip.hide()
        fill(look, offer.code.code)
    }

    /** The focused field, its split-field boxes, and what the detector makes of it. */
    private class Look(val field: AccessibilityNodeInfo, val boxes: List<AccessibilityNodeInfo>, val assessment: Assessment)

    /** Looks at the focused field in any window, except system UI (the shade, quick settings). */
    private fun look(code: String): Look? {
        val field = focusedField() ?: return null
        if (field.packageName == SYSTEM_UI) return null
        val boxes = FieldReader.siblings(field)
        val assessment = CodeFieldDetector.assess(
            FieldReader.field(field),
            boxes.map(FieldReader::field),
            FieldReader.screen(rootInActiveWindow),
            code.length,
        )
        return Look(field, boxes, assessment)
    }

    /**
     * The editable field with input focus. Compose and web views draw their fields as virtual
     * children of one host view, and the input-focus query answers with that host; the field is
     * then the focused, editable node under it.
     */
    private fun focusedField(): AccessibilityNodeInfo? {
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        if (focused.isEditable) return focused
        val queue = ArrayDeque(listOf(focused))
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_SEARCH) {
            val node = queue.removeFirst()
            visited++
            if (node.isEditable && node.isFocused) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return null
    }

    private fun fill(look: Look, code: String) {
        chip.hide()
        val boxes = look.boxes.takeIf { look.assessment.split }
        val ok = filler.fill(look.field, boxes, code)
        val where = "${look.field.packageName}${if (boxes != null) " (${boxes.size} boxes)" else ""}"
        // Either way, stop: a fill that failed once would fail again, and the code is on the
        // clipboard (the paste fallback put it there) for a manual paste.
        app.fill.end(if (ok) "filled into $where" else "couldn't fill $where")
        if (ok && app.settings.settings.value?.submitAfterFill == true) {
            val filled = boxes ?: listOf(look.field)
            handler.postDelayed({ submitIfWaiting(filled) }, SUBMIT_DELAY_MS)
        }
    }

    /**
     * Presses Enter unless the app took the code on its own: many submit once the last digit is
     * in, and then focus leaves the field (a new screen, a disabled field). The field's own IME
     * action (Done / Go) needs no extra access; web views often don't offer it, so otherwise a
     * real Enter key through Shizuku. Once only: a second Enter could land on the next screen.
     */
    private fun submitIfWaiting(filled: List<AccessibilityNodeInfo>) {
        val focused = focusedField()
        if (focused == null || focused !in filled || !focused.isEnabled) {
            app.diagnostics.event("Fill: the app moved on by itself")
            return
        }
        val imeEnter = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER
        if (imeEnter in focused.actionList && focused.performAction(imeEnter.id)) {
            app.diagnostics.event("Fill: pressed Enter (the field's action)")
            return
        }
        if (app.shizuku.state.value != ShizukuShell.State.READY) {
            app.diagnostics.event("Fill: didn't press Enter: the field has no Enter action and Shizuku isn't running")
            return
        }
        app.scope.launch {
            val result = runCatching { app.shizuku.run(listOf("input", "keyevent", "KEYCODE_ENTER")) }
            app.diagnostics.event(result.fold({ "Fill: pressed Enter (key event, exit ${it.exitCode})" }, { "Fill: couldn't press Enter: $it" }))
        }
    }

    private fun imeTop(): Int? {
        val ime = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } ?: return null
        return Rect().also(ime::getBoundsInScreen).top
    }

    /** What the field reports about itself, for tuning the detector. Not its text: it could be a password. */
    private fun describe(field: AccessibilityNodeInfo): String {
        val info = FieldReader.field(field)
        return listOfNotNull(
            "class=${field.className}",
            info.hint?.let { "hint=\"$it\"" },
            info.viewId?.let { "id=$it" },
            info.label?.let { "label=\"$it\"" },
            info.contentDescription?.let { "description=\"$it\"" },
            "inputType=0x${field.inputType.toString(16)}",
            "maxLength=${info.maxLength}",
            "password".takeIf { info.password },
        ).joinToString(" ")
    }

    private fun log(text: String) {
        if (text == lastLogged) return
        lastLogged = text
        app.diagnostics.event("Fill: $text")
    }

    companion object {
        @Volatile
        var instance: FillService? = null
            private set

        val connected: Boolean get() = instance != null

        fun component(context: Context) = ComponentName(context, FillService::class.java)

        private const val SYSTEM_UI = "com.android.systemui"
        private const val SETTLE_MS = 150L
        private const val MAX_SEARCH = 500

        /** Long enough for an app that submits on its own to have moved focus. */
        private const val SUBMIT_DELAY_MS = 250L
        private const val EVENTS = AccessibilityEvent.TYPE_VIEW_FOCUSED or
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_WINDOWS_CHANGED
    }
}
