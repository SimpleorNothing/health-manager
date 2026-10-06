const nutrients=['kcal','carbs','protein','fat','fiber'];
export function portionQuantity(text){
  const s=String(text||'').replace(/\s/g,'').replace(/봉지/g,'봉').replace(/반/g,'0.5').replace(/한(?=봉|개|공기|인분|조각|컵)/g,'1');
  const quantities=[];
  const re=/(\d+(?:\.\d+)?)(?:\/(\d+(?:\.\d+)?))?(kg|mg|g|ml|봉|개|공기|인분|조각|컵)/gi;
  for(const m of s.matchAll(re)){
    let value=Number(m[1])/(m[2]?Number(m[2]):1),unit=m[3].toLowerCase();
    if(unit==='kg'){value*=1000;unit='g'}if(unit==='mg'){value/=1000;unit='g'}
    if(Number.isFinite(value)&&value>0)quantities.push({value,unit});
  }
  return quantities;
}
export function scalePortion(item,portion){
  const base=item._portionBase||{portion:item.portion,...Object.fromEntries(nutrients.map(k=>[k,Number(item[k])||0]))};
  const before=portionQuantity(base.portion),after=portionQuantity(portion);
  const old=before.find(a=>after.some(b=>a.unit===b.unit));
  if(!old)return null;
  const ratio=after.find(a=>a.unit===old.unit).value/old.value;
  return {...item,portion,_portionBase:base,...Object.fromEntries(nutrients.map(k=>[k,base[k]*ratio]))};
}
export function prepareMealItems(analysis){
  const items=analysis.items||[];
  // Older photo responses contain only meal totals; one food owns the entire total.
  return items.length===1?items.map(item=>({...Object.fromEntries(nutrients.map(k=>[k,Number(analysis[k])||0])),...item})):items;
}
export function totalMealItems(analysis,items){
  return {...analysis,items,...Object.fromEntries(nutrients.map(k=>[k,Math.round(items.reduce((sum,item)=>sum+(Number(item[k])||0),0))]))};
}
export function scaleMealText(analysis,before,after){
  const oldParts=before.split(/[,，]/).map(s=>s.trim()),newParts=after.split(/[,，]/).map(s=>s.trim());
  let items=prepareMealItems(analysis);
  if(!items.length&&oldParts.length===1){
    const match=oldParts[0].match(/^(.*?)\s*((?:반|한|\d+(?:\.\d+)?(?:\/\d+(?:\.\d+)?)?)\s*(?:봉지?|개|공기|인분|조각|컵|kg|mg|g|ml).*)$/i);
    if(match)items=prepareMealItems({...analysis,items:[{name:match[1].trim(),portion:match[2]}]});
  }
  if(items.length!==oldParts.length||items.length!==newParts.length)return null;
  const updated=[];
  for(let i=0;i<items.length;i++){
    const item=items[i];
    if(!newParts[i].startsWith(item.name)||!oldParts[i].startsWith(item.name)||!nutrients.every(k=>Number.isFinite(Number(item[k]))))return null;
    const portion=newParts[i].slice(item.name.length).trim();
    if(portion===item.portion){updated.push(item);continue}
    const next=scalePortion(item,portion);if(!next)return null;updated.push(next);
  }
  return totalMealItems(analysis,updated);
}
