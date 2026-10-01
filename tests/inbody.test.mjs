import test from 'node:test';
import assert from 'node:assert/strict';
import {parseInBodyCsv,mergeInBody} from '../src/inbody.js';
const csv='\uFEFF날짜,측정장비,체중(kg),골격근량(kg),체지방률(%),기초대사량(kcal)\r\n20261001203000,"test,device",70,28,22,1500\r\n20260901103000,test,69,27,-,1490\r\n';
test('parses BOM, quotes, original timestamps and missing values',()=>{const r=parseInBodyCsv(csv);assert.equal(r.length,2);assert.equal(r[0].bodyFat,null);assert.equal(r[1].measuredAt,'2026-10-01T20:30:00+09:00');assert.equal(r[1].device,'test,device');assert.equal(r[1].skeletalMuscleMass,28)});
test('reimport is idempotent and preserves existing records and profile',()=>{const old={weight:[{id:'manual',date:'2026-10-02',value:'71'}],glucose:[{value:100}],nutrition:{bmr:'1600'}};const r=parseInBodyCsv(csv),next=mergeInBody(old,r);assert.deepEqual(mergeInBody(next,r),next);assert.equal(next.weight.length,3);assert.deepEqual(next.glucose,old.glucose);assert.equal(next.nutrition.bmr,'1600');assert.equal(mergeInBody({weight:[]},r).nutrition.bmr,'1500')});
test('rejects malformed dates, headers and nonnumeric measurements',()=>{assert.throws(()=>parseInBodyCsv('bad'));assert.throws(()=>parseInBodyCsv(csv.replace('20261001203000','20260230203000')));assert.throws(()=>parseInBodyCsv(csv.replace(',70,',',invalid,')))});
