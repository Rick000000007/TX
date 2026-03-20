#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <memory>
#include <thread>
#include <atomic>
#include <mutex>
#include <queue>
#include <string>
#include <unordered_map>

#include "tx/terminal.hpp"
#include "tx/config.hpp"

#define LOG_TAG "TX_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace tx;

/**
 * TX Terminal - JNI Bridge
 * 
 * ARCHITECTURE NOTE:
 * The native OpenGL ES renderer in this file is currently EXPERIMENTAL.
 * It lacks proper glyph rasterization (FreeType integration is stubbed).
 * 
 * The PRIMARY renderer is the Canvas-based implementation in TerminalSurface.kt,
 * which provides reliable, visible terminal output.
 * 
 * This JNI layer still provides:
 * - PTY management
 * - ANSI parser
 * - Screen buffer management
 * - Basic GL context setup (for future use)
 */

// Terminal instance with optional EGL context
struct TerminalInstance {
    std::unique_ptr<Terminal> terminal;
    std::thread reader_thread;
    std::atomic<bool> running{false};
    std::atomic<bool> should_exit{false};
    
    // EGL (optional, for experimental GL renderer)
    EGLDisplay egl_display = EGL_NO_DISPLAY;
    EGLSurface egl_surface = EGL_NO_SURFACE;
    EGLContext egl_context = EGL_NO_CONTEXT;
    ANativeWindow* native_window = nullptr;
    bool egl_initialized = false;
    
    // Thread safety
    std::mutex render_mutex;
    std::mutex input_mutex;
    std::mutex lifecycle_mutex;
    
    // Input queue
    std::queue<std::string> input_queue;
    
    // Exit code
    std::atomic<int> exit_code{-1};
    
    ~TerminalInstance() {
        cleanup();
    }
    
    void cleanup() {
        std::lock_guard<std::mutex> lock(lifecycle_mutex);
        
        running = false;
        should_exit = true;
        
        if (reader_thread.joinable()) {
            reader_thread.join();
        }
        
        if (terminal) {
            terminal->shutdown();
            terminal.reset();
        }
        
        cleanupEGL();
    }
    
