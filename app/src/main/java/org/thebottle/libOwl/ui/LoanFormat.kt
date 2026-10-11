package org.thebottle.libOwl.ui

import org.thebottle.libOwl.data.Loan
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

fun millisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

fun localDateToMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun formatDate(millis: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(millisToLocalDate(millis))

/** Whole days from today until the due date (negative = overdue). */
fun daysUntil(dueMillis: Long): Long = ChronoUnit.DAYS.between(LocalDate.now(), millisToLocalDate(dueMillis))

val Loan.isActive: Boolean get() = returnedDate == null
val Loan.isOverdue: Boolean get() = isActive && dueDate != null && daysUntil(dueDate) < 0

/** Short description of where a loan stands, e.g. "Due Oct 20, 2026" or "Overdue by 3 days". */
fun Loan.dueText(): String {
    returnedDate?.let { return "Returned ${formatDate(it)}" }
    val due = dueDate ?: return "No due date"
    val days = daysUntil(due)
    return when {
        days < 0 -> "Overdue by ${-days} day${if (-days == 1L) "" else "s"}"
        days == 0L -> "Due today"
        days == 1L -> "Due tomorrow"
        else -> "Due ${formatDate(due)}"
    }
}
