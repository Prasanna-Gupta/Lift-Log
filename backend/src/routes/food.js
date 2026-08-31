import 'dotenv/config';

const USDA_BASE = 'https://api.nal.usda.gov/fdc/v1';

// USDA nutrient IDs we care about — these are stable, well-known IDs in FDC's system.
// Energy: 1008 (kcal), Protein: 1003, Fat: 1004, Fiber: 1079
const NUTRIENT_IDS = {
  calories: 1008,
  protein: 1003,
  fat: 1004,
  fiber: 1079,
};

// The /foods/search endpoint returns foodNutrients as a FLAT array:
// [{ nutrientId, nutrientName, unitName, value }, ...]
function extractNutrientsFromSearchResult(foodNutrients = []) {
  const find = (id) => foodNutrients.find((n) => n.nutrientId === id)?.value ?? null;
  return {
    caloriesPer100g: find(NUTRIENT_IDS.calories),
    proteinPer100g: find(NUTRIENT_IDS.protein),
    fatPer100g: find(NUTRIENT_IDS.fat),
    fiberPer100g: find(NUTRIENT_IDS.fiber),
  };
}

// The /food/:id detail endpoint returns foodNutrients as a NESTED array:
// [{ nutrient: { id, name, unitName }, amount }, ...]
// This is the "gotcha" — same concept, different shape than the search endpoint.
function extractNutrientsFromDetailResult(foodNutrients = []) {
  const find = (id) => foodNutrients.find((n) => n.nutrient?.id === id)?.amount ?? null;
  return {
    caloriesPer100g: find(NUTRIENT_IDS.calories),
    proteinPer100g: find(NUTRIENT_IDS.protein),
    fatPer100g: find(NUTRIENT_IDS.fat),
    fiberPer100g: find(NUTRIENT_IDS.fiber),
  };
}

export default async function foodRoutes(fastify) {
  // GET /food/search?q=banana
  fastify.get('/food/search', async (request, reply) => {
    const { q } = request.query;
    if (!q || q.trim().length === 0) {
      return reply.code(400).send({ error: 'Query parameter "q" is required' });
    }

    const apiKey = process.env.USDA_API_KEY;
    if (!apiKey) {
      return reply.code(500).send({ error: 'USDA_API_KEY not configured on server' });
    }

    const url = `${USDA_BASE}/foods/search?query=${encodeURIComponent(q)}&pageSize=25&api_key=${apiKey}`;

    try {
      const res = await fetch(url);
      if (!res.ok) {
        return reply.code(res.status).send({ error: `USDA API error: ${res.statusText}` });
      }
      const data = await res.json();

      const results = (data.foods || []).map((food) => ({
        fdcId: food.fdcId,
        description: food.description,
        dataType: food.dataType,
        brandOwner: food.brandOwner || null,
        ...extractNutrientsFromSearchResult(food.foodNutrients),
      }));

      return results;
    } catch (err) {
      return reply.code(502).send({ error: `Failed to reach USDA API: ${err.message}` });
    }
  });

  // GET /food/:fdcId/portions
  fastify.get('/food/:fdcId/portions', async (request, reply) => {
    const { fdcId } = request.params;
    const apiKey = process.env.USDA_API_KEY;
    if (!apiKey) {
      return reply.code(500).send({ error: 'USDA_API_KEY not configured on server' });
    }

    const url = `${USDA_BASE}/food/${fdcId}?api_key=${apiKey}`;

    try {
      const res = await fetch(url);
      if (!res.ok) {
        return reply.code(res.status).send({ error: `USDA API error: ${res.statusText}` });
      }
      const data = await res.json();

      const baseNutrients = extractNutrientsFromDetailResult(data.foodNutrients);

      // foodPortions exists for Foundation/SR Legacy foods (whole/generic ingredients).
      // Branded foods instead carry servingSize/servingSizeUnit directly on the food object.
      // We normalize both into one "portions" list so the app doesn't need to know the difference.
      const portions = [];

      if (Array.isArray(data.foodPortions) && data.foodPortions.length > 0) {
        for (const p of data.foodPortions) {
         const unitName = p.measureUnit?.name && p.measureUnit.name.toLowerCase() !== 'undetermined'
           ? p.measureUnit.name
           : null;
         const label = [p.modifier, unitName]
           .filter(Boolean)
           .join(' ')
           .trim() || p.portionDescription || 'portion';
          if (p.gramWeight) {
            portions.push({ label, grams: p.gramWeight });
          }
        }
      }

      if (data.servingSize && data.servingSizeUnit) {
        const unit = data.servingSizeUnit.toLowerCase();
        if (unit === 'g' || unit === 'grm') {
          portions.push({ label: `1 serving (${data.servingSize}g)`, grams: data.servingSize });
        }
      }

      portions.push({ label: '100g', grams: 100 });

      return {
        fdcId: data.fdcId,
        description: data.description,
        ...baseNutrients,
        portions,
      };
    } catch (err) {
      return reply.code(502).send({ error: `Failed to reach USDA API: ${err.message}` });
    }
  });
}