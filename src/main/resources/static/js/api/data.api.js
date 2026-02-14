import { api } from "./clients.js";

export async function getData(pipeline, chart, payload) {
  return await api.post(`/data?pipelineId=${pipeline}&id=${chart}`, payload);
}
