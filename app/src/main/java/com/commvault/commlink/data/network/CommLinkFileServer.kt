package com.commvault.commlink.data.network

import android.content.Context
import android.os.Environment
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.TempFile
import fi.iki.elonen.NanoHTTPD.TempFileManager
import fi.iki.elonen.NanoHTTPD.TempFileManagerFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Properties

class CommLinkFileServer(private val context: Context, port: Int = 8080) : NanoHTTPD("0.0.0.0", port) {

    private val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
    
    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        
        // Configure NanoHTTPD to use Android's cache directory for temp files during uploads
        tempFileManagerFactory = TempFileManagerFactory {
            object : TempFileManager {
                private val tempFiles = mutableListOf<TempFile>()
                override fun clear() {
                    tempFiles.forEach { try { it.delete() } catch(e: Exception) {} }
                    tempFiles.clear()
                }
                override fun createTempFile(filename_hint: String?): TempFile {
                    val file = File.createTempFile("nano-", "", context.cacheDir)
                    val fstream = FileOutputStream(file)
                    val tempFile = object : TempFile {
                        override fun delete() { file.delete() }
                        override fun getName(): String = file.absolutePath
                        override fun open(): java.io.OutputStream = fstream
                    }
                    tempFiles.add(tempFile)
                    return tempFile
                }
            }
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        try {
            val response = when {
                method == Method.GET && uri == "/" -> serveHtmlUI()
                method == Method.GET && uri == "/api/list" -> serveFileList()
                method == Method.GET && uri == "/download" -> serveFileDownload(session)
                method == Method.POST && uri == "/upload" -> handleFileUpload(session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
            }
            response.addHeader("Access-Control-Allow-Origin", "*")
            return response
        } catch (e: Exception) {
            Log.e("CommLinkFileServer", "Error serving request", e)
            val errResp = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "500 Internal Error: ${e.message}")
            errResp.addHeader("Access-Control-Allow-Origin", "*")
            return errResp
        }
    }

    private fun serveHtmlUI(): Response {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>CommLink | File Transfer</title>
                <style>
                    :root {
                        --bg: #F4F7FB;
                        --surface: #FFFFFF;
                        --primary: #10B981;
                        --primary-hover: #059669;
                        --text-main: #0F172A;
                        --text-muted: #64748B;
                        --border: #E2E8F0;
                        --radius-lg: 24px;
                        --radius-md: 16px;
                        --shadow: 0 10px 30px rgba(51,65,85,0.08);
                    }
                    * { box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Inter", Roboto, sans-serif; }
                    body { background: var(--bg); color: var(--text-main); margin: 0; padding: 2rem; min-height: 100vh; display: flex; justify-content: center; }
                    .dashboard { width: 100%; max-width: 900px; }
                    
                    header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 2.5rem; background: var(--surface); padding: 1.5rem 2rem; border-radius: var(--radius-lg); box-shadow: var(--shadow); }
                    h1 { font-size: 2rem; margin: 0; font-weight: 900; letter-spacing: 1px; color: var(--text-main); display: flex; align-items: center; gap: 10px;}
                    h1 span { color: var(--primary); }
                    .logo-icon { width: 40px; height: 40px; background: var(--primary); border-radius: 12px; display: inline-flex; align-items: center; justify-content: center; color: white; font-size: 20px; font-weight: bold; }
                    .badge { background: rgba(16, 185, 129, 0.1); color: var(--primary); padding: 8px 16px; border-radius: 20px; font-size: 0.85rem; font-weight: 700; }
                    
                    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 2rem; }
                    @media (max-width: 768px) { .grid { grid-template-columns: 1fr; } }
                    
                    .card { background: var(--surface); border-radius: var(--radius-lg); padding: 2rem; box-shadow: var(--shadow); }
                    .card h2 { margin-top: 0; font-size: 1.25rem; margin-bottom: 1.5rem; font-weight: 800; color: var(--text-main); }
                    
