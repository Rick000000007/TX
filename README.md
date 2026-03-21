# TX Terminal

<p align="center">
  <b>A high-performance terminal emulator for Android</b>
</p>

<p align="center">
  <a href="https://github.com/Rick000000007/TX/actions"><img src="https://github.com/Rick000000007/TX/workflows/Android%20Debug%20APK/badge.svg" alt="Build Status"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License"></a>
</p>

---

## Overview

TX Terminal is a modern Android terminal emulator built with:
- **Kotlin + Jetpack Compose** for the UI layer
- **C++** for the core terminal engine
- **JNI** for bridging between Kotlin and C++
- **OpenGL ES 3.0** for hardware-accelerated rendering

## Features

- **Modern UI**: Built with Jetpack Compose for a smooth, native Android experience
- **Multiple Sessions**: Tab-based interface for managing multiple terminals
- **Full Terminal Support**: Complete ANSI/VT100 escape sequence support
- **Customizable**: Fonts, colors, and behavior settings
- **Extra Keys Bar**: Quick access to special keys (Ctrl, arrows, etc.)
- **Copy/Paste**: Full clipboard integration
- **Performance**: Native C++ core with OpenGL ES rendering
- **Lightweight**: Minimal resource usage

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        ANDROID UI (Kotlin)                      │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────────┐  │
│  │  Jetpack    │  │  Terminal   │  │  Settings/Preferences   │  │
│  │  Compose    │  │  ViewModel  │  │  (DataStore)            │  │
│  └────────┘────└────────┘───└──────────────────────────┘  │
│           │              │                                      │
│  ┌────────└────────────────────────────────────────────────────────────┐ │
│  │                    JNI Bridge (C++)                         │ │
│  └─────────────────────────┘───────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────────────────────┐
│                      CORE ENGINE (C++)                          │
│  ┌─────────────┐  ┌────────┐  ┌─────────────┐  ┌────────┐ │
│  │   PTY       │  │   Parser   │  │   Screen    │  │Renderer │ │
│  │  (/dev/ptmx)│  │(ANSI/VT100)│  │   Buffer    │  │ (GLES3) │ │
│  └─────────────┘  └────────┘  └─────────────┘  └────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

## Building

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34
- NDK r25 or newer
- CMake 3.22+
- JDK 17

### Build Instructions

1. Clone the repository:
```bash
git clone https://github.com/Rick000000007/TX.git
cd TX/android
```

2. Build the debug APK:
```bash
./gradlew assembleDebug
```

3. The APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
TX/
├─── android/                    # Android app module
│   ├─── app/
│   │   ├─── src/main/
│   │   │   ├─── java/com/tx/terminal/
│   │   │   │   ├─── ui/         # Jetpack Compose UI
│   │   │   │   ├─── data/       # Data models and preferences
│   │   │   │   ├─── jni/        # JNI bridge
│   │   │   │   └─── viewmodel/  # ViewModels
│   │   │   ├─── cpp/            # Native C++ code
│   │   │   └─── res/            # Android resources
│   │   └─── build.gradle        # App build configuration
│   └─── build.gradle            # Project build configuration
├─── src/                        # Shared C++ core
│   ├─── pty.cpp                 # PTY implementation
│   ├─── parser.cpp              # ANSI parser
│   ├─── screen.cpp              # Screen buffer
│   ├─── renderer.cpp            # OpenGL renderer
│   ├─── terminal.cpp            # Terminal orchestration
│   └─── config.cpp              # Configuration
├─── include/tx/                 # C++ headers
│   ├─── common.hpp
│   ├─── pty.hpp
│   ├─── parser.hpp
│   ├─── screen.hpp
│   ├─── renderer.hpp
│   └─── terminal.hpp
└─── tests/                      # Unit tests
    ├─── test_parser.cpp
    ├─── test_screen.cpp
    └─── test_pty.cpp
```

## Configuration

TX Terminal can be customized through the Settings UI:

| Setting | Description | Default |
|---------|-------------|---------|
| Font Size | Terminal font size in sp | 14 |
| Background Color | Terminal background color | Black |
| Foreground Color | Terminal text color | White |
| Cursor Color | Cursor color | White |
| Cursor Blink | Enable cursor blinking | false |
| Show Extra Keys | Show extra keys bar | true |
| Vibrate on Keypress | Haptic feedback | true |
| Scrollback Lines | Number of scrollback lines | 10000 |

## Key Bindings

### Extra Keys Bar
- **ESC**: Escape key
- **TAB**: Tab key
- **Arrows**: Directional navigation
- **HOME/END**: Line navigation
- **PGUP/PGDN**: Page navigation
- **^C**: Send SIGINT (Ctrl+C)
- **^D**: Send EOF (Ctrl+D)
- **^Z**: Suspend process

### Gestures
- **Tap**: Show keyboard
- **Long press**: Context menu
- **Swipe up/down**: Scroll through history

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
