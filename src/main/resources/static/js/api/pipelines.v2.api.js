import { api } from "./clients.js";

export async function createSource(pipelineId, config) {
  return await api.post(`/v2/pipelines/${pipelineId}/sources/${config.id}`, config);
}

export async function updateSource(pipelineId, config) {
  return await api.put(`/v2/pipelines/${pipelineId}/sources/${config.id}`, config);
}

export async function deleteSource(pipelineId, config) {
  return await api.delete(`/v2/pipelines/${pipelineId}/sources/${config.id}`);
}

export async function createGenerator(pipelineId, config) {
  return await api.post(`/v2/pipelines/${pipelineId}/generators/${config.id}`, config);
}

export async function updateGenerator(pipelineId, config) {
  return await api.put(`/v2/pipelines/${pipelineId}/generators/${config.id}`, config);
}

export async function deleteGenerator(pipelineId, config) {
  return await api.delete(`/v2/pipelines/${pipelineId}/generators/${config.id}`);
}

export async function promotePipeline(pipelineId, name, widgets) {
  return await api.post(`/v2/pipelines/${pipelineId}/promote`, { name, widgets });
}

export async function deletePipeline(pipelineId) {
  return await api.delete(`/v2/pipelines/${pipelineId}`);
}
