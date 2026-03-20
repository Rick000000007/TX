#include "tx/terminal.hpp"
#include <sstream>
#include <cstring>

namespace tx {

// Key code constants for terminal input
// These map to Android KeyEvent constants when used from the Android layer
namespace KeyCode {
    // Arrow keys (match Android KeyEvent values)
    constexpr int DPAD_UP = 19;
    constexpr int DPAD_DOWN = 20;
    constexpr int DPAD_LEFT = 21;
    constexpr int DPAD_RIGHT = 22;
    
    // Navigation keys
    constexpr int MOVE_HOME = 122;
    constexpr int MOVE_END = 123;
    constexpr int PAGE_UP = 92;
    constexpr int PAGE_DOWN = 93;
    
    // Editing keys
    constexpr int DEL = 67;           // Backspace
    constexpr int FORWARD_DEL = 112;  // Delete
    constexpr int INSERT = 124;
    
    // Function keys
    constexpr int F1 = 131;
    constexpr int F2 = 132;
    constexpr int F3 = 133;
    constexpr int F4 = 134;
    constexpr int F5 = 135;
    constexpr int F6 = 136;
    constexpr int F7 = 137;
    constexpr int F8 = 138;
    constexpr int F9 = 139;
    constexpr int F10 = 140;
    constexpr int F11 = 141;
    constexpr int F12 = 142;
    
    // Other special keys
    constexpr int TAB = 61;
    constexpr int ENTER = 66;
    constexpr int NUMPAD_ENTER = 160;
    constexpr int ESCAPE = 111;
    constexpr int SPACE = 62;
    
    // Modifier masks
    constexpr int MOD_SHIFT = 1;
    constexpr int MOD_CTRL = 2;
    constexpr int MOD_ALT = 4;
    constexpr int MOD_META = 8;
}

Terminal::Terminal() = default;

Terminal::~Terminal() {
    shutdown();
}

bool Terminal::initialize(const TerminalConfig& config) {
    config_ = config;
    
    // Initialize screen
    screen_.resize(config.cols, config.rows);
    
    // Initialize renderer (note: GL renderer is experimental)
    // The actual rendering is handled by Canvas in the Android layer
    if (!renderer_.initialize(800, 600)) {  // Default size, will be resized
        // Log warning but don't fail - Canvas renderer is primary
        LOGD("GL renderer initialization returned false - using Canvas renderer");
    }
    renderer_.setConfig(config.render);
    
    // Initialize PTY
    if (!pty_.open(config.shell, config.env).success) {
        return false;
    }
    
    // Set PTY callbacks
    pty_.setDataCallback([this](const uint8_t* data, size_t len) {
        parser_.parse(data, len, createParserActions());
    });
    
    pty_.setExitCallback([this](int exit_code) {
        running_ = false;
    });
    
    // Set initial PTY size
    pty_.resize(config.cols, config.rows);
    
    initialized_ = true;
    running_ = true;
    
    return true;
}

void Terminal::shutdown() {
    pty_.close();
    renderer_.shutdown();
    initialized_ = false;
    running_ = false;
}

void Terminal::update() {
    if (!initialized_) return;
    
    // Read from PTY (non-blocking)
    pty_.read();
    
    // Check if child is still running
    if (!pty_.isChildRunning()) {
        running_ = false;
    }
}

void Terminal::render() {
    if (!initialized_) return;
    
    // NOTE: Actual rendering is handled by Canvas in the Android layer.
    // This GL renderer call is for experimental/future use.
    renderer_.render(screen_);
}

void Terminal::onKey(int key, int mods, bool pressed) {
    if (!pressed) return;
    
    std::string seq = keyToSequence(key, mods);
    if (!seq.empty()) {
        pty_.write(seq);
    }
}

void Terminal::onChar(char32_t codepoint) {
    // Convert to UTF-8 and send
    char utf8[5] = {};
    if (codepoint < 0x80) {
        utf8[0] = static_cast<char>(codepoint);
    } else if (codepoint < 0x800) {
        utf8[0] = static_cast<char>(0xC0 | (codepoint >> 6));
        utf8[1] = static_cast<char>(0x80 | (codepoint & 0x3F));
    } else if (codepoint < 0x10000) {
        utf8[0] = static_cast<char>(0xE0 | (codepoint >> 12));
        utf8[1] = static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F));
        utf8[2] = static_cast<char>(0x80 | (codepoint & 0x3F));
    } else {
        utf8[0] = static_cast<char>(0xF0 | (codepoint >> 18));
        utf8[1] = static_cast<char>(0x80 | ((codepoint >> 12) & 0x3F));
        utf8[2] = static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F));
        utf8[3] = static_cast<char>(0x80 | (codepoint & 0x3F));
    }
    
    pty_.write(utf8);
}

