// Show the newest saved version of each food description without changing records.
export function mealHistoryOptions(meals = [], query = '') {
  const key = food => food.trim().replace(/\s+/g, ' ').toLocaleLowerCase();
  const seen = new Set();
  return meals.map((meal, index) => ({meal, index}))
    .filter(({meal}) => typeof meal.food === 'string' && meal.food.trim())
    .sort((a, b) => String(b.meal.date || '').localeCompare(String(a.meal.date || '')) ||
      String(b.meal.recordedAt || '').localeCompare(String(a.meal.recordedAt || '')) || b.index - a.index)
    .map(({meal}) => meal)
    .filter(meal => {
      const food = key(meal.food);
      if (seen.has(food)) return false;
      seen.add(food);
      return food.includes(key(query));
    });
}

export function copyMealHistory(meal) {
  return {food: meal.food.trim(), analysis: meal.analysis ? JSON.parse(JSON.stringify(meal.analysis)) : null};
}
