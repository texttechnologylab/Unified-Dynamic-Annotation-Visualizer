import { api } from "./clients.js";

export async function getData(pipelineId, generatorId, chartType, filter) {
  return await api.post(
    `/data?pipelineId=${pipelineId}&generatorId=${generatorId}&chartType=${chartType}`,
    filter,
  );
}
