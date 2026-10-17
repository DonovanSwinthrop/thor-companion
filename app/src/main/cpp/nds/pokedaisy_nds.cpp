// DS games: melonDS's core behind a small JNI surface for NdsCore.kt.
//
// One NDS at a time, driven from the emulation thread: load a ROM (+ its save), run a frame (both screens'
// pixels out), read the audio it made, feed keys and the touch screen. melonDS calls back through its
// Platform layer (below) for files, threads, logs and save writes; everything a phone game doesn't need
// (multiplayer, Wi-Fi, camera, mic, add-ons) is a no-op. The free BIOS and generated firmware boot retail
// games directly, so no BIOS files are needed.

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <ctime>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "NDS.h"
#include "NDSCart.h"
#include "GPU_Soft.h"
#include "Platform.h"
#include "SPU.h"
#include "RTC.h"
#include "Savestate.h"

using namespace melonDS;

#define TAG "pokedaisy/nds"

namespace {
std::unique_ptr<NDS> g_nds;
std::mutex g_lock;                 // around g_nds: JNI calls come from the emu thread and the UI thread (saves)
std::string g_savePath;
std::vector<u8> g_save;            // the save as the game last wrote it
std::atomic<bool> g_saveDirty{false};
u32 g_keys = 0xFFF;                // melonDS's mask: a bit cleared = pressed
}