void Terminal::onMouseMove(int x, int y) {
    // Convert to cell coordinates
    int col = static_cast<int>(x / renderer_.getConfig().cell_width);
    int row = static_cast<int>(y / renderer_.getConfig().cell_height);
    
    // Handle selection drag
    if (screen_.getSelection().active) {
        updateSelection(col, row);
    }
}

void Terminal::onMouseButton(int button, bool pressed, int mods) {
    if (!screen_.cursorVisible()) return;
    
    int mouse_mode = screen_.getSelection().active ? 0 : 0;  // Get from screen
    if (mouse_mode == 0) return;
    
    // Convert to cell coordinates
    // int col = ...
    // int row = ...
    
    // sendMouseEvent(button, col, row, !pressed);
}

void Terminal::onMouseScroll(int delta) {
    if (delta > 0) {
        scrollUp(3);
    } else {
        scrollDown(3);
    }
}

void Terminal::onResize(int width, int height) {
    handleResize(width, height);
}

void Terminal::sendText(std::string_view text) {
    if (screen_.getSelection().active) {
        // Bracketed paste mode
        pty_.write(std::string("\x1b[200~"));
        pty_.write(std::string(text));
        pty_.write(std::string("\x1b[201~"));
    } else {
        pty_.write(std::string(text));
    }
}

void Terminal::sendKey(const std::string& sequence) {
    pty_.write(sequence);
}

void Terminal::copySelection() {
    std::string text = screen_.getSelectedText();
    if (!text.empty()) {
        // Platform-specific clipboard copy
        #if defined(TX_ANDROID)
        // Use Android clipboard API via JNI
        #else
        // Use xclip or similar on Linux
        #endif
    }
}

void Terminal::pasteText(const std::string& text) {
    sendText(text);
}

void Terminal::startSearch(const std::string& query) {
    search_query_ = query;
    search_results_.clear();
    current_search_result_ = -1;
    
    // Search in history and current screen
    // ...
}

void Terminal::findNext() {
    if (search_results_.empty()) return;
    
    current_search_result_++;
    if (current_search_result_ >= static_cast<int>(search_results_.size())) {
        current_search_result_ = 0;
    }
    
    // Scroll to result
    // ...
}

void Terminal::findPrevious() {
    if (search_results_.empty()) return;
    
    current_search_result_--;
    if (current_search_result_ < 0) {
        current_search_result_ = static_cast<int>(search_results_.size()) - 1;
    }
    
    // Scroll to result
    // ...
}

void Terminal::endSearch() {
    search_query_.clear();
    search_results_.clear();
    current_search_result_ = -1;
}

void Terminal::scrollUp(int lines) {
    scroll_offset_ = std::min(scroll_offset_ + lines, screen_.getHistorySize());
}

void Terminal::scrollDown(int lines) {
    scroll_offset_ = std::max(0, scroll_offset_ - lines);
}

void Terminal::scrollToTop() {
    scroll_offset_ = screen_.getHistorySize();
}

void Terminal::scrollToBottom() {
    scroll_offset_ = 0;
}

void Terminal::startSelection(int col, int row) {
    screen_.setSelection(col, row, col, row);
}

void Terminal::updateSelection(int col, int row) {
    const Selection& sel = screen_.getSelection();
    if (sel.active) {
        screen_.setSelection(sel.start_col, sel.start_row, col, row);
    }
}

void Terminal::endSelection() {
    // Selection remains visible but drag operation ends
}

void Terminal::selectWord(int col, int row) {
    // Find word boundaries
    // ...
}

void Terminal::selectLine(int row) {
    screen_.setSelection(0, row, screen_.cols() - 1, row);
}

void Terminal::selectAll() {
    screen_.setSelection(0, 0, screen_.cols() - 1, screen_.rows() - 1);
}

