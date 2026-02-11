import { api } from "./clients.js";

export async function getData(pipeline, chart, payload) {
  const response = await api.post(
    `/data?pipelineId=${pipeline}&id=${chart}`,
    payload,
  );

  return response.json();
}
