package com.hisbaan.notiq.fill

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.hisbaan.notiq.NotiqApp

/**
 * Puts a code into a field: set its text (one character per box for split fields), check it
 * took, else paste. Password fields read back masked, so for those the action's result is all
 * there is to go on.
 */
class Filler(private val app: NotiqApp) {
    /** [boxes] is the split field's boxes, or null for a single field. */
    fun fill(field: AccessibilityNodeInfo, boxes: List<AccessibilityNodeInfo>?, code: String): Boolean {
        if (boxes != null && boxes.size == code.length) {
            val set = boxes.zip(code.toList()).all { (box, char) -> setText(box, char.toString()) }
            if (set && boxes.all { took(it, null) }) return true
            return paste(boxes.first(), code)
        }
        if (setText(field, code) && took(field, code)) return true
        return paste(field, code)
    }

    private fun setText(node: AccessibilityNodeInfo, text: String): Boolean =
        node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) },
        )

    /** Whether the field now shows [expected] (or anything, when null). */
    private fun took(node: AccessibilityNodeInfo, expected: String?): Boolean {
        if (node.isPassword) return true
        node.refresh()
        val text = node.text?.toString()?.takeUnless { node.isShowingHintText }.orEmpty()
        return if (expected == null) text.isNotEmpty() else text.filter { it.isLetterOrDigit() } == expected
    }

    /** Split fields and custom widgets often handle a paste even when they ignore set-text. */
    private fun paste(node: AccessibilityNodeInfo, code: String): Boolean {
        app.clipboard.copyCode(code, app.settings.settings.value?.clearAfterSeconds ?: 0)
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val pasted = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        app.diagnostics.event("Fill: set-text didn't take, pasted (${if (pasted) "ok" else "refused"})")
        return pasted
    }
}