                    /* Upload Section */
                    .upload-area { border: 2px dashed var(--border); border-radius: var(--radius-md); padding: 3rem 1.5rem; text-align: center; cursor: pointer; transition: all 0.3s ease; background: #F8FAFC; }
                    .upload-area:hover, .upload-area.dragover { border-color: var(--primary); background: rgba(16, 185, 129, 0.05); }
                    .upload-icon { font-size: 3rem; margin-bottom: 1rem; color: var(--primary); }
                    .upload-text { font-weight: 700; margin-bottom: 0.5rem; color: var(--text-main); }
                    .upload-sub { color: var(--text-muted); font-size: 0.85rem; font-weight: 500; }
                    .upload-progress { margin-top: 1rem; font-size: 0.9rem; color: var(--primary); font-weight: bold; }
                    
                    /* File List Section */
                    .file-list-container { max-height: 500px; overflow-y: auto; padding-right: 0.5rem; }
                    .file-list-container::-webkit-scrollbar { width: 6px; }
                    .file-list-container::-webkit-scrollbar-thumb { background: #CBD5E1; border-radius: 10px; }
                    
                    .file-item { display: flex; align-items: center; padding: 1rem; border-radius: var(--radius-md); background: #F8FAFC; margin-bottom: 0.75rem; border: 1px solid transparent; transition: all 0.2s; box-shadow: 0 2px 4px rgba(0,0,0,0.02); }
                    .file-item:hover { background: #F1F5F9; border-color: var(--border); }
                    .file-icon { font-size: 1.5rem; margin-right: 1rem; width: 44px; height: 44px; display: flex; align-items: center; justify-content: center; background: rgba(16, 185, 129, 0.1); color: var(--primary); border-radius: 12px; }
                    .file-info { flex: 1; overflow: hidden; }
                    .file-name { font-weight: 700; font-size: 0.95rem; margin-bottom: 0.2rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; color: var(--text-main); }
                    .file-size { color: var(--text-muted); font-size: 0.8rem; font-weight: 500; }
                    .btn-download { background: var(--primary); color: white; text-decoration: none; padding: 8px 16px; border-radius: 10px; font-size: 0.85rem; font-weight: 700; transition: background 0.2s; box-shadow: 0 4px 12px rgba(16, 185, 129, 0.2); }
                    .btn-download:hover { background: var(--primary-hover); transform: translateY(-1px); }
                    .empty-state { text-align: center; padding: 3rem 1rem; color: var(--text-muted); font-weight: 600; }
                </style>
            </head>
            <body>
                <div class="dashboard">
                    <header>
                        <h1><div class="logo-icon">C</div> COMM<span>LINK</span></h1>
                        <div class="badge">● SECURE WIFI LINK</div>
                    </header>
                    
                    <div class="grid">
                        <div class="card">
                            <h2>Send to Phone</h2>
                            <div class="upload-area" id="drop-zone" onclick="document.getElementById('file-input').click()">
                                <div class="upload-icon">📤</div>
                                <div class="upload-text">Click or Drag & Drop</div>
                                <div class="upload-sub">Files will be sent instantly over WiFi</div>
                                <div id="upload-status" class="upload-progress"></div>
                                <input type="file" id="file-input" style="display: none" multiple onchange="handleFiles(this.files)">
                            </div>
                        </div>
                        
                        <div class="card">
                            <h2>Files on Phone</h2>
                            <div class="file-list-container" id="file-list">
                                <div class="empty-state">Loading files...</div>
                            </div>
                        </div>
                    </div>
                </div>

                <script>
                    const dropZone = document.getElementById('drop-zone');
                    const status = document.getElementById('upload-status');
                    
                    dropZone.addEventListener('dragover', (e) => { e.preventDefault(); dropZone.classList.add('dragover'); });
                    dropZone.addEventListener('dragleave', (e) => { e.preventDefault(); dropZone.classList.remove('dragover'); });
                    dropZone.addEventListener('drop', (e) => { e.preventDefault(); dropZone.classList.remove('dragover'); handleFiles(e.dataTransfer.files); });

                    function formatBytes(bytes, decimals = 2) {
                        if (!+bytes) return '0 Bytes';
                        const k = 1024, dm = decimals < 0 ? 0 : decimals, sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'], i = Math.floor(Math.log(bytes) / Math.log(k));
                        return `${"$"}{parseFloat((bytes / Math.pow(k, i)).toFixed(dm))} ${"$"}{sizes[i]}`;
                    }
                    
                    function getFileIcon(filename) {
                        const ext = filename.split('.').pop().toLowerCase();
                        if(['jpg','jpeg','png','gif','svg'].includes(ext)) return '🖼️';
                        if(['mp4','mov','avi','mkv'].includes(ext)) return '🎬';
                        if(['mp3','wav','ogg','m4a'].includes(ext)) return '🎵';
                        if(['pdf'].includes(ext)) return '📄';
                        if(['zip','rar','7z','tar','gz'].includes(ext)) return '📦';
                        return '📁';
                    }

                    async function loadFiles() {
                        try {
                            const res = await fetch('/api/list');
                            const files = await res.json();
                            const container = document.getElementById('file-list');
                            container.innerHTML = '';
                            
                            if(files.length === 0) {
                                container.innerHTML = '<div class="empty-state">No files found. Send something to your phone!</div>';
                                return;
                            }

                            files.sort((a,b) => b.lastModified - a.lastModified).forEach(f => {
                                container.innerHTML += `
                                    <div class="file-item">
                                        <div class="file-icon">${"$"}{getFileIcon(f.name)}</div>
                                        <div class="file-info">
                                            <div class="file-name" title="${"$"}{f.name}">${"$"}{f.name}</div>
                                            <div class="file-size">${"$"}{formatBytes(f.size)}</div>
                                        </div>
                                        <a href="/download?file=${"$"}{encodeURIComponent(f.name)}" class="btn-download" download>Download</a>
                                    </div>
                                `;
                            });
                        } catch (e) {
                            document.getElementById('file-list').innerHTML = '<div class="empty-state">Error loading files.</div>';
                        }
                    }

                    async function handleFiles(files) {
                        if(files.length === 0) return;
                        status.textContent = `Uploading ${"$"}{files.length} file(s)...`;
                        
                        let success = 0;
                        for (let file of files) {
                            const formData = new FormData();
                            formData.append('file', file);
                            try { 
                                const res = await fetch('/upload', { method: 'POST', body: formData }); 
                                if(res.ok) success++;
                            } catch (e) { console.error('Upload failed', e); }
                        }
                        
                        status.textContent = `Successfully sent ${"$"}{success} file(s)!`;
                        setTimeout(() => { status.textContent = ''; }, 3000);
                        
                        document.getElementById('file-input').value = ""; // Reset input
                        loadFiles();
                    }

                    // Initial load
                    loadFiles();
                    // Poll for updates every 5 seconds
                    setInterval(loadFiles, 5000);
                </script>
            </body>
            </html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }

    private fun serveFileList(): Response {
        val files = baseDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val jsonArray = JSONArray()
        files.forEach { file ->
            val obj = JSONObject()
            obj.put("name", file.name)
            obj.put("size", file.length())
            obj.put("lastModified", file.lastModified())
            jsonArray.put(obj)
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", jsonArray.toString())
    }

    private fun serveFileDownload(session: IHTTPSession): Response {
        val fileName = session.parameters["file"]?.firstOrNull()
        if (fileName.isNullOrBlank()) return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing file parameter")

        val file = File(baseDir, fileName)
        if (!file.exists() || !file.isFile) return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found")

        return try {
            val fis = FileInputStream(file)
            newChunkedResponse(Response.Status.OK, "application/octet-stream", fis).apply {
                addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
                addHeader("Content-Length", file.length().toString())
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error reading file")
        }
    }

    private fun handleFileUpload(session: IHTTPSession): Response {
        try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            
            for ((key, tempFilePath) in files) {
                val paramList = session.parameters[key]
                val originalName = paramList?.firstOrNull() ?: "uploaded_file_${System.currentTimeMillis()}"
                
                val tempFile = File(tempFilePath)
                var finalTarget = File(baseDir, originalName)
                var counter = 1
                while (finalTarget.exists()) {
                    val nameWithoutExt = originalName.substringBeforeLast(".")
                    val ext = if (originalName.contains(".")) "." + originalName.substringAfterLast(".") else ""
                    finalTarget = File(baseDir, "${nameWithoutExt}_$counter$ext")
                    counter++
                }

                tempFile.copyTo(finalTarget, overwrite = true)
            }
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Upload successful")
        } catch (e: Exception) {
            Log.e("CommLinkFileServer", "Upload error", e)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Upload failed: ${e.message}")
        }
    }
}
