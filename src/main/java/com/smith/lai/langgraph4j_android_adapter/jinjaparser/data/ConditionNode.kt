package com.smith.lai.langgraph4j_android_adapter.jinjaparser.data

sealed class ConditionNode {
    data class Literal(val value: String) : ConditionNode()
    data class UnaryOp(val op: String, val child: ConditionNode) : ConditionNode()
    data class BinaryOp(val left: ConditionNode, val op: String, val right: ConditionNode) : ConditionNode()
    data class IsDefined(val variable: String) : ConditionNode()
    data class IsNotNone(val variable: String) : ConditionNode()
}