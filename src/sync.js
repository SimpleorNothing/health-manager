// Persisted outbox and one request at a time prevent lost/offline updates.
export function installSync(win, storage, rowsFor, empty) {
  const bridge=win.HealthManager;
  if(!bridge?.syncHealthRecords)return;
  const rawSet=storage.setItem.bind(storage), keys=['healthData','healthConnectData'];
  let ready=false, loading=false, active=null, retry=0, watchdog=0;
  const read=()=>({healthData:JSON.parse(storage.getItem(keys[0])||JSON.stringify(empty)),healthConnectData:JSON.parse(storage.getItem(keys[1])||'{}')});
  const status=message=>{win.healthServerStatus=message;win.dispatchEvent(new win.CustomEvent('health-server-status',{detail:message}))};
  const later=()=>{win.clearTimeout(retry);retry=win.setTimeout(flush,5000)};
  const load=()=>{if(loading)return;loading=true;status('기기 저장 유지 · 서버 연결 확인 중');watchdog=win.setTimeout(()=>win.receiveServerRestoreError(),20000);try{bridge.loadHealthRecords()}catch(e){win.receiveServerRestoreError()}};
  function flush(){
    if(active)return;
    if(!ready){load();return}
    if(storage.getItem('health-sync-pending')!=='1')return;
    active=JSON.stringify(read());
    const state=JSON.parse(active),rows=rowsFor(state.healthData,state.healthConnectData);
    rows.push({id:'app-state-v1',recorded_at:new Date().toISOString(),type:'app_state',source:'app',payload:state});
    status('기기 저장 완료 · 서버 저장 중…');
    watchdog=win.setTimeout(()=>win.receiveServerSyncResult(0),60000);
    try{bridge.syncHealthRecords(JSON.stringify({replace:true,rows}))}catch(e){win.receiveServerSyncResult(0)}
  }
  const persist=(key,value)=>{const changed=keys.includes(key)&&storage.getItem(key)!==String(value);rawSet(key,value);if(changed){rawSet('health-sync-pending','1');status('기기 저장 완료 · 서버 저장 대기');flush()}};
  if(win.Storage){const original=win.Storage.prototype.setItem;win.Storage.prototype.setItem=function(key,value){if(this===storage)return persist(key,value);return original.call(this,key,value)}}else storage.setItem=persist;
  win.receiveServerRecords=rows=>{
    win.clearTimeout(watchdog);loading=false;
    try{
      if(!Array.isArray(rows))throw Error('invalid records');
      const snapshot=rows.find(r=>r.id==='app-state-v1');
      if(storage.getItem('health-sync-pending')!=='1'){
        const current=read();
        if(snapshot){const state=typeof snapshot.payload==='string'?JSON.parse(snapshot.payload):snapshot.payload;if(!state?.healthData||!state?.healthConnectData)throw Error('invalid snapshot');for(const key of keys)rawSet(key,JSON.stringify(state[key]))}
        else{
          for(const r of rows){const item=typeof r.payload==='string'?JSON.parse(r.payload):r.payload;const kind={glucose:'glucose',weight:'weight',meal:'meals',exercise:'exercise'}[r.type];if(kind&&item?.id){const list=current.healthData[kind]||[];if(!list.some(x=>String(x.id)===String(item.id)))list.push(item);current.healthData[kind]=list}}
          rawSet('healthData',JSON.stringify(current.healthData));rawSet('health-sync-pending','1');
        }
        win.dispatchEvent(new win.CustomEvent('health-data-restored',{detail:read()}));
      }
      ready=true;status('기기·서버 데이터 불러오기 완료');flush();
    }catch(e){win.receiveServerRestoreError()}
  };
  win.receiveServerRestoreError=()=>{win.clearTimeout(watchdog);loading=false;ready=false;status('기기 저장 유지 · 서버 연결 실패, 자동 재시도');later()};
  win.receiveServerSyncResult=code=>{
    if(!active)return;
    win.clearTimeout(watchdog);
    const sent=active;active=null;
    if(code>=200&&code<300){if(JSON.stringify(read())===sent){rawSet('health-sync-pending','0');rawSet('health-sync-saved-at',new Date().toISOString());status('기기·D1 서버 저장 완료 · '+new Date().toLocaleTimeString('ko-KR'))}else flush()}
    else{status('기기 저장 완료 · 서버 저장 실패, 자동 재시도');later()}
  };
  win.retryServerSync=()=>{rawSet('health-sync-pending','1');flush()};
  win.addEventListener('online',flush);
  win.document.addEventListener('visibilitychange',()=>{if(win.document.visibilityState==='visible')flush()});
  load();
}
