package com.example.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

data class DiagnosticLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val tag: String,
    val message: String,
    val details: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

enum class LogLevel {
    DEBUG, INFO, WARNING, ERROR, SUCCESS
}

object DiagnosticLogger {
    private const val MAX_LOGS = 200
    private val logs = CopyOnWriteArrayList<DiagnosticLogEntry>()
    private var listener: (() -> Unit)? = null

    fun setListener(l: (() -> Unit)?) {
        listener = l
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.WARNING, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)
    fun success(tag: String, message: String) = log(LogLevel.SUCCESS, tag, message)

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val details = throwable?.let {
            val sw = java.io.StringWriter()
            val pw = java.io.PrintWriter(sw)
            it.printStackTrace(pw)
            sw.toString()
        }

        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO, LogLevel.SUCCESS -> Log.i(tag, message)
            LogLevel.WARNING -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }

        val entry = DiagnosticLogEntry(
            level = level,
            tag = tag,
            message = message,
            details = details
        )

        logs.add(entry)
        while (logs.size > MAX_LOGS) {
            logs.removeAt(0)
        }
        listener?.invoke()
    }

    fun getLogs(): List<DiagnosticLogEntry> = logs.toList()

    fun clear() {
        logs.clear()
        listener?.invoke()
    }

    fun getAllLogsFormatted(): String {
        val sb = StringBuilder()
        sb.append("=== DIAGNOSTIC LOGS AI IMAGE LAB ===\n")
        sb.append("Generated at: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")
        logs.forEach { entry ->
            sb.append("[${entry.formattedTime}] [${entry.level}] [${entry.tag}] ${entry.message}\n")
            if (!entry.details.isNullOrBlank()) {
                sb.append("Stack Trace:\n${entry.details}\n")
            }
        }
        return sb.toString()
    }
}
