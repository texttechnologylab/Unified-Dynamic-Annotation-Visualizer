import { api } from "./clients.js";

export async function getData(pipelineId, generatorId, chartType, filter) {
  return await api
    .post(
      `/data?pipelineId=${pipelineId}&generatorId=${generatorId}&chartType=${chartType}`,
      filter,
    )
    .then((data) => {
      return data;
    })
    .catch(async () => {
      return await d3.json(`/data/${chartType}.json`);
    });
}
