package com.smith.lai.langgraph4j_android_adapter.jinjaparser

import android.util.Log

class Utils {

}

class TemplateException(message: String, cause: Throwable? = null) : Exception(message, cause)

// Logger object for centralized logging
object Logger {
    fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}