const json=(data,status=200,origin="*")=>new Response(JSON.stringify(data),{status,headers:{"content-type":"application/json","access-control-allow-origin":origin,"access-control-allow-headers":"authorization,content-type","access-control-allow-methods":"GET,POST,OPTIONS"}});
function auth(req,env){const h=req.headers.get("authorization")||"";return env.HEALTH_API_TOKEN&&h===`Bearer ${env.HEALTH_API_TOKEN}`;}
export default {async fetch(req,env){
 const origin=req.headers.get("origin")||"*"; if(req.method==="OPTIONS")return json({ok:true},200,origin);
 if(!auth(req,env))return json({error:"unauthorized"},401,origin);
 const u=new URL(req.url);
 if(u.pathname==="/health"&&req.method==="GET"){const r=await env.DB.prepare("SELECT * FROM health_records ORDER BY recorded_at DESC LIMIT 2000").all();return json(r.results,200,origin);}
 if(u.pathname==="/health"&&req.method==="POST"){const body=await req.json();const rows=Array.isArray(body)?body:[body];const stmts=rows.filter(r=>r&&r.id&&r.recorded_at&&r.type&&r.source).map(r=>env.DB.prepare("INSERT INTO health_records(id,recorded_at,type,value,unit,source,payload) VALUES(?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET recorded_at=excluded.recorded_at,type=excluded.type,value=excluded.value,unit=excluded.unit,source=excluded.source,payload=excluded.payload").bind(r.id,r.recorded_at,r.type,r.value??null,r.unit??null,r.source,JSON.stringify(r.payload??null)));if(stmts.length)await env.DB.batch(stmts);return json({ok:true,count:stmts.length},200,origin);}
 return json({error:"not_found"},404,origin);
}};