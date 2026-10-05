package io.github.simpleornothing.healthmanager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.ValueCallback
import android.net.Uri
import android.content.ClipData
import android.content.Intent
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

class MainActivity:ComponentActivity(){
 private lateinit var repo:HealthConnectRepository; private lateinit var web:WebView
 private var healthRefreshRunning=false
 private var fileCallback:ValueCallback<Array<Uri>>?=null
 private var pendingCameraUri:Uri?=null
 private val filePicker=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){r->
  val cb=fileCallback?:return@registerForActivityResult
  fileCallback=null
  val selected=WebChromeClient.FileChooserParams.parseResult(r.resultCode,r.data)
  val cameraResult=if(r.resultCode==RESULT_OK&&selected.isNullOrEmpty()) pendingCameraUri?.let{arrayOf(it)}else null
  pendingCameraUri=null
  cb.onReceiveValue(cameraResult?:selected)
 }
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);repo=HealthConnectRepository(this)
  web=WebView(this).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.cacheMode=WebSettings.LOAD_NO_CACHE;clearCache(true);webViewClient=WebViewClient();webChromeClient=object:WebChromeClient(){override fun onShowFileChooser(webView:WebView?,filePathCallback:ValueCallback<Array<Uri>>?,fileChooserParams:FileChooserParams?):Boolean{fileCallback?.onReceiveValue(null);fileCallback=filePathCallback;return try{val pick=Intent(Intent.ACTION_GET_CONTENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/*"};val image=File(File(cacheDir,"camera").apply{mkdirs()},"meal-${UUID.randomUUID()}.jpg");val captureUri=FileProvider.getUriForFile(this@MainActivity,"${BuildConfig.APPLICATION_ID}.fileprovider",image);pendingCameraUri=captureUri;val camera=Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply{putExtra(MediaStore.EXTRA_OUTPUT,captureUri);addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION);clipData=ClipData.newRawUri("식사 사진",captureUri)};val chooser=Intent.createChooser(pick,"사진 선택 · 촬영").apply{putExtra(Intent.EXTRA_INITIAL_INTENTS,arrayOf(camera))};filePicker.launch(chooser);true}catch(e:Exception){pendingCameraUri=null;fileCallback=null;false}}};addJavascriptInterface(Bridge(),"HealthManager");loadUrl("https://simpleornothing.github.io/health-manager/?app="+BuildConfig.VERSION_CODE)};setContentView(web)}
 override fun onResume(){super.onResume();if(::web.isInitialized)web.post { web.evaluateJavascript("window.dispatchEvent(new Event('health-manager-resumed'))",null) }}
 private val permissionLauncher=registerForActivityResult(PermissionController.createRequestPermissionResultContract()){ refreshHealth() }
 inner class Bridge{
  @JavascriptInterface fun requestHealthPermissions(){ runOnUiThread { Toast.makeText(this@MainActivity,"Health Connect 권한을 확인합니다",Toast.LENGTH_SHORT).show(); lifecycleScope.launch { try { if(repo.hasPermissions()){ Toast.makeText(this@MainActivity,"Health Connect 권한이 이미 허용되어 있습니다",Toast.LENGTH_SHORT).show(); refreshHealth() } else { permissionLauncher.launch(repo.permissions) } } catch(e:Exception) { Toast.makeText(this@MainActivity,"Health Connect 오류: "+(e.message ?: "권한 요청 실패"),Toast.LENGTH_LONG).show(); web.evaluateJavascript("window.receiveHealthConnectError && window.receiveHealthConnectError("+JSONObject.quote(e.message ?: "Health Connect 권한 요청 실패")+")",null) } } } }
  @JavascriptInterface fun refreshHealthData()=refreshHealth()
  @JavascriptInterface fun writeHealthRecord(json:String){ lifecycleScope.launch { try {
   if(!repo.hasPermissions()) throw SecurityException("Health Connect 쓰기 권한을 허용해 주세요.")
   val o=JSONObject(json); val kind=o.getString("kind")
   repo.writeManual(kind,o.getString("id"),o.getString("date"),if(o.has("value"))o.optDouble("value") else null,o.optString("name").ifBlank{null},if(o.has("minutes"))o.optLong("minutes") else null,if(o.has("kcal"))o.optDouble("kcal") else null,if(o.has("carbs"))o.optDouble("carbs") else null,if(o.has("protein"))o.optDouble("protein") else null,if(o.has("fat"))o.optDouble("fat") else null,o.optString("mealType").ifBlank{null})
   web.evaluateJavascript("window.receiveHealthConnectWriteResult && window.receiveHealthConnectWriteResult(true,"+JSONObject.quote(kind)+")",null)
  }catch(e:Exception){ web.evaluateJavascript("window.receiveHealthConnectWriteResult && window.receiveHealthConnectWriteResult(false,"+JSONObject.quote(e.message?:"Health Connect 기록 실패")+")",null) } } }
  @JavascriptInterface fun loadHealthRecords(){ lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO){ try { val c=(URL("https://health-manager-api.cw120-park.workers.dev/health").openConnection() as HttpURLConnection); c.connectTimeout=15000; c.readTimeout=15000; c.requestMethod="GET"; c.setRequestProperty("authorization","Bearer "+BuildConfig.HEALTH_API_TOKEN); val code=c.responseCode; if(code !in 200..299){c.disconnect();throw java.io.IOException("HTTP $code")}; val body=BufferedReader(InputStreamReader(c.inputStream)).use{it.readText()}; runOnUiThread{web.evaluateJavascript("window.receiveServerRecords && window.receiveServerRecords("+body+")",null)}; c.disconnect() }catch(e:Exception){runOnUiThread{web.evaluateJavascript("window.receiveServerRestoreError && window.receiveServerRestoreError("+JSONObject.quote(e.message?:"오류")+")",null)}} } }
  @JavascriptInterface fun syncHealthRecords(json:String){ lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO){ try { val c=(URL("https://health-manager-api.cw120-park.workers.dev/health").openConnection() as HttpURLConnection); c.connectTimeout=15000; c.readTimeout=15000; c.requestMethod="POST"; c.setRequestProperty("content-type","application/json"); c.setRequestProperty("authorization","Bearer "+BuildConfig.HEALTH_API_TOKEN); c.doOutput=true; c.outputStream.use{it.write(json.toByteArray())}; val code=c.responseCode; runOnUiThread{web.evaluateJavascript("window.receiveServerSyncResult && window.receiveServerSyncResult("+code+")",null)}; c.disconnect() }catch(e:Exception){runOnUiThread{web.evaluateJavascript("window.receiveHealthConnectError && window.receiveHealthConnectError("+JSONObject.quote("서버 동기화 실패: "+(e.message?:"오류"))+")",null)}} } }
 }
 private fun refreshHealth(){runOnUiThread {
  if(healthRefreshRunning)return@runOnUiThread
  healthRefreshRunning=true
  lifecycleScope.launch {
   try {
    if(!repo.hasPermissions())throw SecurityException("Health Connect 읽기 권한을 확인해 주세요.")
    web.evaluateJavascript("window.receiveHealthConnectStatus && window.receiveHealthConnectStatus('건강 데이터를 읽고 있습니다…')",null)
    val s=repo.today();val j=JSONObject()
    j.put("glucose",s.glucoseMgDl?:JSONObject.NULL);j.put("weight",s.weightKg?:JSONObject.NULL);j.put("bodyFat",s.bodyFatPct?:JSONObject.NULL);j.put("leanBodyMass",s.leanBodyMassKg?:JSONObject.NULL);j.put("steps",s.steps);j.put("exerciseMinutes",s.exerciseMinutes);j.put("calories",s.caloriesKcal?:JSONObject.NULL)
    j.put("diagnostics",JSONObject(s.diagnostics));j.put("syncedAt",java.time.Instant.now().toString())
    // Publish current measurements before the slower history reads.
    web.evaluateJavascript("window.receiveHealthConnectData && window.receiveHealthConnectData("+j.toString()+")",null)
    try {
     val a=org.json.JSONArray()
     repo.history(30).forEach{d->val o=JSONObject();o.put("date",d.date);o.put("glucose",d.glucoseMgDl);o.put("weight",d.weightKg);o.put("bodyFat",d.bodyFatPct);o.put("leanBodyMass",d.leanBodyMassKg);o.put("steps",d.steps);o.put("exerciseMinutes",d.exerciseMinutes);o.put("calories",d.caloriesKcal);a.put(o)}
     j.put("history",a)
     web.evaluateJavascript("window.receiveHealthConnectData && window.receiveHealthConnectData("+j.toString()+")",null)
    }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){
     web.evaluateJavascript("window.receiveHealthConnectStatus && window.receiveHealthConnectStatus("+JSONObject.quote("현재 값 조회 완료 · 과거 기록 조회 실패: "+(e.message?:"오류"))+")",null)
    }
   }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){
    web.evaluateJavascript("window.receiveHealthConnectError && window.receiveHealthConnectError("+JSONObject.quote("건강 데이터 조회 실패: "+(e.message?:"오류"))+")",null)
   }finally{healthRefreshRunning=false}
  }
 }}
}
