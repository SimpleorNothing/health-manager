package io.github.simpleornothing.healthmanager
import android.app.Activity
import android.os.Bundle
import android.widget.TextView
class PermissionsRationaleActivity:Activity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContentView(TextView(this).apply{text="건강관리 앱은 사용자가 허용한 혈당, 체중, 체지방, 걸음 수, 운동 및 소모 칼로리만 Health Connect에서 읽어 일일 건강 현황을 표시합니다. 권한은 언제든 취소할 수 있습니다.";textSize=18f;setPadding(48,80,48,48)})}}