ParserActions Terminal::createParserActions() {
    ParserActions actions;
    
    actions.print = [this](char32_t codepoint) {
        screen_.write(codepoint);
    };
    
    actions.execute = [this](uint8_t ctrl) {
        switch (ctrl) {
            case 0x07: /* BEL */ break;
            case 0x08: /* BS */ screen_.backspace(); break;
            case 0x09: /* HT */ screen_.horizontalTab(); break;
            case 0x0A: /* LF */ screen_.lineFeed(); break;
            case 0x0B: /* VT */ screen_.lineFeed(); break;
            case 0x0C: /* FF */ screen_.lineFeed(); break;
            case 0x0D: /* CR */ screen_.carriageReturn(); break;
            case 0x0E: /* SO */ break;
            case 0x0F: /* SI */ break;
            case 0x84: /* IND */ screen_.index(); break;
            case 0x85: /* NEL */ screen_.nextLine(); break;
            case 0x8D: /* RI */ screen_.reverseLineFeed(); break;
            default: break;
        }
    };
    
    actions.csi = [this](uint8_t cmd, const int* args, int argc) {
        // Handle CSI sequences
        int arg0 = argc > 0 ? args[0] : 0;
        int arg1 = argc > 1 ? args[1] : 0;
        
        switch (cmd) {
            case '@':  // ICH - Insert Characters
                screen_.insertCharacters(arg0 ? arg0 : 1);
                break;
            case 'A':  // CUU - Cursor Up
                screen_.moveCursor(0, -(arg0 ? arg0 : 1));
                break;
            case 'B':  // CUD - Cursor Down
                screen_.moveCursor(0, arg0 ? arg0 : 1);
                break;
            case 'C':  // CUF - Cursor Forward
                screen_.moveCursor(arg0 ? arg0 : 1, 0);
                break;
            case 'D':  // CUB - Cursor Backward
                screen_.moveCursor(-(arg0 ? arg0 : 1), 0);
                break;
            case 'E':  // CNL - Cursor Next Line
                screen_.setCursor(0, screen_.cursorRow() + (arg0 ? arg0 : 1));
                break;
            case 'F':  // CPL - Cursor Previous Line
                screen_.setCursor(0, screen_.cursorRow() - (arg0 ? arg0 : 1));
                break;
            case 'G':  // CHA - Cursor Horizontal Absolute
                screen_.setCursor((arg0 ? arg0 : 1) - 1, screen_.cursorRow());
                break;
            case 'H':  // CUP - Cursor Position
            case 'f':  // HVP - Horizontal and Vertical Position
                screen_.setCursor((arg1 ? arg1 : 1) - 1, (arg0 ? arg0 : 1) - 1);
                break;
            case 'J':  // ED - Erase in Display
                screen_.eraseInDisplay(arg0);
                break;
            case 'K':  // EL - Erase in Line
                screen_.eraseInLine(arg0);
                break;
            case 'L':  // IL - Insert Lines
                screen_.insertLines(arg0 ? arg0 : 1);
                break;
            case 'M':  // DL - Delete Lines
                screen_.deleteLines(arg0 ? arg0 : 1);
                break;
            case 'P':  // DCH - Delete Characters
                screen_.deleteCharacters(arg0 ? arg0 : 1);
                break;
            case 'S':  // SU - Scroll Up
                screen_.scrollUp(arg0 ? arg0 : 1);
                break;
            case 'T':  // SD - Scroll Down
                screen_.scrollDown(arg0 ? arg0 : 1);
                break;
            case 'X':  // ECH - Erase Characters
                screen_.eraseCharacters(arg0 ? arg0 : 1);
                break;
            case 'd':  // VPA - Vertical Position Absolute
                screen_.setCursor(screen_.cursorCol(), (arg0 ? arg0 : 1) - 1);
                break;
            case 'm':  // SGR - Select Graphic Rendition
                if (argc == 0) {
                    screen_.resetAttributes();
                } else {
                    for (int i = 0; i < argc; ++i) {
                        int attr = args[i];
                        if (attr == 0) {
                            screen_.resetAttributes();
                        } else if (attr == 38 && i + 2 < argc && args[i + 1] == 5) {
                            // 256-color foreground
                            screen_.setForeground(get_ansi_palette()[args[i + 2]]);
                            i += 2;
                        } else if (attr == 48 && i + 2 < argc && args[i + 1] == 5) {
                            // 256-color background
                            screen_.setBackground(get_ansi_palette()[args[i + 2]]);
                            i += 2;
                        } else if (attr == 38 && i + 4 < argc && args[i + 1] == 2) {
                            // True color foreground
                            screen_.setForeground(rgb(args[i + 2], args[i + 3], args[i + 4]));
                            i += 4;
                        } else if (attr == 48 && i + 4 < argc && args[i + 1] == 2) {
                            // True color background
                            screen_.setBackground(rgb(args[i + 2], args[i + 3], args[i + 4]));
                            i += 4;
                        } else if (attr >= 30 && attr <= 37) {
                            // ANSI foreground
                            screen_.setForeground(get_ansi_palette()[attr - 30]);
                        } else if (attr >= 40 && attr <= 47) {
                            // ANSI background
                            screen_.setBackground(get_ansi_palette()[attr - 40]);
                        } else if (attr >= 90 && attr <= 97) {
                            // Bright ANSI foreground
                            screen_.setForeground(get_ansi_palette()[attr - 90 + 8]);
                        } else if (attr >= 100 && attr <= 107) {
                            // Bright ANSI background
                            screen_.setBackground(get_ansi_palette()[attr - 100 + 8]);
                        } else {
                            screen_.setAttribute(attr, true);
                        }
                    }
                }
                break;
            case 'r':  // DECSTBM - Set Top and Bottom Margins
                screen_.setScrollRegion((arg0 ? arg0 : 1) - 1, (arg1 ? arg1 : screen_.rows()) - 1);
                screen_.setCursor(0, 0);
                break;
            case 's':  // SCP - Save Cursor Position
                screen_.saveCursor();
                break;
            case 'u':  // RCP - Restore Cursor Position
                screen_.restoreCursor();
                break;
            case 'h':  // SM - Set Mode
                // Handle various modes
                break;
            case 'l':  // RM - Reset Mode
                // Handle various modes
                break;
        }
    };
    
    actions.osc = [this](int cmd, std::string_view data) {
        switch (cmd) {
            case 0:  // Set window title and icon
            case 1:  // Set icon name
            case 2:  // Set window title
                // TODO: Set window title
                break;
            case 4:  // Set/read color palette
                // TODO: Color palette manipulation
                break;
            case 7:  // Set current working directory (hyperlink)
                // TODO: Track working directory
                break;
            case 8:  // Hyperlink
                // TODO: Handle hyperlinks
                break;
            case 9:  // iTerm2 notifications
                // TODO: Handle notifications
                break;
            case 10: // Set foreground color
            case 11: // Set background color
            case 12: // Set cursor color
                // TODO: Color setting
                break;
            case 52: // Clipboard manipulation
                // TODO: Clipboard integration
                break;
            case 133: // FinalTerm shell integration
                // TODO: Shell integration
                break;
        }
    };
    
    actions.esc = [this](uint8_t intermediate, uint8_t final_byte) {
        switch (final_byte) {
            case '7':  // DECSC - Save Cursor
                screen_.saveCursor();
                break;
            case '8':  // DECRC - Restore Cursor
                screen_.restoreCursor();
                break;
            case 'c':  // RIS - Full Reset
                screen_.reset();
                break;
            case 'M':  // RI - Reverse Index
                screen_.reverseLineFeed();
                break;
        }
    };
    
    return actions;
}

