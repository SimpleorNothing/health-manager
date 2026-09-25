package io.github.simpleornothing.healthmanager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity:ComponentActivity(){
 private lateinit var repo:HealthConnectRepository; private lateinit var web:WebView
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);repo=HealthConnectRepository(this)
  web=WebView(this).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;webViewClient=WebViewClient();addJavascriptInterface(Bridge(),"HealthManager");loadUrl("https://simpleornothing.github.io/health-manager/")};setContentView(web)}
 private val permissionLauncher=registerForActivityResult(PermissionController.createRequestPermissionResultContract()){refreshHealth()}
 inner class Bridge{
  @JavascriptInterface fun requestHealthPermissions()=runOnUiThread{lifecycleScope.launch{if(repo.hasPermissions())refreshHealth() else permissionLauncher.launch(repo.permissions)}}
  @JavascriptInterface fun refreshHealthData()=refreshHealth()
 }
 private fun refreshHealth(){lifecycleScope.launch{if(!repo.hasPermissions())return@launch;val s=repo.today();val j=JSONObject()
  j.put("glucose",s.glucoseMgDl);j.put("weight",s.weightKg);j.put("bodyFat",s.bodyFatPct);j.put("steps",s.steps);j.put("exerciseMinutes",s.exerciseMinutes);j.put("calories",s.caloriesKcal)
  web.evaluateJavascript("window.receiveHealthConnectData && window.receiveHealthConnectData("+j.toString()+")",null)}}
}