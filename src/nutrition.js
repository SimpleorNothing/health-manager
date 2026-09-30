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
  const delta=Number(p.goalWeight)-w;
  // Modest product default, shown explicitly; goal weight never replaces current weight in BMR.
  const adjustment=bmr&&validWeight(p.goalWeight)?Math.abs(delta)<=.5?0:delta>0?200:-200:0;
  const kcal=Math.round(bmr?Math.max(bmr,bmr*activity+adjustment):w*30);
  const protein=Math.round(w*1.2),fat=Math.round(kcal*.25/9),carbs=Math.round(Math.max(0,kcal-protein*4-fat*9)/4);
  return {kcal,protein,fat,carbs,bmr:bmr===null?null:Math.round(bmr),activity,adjustment,source,weight,configured:bmr!==null};
}
export function intakeStatus(value,target,complete){
  if(!complete)return value<target?'남은 '+Math.round(target-value):value>target?'목표 초과 '+Math.round(value-target):'목표 도달';
  return value<target*.8?'부족':value>target*1.2?'과다':'적정';
}
