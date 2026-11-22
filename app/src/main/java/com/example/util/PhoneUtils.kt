package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri

object PhoneUtils {
    /**
     * Cleans a phone number for tel: dialing:
     * - Returns "" if null, blank, or "Not provided" (case-insensitive)
     * - Strips spaces, dashes (-), and brackets ((), [], {})
     * - Keeps a single leading '+' if present
     * - Requires at least one digit; otherwise returns ""
     */
    fun cleanPhoneNumber(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals("Not provided", ignoreCase = true)) {
            return ""
        }
        val hasLeadingPlus = trimmed.startsWith("+")
        val body = if (hasLeadingPlus) trimmed.substring(1) else trimmed
        val stripped = body.replace(Regex("[\\s\\-()\\[\\]{}+]"), "")
        if (!stripped.any { it.isDigit() }) {
            return ""
        }
        return if (hasLeadingPlus) "+$stripped" else stripped
    }

    fun isUsablePhone(raw: String?): Boolean = cleanPhoneNumber(raw).isNotEmpty()

    fun openSystemDialer(context: Context, rawPhone: String?): Boolean {
        val cleaned = cleanPhoneNumber(rawPhone)
        if (cleaned.isEmpty()) return false
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleaned")).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
