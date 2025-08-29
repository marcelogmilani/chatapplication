package com.marcos.chatapplication.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateFormatter {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
    private val dayOfWeekFormat = SimpleDateFormat("EEEE", Locale.getDefault())

    fun formatConversationTimestamp(date: Date?): String {
        if (date == null) return ""

        val messageCalendar = Calendar.getInstance().apply { time = date }
        val now = Calendar.getInstance()

        return when {
            isSameDay(now, messageCalendar) -> {
                timeFormat.format(date)
            }
            isYesterday(now, messageCalendar) -> {
                "Ontem"
            }
            now.timeInMillis - messageCalendar.timeInMillis < 6 * 24 * 60 * 60 * 1000 -> {
                dayOfWeekFormat.format(date).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
            }
            else -> {
                dateFormat.format(date)
            }
        }
    }

    fun formatMessageTimestamp(date: Date?): String {
        if (date == null) return ""
        return timeFormat.format(date)
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(now: Calendar, cal: Calendar): Boolean {
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return isSameDay(yesterday, cal)
    }
}