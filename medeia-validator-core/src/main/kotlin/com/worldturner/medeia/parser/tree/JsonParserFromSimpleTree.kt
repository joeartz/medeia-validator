package com.worldturner.medeia.parser.tree

import com.worldturner.medeia.parser.ArrayNode
import com.worldturner.medeia.parser.JsonParserAdapter
import com.worldturner.medeia.parser.JsonTokenData
import com.worldturner.medeia.parser.JsonTokenDataAndLocationConsumer
import com.worldturner.medeia.parser.JsonTokenLocation
import com.worldturner.medeia.parser.JsonTokenType
import com.worldturner.medeia.parser.ObjectNode
import com.worldturner.medeia.parser.SimpleNode
import com.worldturner.medeia.parser.TOKEN_END_ARRAY
import com.worldturner.medeia.parser.TOKEN_END_OBJECT
import com.worldturner.medeia.parser.TOKEN_START_ARRAY
import com.worldturner.medeia.parser.TOKEN_START_OBJECT
import com.worldturner.medeia.parser.TreeNode
import com.worldturner.medeia.pointer.JsonPointer
import com.worldturner.medeia.pointer.JsonPointerBuilder
import java.util.ArrayDeque
import kotlin.reflect.KMutableProperty0

class JsonParserFromSimpleTree(val tree: TreeNode, val consumer: JsonTokenDataAndLocationConsumer) : JsonParserAdapter {
    private val propertyNamesStack = ArrayDeque<MutableSet<String>>()

    inner class DynamicTokenLocation : JsonTokenLocation {
        override val level: Int
            get() = this@JsonParserFromSimpleTree.level
        override val pointer: JsonPointer
            get() = jsonPointerBuilder.toJsonPointer()
        override val propertyNames: Set<String>
            get() = propertyNamesStack.peek() ?: emptySet()
        override val column: Int
            get() = currentNode?.column ?: -1
        override val line: Int
            get() = currentNode?.line ?: -1

        override fun toString(): String {
            return if (line != -1) {
                if (column != -1)
                    "at $line:$column ($pointer)"
                else
                    "at $line ($pointer)"
            } else {
                "at $pointer"
            }
        }
    }

    private val dynamicLocation = DynamicTokenLocation()

    private var level = 0
    private val jsonPointerBuilder = JsonPointerBuilder()
    private var currentNode: TreeNode? = null

    private fun generateEvents(node: TreeNode) {
        ::currentNode.withValue(node) {
            when (node) {
                is SimpleNode -> {
                    jsonPointerBuilder.consume(node.token)
                    consumer.consume(node.token, dynamicLocation)
                }
                is ArrayNode -> {
                    jsonPointerBuilder.consume(TOKEN_START_ARRAY)
                    consumer.consume(TOKEN_START_ARRAY, dynamicLocation)
                    level++
                    node.nodes.forEach {
                        generateEvents(it)
                    }
                    level--
                    jsonPointerBuilder.consume(TOKEN_END_ARRAY)
                    consumer.consume(TOKEN_END_ARRAY, dynamicLocation)
                }
                is ObjectNode -> {
                    jsonPointerBuilder.consume(TOKEN_START_OBJECT)
                    consumer.consume(TOKEN_START_OBJECT, dynamicLocation)
                    level++
                    propertyNamesStack.addFirst(HashSet())
                    node.nodes.forEach {
                        val fieldNameToken = JsonTokenData(JsonTokenType.FIELD_NAME, text = it.key)
                        jsonPointerBuilder.consume(fieldNameToken)
                        consumer.consume(fieldNameToken, dynamicLocation)
                        generateEvents(it.value)
                        propertyNamesStack.peek() += it.key
                    }
                    propertyNamesStack.removeFirst()
                    level--
                    jsonPointerBuilder.consume(TOKEN_END_OBJECT)
                    consumer.consume(TOKEN_END_OBJECT, dynamicLocation)
                }
            }
        }
    }

    override fun parseAll() {
        generateEvents(tree)
    }

    override fun close() {
    }
}

private inline fun <T> KMutableProperty0<T>.withValue(
    assignValue: T,
    action: () -> Unit
) {
    val savedValue = this.get()
    this.set(assignValue)
    try {
        action()
    } finally {
        this.set(savedValue)
    }
}