package dev.thor.rombutler.receive

import fi.iki.elonen.NanoHTTPD
import java.io.File

/**
 * Tiny embedded HTTP server for the LAN receive mode: serves a minimal
 * upload page and stores uploaded files into the download folder, where
 * the normal scan flow picks them up.
 *
 * Security posture: LAN-only convenience feature, started explicitly by
 * the user and only while the foreground service runs. Filenames are
 * sanitized to their last path segment — no path traversal.
 */
class ReceiveServer(
    port: Int,
    private val targetDir: File,
    private val onFileReceived: (String) -> Unit,
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response = when {
        session.method == Method.POST && session.uri == "/upload" -> handleUpload(session)
        else -> newFixedLengthResponse(Response.Status.OK, "text/html", UPLOAD_PAGE)
    }

    private fun handleUpload(session: IHTTPSession): Response {
        return try {
            // NanoHTTPD writes multipart bodies to temp files
            val body = mutableMapOf<String, String>()
            session.parseBody(body)

            var saved = 0
            for ((param, tempPath) in body) {
                val original = session.parameters[param]?.firstOrNull() ?: continue
                val name = sanitizeFileName(original) ?: continue
                var target = File(targetDir, name)
                // Never overwrite: suffix (1), (2), ...
                var counter = 1
                while (target.exists()) {
                    val stem = name.substringBeforeLast('.')
                    val ext = name.substringAfterLast('.', "")
                    target = File(
                        targetDir,
                        if (ext.isEmpty()) "$stem ($counter)" else "$stem ($counter).$ext",
                    )
                    counter++
                }
                File(tempPath).copyTo(target)
                onFileReceived(target.name)
                saved++
            }
            newFixedLengthResponse(Response.Status.OK, "text/plain", "OK: $saved")
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Fehler: ${e.message}",
            )
        }
    }

    companion object {
        const val DEFAULT_PORT = 8737 // T=8, H=7, O=3... close enough to THOR

        /** Last path segment only; rejects empty/hidden results. */
        fun sanitizeFileName(raw: String): String? {
            val name = raw.replace('\\', '/').substringAfterLast('/').trim()
            return name.takeIf { it.isNotEmpty() && !it.startsWith(".") }
        }

        // language=HTML
        private val UPLOAD_PAGE = """
            <!DOCTYPE html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Thor ROM Butler</title>
            <style>
              body{font-family:sans-serif;background:#060A14;color:#E6EEF8;
                   display:flex;flex-direction:column;align-items:center;
                   justify-content:center;min-height:90vh;margin:0}
              h1{color:#38C8FF}#drop{border:2px dashed #2A3A5C;border-radius:16px;
                   padding:60px 80px;text-align:center;cursor:pointer}
              #drop.hover{border-color:#F5C542}#log{margin-top:24px;color:#93A5C0}
            </style></head><body>
            <h1>&#9889; Thor ROM Butler</h1>
            <div id="drop">ROMs hier ablegen / drop ROMs here<br><br>
              <input type="file" id="file" multiple></div>
            <div id="log"></div>
            <script>
              const drop=document.getElementById('drop'),log=document.getElementById('log');
              async function send(files){
                for(const f of files){
                  const fd=new FormData();fd.append('file',f,f.name);
                  log.textContent='Sende / sending: '+f.name+' …';
                  const r=await fetch('/upload',{method:'POST',body:fd});
                  log.textContent=(r.ok?'&#10003; ':'&#9888; ')+f.name;
                  log.innerHTML=(r.ok?'&#10003; ':'&#9888; ')+f.name;
                }
                log.innerHTML+='<br>Fertig / done.';
              }
              drop.addEventListener('dragover',e=>{e.preventDefault();drop.classList.add('hover')});
              drop.addEventListener('dragleave',()=>drop.classList.remove('hover'));
              drop.addEventListener('drop',e=>{e.preventDefault();drop.classList.remove('hover');send(e.dataTransfer.files)});
              document.getElementById('file').addEventListener('change',e=>send(e.target.files));
            </script></body></html>
        """.trimIndent()
    }
}
