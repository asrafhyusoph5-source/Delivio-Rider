package com.example.data.models

import com.google.firebase.Timestamp
import java.util.Date

data class OrderChatThread(
    val id: String = "",
    val orderId: String = "",
    val orderNumber: String = "",
    val channel: String = "rider",
    val customerId: String = "",
    val customerName: String = "",
    val merchantId: String = "",
    val merchantName: String = "",
    val riderId: String = "",
    val riderName: String = "",
    val createdAt: Any? = null,
    val lastMessageAt: Any? = null,
    val lastMessageText: String = "",
    val lastSenderRole: String = "",
    val customerReadAt: Any? = null,
    val partnerReadAt: Any? = null,
    val expireAt: Any? = null
) {
    val lastMessageTimeMillis: Long
        get() = parseChatTimestamp(lastMessageAt)

    val partnerReadTimeMillis: Long
        get() = parseChatTimestamp(partnerReadAt)

    val expireTimeMillis: Long
        get() = parseChatTimestamp(expireAt)

    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val exp = expireTimeMillis
        return exp in 1..nowMillis
    }

    /**
     * Unread: the other side's last message is newer than my read time
     * (lastSenderRole is not me and lastMessageAt is greater than my ReadAt, or my ReadAt is empty).
     */
    fun hasUnreadForDriver(): Boolean {
        if (lastSenderRole.equals("rider", ignoreCase = true) || lastSenderRole.isBlank()) {
            return false
        }
        val lastMsgTime = lastMessageTimeMillis
        if (lastMsgTime <= 0L) return false
        val myReadTime = partnerReadTimeMillis
        return myReadTime <= 0L || lastMsgTime > myReadTime
    }
}

data class OrderChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderRole: String = "rider", // "customer" | "merchant" | "rider"
    val type: String = "text", // "text" | "image"
    val text: String = "",
    val image: String = "", // JPEG data URI string, only for type image, max 400000 characters
    val source: String = "", // "camera" | "gallery", only for images
    val createdAt: Any? = null,
    val expireAt: Any? = null
) {
    val isFromDriver: Boolean
        get() = senderRole.equals("rider", ignoreCase = true)

    val timeMillis: Long
        get() = parseChatTimestamp(createdAt)

    val expireTimeMillis: Long
        get() = parseChatTimestamp(expireAt)

    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val exp = expireTimeMillis
        return exp in 1..nowMillis
    }
}

fun parseChatTimestamp(raw: Any?): Long {
    return when (raw) {
        is Timestamp -> raw.toDate().time
        is Date -> raw.time
        is Number -> {
            val n = raw.toLong()
            if (n < 1000000000000L) n * 1000L else n
        }
        else -> 0L
    }
}

fun formatCustomerFirstName(fullName: String?): String {
    val clean = (fullName ?: "").trim()
    if (clean.isBlank()) return "Customer"
    val firstWord = clean.split("\\s+".toRegex()).firstOrNull() ?: "Customer"
    return firstWord.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

object OrderChatConstants {
    const val CLEANUP_INTERVAL_MILLIS = 30L * 60L * 1000L // 30 minutes
    const val CLEANUP_MESSAGES_LIMIT = 100L

    val CLOSED_STATUSES = setOf("delivered", "completed", "cancelled", "canceled", "rejected")

    val QUICK_REPLIES = listOf(
        "I'm on my way",
        "I've arrived",
        "I'm outside",
        "Please come out"
    )

    fun buildChatId(orderId: String, riderUid: String): String = "${orderId}_rider_${riderUid}"

    fun formatCustomerFirstName(fullName: String?): String = com.example.data.models.formatCustomerFirstName(fullName)

    fun isChatOpen(status: String): Boolean {
        return status.trim().lowercase() !in CLOSED_STATUSES
    }

    fun isInstantOrder(serviceType: String): Boolean {
        val st = serviceType.trim().lowercase()
        return st == "request_rider" || st == "request" || st.contains("request") || st.contains("instant")
    }

    fun getOrderClosedTimeMillis(order: Order): Long? {
        val s = order.status.trim().lowercase()
        if (s !in CLOSED_STATUSES) return null

        // Try statusHistory first
        for (item in order.statusHistory.reversed()) {
            val status = item["status"]?.toString()?.trim()?.lowercase() ?: ""
            if (status in CLOSED_STATUSES) {
                val ts = item["timestamp"] ?: item["at"] ?: item["time"] ?: item["createdAt"]
                val ms = parseChatTimestamp(ts)
                if (ms > 0L) return ms
            }
        }

        // Fallback to deliveredAt or updatedAt
        val delMs = parseChatTimestamp(order.deliveredAt)
        if (delMs > 0L) return delMs

        val upMs = parseChatTimestamp(order.updatedAt)
        if (upMs > 0L) return upMs

        return null
    }

    fun isChatExpiredAfter24Hours(order: Order, now: Long = System.currentTimeMillis()): Boolean {
        val closedAt = getOrderClosedTimeMillis(order) ?: return false
        val twentyFourHours = 24L * 60L * 60L * 1000L
        return (now - closedAt) > twentyFourHours
    }

    fun canViewTripChat(serviceType: String, closedTimeMillis: Long?, now: Long = System.currentTimeMillis()): Boolean {
        if (isInstantOrder(serviceType)) return false
        if (closedTimeMillis == null || closedTimeMillis <= 0L) return false
        val twentyFourHours = 24L * 60L * 60L * 1000L
        return (now - closedTimeMillis) <= twentyFourHours
    }
}
