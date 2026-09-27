package io.github.simpleornothing.healthmanager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.ValueCallback
import android.net.Uri
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class MainActivity:ComponentActivity(){
 private lateinit var repo:HealthConnectRepository; private lateinit var web:WebView
 private var fileCallback:ValueCallback<Array<Uri>>?=null
 private val filePicker=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){r->val cb=fileCallback?:return@registerForActivityResult;fileCallback=null;cb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode,r.data))}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);repo=HealthConnectRepository(this)
  web=WebView(this).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.cacheMode=WebSettings.LOAD_NO_CACHE;clearCache(true);webViewClient=WebViewClient();webChromeClient=object:WebChromeClient(){override fun onShowFileChooser(webView:WebView?,filePathCallback:ValueCallback<Array<Uri>>?,fileChooserParams:FileChooserParams?):Boolean{fileCallback?.onReceiveValue(null);fileCallback=filePathCallback;return try{val pick=Intent(Intent.ACTION_GET_CONTENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/*"};val camera=Intent(MediaStore.ACTION_IMAGE_CAPTURE);val chooser=Intent.createChooser(pick,"사진 선택 · 촬영").apply{putExtra(Intent.EXTRA_INITIAL_INTENTS,arrayOf(camera))};filePicker.launch(chooser);true}catch(e:Exception){fileCallback=null;false}}};addJavascriptInterface(Bridge(),"HealthManager");loadUrl("https://simpleornothing.github.io/health-manager/?app="+BuildConfig.VERSION_CODE)};setContentView(web)}
 private val permissionLauncher=registerForActivityResult(PermissionController.createRequestPermissionResultContract()){ refreshHealth() }
 inner class Bridge{
  @JavascriptInterface fun requestHealthPermissions(){ runOnUiThread { Toast.makeText(this@MainActivity,"Health Connect 권한을 확인합니다",Toast.LENGTH_SHORT).show(); lifecycleScope.launch { try { if(repo.hasPermissions()){ Toast.makeText(this@MainActivity,"Health Connect 권한이 이미 허용되어 있습니다",Toast.LENGTH_SHORT).show(); refreshHealth() } else { permissionLauncher.launch(repo.permissions) } } catch(e:Exception) { Toast.makeText(this@MainActivity,"Health Connect 오류: "+(e.message ?: "권한 요청 실패"),Toast.LENGTH_LONG).show(); web.evaluateJavascript("window.receiveHealthConnectError && window.receiveHealthConnectError("+JSONObject.quote(e.message ?: "Health Connect 권한 요청 실패")+")",null) } } } }
  @JavascriptInterface fun refreshHealthData()=refreshHealth()
  @JavascriptInterface fun syncHealthRecords(json:String){ lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO){ try { val c=(URL("https://health-manager-api.cw120-park.workers.dev/health").openConnection() as HttpURLConnection); c.requestMethod="POST"; c.setRequestProperty("content-type","application/json"); c.setRequestProperty("authorization","Bearer "+BuildConfig.HEALTH_API_TOKEN); c.doOutput=true; c.outputStream.use{it.write(json.toByteArray())}; val code=c.responseCode; runOnUiThread{web.evaluateJavascript("window.receiveServerSyncResult && window.receiveServerSyncResult("+code+")",null)}; c.disconnect() }catch(e:Exception){runOnUiThread{web.evaluateJavascript("window.receiveHealthConnectError && window.receiveHealthConnectError("+JSONObject.quote("서버 동기화 실패: "+(e.message?:"오류"))+")",null)}} } }
 }
 private fun refreshHealth(){lifecycleScope.launch{if(!repo.hasPermissions())return@launch;val s=repo.today();val j=JSONObject()
  j.put("glucose",s.glucoseMgDl);j.put("weight",s.weightKg);j.put("bodyFat",s.bodyFatPct);j.put("leanBodyMass",s.leanBodyMassKg);j.put("steps",s.steps);j.put("exerciseMinutes",s.exerciseMinutes);j.put("calories",s.caloriesKcal)
  val history=repo.history(30); val a=org.json.JSONArray()
  history.forEach{d->val o=JSONObject();o.put("date",d.date);o.put("glucose",d.glucoseMgDl);o.put("weight",d.weightKg);o.put("bodyFat",d.bodyFatPct);o.put("leanBodyMass",d.leanBodyMassKg);o.put("steps",d.steps);o.put("exerciseMinutes",d.exerciseMinutes);o.put("calories",d.caloriesKcal);a.put(o)}
  j.put("history",a)
  val diag=repo.diagnostics(); val dj=JSONObject(); diag.forEach{(k,v)->dj.put(k,v)}; j.put("diagnostics",dj)
  web.evaluateJavascript("window.receiveHealthConnectData && window.receiveHealthConnectData("+j.toString()+")",null)}}
}