    void cleanupEGL() {
        if (!egl_initialized) return;
        
        if (egl_surface != EGL_NO_SURFACE) {
            eglMakeCurrent(egl_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            eglDestroySurface(egl_display, egl_surface);
            egl_surface = EGL_NO_SURFACE;
        }
        
        if (egl_context != EGL_NO_CONTEXT) {
            eglDestroyContext(egl_display, egl_context);
            egl_context = EGL_NO_CONTEXT;
        }
        
        if (egl_display != EGL_NO_DISPLAY) {
            eglTerminate(egl_display);
            egl_display = EGL_NO_DISPLAY;
        }
        
        if (native_window) {
            ANativeWindow_release(native_window);
            native_window = nullptr;
        }
        
        egl_initialized = false;
    }
    
    bool initializeEGL(ANativeWindow* window) {
        // NOTE: EGL initialization is currently disabled as the GL renderer
        // is experimental. The Canvas-based renderer in Kotlin is the primary
        // rendering path. This function is kept for future GL renderer work.
        
        LOGI("EGL initialization skipped - using Canvas renderer");
        
        // Store the window reference for potential future use
        native_window = window;
        ANativeWindow_acquire(native_window);
        
        // Return true to indicate the surface is "attached" even though
        // we're not using GL rendering
        return true;
    }
    
    void render() {
        // NOTE: Rendering is handled by the Canvas-based renderer in Kotlin.
        // This function is a no-op for the native GL renderer (which is experimental).
        std::lock_guard<std::mutex> lock(render_mutex);
        
        // The native GL renderer would render here if enabled.
        // For now, all rendering happens in TerminalSurface.kt using Canvas API.
    }
};

// Global map of terminal instances
static std::mutex instances_mutex;
static std::unordered_map<jlong, std::unique_ptr<TerminalInstance>> instances;
static jlong next_handle = 1;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_tx_terminal_jni_NativeTerminal_create(
    JNIEnv* env,
    jclass clazz,
    jint columns,
    jint rows,
    jstring shell_path,
    jstring initial_command
) {
    LOGD("Creating terminal: %dx%d", columns, rows);
    
    auto instance = std::make_unique<TerminalInstance>();
    
    // Get shell path
    const char* shell_str = env->GetStringUTFChars(shell_path, nullptr);
    if (!shell_str) {
        LOGE("Failed to get shell path string");
        return 0;
    }
    std::string shell(shell_str);
    env->ReleaseStringUTFChars(shell_path, shell_str);
    
    // Get initial command if provided
    std::string init_cmd;
    if (initial_command) {
        const char* cmd_str = env->GetStringUTFChars(initial_command, nullptr);
        if (cmd_str) {
            init_cmd = cmd_str;
            env->ReleaseStringUTFChars(initial_command, cmd_str);
        }
    }
    
    // Configure terminal
    TerminalConfig config;
    config.cols = columns;
    config.rows = rows;
    config.shell = shell;
    config.env.push_back("TERM=xterm-256color");
    config.env.push_back("COLORTERM=truecolor");
    config.env.push_back("ANDROID=1");
    
    // Create terminal
    instance->terminal = std::make_unique<Terminal>();
    
    if (!instance->terminal->initialize(config)) {
        LOGE("Failed to initialize terminal");
        return 0;
    }
    
    instance->running = true;
    
    // Start reader thread
    instance->reader_thread = std::thread([instance_ptr = instance.get()]() {
        while (instance_ptr->running && !instance_ptr->should_exit) {
            if (instance_ptr->terminal) {
                instance_ptr->terminal->update();
                
                // Check if process exited
                if (!instance_ptr->terminal->isRunning()) {
                    instance_ptr->exit_code = 0;
                    instance_ptr->running = false;
                    break;
                }
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    });
    
    // Store instance
    jlong handle = next_handle++;
    {
        std::lock_guard<std::mutex> lock(instances_mutex);
        instances[handle] = std::move(instance);
    }
    
    LOGD("Terminal created with handle: %ld", handle);
    return handle;
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_jni_NativeTerminal_destroy(
    JNIEnv* env,
    jclass clazz,
    jlong handle
) {
    LOGD("Destroying terminal: %ld", handle);
    
    std::lock_guard<std::mutex> lock(instances_mutex);
    auto it = instances.find(handle);
    if (it != instances.end()) {
        it->second->cleanup();
        instances.erase(it);
    }
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_jni_NativeTerminal_setSurface(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jobject surface
) {
    std::lock_guard<std::mutex> lock(instances_mutex);
    auto it = instances.find(handle);
    if (it == instances.end()) {
        LOGE("Invalid handle: %ld", handle);
        return;
    }
    
    auto& instance = it->second;
    
    // Get native window
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        LOGE("Failed to get native window");
        return;
    }
    
    // Initialize EGL (currently a no-op, see note above)
    if (!instance->initializeEGL(window)) {
        ANativeWindow_release(window);
        return;
    }
    
    // Get window size and resize terminal
    int width = ANativeWindow_getWidth(window);
    int height = ANativeWindow_getHeight(window);
    
    if (instance->terminal) {
        instance->terminal->onResize(width, height);
    }
    
    LOGD("Surface set: %dx%d", width, height);
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_jni_NativeTerminal_destroySurface(
    JNIEnv* env,
    jclass clazz,
    jlong handle
) {
    std::lock_guard<std::mutex> lock(instances_mutex);
    auto it = instances.find(handle);
    if (it != instances.end()) {
        it->second->cleanupEGL();
    }
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_jni_NativeTerminal_render(
    JNIEnv* env,
    jclass clazz,
    jlong handle
) {
    std::lock_guard<std::mutex> lock(instances_mutex);
    auto it = instances.find(handle);
    if (it != instances.end()) {
        it->second->render();
    }
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_jni_NativeTerminal_resize(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jint columns,
    jint rows
) {
    std::lock_guard<std::mutex> lock(instances_mutex);
    auto it = instances.find(handle);
    if (it != instances.end() && it->second->terminal) {
        it->second->terminal->getPTY().resize(columns, rows);
    }
}

/**
 * Send key event to terminal
 * 
 * NOTE: Key mapping is primarily handled in Kotlin (TerminalSurface.kt)
 * for proper Android keycode translation. This native function receives
 * pre-translated key codes for any additional native handling.
 */
JNIEXPORT void JNICALL
Java_com_tx_terminal_jni_NativeTerminal_sendKey(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jint key_code,
    jint modifiers,
    jboolean pressed
) {
    if (!pressed) return;  // Only handle key down for now
    
    std::lock_guard<std::mutex> lock(instances_mutex);
    auto it = instances.find(handle);
    if (it == instances.end() || !it->second->terminal) {
        return;
    }
    
    auto& terminal = it->second->terminal;
    
    // Convert modifier flags
    int mods = 0;
    if (modifiers & 1) mods |= 1;  // Shift
    if (modifiers & 2) mods |= 2;  // Ctrl
    if (modifiers & 4) mods |= 4;  // Alt
    if (modifiers & 8) mods |= 8;  // Meta
    
    terminal->onKey(key_code, mods, true);
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_jni_NativeTerminal_sendChar(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jint codepoint
) {
    std::lock_guard<std::mutex> lock(instances_mutex);
    auto it = instances.find(handle);
    if (it != instances.end() && it->second->terminal) {
        it->second->terminal->onChar(static_cast<char32_t>(codepoint));
    }
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_jni_NativeTerminal_sendText(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jstring text
) {
    std::lock_guard<std::mutex> lock(instances_mutex);
    auto it = instances.find(handle);
    if (it == instances.end() || !it->second->terminal) {
        return;
    }
    
    const char* text_str = env->GetStringUTFChars(text, nullptr);
    if (!text_str) {
        LOGE("Failed to get text string");
        return;
    }
    std::string input(text_str);
    env->ReleaseStringUTFChars(text, text_str);
    
    it->second->terminal->sendText(input);
}

JNIEXPORT jstring JNICALL
Java_com_tx_terminal_jni_NativeTerminal_getScreenContent(
    JNIEnv* env,
    jclass clazz,
    jlong handle
) {
    std::lock_guard<std::mutex> lock(instances_mutex);
    auto it = instances.find(handle);
    if (it == instances.end() || !it->second->terminal) {
        return env->NewStringUTF("");
    }
    
    // Get screen content
    const auto& screen = it->second->terminal->getScreen();
    std::string content;
    
    for (int row = 0; row < screen.rows(); ++row) {
        const auto* line = screen.getRow(row);
        if (!line) continue;
        
        for (int col = 0; col < screen.cols(); ++col) {
            if (!line[col].empty()) {
                char32_t cp = line[col].codepoint;
                // Convert to UTF-8
                if (cp < 0x80) {
                    content += static_cast<char>(cp);
                } else if (cp < 0x800) {
                    content += static_cast<char>(0xC0 | (cp >> 6));
                    content += static_cast<char>(0x80 | (cp & 0x3F));
                } else if (cp < 0x10000) {
                    content += static_cast<char>(0xE0 | (cp >> 12));
                    content += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
                    content += static_cast<char>(0x80 | (cp & 0x3F));
                } else {
                    content += static_cast<char>(0xF0 | (cp >> 18));
                    content += static_cast<char>(0x80 | ((cp >> 12) & 0x3F));
                    content += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
                    content += static_cast<char>(0x80 | (cp & 0x3F));
                }
            }
        }
        content += '\n';
    }
    
    return env->NewStringUTF(content.c_str());
}

JNIEXPORT jint JNICALL
Java_com_tx_terminal_jni_NativeTerminal_getColumns(
    JNIEnv* env,
    jclass clazz,
    jlong handle
) {
    std::lock_guard<std::mutex> lock(instances_mutex);
    auto it = instances.find(handle);
    if (it != instances.end() && it->second->terminal) {
        return it->second->terminal->getScreen().cols();
    }
    return 80;
}

JNIEXPORT jint JNICALL
Java_com_tx_terminal_jni_NativeTerminal_getRows(
    JNIEnv* env,
    jclass clazz,
    jlong handle
) {
    std::lock_guard<std::mutex> lock(instances_mutex);
    auto it = instances.find(handle);
    if (it != instances.end() && it->second->terminal) {
        return it->second->terminal->getScreen().rows();
    }
    return 24;
}

JNIEXPORT jboolean JNICALL
Java_com_tx_terminal_jni_NativeTerminal_isRunning(
    JNIEnv* env,
    jclass clazz,
    jlong handle
) {
    std::lock_guard<std::mutex> lock(instances_mutex);
    auto it = instances.find(handle);
    if (it != instances.end()) {
        return it->second->running.load() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_tx_terminal_jni_NativeTerminal_getExitCode(
    JNIEnv* env,
    jclass clazz,
    jlong handle
) {
    std::lock_guard<std::mutex> lock(instances_mutex);
    auto it = instances.find(handle);
    if (it != instances.end()) {
        return it->second->exit_code.load();
    }
    return -1;
}

JNIEXPORT jstring JNICALL
Java_com_tx_terminal_jni_NativeTerminal_copySelection(
    JNIEnv* env,
    jclass clazz,
    jlong handle
) {
    std::lock_guard<std::mutex> lock(instances_mutex);
    auto it = instances.find(handle);
    if (it != instances.end() && it->second->terminal) {
        std::string text = it->second->terminal->getScreen().getSelectedText();
        return env->NewStringUTF(text.c_str());
    }
    return env->NewStringUTF("");
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_jni_NativeTerminal_setSelection(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jint start_col,
    jint start_row,
    jint end_col,
    jint end_row
) {
    std::lock_guard<std::mutex> lock(instances_mutex);
    auto it = instances.find(handle);
    if (it != instances.end() && it->second->terminal) {
        it->second->terminal->getScreen().setSelection(start_col, start_row, end_col, end_row);
    }
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_jni_NativeTerminal_clearSelection(
    JNIEnv* env,
    jclass clazz,
    jlong handle
) {
    std::lock_guard<std::mutex> lock(instances_mutex);
    auto it = instances.find(handle);
    if (it != instances.end() && it->second->terminal) {
        it->second->terminal->getScreen().clearSelection();
    }
}

JNIEXPORT jstring JNICALL
Java_com_tx_terminal_jni_NativeTerminal_getVersion(
    JNIEnv* env,
    jclass clazz
) {
    return env->NewStringUTF(tx::VERSION);
}

} // extern "C"
