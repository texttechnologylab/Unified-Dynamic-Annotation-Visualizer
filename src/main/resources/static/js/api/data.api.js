import { api } from "./clients.js";

// Bundled sample data instead of the real dataset. Only the editor opts in (it previews
// widgets before their generators are built); a view and its exports never show it.
async function sampleData(chartType) {
  return {
    data: [await d3.json(`/data/${chartType}.json`)],
    meta: { total: 1, ids: ["1"] },
  };
}

export async function getData(
  pipelineId,
  generatorId,
  chartType,
  page,
  size,
  filter,
) {
  let result;
  try {
    result = await api.post(
      `/data?pipelineId=${pipelineId}&generatorId=${generatorId}&chartType=${chartType}&page=${page}&size=${size}`,
      filter,
    );
  } catch (error) {
    if (globalThis.__UDAV_SAMPLE_DATA__) return sampleData(chartType);
    throw error;
  }

  if (result.meta.total === 0) {
    if (globalThis.__UDAV_SAMPLE_DATA__) return sampleData(chartType);
    throw new Error("No data for this widget");
  }

  return result;
}
