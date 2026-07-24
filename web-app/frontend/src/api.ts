let csrfHeader = "X-XSRF-TOKEN";
let csrfToken = "";

export class ApiError extends Error {
  constructor(message: string, public status: number) {
    super(message);
  }
}

export async function primeCsrf() {
  const response = await fetch("/api/auth/csrf", { credentials: "same-origin" });
  const data = await response.json();
  csrfHeader = data.headerName;
  csrfToken = data.token;
  return data as { registrationEnabled: boolean };
}

export async function api<T>(
  path: string,
  init: Omit<RequestInit, "body"> & { body?: BodyInit | Record<string, unknown> } = {}
): Promise<T> {
  const headers = new Headers(init.headers);
  const method = (init.method || "GET").toUpperCase();
  let body = init.body;
  if (body && typeof body === "object" && !(body instanceof FormData) &&
      !(body instanceof URLSearchParams) && !(body instanceof Blob)) {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(body);
  }
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    headers.set(csrfHeader, csrfToken);
  }
  const response = await fetch(path, {
    ...init,
    method,
    headers,
    body: body as BodyInit | undefined,
    credentials: "same-origin"
  });
  if (!response.ok) {
    let message = `Request failed (${response.status}).`;
    try {
      const payload = await response.json();
      message = payload.error || message;
    } catch {
      // The status remains the useful fallback for non-JSON failures.
    }
    throw new ApiError(message, response.status);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export async function login(username: string, password: string) {
  const body = new URLSearchParams({ username, password });
  await api("/api/auth/login", { method: "POST", body });
  await primeCsrf();
}

export async function logout() {
  await api("/api/auth/logout", { method: "POST" });
  await primeCsrf();
}
