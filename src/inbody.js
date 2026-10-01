// Imported measurements keep their original Korea-local timestamp and source.
export function parseInBodyCsv(text) {
  const rows=[];let row=[],cell='',quoted=false;
  const input=String(text).replace(/^\uFEFF/,'');
  for(let i=0;i<input.length;i++){
    const c=input[i];
    if(c==='"'){if(quoted&&input[i+1]==='"'){cell+='"';i++}else quoted=!quoted}
    else if(!quoted&&(c===','||c==='\n'||c==='\r')){
      row.push(cell);cell='';if(c!==','){if(row.some(x=>x.trim()))rows.push(row);row=[];if(c==='\r'&&input[i+1]==='\n')i++}
    }else cell+=c;
  }
  if(quoted)throw Error('CSV 따옴표가 닫히지 않았습니다.');
  row.push(cell);if(row.some(x=>x.trim()))rows.push(row);
  const headers=rows.shift()?.map(x=>x.trim())||[];
  for(const key of ['날짜','체중(kg)','골격근량(kg)','체지방률(%)','기초대사량(kcal)'])if(!headers.includes(key))throw Error('인바디 CSV 항목을 확인하세요: '+key);
  const records=rows.map((values,index)=>{
    const raw=Object.fromEntries(headers.map((h,i)=>[h,values[i]?.trim()||''])),stamp=raw['날짜'];
    if(!/^\d{14}$/.test(stamp))throw Error((index+2)+'행의 측정일 형식이 올바르지 않습니다.');
    const date=stamp.slice(0,4)+'-'+stamp.slice(4,6)+'-'+stamp.slice(6,8);
    const measuredAt=date+'T'+stamp.slice(8,10)+':'+stamp.slice(10,12)+':'+stamp.slice(12,14)+'+09:00';
    const check=new Date(measuredAt);
    if(!Number.isFinite(check.getTime())||new Date(check.getTime()+9*3600000).toISOString().slice(0,19)!==measuredAt.slice(0,19))throw Error((index+2)+'행의 측정일이 유효하지 않습니다.');
    const number=key=>{const value=raw[key];if(!value||value==='-')return null;const n=Number(value);if(!Number.isFinite(n))throw Error((index+2)+'행의 숫자를 확인하세요: '+key);return n};
    const weight=number('체중(kg)');if(weight===null||weight<=0)throw Error((index+2)+'행의 체중을 확인하세요.');
    return {id:'inbody-'+stamp+'-'+raw['측정장비'],date,measuredAt,source:'inbody-csv',device:raw['측정장비'],weight,skeletalMuscleMass:number('골격근량(kg)'),bodyFat:number('체지방률(%)'),bodyFatMass:number('체지방량(kg)'),bmr:number('기초대사량(kcal)'),bmi:number('BMI(kg/m²)'),raw};
  });
  if(!records.length)throw Error('측정 기록이 없습니다.');
  return [...new Map(records.map(r=>[r.id,r])).values()].sort((a,b)=>a.measuredAt.localeCompare(b.measuredAt));
}
export function mergeInBody(data,records){
  const inbody=[...new Map([...(data.inbody||[]),...records].map(r=>[r.id,r])).values()].sort((a,b)=>a.measuredAt.localeCompare(b.measuredAt));
  const imported=records.map(r=>({...r,value:String(r.weight)}));
  const weight=[...new Map([...(data.weight||[]),...imported].map(r=>[String(r.id),r])).values()].sort((a,b)=>a.date.localeCompare(b.date)||String(a.measuredAt||'').localeCompare(String(b.measuredAt||'')));
  const latest=inbody.at(-1),nutrition={...data.nutrition};
  // An explicit profile/BMR remains authoritative; initialize only a blank BMR.
  if(!nutrition.bmr&&!nutrition.age&&latest.bmr)nutrition.bmr=String(latest.bmr);
  return {...data,inbody,weight,nutrition};
}