// ---------------------------------------------------------------------------------------------------------
// melonDS's Platform layer
// ---------------------------------------------------------------------------------------------------------
namespace melonDS::Platform {

void SignalStop(StopReason, void*) {}

static const char* modeString(FileMode mode, bool exists) {
    bool r = mode & FileMode::Read, w = mode & FileMode::Write, app = mode & FileMode::Append;
    if (app) return r ? "a+b" : "ab";
    if (r && w) return (exists && (mode & FileMode::Preserve)) ? "r+b" : "w+b";
    if (w) return "wb";
    return "rb";
}

FileHandle* OpenFile(const std::string& path, FileMode mode) {
    if ((mode & (FileMode::ReadWrite | FileMode::Append)) == FileMode::None) return nullptr;
    bool exists = FileExists(path);
    if ((mode & FileMode::NoCreate) && !exists) return nullptr;
    return reinterpret_cast<FileHandle*>(std::fopen(path.c_str(), modeString(mode, exists)));
}
std::string GetLocalFilePath(const std::string& filename) { return filename; }
FileHandle* OpenLocalFile(const std::string& path, FileMode mode) { return OpenFile(path, mode); }
bool FileExists(const std::string& name) {
    FILE* f = std::fopen(name.c_str(), "rb");
    if (!f) return false;
    std::fclose(f);
    return true;
}
bool LocalFileExists(const std::string& name) { return FileExists(name); }
bool CheckFileWritable(const std::string& filepath) {
    FILE* f = std::fopen(filepath.c_str(), "ab");
    if (!f) return false;
    std::fclose(f);
    return true;
}
bool CheckLocalFileWritable(const std::string& filepath) { return CheckFileWritable(filepath); }
static FILE* F(FileHandle* h) { return reinterpret_cast<FILE*>(h); }
bool CloseFile(FileHandle* file) { return std::fclose(F(file)) == 0; }
bool IsEndOfFile(FileHandle* file) { return std::feof(F(file)) != 0; }
bool FileReadLine(char* str, int count, FileHandle* file) { return std::fgets(str, count, F(file)) != nullptr; }
u64 FilePosition(FileHandle* file) { return (u64) std::ftell(F(file)); }
bool FileSeek(FileHandle* file, s64 offset, FileSeekOrigin origin) {
    int o = origin == FileSeekOrigin::Start ? SEEK_SET : origin == FileSeekOrigin::Current ? SEEK_CUR : SEEK_END;
    return std::fseek(F(file), (long) offset, o) == 0;
}
void FileRewind(FileHandle* file) { std::rewind(F(file)); }
u64 FileRead(void* data, u64 size, u64 count, FileHandle* file) { return std::fread(data, size, count, F(file)); }
bool FileFlush(FileHandle* file) { return std::fflush(F(file)) == 0; }
u64 FileWrite(const void* data, u64 size, u64 count, FileHandle* file) { return std::fwrite(data, size, count, F(file)); }
u64 FileWriteFormatted(FileHandle* file, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    int n = std::vfprintf(F(file), fmt, args);
    va_end(args);
    return n < 0 ? 0 : (u64) n;
}
u64 FileLength(FileHandle* file) {
    long pos = std::ftell(F(file));
    std::fseek(F(file), 0, SEEK_END);
    long len = std::ftell(F(file));
    std::fseek(F(file), pos, SEEK_SET);
    return (u64) len;
}

void Log(LogLevel level, const char* fmt, ...) {
    int prio = level == LogLevel::Error ? ANDROID_LOG_ERROR : level == LogLevel::Warn ? ANDROID_LOG_WARN
             : level == LogLevel::Info ? ANDROID_LOG_INFO : ANDROID_LOG_DEBUG;
    if (prio == ANDROID_LOG_DEBUG) return;
    va_list args;
    va_start(args, fmt);
    __android_log_vprint(prio, TAG, fmt, args);
    va_end(args);
}

struct Thread { std::thread t; };
Thread* Thread_Create(std::function<void()> func) { return new Thread{std::thread(std::move(func))}; }
void Thread_Free(Thread* thread) {
    if (thread->t.joinable()) thread->t.detach();
    delete thread;
}
void Thread_Wait(Thread* thread) { if (thread->t.joinable()) thread->t.join(); }

struct Semaphore {
    std::mutex m;
    std::condition_variable cv;
    int count = 0;
};
Semaphore* Semaphore_Create() { return new Semaphore(); }
void Semaphore_Free(Semaphore* sema) { delete sema; }
void Semaphore_Reset(Semaphore* sema) {
    std::lock_guard<std::mutex> l(sema->m);
    sema->count = 0;
}
void Semaphore_Wait(Semaphore* sema) {
    std::unique_lock<std::mutex> l(sema->m);
    sema->cv.wait(l, [sema] { return sema->count > 0; });
    sema->count--;
}
bool Semaphore_TryWait(Semaphore* sema, int timeout_ms) {
    std::unique_lock<std::mutex> l(sema->m);
    if (timeout_ms == 0) {
        if (sema->count <= 0) return false;
    } else if (!sema->cv.wait_for(l, std::chrono::milliseconds(timeout_ms), [sema] { return sema->count > 0; })) {
        return false;
    }
    sema->count--;
    return true;
}
void Semaphore_Post(Semaphore* sema, int count) {
    {
        std::lock_guard<std::mutex> l(sema->m);
        sema->count += count;
    }
    sema->cv.notify_all();
}

struct Mutex { std::mutex m; };
Mutex* Mutex_Create() { return new Mutex(); }
void Mutex_Free(Mutex* mutex) { delete mutex; }
void Mutex_Lock(Mutex* mutex) { mutex->m.lock(); }
void Mutex_Unlock(Mutex* mutex) { mutex->m.unlock(); }
bool Mutex_TryLock(Mutex* mutex) { return mutex->m.try_lock(); }

void Sleep(u64 usecs) { std::this_thread::sleep_for(std::chrono::microseconds(usecs)); }
u64 GetMSCount() {
    return (u64) std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
}
u64 GetUSCount() {
    return (u64) std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

// The game wrote its save: keep a copy, written to the file by ndsFlushSave (the emu thread, every few seconds
// and on pause / close) - never mid-frame.
void WriteNDSSave(const u8* savedata, u32 savelen, u32, u32, void*) {
    g_save.assign(savedata, savedata + savelen);
    g_saveDirty = true;
}
void WriteGBASave(const u8*, u32, u32, u32, void*) {}
void WriteFirmware(const Firmware&, u32, u32, void*) {}
void WriteDateTime(int, int, int, int, int, int, void*) {}

void MP_Begin(void*) {}
void MP_End(void*) {}
int MP_SendPacket(u8*, int, u64, void*) { return 0; }
int MP_RecvPacket(u8*, u64*, void*) { return 0; }
int MP_SendCmd(u8*, int, u64, void*) { return 0; }
int MP_SendReply(u8*, int, u64, u16, void*) { return 0; }
int MP_SendAck(u8*, int, u64, void*) { return 0; }
int MP_RecvHostPacket(u8*, u64*, void*) { return 0; }
u16 MP_RecvReplies(u8*, u64, u16, void*) { return 0; }
int Net_SendPacket(u8*, int, void*) { return 0; }
int Net_RecvPacket(u8*, void*) { return 0; }

void Camera_Start(int, void*) {}
void Camera_Stop(int, void*) {}
void Camera_CaptureFrame(int, u32* frame, int width, int height, bool, void*) {
    std::memset(frame, 0, (size_t) width * height * 2);
}
void Mic_Start(void*) {}
void Mic_Stop(void*) {}
int Mic_ReadInput(s16*, int, void*) { return 0; }

AACDecoder* AAC_Init() { return nullptr; }
void AAC_DeInit(AACDecoder*) {}
bool AAC_Configure(AACDecoder*, int, int) { return false; }
bool AAC_DecodeFrame(AACDecoder*, const void*, int, void*, int) { return false; }

bool Addon_KeyDown(KeyType, void*) { return false; }
void Addon_RumbleStart(u32, void*) {}
void Addon_RumbleStop(void*) {}
float Addon_MotionQuery(MotionQueryType, void*) { return 0.f; }

// The JIT's fast memory finds ASharedMemory_create in libandroid this way (apps can't open /dev/ashmem)
DynamicLibrary* DynamicLibrary_Load(const char* lib) { return reinterpret_cast<DynamicLibrary*>(dlopen(lib, RTLD_NOW)); }
void DynamicLibrary_Unload(DynamicLibrary* lib) { if (lib) dlclose(lib); }
void* DynamicLibrary_LoadFunction(DynamicLibrary* lib, const char* name) { return lib ? dlsym(lib, name) : nullptr; }

} // namespace melonDS::Platform

// ---------------------------------------------------------------------------------------------------------
// JNI: com.pokedaisy.app.nds.NdsCore
// ---------------------------------------------------------------------------------------------------------
static std::vector<u8> readFile(const std::string& path) {
    std::vector<u8> out;
    FILE* f = std::fopen(path.c_str(), "rb");
    if (!f) return out;
    std::fseek(f, 0, SEEK_END);
    long len = std::ftell(f);
    std::fseek(f, 0, SEEK_SET);
    if (len > 0) {
        out.resize((size_t) len);
        if (std::fread(out.data(), 1, out.size(), f) != out.size()) out.clear();
    }
    std::fclose(f);
    return out;
}

static std::string str(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

static void setClock(NDS& nds) {
    time_t now = time(nullptr);
    struct tm t;
    localtime_r(&now, &t);
    nds.RTC.SetDateTime(t.tm_year + 1900, t.tm_mon + 1, t.tm_mday, t.tm_hour, t.tm_min, t.tm_sec);
}

static bool flushSave() {
    if (!g_saveDirty || g_savePath.empty()) return false;
    g_saveDirty = false;
    std::string tmp = g_savePath + ".tmp";
    FILE* f = std::fopen(tmp.c_str(), "wb");
    if (!f) return false;
    bool ok = std::fwrite(g_save.data(), 1, g_save.size(), f) == g_save.size();
    ok = std::fclose(f) == 0 && ok;
    // write next to it, then swap: a crash mid-write never leaves half a save
    return ok && std::rename(tmp.c_str(), g_savePath.c_str()) == 0;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_pokedaisy_app_nds_NdsCore_load(JNIEnv* env, jclass, jstring jrom, jstring jsave, jboolean jit) {
    std::lock_guard<std::mutex> l(g_lock);
    g_nds.reset();
    g_savePath = str(env, jsave);
    std::vector<u8> rom = readFile(str(env, jrom));
    if (rom.empty()) return JNI_FALSE;
    g_save = readFile(g_savePath);
    g_saveDirty = false;

    NDSArgs args;
    if (!jit) args.JIT = std::nullopt;
    g_nds = std::make_unique<NDS>(std::move(args), nullptr);
    g_nds->SetRenderer(std::make_unique<SoftRenderer>(*g_nds));

    NDSCart::NDSCartArgs cartArgs;
    if (!g_save.empty()) {
        cartArgs.SRAM = std::make_unique<u8[]>(g_save.size());
        std::memcpy(cartArgs.SRAM.get(), g_save.data(), g_save.size());
        cartArgs.SRAMLength = (u32) g_save.size();
    }
    auto romData = std::make_unique<u8[]>(rom.size());
    std::memcpy(romData.get(), rom.data(), rom.size());
    auto cart = NDSCart::ParseROM(std::move(romData), (u32) rom.size(), nullptr, std::move(cartArgs));
    if (!cart) {
        g_nds.reset();
        return JNI_FALSE;
    }
    g_nds->Reset();
    g_nds->SetNDSCart(std::move(cart));
    g_nds->SetupDirectBoot("game.nds");   // the free BIOS can't run the DS menu: boot the game itself
    setClock(*g_nds);
    g_nds->Start();
    g_keys = 0xFFF;
    return JNI_TRUE;
}

// Runs one frame and copies both screens (256x192 each, ARGB) into the arrays.
JNIEXPORT jboolean JNICALL
Java_com_pokedaisy_app_nds_NdsCore_runFrame(JNIEnv* env, jclass, jintArray top, jintArray bottom) {
    std::lock_guard<std::mutex> l(g_lock);
    if (!g_nds) return JNI_FALSE;
    g_nds->SetKeyMask(g_keys);
    g_nds->RunFrame();
    void* t = nullptr;
    void* b = nullptr;
    if (!g_nds->GPU.GetFramebuffers(&t, &b) || !t || !b) return JNI_FALSE;
    const int n = 256 * 192;
    jint* dt = env->GetIntArrayElements(top, nullptr);
    jint* db = env->GetIntArrayElements(bottom, nullptr);
    const u32* st = static_cast<const u32*>(t);
    const u32* sb = static_cast<const u32*>(b);
    for (int i = 0; i < n; i++) {
        dt[i] = (jint) (st[i] | 0xFF000000u);
        db[i] = (jint) (sb[i] | 0xFF000000u);
    }
    env->ReleaseIntArrayElements(top, dt, 0);
    env->ReleaseIntArrayElements(bottom, db, 0);
    return JNI_TRUE;
}

// The audio made so far (48 kHz stereo, interleaved): returns sample frames written.
JNIEXPORT jint JNICALL
Java_com_pokedaisy_app_nds_NdsCore_readAudio(JNIEnv* env, jclass, jshortArray out) {
    std::lock_guard<std::mutex> l(g_lock);
    if (!g_nds) return 0;
    jsize cap = env->GetArrayLength(out) / 2;
    jshort* d = env->GetShortArrayElements(out, nullptr);
    int n = g_nds->SPU.ReadOutput(reinterpret_cast<s16*>(d), cap);
    env->ReleaseShortArrayElements(out, d, 0);
    return n;
}

// Pressed buttons, NdsCore's bits (A B SELECT START RIGHT LEFT UP DOWN R L X Y = bits 0..11).
JNIEXPORT void JNICALL
Java_com_pokedaisy_app_nds_NdsCore_setKeys(JNIEnv*, jclass, jint pressed) {
    g_keys = ~((u32) pressed) & 0xFFF;
}

JNIEXPORT void JNICALL
Java_com_pokedaisy_app_nds_NdsCore_touch(JNIEnv*, jclass, jint x, jint y, jboolean down) {
    std::lock_guard<std::mutex> l(g_lock);
    if (!g_nds) return;
    if (down) g_nds->TouchScreen((u16) std::max(0, std::min(255, (int) x)), (u16) std::max(0, std::min(191, (int) y)));
    else g_nds->ReleaseScreen();
}

JNIEXPORT jboolean JNICALL
Java_com_pokedaisy_app_nds_NdsCore_flushSave(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> l(g_lock);
    return flushSave() ? JNI_TRUE : JNI_FALSE;
}

// Main RAM (4 MB at 0x02000000) for the companion's readers; false outside it.
JNIEXPORT jboolean JNICALL
Java_com_pokedaisy_app_nds_NdsCore_readRam(JNIEnv* env, jclass, jint addr, jbyteArray out) {
    std::lock_guard<std::mutex> l(g_lock);
    if (!g_nds) return JNI_FALSE;
    u32 a = (u32) addr, len = (u32) env->GetArrayLength(out);
    if (a < 0x02000000u || a + len > 0x02400000u) return JNI_FALSE;
    u32 off = (a - 0x02000000u) & g_nds->MainRAMMask;
    if (off + len > g_nds->MainRAMMask + 1) return JNI_FALSE;
    env->SetByteArrayRegion(out, 0, (jsize) len, reinterpret_cast<const jbyte*>(g_nds->MainRAM + off));
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_pokedaisy_app_nds_NdsCore_saveState(JNIEnv* env, jclass, jstring jpath) {
    std::lock_guard<std::mutex> l(g_lock);
    if (!g_nds) return JNI_FALSE;
    Savestate state;
    if (state.Error || !g_nds->DoSavestate(&state) || state.Error) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "save state: the core couldn't write one");
        return JNI_FALSE;
    }
    std::string path = str(env, jpath);
    FILE* f = std::fopen(path.c_str(), "wb");
    if (!f) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "save state: can't open %s", path.c_str());
        return JNI_FALSE;
    }
    bool ok = std::fwrite(state.Buffer(), 1, state.Length(), f) == state.Length();
    return (std::fclose(f) == 0 && ok) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pokedaisy_app_nds_NdsCore_loadState(JNIEnv* env, jclass, jstring jpath) {
    std::lock_guard<std::mutex> l(g_lock);
    if (!g_nds) return JNI_FALSE;
    std::vector<u8> data = readFile(str(env, jpath));
    if (data.empty()) return JNI_FALSE;
    Savestate state(data.data(), (u32) data.size(), false);
    if (state.Error) return JNI_FALSE;
    return g_nds->DoSavestate(&state) && !state.Error ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_pokedaisy_app_nds_NdsCore_close(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> l(g_lock);
    flushSave();
    g_nds.reset();
}

} // extern "C"
