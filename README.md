# TX Terminal

<p align="center">
  <b>An Android terminal emulator with PTY-backed shell execution</b>
</p>

---

## Overview

TX Terminal is an Android terminal emulator built with:
- **Kotlin + Jetpack Compose** for the UI layer
- **C++** for the core terminal engine (PTY, ANSI parser, screen buffer)
- **Android Canvas API** for reliable text rendering
- **JNI** for bridging between Kotlin and C++

## Current Status

### What Works (Implemented)

| Feature | Status | Notes |
|---------|--------|-------|
| PTY-backed shell | ✅ Working | Spawns `/system/bin/sh` with proper terminal setup |
| ANSI/VT100 parsing | ✅ Working | Comprehensive escape sequence support |
| Screen buffer | ✅ Working | Full terminal emulation with scrollback |
| Text rendering | ✅ Working | Canvas-based renderer (primary) |
| Keyboard input | ✅ Working | Proper Android keycode mapping |
| Soft keyboard | ✅ Working | InputConnection-based, reliable |
| Cursor | ✅ Working | Visible with blinking |
| Session management | ✅ Working | Multiple tabs, create/close/switch |
| Copy/Paste | ✅ Working | Clipboard integration |
| Resize | ✅ Working | Terminal resizes with window |
| Colors | ✅ Working | ANSI 256-color and true color support |

### What's Experimental / Incomplete

| Feature | Status | Notes |
|---------|--------|-------|
| GL renderer | ⚠️ Experimental | Glyph rasterization stubbed, not functional |
| Font atlas | ⚠️ Stubbed | FreeType integration not complete |
| Selection | ⚠️ Partial | Basic selection works, visual feedback limited |
| App-private runtime | ⚠️ Scaffolded | Currently uses `/system/bin/sh` only |
| Package management | ❌ Not started | No apt/pkg support |

### Renderer Architecture

**Primary Renderer: Canvas 2D**
- Uses Android's `Canvas.drawText()` API
- Reliable, visible, hardware-accelerated
- Proper monospace font rendering
- Handles all text output correctly

**Experimental Renderer: OpenGL ES**
- Located in `src/renderer.cpp`
- Currently incomplete - glyph atlas is stubbed
- Not used for actual rendering
- Kept for future development

## Building

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34
- NDK r25.2.9519653 (pinned in build.gradle)
- CMake 3.22+
- JDK 17

### Build Instructions

```bash
cd android
./gradlew assembleDebug
```

The APK will be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    ANDROID UI (Kotlin)                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │  Jetpack     │  │  Terminal    │  │  Settings/Prefs  │  │
│  │  Compose     │  │  ViewModel   │  │  (DataStore)     │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
│           │              │                                   │
│  ┌────────┴──────────────┴────────────────────────────────┐ │
│  │              TerminalSurface (Canvas renderer)          │ │
│  │         PRIMARY: Android Canvas 2D API                  │ │
│  │         (reliable, visible, working)                    │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────┴───────────────────────────────┐
│                      JNI BRIDGE (C++)                       │
│         (session management, PTY, screen buffer)            │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────┴───────────────────────────────┐
│                     CORE ENGINE (C++)                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────────┐  │
│  │   PTY    │  │  Parser  │  │  Screen  │  │  Renderer  │  │
│  │(/dev/ptm)│  │(ANSI/VTx)│  │  Buffer  │  │ (GL stub)  │  │
│  └──────────┘  └──────────┘  └──────────┘  └────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## Project Structure

```
TX/
├── android/
│   └── app/src/main/
│       ├── java/com/tx/terminal/
│       │   ├── ui/           # Jetpack Compose UI
│       │   ├── data/         # Session management
│       │   └── jni/          # JNI bridge
│       └── cpp/              # Native code
│           ├── tx_jni.cpp    # JNI implementation
│           └── CMakeLists.txt
├── src/                      # Shared C++ core
│   ├── pty.cpp               # PTY implementation
│   ├── parser.cpp            # ANSI parser
│   ├── screen.cpp            # Screen buffer
│   ├── terminal.cpp          # Terminal orchestration
│   └── renderer.cpp          # GL renderer (experimental)
└── include/tx/               # C++ headers
```

## Runtime / Environment

**Current implementation:**
- Shell: `/system/bin/sh` (Android system shell)
- Environment: Minimal (`TERM=xterm-256color`, `ANDROID=1`)
- Home: Not set (uses system default)
- PATH: Inherited from parent

**Future direction:**
- App-private HOME directory
- App-private TMP directory
- Extended PATH with bundled tools
- Optional BusyBox integration

## Key Mappings

TX uses proper Android keycode mapping:

| Key | Sequence |
|-----|----------|
| Arrow Up | `\x1b[A` or `\x1b[1;5A` (Ctrl) |
| Arrow Down | `\x1b[B` or `\x1b[1;5B` (Ctrl) |
| Arrow Right | `\x1b[C` or `\x1b[1;5C` (Ctrl) |
| Arrow Left | `\x1b[D` or `\x1b[1;5D` (Ctrl) |
| Home | `\x1b[H` or `\x1b[1;5H` (Ctrl) |
| End | `\x1b[F` or `\x1b[1;5F` (Ctrl) |
| Page Up | `\x1b[5~` or `\x1b[5;5~` (Ctrl) |
| Page Down | `\x1b[6~` or `\x1b[6;5~` (Ctrl) |
| Delete | `\x1b[3~` |
| Backspace | `\x7f` or `\x08` (Ctrl) |
| Tab | `\t` or `\x1b[Z` (Shift) |
| Enter | `\r` |
| Escape | `\x1b` |
| Ctrl+A-Z | `\x01` through `\x1a` |

## Comparison with Termux

TX is inspired by Termux but has different goals:

| Aspect | TX | Termux |
|--------|-----|--------|
| Architecture | Kotlin-first, modern Android | Java-heavy, older patterns |
| Rendering | Canvas 2D (reliable) | Native View (mature) |
| Package ecosystem | None (uses system shell) | Full apt/pkg ecosystem |
| Maturity | Early, experimental | Mature, production-ready |
| Use case | Terminal foundation, learning | Full Linux environment |

**Termux is more mature** for users wanting a complete Linux environment.
**TX is a cleaner foundation** for terminal emulator development.

## License

```
MIT License

Copyright (c) 2024 TX Terminal Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Acknowledgments

- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern Android UI toolkit
- [Termux](https://termux.dev/) - Inspiration for terminal functionality

