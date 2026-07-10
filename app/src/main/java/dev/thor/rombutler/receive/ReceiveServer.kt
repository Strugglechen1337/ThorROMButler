package dev.thor.rombutler.receive

import dev.thor.rombutler.data.files.IncomingFile
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.IOException

/**
 * Tiny embedded HTTP server for the LAN receive mode. A random per-session
 * path is required for both the upload page and POST endpoint; received files
 * become visible to the scanner only after their copy completed.
 */
class ReceiveServer(
    port: Int,
    private val targetDir: File,
    private val sessionToken: String,
    private val onFileReceived: (String) -> Unit,
) : NanoHTTPD(port) {

    private val pagePath = "/$sessionToken/"
    private val uploadPath = "/$sessionToken/upload"

    override fun serve(session: IHTTPSession): Response = when {
        session.method == Method.POST && session.uri == uploadPath -> handleUpload(session)
        session.method == Method.GET && session.uri == pagePath ->
            newFixedLengthResponse(Response.Status.OK, "text/html", UPLOAD_PAGE)

        else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
    }

    private fun handleUpload(session: IHTTPSession): Response {
        return try {
            // NanoHTTPD finishes multipart reception into temp files first.
            val body = mutableMapOf<String, String>()
            session.parseBody(body)

            var saved = 0
            for ((param, tempPath) in body) {
                val original = session.parameters[param]?.firstOrNull() ?: continue
                val name = IncomingFile.sanitizeName(original) ?: continue
                val target = IncomingFile.uniqueTarget(targetDir, name) ?: continue
                val tempFile = File(tempPath)
                ensureFreeSpace(tempFile.length())
                tempFile.inputStream().use { input -> IncomingFile.copyAtomically(input, target) }
                onFileReceived(target.name)
                saved++
            }
            newFixedLengthResponse(Response.Status.OK, "text/plain", "OK: $saved")
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Upload failed: ${e.message}",
            )
        }
    }

    private fun ensureFreeSpace(incomingBytes: Long) {
        val usable = targetDir.usableSpace
        if (usable in 1 until incomingBytes + SPACE_MARGIN_BYTES) {
            throw IOException("Not enough free space")
        }
    }

    companion object {
        const val DEFAULT_PORT = 8737
        private const val SPACE_MARGIN_BYTES = 64L * 1024 * 1024

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
                  const r=await fetch('upload',{method:'POST',body:fd});
                  log.textContent=(r.ok?'✓ ':'⚠ ')+f.name;
                }
                log.textContent+=' · Fertig / done.';
              }
              drop.addEventListener('dragover',e=>{e.preventDefault();drop.classList.add('hover')});
              drop.addEventListener('dragleave',()=>drop.classList.remove('hover'));
              drop.addEventListener('drop',e=>{e.preventDefault();drop.classList.remove('hover');send(e.dataTransfer.files)});
              document.getElementById('file').addEventListener('change',e=>send(e.target.files));
            </script></body></html>
        """.trimIndent()
    }
}
