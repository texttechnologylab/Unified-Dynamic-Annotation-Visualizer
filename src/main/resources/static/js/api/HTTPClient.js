export default class HTTPClient {
  constructor(baseUrl) {
    this.baseUrl = baseUrl;
  }

  async request(path, options = {}) {
    const response = await fetch(this.baseUrl + path, {
      headers: {
        "Content-Type": "application/json",
        ...options.headers,
      },
      ...options,
    });

    if (!response.ok) {
      const error = await response.text();
      throw new Error(`${response.status}: ${error}`);
    }

    return response;
  }

  get(path) {
    return this.request(path);
  }

  post(path, body) {
    return this.request(path, {
      method: "POST",
      body: JSON.stringify(body),
    });
  }

  put(path, body) {
    return this.request(path, {
      method: "PUT",
      body: JSON.stringify(body),
    });
  }

  delete(path) {
    return this.request(path, {
      method: "DELETE",
    });
  }
}

export const http = new HTTPClient("/api");