/**
 * Convert key code and modifiers to terminal escape sequence
 * 
 * NOTE: This function receives Android key codes (from KeyEvent).
 * The mapping is designed to match Android's KeyEvent constants.
 */
std::string Terminal::keyToSequence(int key, int mods) {
    using namespace KeyCode;
    
    bool shift = mods & MOD_SHIFT;
    bool ctrl = mods & MOD_CTRL;
    bool alt = mods & MOD_ALT;
    
    // Arrow keys
    switch (key) {
        case DPAD_UP:
            return ctrl ? "\x1b[1;5A" : "\x1b[A";
        case DPAD_DOWN:
            return ctrl ? "\x1b[1;5B" : "\x1b[B";
        case DPAD_RIGHT:
            return ctrl ? "\x1b[1;5C" : "\x1b[C";
        case DPAD_LEFT:
            return ctrl ? "\x1b[1;5D" : "\x1b[D";
    }
    
    // Navigation keys
    switch (key) {
        case MOVE_HOME:
            return ctrl ? "\x1b[1;5H" : "\x1b[H";
        case MOVE_END:
            return ctrl ? "\x1b[1;5F" : "\x1b[F";
        case PAGE_UP:
            return ctrl ? "\x1b[5;5~" : "\x1b[5~";
        case PAGE_DOWN:
            return ctrl ? "\x1b[6;5~" : "\x1b[6~";
    }
    
    // Editing keys
    switch (key) {
        case DEL:
            return ctrl ? "\x08" : "\x7f";  // Backspace: BS or DEL
        case FORWARD_DEL:
            return "\x1b[3~";
        case INSERT:
            return "\x1b[2~";
    }
    
    // Function keys
    switch (key) {
        case F1: return "\x1bOP";
        case F2: return "\x1bOQ";
        case F3: return "\x1bOR";
        case F4: return "\x1bOS";
        case F5: return "\x1b[15~";
        case F6: return "\x1b[17~";
        case F7: return "\x1b[18~";
        case F8: return "\x1b[19~";
        case F9: return "\x1b[20~";
        case F10: return "\x1b[21~";
        case F11: return "\x1b[23~";
        case F12: return "\x1b[24~";
    }
    
    // Regular keys with modifiers
    if (key < 256) {
        char c = static_cast<char>(key);
        
        if (ctrl) {
            // Control modifier
            if (c >= 'a' && c <= 'z') {
                c = c - 'a' + 1;
            } else if (c >= 'A' && c <= 'Z') {
                c = c - 'A' + 1;
            } else if (c == ' ' || c == '2') {
                c = 0;
            } else if (c == '3' || c == '[') {
                c = 0x1B;
            } else if (c == '4' || c == '\\') {
                c = 0x1C;
            } else if (c == '5' || c == ']') {
                c = 0x1D;
            } else if (c == '6' || c == '^') {
                c = 0x1E;
            } else if (c == '7' || c == '_' || c == '/') {
                c = 0x1F;
            } else if (c == '8' || c == '?') {
                c = 0x7F;
            }
            return std::string(1, c);
        }
        
        if (alt) {
            return "\x1b" + std::string(1, c);
        }
        
        return std::string(1, c);
    }
    
    return "";
}

