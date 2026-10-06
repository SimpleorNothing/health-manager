import assert from 'node:assert/strict';
import {scalePortion,prepareMealItems,totalMealItems,scaleMealText} from '../src/portions.js';
const original={items:[{name:'구운 아몬드',portion:'1봉(40g)'}],kcal:230,carbs:8,protein:9,fat:20,fiber:4};
const item=prepareMealItems(original)[0];
for(const amount of ['반봉지','1/2봉(20g)','0.5봉','20g']){
 const half=scalePortion(item,amount);assert.equal(half.kcal,115);assert.equal(half.protein,4.5);
 assert.equal(scalePortion(half,'1봉(40g)').kcal,230);
 assert.equal(scalePortion(half,'1/4봉').kcal,57.5);
}
assert.equal(scaleMealText(original,'구운 아몬드 1봉(40g)','구운 아몬드 반봉지').kcal,115);
assert.equal(scaleMealText(original,'구운 아몬드 1봉(40g)','다른 음식 반봉지'),null);
assert.equal(scalePortion(item,'조금'),null);
const other={name:'우유',portion:'1컵',kcal:130,carbs:10,protein:7,fat:7,fiber:0};
assert.equal(totalMealItems(original,[scalePortion(item,'반봉지'),other]).kcal,245);
assert.equal(totalMealItems(original,[other]).kcal,130);
assert.equal(totalMealItems(original,[]).kcal,0);
console.log('PASS: half portions, repeated edits, text edits, mixed meals and deletion');

assert.equal(scaleMealText({...original,items:undefined},'구운 아몬드 1봉(40g)','구운 아몬드 반봉지').kcal,115);
