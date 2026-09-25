package io.github.simpleornothing.healthmanager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity:ComponentActivity(){
 private lateinit var repo:HealthConnectRepository; private lateinit var web:WebView
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);repo=HealthConnectRepository(this)
  web=WebView(this).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;webViewClient=WebViewClient();webChromeClient=WebChromeClient();addJavascriptInterface(Bridge(),"HealthManager");loadUrl("https://simpleornothing.github.io/health-manager/")};setContentView(web)}
 private val permissionLauncher=registerForActivityResult(PermissionController.createRequestPermissionResultContract()){ refreshHealth() }
 inner class Bridge{
  @JavascriptInterface fun requestHealthPermissions(){ runOnUiThread { Toast.makeText(this@MainActivity,"Health Connect 권한을 확인합니다",Toast.LENGTH_SHORT).show(); lifecycleScope.launch { try { if(repo.hasPermissions()){ Toast.makeText(this@MainActivity,"Health Connect 권한이 이미 허용되어 있습니다",Toast.LENGTH_SHORT).show(); refreshHealth() } else { permissionLauncher.launch(repo.permissions) } } catch(e:Exception) { Toast.makeText(this@MainActivity,"Health Connect 오류: "+(e.message ?: "권한 요청 실패"),Toast.LENGTH_LONG).show(); web.evaluateJavascript("window.receiveHealthConnectError && window.receiveHealthConnectError("+JSONObject.quote(e.message ?: "Health Connect 권한 요청 실패")+")",null) } } } }
  @JavascriptInterface fun refreshHealthData()=refreshHealth()
 }
 private fun refreshHealth(){lifecycleScope.launch{if(!repo.hasPermissions())return@launch;val s=repo.today();val j=JSONObject()
  j.put("glucose",s.glucoseMgDl);j.put("weight",s.weightKg);j.put("bodyFat",s.bodyFatPct);j.put("leanBodyMass",s.leanBodyMassKg);j.put("steps",s.steps);j.put("exerciseMinutes",s.exerciseMinutes);j.put("calories",s.caloriesKcal)
  val history=repo.history(30); val a=org.json.JSONArray()
  history.forEach{d->val o=JSONObject();o.put("date",d.date);o.put("glucose",d.glucoseMgDl);o.put("weight",d.weightKg);o.put("bodyFat",d.bodyFatPct);o.put("leanBodyMass",d.leanBodyMassKg);o.put("steps",d.steps);o.put("exerciseMinutes",d.exerciseMinutes);o.put("calories",d.caloriesKcal);a.put(o)}
  j.put("history",a)
  val diag=repo.diagnostics(); val dj=JSONObject(); diag.forEach{(k,v)->dj.put(k,v)}; j.put("diagnostics",dj)
  web.evaluateJavascript("window.receiveHealthConnectData && window.receiveHealthConnectData("+j.toString()+")",null)}}
}