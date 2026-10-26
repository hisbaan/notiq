package com.hisbaan.notiq.ui

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Fields to try filling on: a labelled code field (Compose), one box per digit (classic EditTexts
 * with a max length, as many apps build them), and two fields that should never get a code.
 */
@Composable
fun FillTestScreen(sendTestCode: () -> Unit, onBack: () -> Unit) {
    Page("Try filling", onBack = onBack) {
        Hint(
            "Send a test code, then tap into a field before it arrives. Depending on the fill mode, the chip appears " +
                "or the code fills in. The password and message fields should get nothing.",
        )
        FilledTonalButton(onClick = sendTestCode) { Text("Send a test code in 5 s") }
        var submitted by remember { mutableStateOf<String?>(null) }
        Section("Code field") {
            TestField("Verification code", KeyboardType.Number, onSubmit = { submitted = "code field: $it" })
        }
        Section("One box per digit") {
            val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
            AndroidView(factory = { splitBoxes(it, BOXES, textColor) { code -> submitted = "boxes: $code" } })
        }
        submitted?.let { Text("Enter pressed with $it", color = MaterialTheme.colorScheme.primary) }
        Section("Not code fields") {
            TestField("Password", KeyboardType.Password, password = true)
            TestField("Message", KeyboardType.Text)
        }
    }
}

@Composable
private fun TestField(label: String, keyboard: KeyboardType, password: Boolean = false, onSubmit: ((String) -> Unit)? = null) {
    var value by remember { mutableStateOf("") }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke(value) }),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Each box takes one digit and moves on to the next, like most apps' code entry; Done on any box submits. */
private fun splitBoxes(context: Context, count: Int, textColor: Int, onSubmit: (String) -> Unit): LinearLayout {
    fun dp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
    val boxes = List(count) {
        EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(1))
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) }
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
    }
    boxes.forEach { box ->
        box.setOnEditorActionListener { _, _, _ ->
            onSubmit(boxes.joinToString("") { it.text })
            true
        }
    }
    boxes.forEachIndexed { i, box ->
        box.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1) boxes.getOrNull(i + 1)?.requestFocus()
                }
            },
        )
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        boxes.forEach(::addView)
    }
}

private const val BOXES = 6
