import { api } from "./clients.js";

export async function getData(
  pipelineId,
  generatorId,
  chartType,
  page,
  filter,
) {
  return await api
    .post(
      `/data?pipelineId=${pipelineId}&generatorId=${generatorId}&chartType=${chartType}&page=${page}`,
      filter,
    )
    .then((data) => {
      return data;
    })
    .catch(async () => {
      return {
        data: [await d3.json(`/data/${chartType}.json`)],
        meta: { total: 1, ids: ["1"] },
      };
    });
}

export async function exportData(
  pipelineId,
  generatorId,
  chartType,
  format,
  filter,
) {
  return await api.post(
    `/data/export?pipelineId=${pipelineId}&generatorId=${generatorId}&chartType=${chartType}&format=${format}`,
    filter,
    "blob",
  );
}
