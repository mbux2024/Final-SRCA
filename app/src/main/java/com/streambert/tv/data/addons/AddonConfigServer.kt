package com.streambert.tv.data.addons

import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject

/**
 * A tiny embedded HTTP server that lets the user manage their installed stream
 * add-ons from a phone on the same Wi-Fi. The TV shows a QR code pointing at
 * http://<lan-ip>:<port>/ ; the phone opens it, sees the installed add-ons, and
 * can add / remove / reorder them. Changes are applied via callbacks.
 *
 * Our own lightweight implementation (NuvioTV's server code is GPL and not reused).
 */
class AddonConfigServer(
    port: Int,
    private val currentAddons: () -> List<String>,
    private val onAdd: (String) -> Unit,
    private val onRemove: (String) -> Unit,
    private val onSetOrder: (List<String>) -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.method == Method.GET && session.uri == "/" ->
                    newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", pageHtml())

                session.method == Method.GET && session.uri == "/api/addons" ->
                    json(addonsJson())

                session.method == Method.POST && session.uri == "/api/addons" -> {
                    val body = HashMap<String, String>()
                    session.parseBody(body)
                    handlePost(body["postData"].orEmpty())
                }

                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}")
        }
    }

    private fun handlePost(bodyText: String): Response {
        val obj = runCatching { JSONObject(bodyText) }.getOrNull()
            ?: return json("""{"ok":false,"error":"bad body"}""")
        when (obj.optString("action")) {
            "add" -> obj.optString("url").takeIf { it.isNotBlank() }?.let(onAdd)
            "remove" -> obj.optString("url").takeIf { it.isNotBlank() }?.let(onRemove)
            "setOrder" -> {
                val arr = obj.optJSONArray("urls") ?: JSONArray()
                val list = (0 until arr.length()).map { arr.getString(it) }
                onSetOrder(list)
            }
        }
        return json(addonsJson())
    }

    private fun addonsJson(): String {
        val arr = JSONArray()
        currentAddons().forEach { arr.put(it) }
        return JSONObject().put("addons", arr).toString()
    }

    private fun json(text: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", text)

    private fun pageHtml(): String = """
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Streambert · Add-ons</title>
<style>
 body{font-family:-apple-system,Segoe UI,Roboto,sans-serif;background:#0b0b0f;color:#eee;margin:0;padding:20px}
 h1{font-size:20px} .card{background:#17171f;border-radius:12px;padding:14px;margin:10px 0;display:flex;align-items:center;gap:10px}
 .u{flex:1;font-size:13px;word-break:break-all;color:#bbb} button{background:#2a2a35;color:#fff;border:0;border-radius:8px;padding:10px 14px;font-size:14px}
 .add{display:flex;gap:8px;margin-top:16px} input{flex:1;padding:12px;border-radius:8px;border:1px solid #333;background:#111;color:#fff}
 .p{background:#e50914}
</style></head><body>
<h1>Manage add-ons</h1>
<div id="list"></div>
<div class="add"><input id="url" placeholder="https://…/manifest.json"><button class="p" onclick="add()">Add</button></div>
<script>
 async function load(){const r=await fetch('/api/addons');const d=await r.json();render(d.addons||[])}
 function render(a){const l=document.getElementById('list');l.innerHTML='';a.forEach((u,i)=>{const c=document.createElement('div');c.className='card';
   c.innerHTML='<div class="u">'+(i+1)+'. '+u+'</div>';
   const up=document.createElement('button');up.textContent='↑';up.onclick=()=>move(a,i,-1);
   const dn=document.createElement('button');dn.textContent='↓';dn.onclick=()=>move(a,i,1);
   const rm=document.createElement('button');rm.textContent='Remove';rm.onclick=()=>post({action:'remove',url:u});
   c.appendChild(up);c.appendChild(dn);c.appendChild(rm);l.appendChild(c);});}
 async function post(b){const r=await fetch('/api/addons',{method:'POST',body:JSON.stringify(b)});const d=await r.json();render(d.addons||[])}
 function add(){const u=document.getElementById('url').value.trim();if(u){post({action:'add',url:u});document.getElementById('url').value=''}}
 function move(a,i,dir){const j=i+dir;if(j<0||j>=a.length)return;const c=a.slice();const t=c[i];c[i]=c[j];c[j]=t;post({action:'setOrder',urls:c})}
 load();setInterval(load,4000);
</script></body></html>
""".trimIndent()

    companion object {
        /** Start on the first free port in [startPort, startPort+attempts). */
        fun startOnAvailablePort(
            currentAddons: () -> List<String>,
            onAdd: (String) -> Unit,
            onRemove: (String) -> Unit,
            onSetOrder: (List<String>) -> Unit,
            startPort: Int = 8723,
            attempts: Int = 12
        ): AddonConfigServer? {
            for (port in startPort until startPort + attempts) {
                val server = AddonConfigServer(port, currentAddons, onAdd, onRemove, onSetOrder)
                val ok = runCatching { server.start(SOCKET_READ_TIMEOUT, false); true }.getOrDefault(false)
                if (ok) return server
            }
            return null
        }
    }
}
