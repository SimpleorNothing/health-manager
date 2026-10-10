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

function foodMatch(text, food) {
  const escaped = food.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  if (!escaped) return null;
  return new RegExp(`(^|[,\\n]\\s*)(${escaped})(?=\\s*(?:[,\\n]|$))`).exec(text);
}

export function isFavoriteSelected(text = '', food = '') {
  return !!foodMatch(text, food);
}

export function toggleFavoriteFood(text = '', food = '') {
  const value = food.trim();
  if (!value) return text;
  const match = foodMatch(text, value);
  if (!match) return [text.trim(), value].filter(Boolean).join(', ');
  const before = text.slice(0, match.index).trim().replace(/[,\n]\s*$/, '').trim();
  const after = text.slice(match.index + match[0].length).trim().replace(/^[,\n]\s*/, '').trim();
  return [before, after].filter(Boolean).join(', ');
}
