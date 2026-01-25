import { http } from "./HTTPClient.js";

export async function getData(pipeline, chart, payload) {
  const response = await http.post(
    `/data?pipelineId=${pipeline}&id=${chart}`,
    payload,
  );

  return response.json();
}
