
package com.nexus.pro.core
object PoW {
    init { System.loadLibrary("pow") }
    private external fun solveNative(prefix: String, target: String, diff: Int): Int
    fun solve(prefix: String, target: String, diff: Int): Int = solveNative(prefix, target, diff)
}
