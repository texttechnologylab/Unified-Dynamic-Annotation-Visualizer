import { api } from "./clients.js";

export async function getData(
  pipelineId,
  generatorId,
  chartType,
  page,
  size,
  filter,
) {
  return await api
    .post(
      `/data?pipelineId=${pipelineId}&generatorId=${generatorId}&chartType=${chartType}&page=${page}&size=${size}`,
      filter,
    )
    .then(async ({ data, meta }) => {
      if (meta.total === 0) {
        return {
          data: [await d3.json(`/data/${chartType}.json`)],
          meta: { total: 1, ids: ["1"] },
        };
      }
      return { data, meta };
    })
    .catch(async () => {
      return {
        data: [await d3.json(`/data/${chartType}.json`)],
        meta: { total: 1, ids: ["1"] },
      };
    });
}
