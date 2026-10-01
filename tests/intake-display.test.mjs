import assert from 'node:assert/strict';
import {intakeDisplay} from '../src/nutrition.js';
for(const complete of [false,true]){
  for(const key of ['kcal','carbs','fat']){
    assert.equal(intakeDisplay(key,101,100,complete).tone,'caution');
    assert.equal(intakeDisplay(key,120,100,complete).tone,'caution');
    assert.equal(intakeDisplay(key,121,100,complete).tone,'over');
  }
  assert.equal(intakeDisplay('protein',150,100,complete).tone,'info');
  assert.equal(intakeDisplay('exercise',32,30,complete,true,'분').tone,'good');
}
assert.equal(intakeDisplay('kcal',79,100,false).tone,'neutral');
assert.equal(intakeDisplay('kcal',79,100,true).label,'섭취 부족');
assert.equal(intakeDisplay('kcal',80,100,true).tone,'good');
assert.equal(intakeDisplay('kcal',0,100,false,false).label,'기록 필요');
assert.equal(intakeDisplay('kcal',100,0,true).label,'기록 필요');
assert.match(intakeDisplay('kcal',2130,1719,false,true,'kcal').label,/411kcal/);
console.log('PASS: recording/completion, excess boundaries, protein, exercise, missing targets and screenshot');
