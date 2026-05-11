export default class HTTPClient {
  constructor(baseUrl) {
    this.baseUrl = baseUrl;
  }

  async request(path, options = {}, responseType = "json") {
    const response = await fetch(this.baseUrl + path, {
      headers: {
        "Content-Type": "application/json",
        ...options.headers,
      },
      ...options,
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(`${error.path} ${response.status}: ${error.error}`);
    }

    const parsers = {
      json: () => response.json(),
      text: () => response.text(),
      blob: () => response.blob(),
    };

    return parsers[responseType]();
  }

  get(path, responseType) {
    return this.request(path, {}, responseType);
  }

  post(path, body, responseType) {
    return this.request(
      path,
      { method: "POST", body: JSON.stringify(body) },
      responseType,
    );
  }

  put(path, body, responseType) {
    return this.request(
      path,
      { method: "PUT", body: JSON.stringify(body) },
      responseType,
    );
  }

  delete(path, responseType) {
    return this.request(path, { method: "DELETE" }, responseType);
  }
}
