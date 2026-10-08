// This list is user-authored and independent of the meal log.
export function saveFavoriteFood(foods = [], text, id = null) {
  const food = String(text || '').trim().replace(/\s+/g, ' ');
  if (!food) return foods;
  const duplicate = foods.find(x => x.id !== id && x.food === food);
  if (duplicate) return foods;
  if (id != null) return foods.map(x => x.id === id ? {...x, food} : x);
  return [...foods, {id: globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`, food}];
}

export function deleteFavoriteFood(foods = [], id) {
  return foods.filter(x => x.id !== id);
}
