export const defaultNutrition = {bmr:'', age:'', sex:'', height:'', activity:'1.2', goalWeight:'63.6'};
export const activityLevels = [['1.2','주로 앉아서 생활'],['1.375','가벼운 활동 · 주 1–3일 운동'],['1.55','보통 활동 · 주 3–5일 운동'],['1.725','활발한 활동 · 주 6–7일 운동']];
const validWeight = v => Number.isFinite(Number(v)) && Number(v)>0;
export function latestWeight(d,hc,today){
  const rows=[...(hc.history||[]).map(x=>({...x,value:x.weight})),...(d.weight||[])];
  // Stable sort preserves the last manual entry on the same date.
  const row=rows.filter(x=>x.date<=today&&validWeight(x.value)).sort((a,b)=>a.date.localeCompare(b.date)).at(-1);
  if(row)return {value:Number(row.value),label:row.date+' 기록'};
  if(validWeight(hc.weight))return {value:Number(hc.weight),label:'Health Connect'};
  return {value:61.1,label:'임시 기본 체중 · 체중 기록 필요'};
}
export function nutritionTargets(d,hc,today){
  const p={...defaultNutrition,...d.nutrition},weight=latestWeight(d,hc,today),w=weight.value;
  const activity=activityLevels.some(([v])=>v===String(p.activity))?Number(p.activity):1.2;
  let bmr=null,source='임시 참고치 · 체중 × 30';
  if(Number(p.bmr)>=500&&Number(p.bmr)<=4000){bmr=Number(p.bmr);source='입력한 인바디 기초대사량'}
  else if(Number(p.age)>=18&&Number(p.age)<=120&&Number(p.height)>=100&&Number(p.height)<=250&&['male','female'].includes(p.sex)){
    bmr=10*w+6.25*Number(p.height)-5*Number(p.age)+(p.sex==='male'?5:-161);source='Mifflin–St Jeor 추정 기초대사량';
  }
  const goalWeight=Number(p.goalWeight)>=30&&Number(p.goalWeight)<=300?Number(p.goalWeight):w;
  // Estimate maintenance at the goal weight using the Mifflin weight coefficient.
  // For a manually entered BMR, anchor that estimate to the measured/current BMR.
  const goalBmr=bmr===null?null:Math.max(0,bmr+10*(goalWeight-w));
  const maintenanceKcal=Math.round(bmr===null?w*30:bmr*activity);
  const rawGoalKcal=bmr===null?goalWeight*30:goalBmr*activity;
  const kcal=Math.round(bmr===null?rawGoalKcal:Math.max(bmr,rawGoalKcal));
  const adjustment=kcal-maintenanceKcal;
  const protein=Math.round(w*1.2),fat=Math.round(kcal*.25/9),carbs=Math.round(Math.max(0,kcal-protein*4-fat*9)/4);
  return {kcal,protein,fat,carbs,bmr:bmr===null?null:Math.round(bmr),activity,adjustment,goalWeight,goalBmr:goalBmr===null?null:Math.round(goalBmr),maintenanceKcal,floorApplied:bmr!==null&&rawGoalKcal<bmr,source,weight,configured:bmr!==null};
}
export function intakeStatus(value,target,complete){
  if(!complete)return value<target?'남은 '+Math.round(target-value):value>target?'목표 초과 '+Math.round(value-target):'목표 도달';
  return value<target*.8?'부족':value>target*1.2?'과다':'적정';
}

// Display thresholds describe progress against an app target, not medical risk.
export function intakeDisplay(key,value,target,complete,shown=true,unit='g'){
  if(!shown||!Number.isFinite(value)||!Number.isFinite(target)||target<=0)return {tone:'neutral',label:'기록 필요',note:''};
  const diff=Math.round(Math.abs(value-target));
  if(key==='exercise')return value>=target?{tone:'good',label:'✓ 목표 달성',note:'운동 목표를 채웠습니다.'}:{tone:'neutral',label:`남은 ${diff}${unit}`,note:''};
  if(value>target){
    if(key==='protein')return {tone:'info',label:`참고 · 목표 초과 ${diff}${unit}`,note:'목표 초과만으로 해롭다고 판단하지 않습니다. 신장질환으로 단백질 제한을 안내받았다면 개인 기준을 따르세요.'};
    const notes={kcal:'반복해서 초과하면 목표 체중 관리에 불리할 수 있어요. 며칠간 평균 섭취량과 체중 변화를 확인하세요.',carbs:'한 끼에 몰아 먹었는지, 당류·정제 탄수화물이 많았는지 식후 혈당과 함께 확인하세요.',fat:'총칼로리와 지방의 종류를 확인하세요. 포화지방이 많은 음식의 양을 점검하세요.'};
    return {tone:value>target*1.2?'over':'caution',label:`⚠ ${value>target*1.2?'초과량 점검':'목표 초과'} ${diff}${unit}`,note:notes[key]||'식사량과 음식 구성을 확인하세요.'};
  }
  if(complete)return value<target*.8?{tone:'caution',label:'섭취 부족',note:'하루 기록이 빠짐없이 입력되었는지 확인하세요.'}:{tone:'good',label:'목표 범위',note:''};
  return {tone:value===target?'good':'neutral',label:value===target?'목표 도달':`남은 ${diff}${unit}`,note:''};
}
