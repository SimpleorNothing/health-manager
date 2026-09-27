const json=(data,status=200,origin="*")=>new Response(JSON.stringify(data),{status,headers:{"content-type":"application/json","access-control-allow-origin":origin,"access-control-allow-headers":"authorization,content-type","access-control-allow-methods":"GET,POST,OPTIONS"}});
function auth(req,env){const h=req.headers.get("authorization")||"";return env.HEALTH_API_TOKEN&&h===`Bearer ${env.HEALTH_API_TOKEN}`;}
export default {async fetch(req,env){
 const origin=req.headers.get("origin")||"*"; if(req.method==="OPTIONS")return json({ok:true},200,origin);
 const u=new URL(req.url);
 if(u.pathname!=="/analyze-meal"&&!auth(req,env))return json({error:"unauthorized"},401,origin);
 if(u.pathname==="/analyze-meal"&&req.method==="POST"){
  try{
   if(!env.OPENAI_API_KEY)return json({error:"openai_key_missing"},500,origin);
   const body=await req.json();if(!body.image)return json({error:"image_required"},400,origin);
   const imageUrl="data:"+(body.mimeType||"image/jpeg")+";base64,"+body.image;
   const prompt="Analyze this meal photo for a Korean health app. Identify visible foods and estimate portions, calories, carbohydrate, protein, fat and fiber. Estimate likely glucose impact from meal composition. Use Korean food names. Return only JSON.";
   const response=await fetch("https://api.openai.com/v1/responses",{method:"POST",headers:{"Authorization":"Bearer "+env.OPENAI_API_KEY,"Content-Type":"application/json"},body:JSON.stringify({model:"gpt-5.6-terra",reasoning:{effort:"low"},input:[{role:"user",content:[{type:"input_text",text:prompt},{type:"input_image",image_url:imageUrl,detail:"high"}]}],text:{format:{type:"json_object"}},max_output_tokens:1000})});
   const data=await response.json();if(!response.ok)return json({error:"openai_error",status:response.status,detail:data?.error?.message||"request_failed"},502,origin);
   let out=data.output_text||"";if(!out)for(const item of data.output||[])for(const part of item.content||[])if(part.type==="output_text")out+=part.text||"";
   const parsed=JSON.parse(out);parsed.model="gpt-5.6-terra";return json(parsed,200,origin);
  }catch(e){return json({error:"analysis_failed",detail:String(e.message||e)},502,origin)}
 }
 if(u.pathname==="/health"&&req.method==="GET"){const r=await env.DB.prepare("SELECT * FROM health_records ORDER BY recorded_at DESC LIMIT 2000").all();return json(r.results,200,origin);}
 if(u.pathname==="/health"&&req.method==="POST"){const body=await req.json();const rows=Array.isArray(body)?body:[body];const stmts=rows.filter(r=>r&&r.id&&r.recorded_at&&r.type&&r.source).map(r=>env.DB.prepare("INSERT INTO health_records(id,recorded_at,type,value,unit,source,payload) VALUES(?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET recorded_at=excluded.recorded_at,type=excluded.type,value=excluded.value,unit=excluded.unit,source=excluded.source,payload=excluded.payload").bind(r.id,r.recorded_at,r.type,r.value??null,r.unit??null,r.source,JSON.stringify(r.payload??null)));if(stmts.length)await env.DB.batch(stmts);return json({ok:true,count:stmts.length},200,origin);}
 return json({error:"not_found"},404,origin);
}};