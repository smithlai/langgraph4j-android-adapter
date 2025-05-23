package com.smith.lai.langgraph4j_android_adapter.jinjaparser.data

sealed class Token {
    data class Text(val value: String, val trimLeft: Boolean = false, val trimRight: Boolean = false) : Token()
    data class Variable(val name: String, val trimLeft: Boolean = false, val trimRight: Boolean = false) : Token()
    data class IfStart(val condition: String) : Token()
    object IfEnd : Token()
    object Else : Token()
    data class ForStart(val item: String, val list: String) : Token()
    object ForEnd : Token()
    data class Set(val variable: String, val value: String) : Token()
    data class RaiseException(val message: String) : Token()
}
