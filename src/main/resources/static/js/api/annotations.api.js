import { api } from "./clients.js";

export async function getAnnotations(query, size) {
  return await api.get(`/annotations?q=${query}&size=${size}`);
}
