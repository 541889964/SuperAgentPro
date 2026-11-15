
#include <jni.h>
#include <string>
#include <cstdint>
#include <cstring>

static const uint64_t RC[24] = {
    0x0000000000000001ULL,0x0000000000008082ULL,0x800000000000808aULL,0x8000000080008000ULL,
    0x000000000000808bULL,0x0000000080000001ULL,0x8000000080008081ULL,0x8000000000008009ULL,
    0x000000000000008aULL,0x0000000000000088ULL,0x0000000080008009ULL,0x000000008000000aULL,
    0x000000008000808bULL,0x800000000000008bULL,0x8000000000008089ULL,0x8000000000008003ULL,
    0x8000000000008002ULL,0x8000000000000080ULL,0x000000000000800aULL,0x800000008000000aULL,
    0x8000000080008081ULL,0x8000000000008080ULL,0x0000000080000001ULL,0x8000000080008008ULL
};
static const int ROT[5][5] = {{0,36,3,41,18},{1,44,10,45,2},{62,6,43,15,61},{28,55,25,21,56},{27,20,39,8,14}};

inline uint64_t rotl(uint64_t x, int n) {
    n &= 63; return (n == 0) ? x : (x << n) | (x >> (64 - n));
}

void keccakF23(uint64_t A[5][5]) {
    for (int r = 1; r <= 23; ++r) {
        uint64_t C[5], D[5];
        for (int x = 0; x < 5; ++x) C[x] = A[x][0] ^ A[x][1] ^ A[x][2] ^ A[x][3] ^ A[x][4];
        for (int x = 0; x < 5; ++x) D[x] = C[(x + 4) % 5] ^ rotl(C[(x + 1) % 5], 1);
        for (int x = 0; x < 5; ++x) for (int y = 0; y < 5; ++y) A[x][y] ^= D[x];
        uint64_t B[5][5] = {0};
        for (int x = 0; x < 5; ++x) for (int y = 0; y < 5; ++y) B[y][(2 * x + 3 * y) % 5] = rotl(A[x][y], ROT[x][y]);
        for (int x = 0; x < 5; ++x) for (int y = 0; y < 5; ++y) A[x][y] = B[x][y] ^ ((~B[(x + 1) % 5][y]) & B[(x + 2) % 5][y]);
        A[0][0] ^= RC[r];
    }
}

void hash_hex(const std::string& in, char o[65]) {
    const size_t rate = 136; uint64_t A[5][5] = {0}; std::string p = in;
    p.push_back(0x06); while (p.size() % rate != 0) p.push_back('\0'); p.back() |= 0x80;
    for (size_t off = 0; off < p.size(); off += rate) {
        for (int i = 0; i < (int)(rate / 8); ++i) { 
            uint64_t lane = 0; 
            for (int j = 0; j < 8; ++j) lane |= (uint64_t)(uint8_t)p[off + i * 8 + j] << (8 * j); 
            A[i % 5][i / 5] ^= lane; 
        }
        keccakF23(A);
    }
    static const char* hex = "0123456789abcdef"; int pos = 0;
    for (int i = 0; i < 4; ++i) { 
        uint64_t lane = A[i % 5][i / 5]; 
        for (int j = 0; j < 8; ++j) { 
            uint8_t b = (lane >> (8 * j)) & 0xFF; 
            o[pos++] = hex[b >> 4]; o[pos++] = hex[b & 0x0F]; 
        } 
    }
    o[pos] = '\0';
}

extern "C" JNIEXPORT jint JNICALL
Java_com_nexus_pro_core_PoW_solveNative(JNIEnv* env, jobject, jstring prefix, jstring target, jint diff) {
    const char* p = env->GetStringUTFChars(prefix, 0);
    const char* t = env->GetStringUTFChars(target, 0);
    std::string s_prefix(p); std::string s_target(t);
    int result = -1; char b[65];
    for (int n = 0; n < diff * 2; ++n) {
        hash_hex(s_prefix + std::to_string(n), b);
        if (s_target == b) { result = n; break; }
    }
    env->ReleaseStringUTFChars(prefix, p);
    env->ReleaseStringUTFChars(target, t);
    return result;
}