std::string Terminal::cursorKey(int key, bool application) {
    using namespace KeyCode;
    
    if (application) {
        switch (key) {
            case DPAD_UP: return "\x1bOA";
            case DPAD_DOWN: return "\x1bOB";
            case DPAD_RIGHT: return "\x1bOC";
            case DPAD_LEFT: return "\x1bOD";
        }
    } else {
        switch (key) {
            case DPAD_UP: return "\x1b[A";
            case DPAD_DOWN: return "\x1b[B";
            case DPAD_RIGHT: return "\x1b[C";
            case DPAD_LEFT: return "\x1b[D";
        }
    }
    return "";
}

std::string Terminal::keypadKey(int key, bool application) {
    if (application) {
        switch (key) {
            case '0': return "\x1bOp";
            case '1': return "\x1bOq";
            case '2': return "\x1bOr";
            case '3': return "\x1bOs";
            case '4': return "\x1bOt";
            case '5': return "\x1bOu";
            case '6': return "\x1bOv";
            case '7': return "\x1bOw";
            case '8': return "\x1bOx";
            case '9': return "\x1bOy";
            case '-': return "\x1bOm";
            case ',': return "\x1bOl";
            case '.': return "\x1bOn";
            case '\r': return "\x1bOM";
            case '=': return "\x1bOX";
            case '*': return "\x1bOj";
            case '+': return "\x1bOk";
            case '/': return "\x1bOo";
        }
    }
    return std::string(1, static_cast<char>(key));
}

void Terminal::sendMouseEvent(int button, int x, int y, bool release) {
    int col = x + 1;
    int row = y + 1;
    
    int value = button;
    if (release) {
        value = 3;
    }
    
    // X10 encoding
    char cb = static_cast<char>(32 + value);
    char cx = static_cast<char>(32 + col);
    char cy = static_cast<char>(32 + row);
    
    std::string seq = "\x1b[M";
    seq += cb;
    seq += cx;
    seq += cy;
    
    pty_.write(seq);
}

void Terminal::handleResize(int width, int height) {
    renderer_.setViewport(width, height);
    
    int cols = renderer_.calcCols();
    int rows = renderer_.calcRows();
    
    screen_.resize(cols, rows);
    pty_.resize(cols, rows);
}

} // namespace tx
