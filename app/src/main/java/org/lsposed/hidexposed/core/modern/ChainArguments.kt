package org.lsposed.hidexposed.core

import java.lang.reflect.Executable

/** Mutable view of modern chain arguments; element writes affect the next chain call. */
internal class ChainArguments(
    args: List<Any?>,
    private val executable: Executable
) : AbstractMutableList<Any?>() {
    private val values: MutableList<Any?> = ArrayList(args)
    override val size: Int get() = values.size

    override fun get(index: Int): Any? = values[index]

    override fun set(index: Int, element: Any?): Any? = values.set(index, element)

    override fun add(index: Int, element: Any?) {
        throw UnsupportedOperationException("Cannot change the argument count of $executable")
    }

    override fun removeAt(index: Int): Any? {
        throw UnsupportedOperationException("Cannot change the argument count of $executable")
    }
}
