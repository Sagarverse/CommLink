package com.commvault.commlink.ui.shortcuts.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.UUID

// ─── Action Tile Types ───
enum class TileCategory(val label: String) {
    SYSTEM("System"),
    WINDOW("Window Management"),
    APP("Apps & Launch"),
    BROWSER("Browser"),
    MEDIA("Media Controls"),
    INPUT("Input & Text"),
    MOUSE("Mouse"),
    NETWORK("Network"),
    SECURITY("Security"),
    POWER("Power"),
    FLOW("Flow Control"),
    CLIPBOARD("Clipboard"),
    UTILITY("Utility")
}

enum class ActionType(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val category: TileCategory,
    val color: Color,
    val hasParam: Boolean = false,
    val paramLabel: String = "",
    val paramHint: String = ""
) {
    // ─── System ───
    LOCK_PC("Lock PC", "Win+L", Icons.Default.Lock, TileCategory.SYSTEM, Color(0xFF5C6BC0)),
    UNLOCK_PC("Unlock PC", "Type password + Enter", Icons.Default.LockOpen, TileCategory.SYSTEM, Color(0xFF66BB6A), true, "Password", "Enter PC password"),
    MINIMIZE_ALL("Minimize All", "Win+D", Icons.Default.DesktopWindows, TileCategory.SYSTEM, Color(0xFF42A5F5)),
    TASK_MANAGER("Task Manager", "Ctrl+Shift+Esc", Icons.Default.Memory, TileCategory.SYSTEM, Color(0xFFEF5350)),
    OPEN_SETTINGS("Open Settings", "Win+I", Icons.Default.Settings, TileCategory.SYSTEM, Color(0xFF78909C)),
    OPEN_FILE_EXPLORER("File Explorer", "Win+E", Icons.Default.Folder, TileCategory.SYSTEM, Color(0xFFFFCA28)),
    SCREENSHOT("Screenshot", "Win+Shift+S", Icons.Default.Screenshot, TileCategory.SYSTEM, Color(0xFF26C6DA)),
    OPEN_RUN("Open Run Dialog", "Win+R", Icons.Default.Terminal, TileCategory.SYSTEM, Color(0xFF8D6E63)),

    // ─── Window Management ───
    CLOSE_WINDOW("Close Window", "Alt+F4", Icons.Default.Close, TileCategory.WINDOW, Color(0xFFE53935)),
    SNAP_LEFT("Snap Left", "Win+Left", Icons.Default.ArrowBack, TileCategory.WINDOW, Color(0xFF1E88E5)),
    SNAP_RIGHT("Snap Right", "Win+Right", Icons.Default.ArrowForward, TileCategory.WINDOW, Color(0xFF1E88E5)),
    MAXIMIZE_WINDOW("Maximize", "Win+Up", Icons.Default.Fullscreen, TileCategory.WINDOW, Color(0xFF43A047)),
    MINIMIZE_WINDOW("Minimize", "Win+Down", Icons.Default.Minimize, TileCategory.WINDOW, Color(0xFFFFA726)),
    SWITCH_WINDOW("Switch Window", "Alt+Tab", Icons.Default.SwapHoriz, TileCategory.WINDOW, Color(0xFF7E57C2)),
    TASK_VIEW("Task View", "Win+Tab", Icons.Default.ViewModule, TileCategory.WINDOW, Color(0xFF5C6BC0)),
    NEXT_DESKTOP("Next Desktop", "Win+Ctrl+Right", Icons.Default.NavigateNext, TileCategory.WINDOW, Color(0xFF26A69A)),
    PREV_DESKTOP("Prev Desktop", "Win+Ctrl+Left", Icons.Default.NavigateBefore, TileCategory.WINDOW, Color(0xFF26A69A)),
    NEW_DESKTOP("New Desktop", "Win+Ctrl+D", Icons.Default.Add, TileCategory.WINDOW, Color(0xFF66BB6A)),
    CLOSE_DESKTOP("Close Desktop", "Win+Ctrl+F4", Icons.Default.RemoveCircle, TileCategory.WINDOW, Color(0xFFEF5350)),

    // ─── Apps & Launch ───
    LAUNCH_APP("Launch App", "Win+R → app name", Icons.Default.Rocket, TileCategory.APP, Color(0xFF7E57C2), true, "App Name", "e.g. notepad, calc, code"),
    RUN_COMMAND("Run Command", "Win+R → command", Icons.Default.Terminal, TileCategory.APP, Color(0xFF5D4037), true, "Command", "e.g. cmd /k ipconfig"),
    LAUNCH_URL("Open URL", "Browser + URL", Icons.Default.Language, TileCategory.APP, Color(0xFF1565C0), true, "URL", "e.g. https://google.com"),
    SEARCH_WEB("Search Web", "Browser + search", Icons.Default.Search, TileCategory.APP, Color(0xFF4CAF50), true, "Query", "e.g. kotlin tutorial"),
    OPEN_CMD("Command Prompt", "cmd.exe", Icons.Default.Terminal, TileCategory.APP, Color(0xFF37474F)),
    OPEN_CMD_ADMIN("CMD (Admin)", "Admin cmd", Icons.Default.AdminPanelSettings, TileCategory.APP, Color(0xFFD32F2F)),
    OPEN_POWERSHELL("PowerShell", "powershell", Icons.Default.Code, TileCategory.APP, Color(0xFF1565C0)),
    OPEN_NOTEPAD("Notepad", "notepad.exe", Icons.Default.EditNote, TileCategory.APP, Color(0xFF8D6E63)),
    OPEN_CALC("Calculator", "calc.exe", Icons.Default.Calculate, TileCategory.APP, Color(0xFF00897B)),
    OPEN_PAINT("Paint", "mspaint", Icons.Default.Brush, TileCategory.APP, Color(0xFFE91E63)),
    OPEN_VSCODE("VS Code", "code", Icons.Default.Code, TileCategory.APP, Color(0xFF0288D1)),
    OPEN_REGISTRY("Registry Editor", "regedit", Icons.Default.Storage, TileCategory.APP, Color(0xFF6D4C41)),
    OPEN_DEVMGR("Device Manager", "devmgmt.msc", Icons.Default.Devices, TileCategory.APP, Color(0xFF546E7A)),
    OPEN_DISKMGMT("Disk Management", "diskmgmt.msc", Icons.Default.Storage, TileCategory.APP, Color(0xFF455A64)),
    OPEN_SERVICES("Services", "services.msc", Icons.Default.MiscellaneousServices, TileCategory.APP, Color(0xFF607D8B)),
    OPEN_EVENTVWR("Event Viewer", "eventvwr.msc", Icons.Default.EventNote, TileCategory.APP, Color(0xFF795548)),
    OPEN_CONTROL_PANEL("Control Panel", "control", Icons.Default.Tune, TileCategory.APP, Color(0xFF78909C)),

    // ─── Browser ───
    OPEN_CHROME("Open Chrome", "chrome", Icons.Default.Public, TileCategory.BROWSER, Color(0xFF4285F4)),
    OPEN_EDGE("Open Edge", "msedge", Icons.Default.Public, TileCategory.BROWSER, Color(0xFF0078D4)),
    OPEN_INCOGNITO("Incognito Mode", "chrome --incognito", Icons.Default.VisibilityOff, TileCategory.BROWSER, Color(0xFF424242)),
    BROWSER_NEW_TAB("New Tab", "Ctrl+T", Icons.Default.Tab, TileCategory.BROWSER, Color(0xFF1E88E5)),
    BROWSER_CLOSE_TAB("Close Tab", "Ctrl+W", Icons.Default.TabUnselected, TileCategory.BROWSER, Color(0xFFE53935)),
    BROWSER_REOPEN_TAB("Reopen Tab", "Ctrl+Shift+T", Icons.Default.Restore, TileCategory.BROWSER, Color(0xFF43A047)),
    BROWSER_REFRESH("Refresh", "F5", Icons.Default.Refresh, TileCategory.BROWSER, Color(0xFF1976D2)),

    // ─── Media ───
    MEDIA_PLAY_PAUSE("Play/Pause", "Media key", Icons.Default.PlayArrow, TileCategory.MEDIA, Color(0xFF00BCD4)),
    MEDIA_NEXT("Next Track", "Media key", Icons.Default.SkipNext, TileCategory.MEDIA, Color(0xFF00BCD4)),
    MEDIA_PREV("Previous Track", "Media key", Icons.Default.SkipPrevious, TileCategory.MEDIA, Color(0xFF00BCD4)),
    VOLUME_UP("Volume Up", "Vol+", Icons.Default.VolumeUp, TileCategory.MEDIA, Color(0xFF4CAF50)),
    VOLUME_DOWN("Volume Down", "Vol-", Icons.Default.VolumeDown, TileCategory.MEDIA, Color(0xFFFFA726)),
    VOLUME_MUTE("Mute", "Mute toggle", Icons.Default.VolumeOff, TileCategory.MEDIA, Color(0xFFEF5350)),

    // ─── Input & Text ───
    TYPE_TEXT("Type Text", "Types a string", Icons.Default.Keyboard, TileCategory.INPUT, Color(0xFF7986CB), true, "Text", "Text to type"),
    KEYSTROKE("Keystroke", "Any key combo", Icons.Default.KeyboardAlt, TileCategory.INPUT, Color(0xFF9575CD), true, "Keys", "e.g. CTRL c, ALT F4"),
    PRESS_ENTER("Press Enter", "Enter key", Icons.Default.KeyboardReturn, TileCategory.INPUT, Color(0xFF4DB6AC)),
    PRESS_TAB("Press Tab", "Tab key", Icons.Default.KeyboardTab, TileCategory.INPUT, Color(0xFF4DB6AC)),
    PRESS_ESCAPE("Press Escape", "Esc key", Icons.Default.Cancel, TileCategory.INPUT, Color(0xFFE57373)),
    PRESS_BACKSPACE("Backspace", "Delete char", Icons.Default.Backspace, TileCategory.INPUT, Color(0xFFFF8A65)),

    // ─── Mouse ───
    MOUSE_CLICK("Mouse Click", "Left/Right click", Icons.Default.Mouse, TileCategory.MOUSE, Color(0xFF42A5F5), true, "Button", "left / right / middle"),
    MOUSE_MOVE("Mouse Move", "Move cursor", Icons.Default.OpenWith, TileCategory.MOUSE, Color(0xFF66BB6A), true, "X,Y", "e.g. 100,50"),
    MOUSE_SCROLL("Mouse Scroll", "Scroll up/down", Icons.Default.SwapVert, TileCategory.MOUSE, Color(0xFFAB47BC), true, "Amount", "e.g. 3 or -3"),

    // ─── Clipboard ───
    CLIPBOARD_COPY("Copy", "Ctrl+C", Icons.Default.ContentCopy, TileCategory.CLIPBOARD, Color(0xFF26A69A)),
    CLIPBOARD_PASTE("Paste", "Ctrl+V", Icons.Default.ContentPaste, TileCategory.CLIPBOARD, Color(0xFF42A5F5)),
    CLIPBOARD_CUT("Cut", "Ctrl+X", Icons.Default.ContentCut, TileCategory.CLIPBOARD, Color(0xFFFFA726)),
    SELECT_ALL("Select All", "Ctrl+A", Icons.Default.SelectAll, TileCategory.CLIPBOARD, Color(0xFF7E57C2)),
    UNDO("Undo", "Ctrl+Z", Icons.Default.Undo, TileCategory.CLIPBOARD, Color(0xFF78909C)),
    REDO("Redo", "Ctrl+Y", Icons.Default.Redo, TileCategory.CLIPBOARD, Color(0xFF78909C)),

    // ─── Network ───
    OPEN_NETWORK("Network Settings", "ncpa.cpl", Icons.Default.Wifi, TileCategory.NETWORK, Color(0xFF29B6F6)),
    FLUSH_DNS("Flush DNS", "ipconfig /flushdns", Icons.Default.Dns, TileCategory.NETWORK, Color(0xFF26C6DA)),
    IPCONFIG("IPConfig", "ipconfig /all", Icons.Default.Info, TileCategory.NETWORK, Color(0xFF00ACC1)),
    PING_TEST("Ping Test", "ping google.com", Icons.Default.NetworkCheck, TileCategory.NETWORK, Color(0xFF00897B), true, "Host", "e.g. google.com"),
    NETSTAT("Netstat", "netstat -an", Icons.Default.Hub, TileCategory.NETWORK, Color(0xFF0097A7)),
    WIFI_PASSWORDS("Wi-Fi Passwords", "netsh wlan show profiles", Icons.Default.Key, TileCategory.NETWORK, Color(0xFFFF7043)),

    // ─── Security ───
    OPEN_DEFENDER("Windows Defender", "windowsdefender:", Icons.Default.Shield, TileCategory.SECURITY, Color(0xFF66BB6A)),
    OPEN_FIREWALL("Firewall Settings", "firewall.cpl", Icons.Default.LocalFireDepartment, TileCategory.SECURITY, Color(0xFFFFA726)),
    DISABLE_FIREWALL("Disable Firewall", "netsh advfirewall set all off", Icons.Default.GppBad, TileCategory.SECURITY, Color(0xFFE53935)),
    ENABLE_FIREWALL("Enable Firewall", "netsh advfirewall set all on", Icons.Default.GppGood, TileCategory.SECURITY, Color(0xFF43A047)),

    // ─── Power ───
    SHUTDOWN("Shutdown", "shutdown /s /t 0", Icons.Default.PowerSettingsNew, TileCategory.POWER, Color(0xFFE53935)),
    RESTART("Restart", "shutdown /r /t 0", Icons.Default.RestartAlt, TileCategory.POWER, Color(0xFFFFA726)),
    SLEEP("Sleep", "rundll32 powrprof.dll,SetSuspendState", Icons.Default.Bedtime, TileCategory.POWER, Color(0xFF5C6BC0)),
    LOG_OFF("Log Off", "shutdown /l", Icons.Default.Logout, TileCategory.POWER, Color(0xFF78909C)),

    // ─── Flow Control ───
    DELAY("Delay", "Wait N milliseconds", Icons.Default.Timer, TileCategory.FLOW, Color(0xFFBDBDBD), true, "Milliseconds", "e.g. 500"),
    REPEAT("Repeat", "Loop N times", Icons.Default.Repeat, TileCategory.FLOW, Color(0xFF8D6E63), true, "Count", "e.g. 3"),
    COMMENT("Comment", "Label / note", Icons.Default.Notes, TileCategory.FLOW, Color(0xFF90A4AE), true, "Note", "Description"),
    NOTIFICATION("Notification", "Show toast on phone", Icons.Default.Notifications, TileCategory.FLOW, Color(0xFFFFB74D), true, "Message", "Notification text"),
    ASK_INPUT("Ask Input", "Prompt for input & type", Icons.Default.HelpOutline, TileCategory.FLOW, Color(0xFFE91E63), true, "Prompt text", "e.g. Enter value"),
}

data class ActionTile(
    val id: String = UUID.randomUUID().toString(),
    val type: ActionType,
    val param: String = ""
)

data class Shortcut(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val iconIndex: Int = 0,
    val colorIndex: Int = 0,
    val tiles: List<ActionTile> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class PayloadTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val category: PayloadCategory,
    val script: String
)

enum class PayloadCategory(val label: String, val icon: ImageVector, val color: Color) {
    RECON("Reconnaissance", Icons.Default.Radar, Color(0xFF42A5F5)),
    PERSISTENCE("Persistence", Icons.Default.Lock, Color(0xFFEF5350)),
    EXFILTRATION("Exfiltration", Icons.Default.CloudUpload, Color(0xFFFFA726)),
    PRANKS("Pranks", Icons.Default.SentimentVerySatisfied, Color(0xFFAB47BC)),
    UTILITY("Utility", Icons.Default.Build, Color(0xFF66BB6A)),
    NETWORK("Network", Icons.Default.Wifi, Color(0xFF26C6DA)),
    CUSTOM("Custom", Icons.Default.Code, Color(0xFF78909C)),
}
