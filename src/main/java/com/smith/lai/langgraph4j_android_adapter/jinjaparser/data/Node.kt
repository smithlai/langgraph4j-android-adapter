package com.smith.lai.langgraph4j_android_adapter.jinjaparser.data

sealed class Node {
    data class Text(val value: String, val trimLeft: Boolean = false, val trimRight: Boolean = false) : Node()
    data class Variable(val name: String, val trimLeft: Boolean = false, val trimRight: Boolean = false) : Node()
    data class If(val condition: ConditionNode, val children: List<Node>, val elseChildren: List<Node> = emptyList()) : Node()
    data class For(val item: String, val list: ListNode, val children: List<Node>) : Node()
    data class Set(val variable: String, val value: String) : Node()
    data class RaiseException(val message: String) : Node()
}