package com.smith.lai.langgraph4j_android_adapter.jinjaparser.data

sealed class ListNode {
    data class Variable(val name: String) : ListNode()
    data class Slice(val list: ListNode, val start: Int?, val end: Int?) : ListNode()
}