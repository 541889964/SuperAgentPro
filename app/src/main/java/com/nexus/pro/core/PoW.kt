
package com.nexus.pro.core
object PoW {
    private val RC = longArrayOf(0x1L,0x8082L,-0x7fffffffffff7f76L,-0x7fffffff7fff8000L,0x808bL,0x80000001L,-0x7fffffff7fff7f7fL,-0x7fffffffffff7ff7L,0x8aL,0x88L,0x80008009L,0x8000000aL,0x8000808bL,-0x7fffffffffffff75L,-0x7fffffffffff7f77L,-0x7fffffffffffff7dL,-0x7ffffffffffffffeL,-0x7fffffffffffff80L,0x800aL,-0x7fffffff7ffffff6L,-0x7fffffff7fff7f7fL,-0x7fffffffffff7f80L,0x80000001L,-0x7fffffff7fff7ff8L)
    private val ROT = arrayOf(intArrayOf(0,36,3,41,18),intArrayOf(1,44,10,45,2),intArrayOf(62,6,43,15,61),intArrayOf(28,55,25,21,56),intArrayOf(27,20,39,8,14))
    private fun rotl(x: Long, n: Int): Long { val k = n and 63; return if (k == 0) x else (x shl k) or (x ushr (64 - k)) }
    private fun keccakF23(A: Array<LongArray>) {
        for (r in 1..23) {
            val C = LongArray(5); val D = LongArray(5)
            for (x in 0..4) C[x] = A[x][0] xor A[x][1] xor A[x][2] xor A[x][3] xor A[x][4]
            for (x in 0..4) D[x] = C[(x + 4) % 5] xor rotl(C[(x + 1) % 5], 1)
            for (x in 0..4) for (y in 0..4) A[x][y] = A[x][y] xor D[x]
            val B = Array(5) { LongArray(5) }
            for (x in 0..4) for (y in 0..4) B[y][(2 * x + 3 * y) % 5] = rotl(A[x][y], ROT[x][y])
            for (x in 0..4) for (y in 0..4) A[x][y] = B[x][y] xor (B[(x + 1) % 5][y].inv() and B[(x + 2) % 5][y])
            A[0][0] = A[0][0] xor RC[r]
        }
    }
    fun hash(input: String): String {
        val rate = 136; val bytes = input.toByteArray()
        val padded = ByteArray(((bytes.size + 1 + rate - 1) / rate) * rate)
        System.arraycopy(bytes, 0, padded, 0, bytes.size)
        padded[bytes.size] = 0x06; padded[padded.size - 1] = (padded[padded.size - 1].toInt() or 0x80).toByte()
        val A = Array(5) { LongArray(5) }; var off = 0
        while (off < padded.size) {
            for (i in 0 until rate / 8) { var lane = 0L; for (j in 0..7) lane = lane or ((padded[off + i * 8 + j].toLong() and 0xFFL) shl (8 * j)); A[i % 5][i / 5] = A[i % 5][i / 5] xor lane }
            keccakF23(A); off += rate
        }
        val sb = StringBuilder(); for (i in 0..3) { val lane = A[i % 5][i / 5]; for (j in 0..7) sb.append(String.format("%02x", (lane ushr (8 * j)) and 0xFFL)) }
        return sb.toString()
    }
    fun solve(prefix: String, target: String, diff: Int): Int { for (n in 0 until diff) if (hash("$prefix$n") == target) return n; return -1 }
}
