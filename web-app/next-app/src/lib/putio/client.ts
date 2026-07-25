const PUTIO_API = "https://api.put.io/v2";

export class PutIoNotConfiguredError extends Error {
  constructor() {
    super("The shared put.io account is not configured on this server.");
  }
}

export type PutIoEnv = { PUTIO_OAUTH_TOKEN?: string };

function token(env: PutIoEnv): string {
  const value = env.PUTIO_OAUTH_TOKEN?.trim();
  if (!value) throw new PutIoNotConfiguredError();
  return value;
}

async function request(
  env: PutIoEnv,
  path: string,
  init: RequestInit = {},
): Promise<Response> {
  const response = await fetch(`${PUTIO_API}${path}`, {
    ...init,
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${token(env)}`,
      "User-Agent": "PirateBrowser-Web/2.0",
      ...init.headers,
    },
    redirect: "follow",
    signal: AbortSignal.timeout(30_000),
  });
  if (!response.ok) throw new Error(`put.io returned HTTP ${response.status}`);
  return response;
}

async function getJson(env: PutIoEnv, path: string): Promise<any> {
  return request(env, path).then((response) => response.json());
}

async function postForm(
  env: PutIoEnv,
  path: string,
  values: Record<string, string>,
): Promise<any> {
  return request(env, path, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams(values),
  }).then((response) => response.json());
}

export function isPutIoConfigured(env: PutIoEnv): boolean {
  return Boolean(env.PUTIO_OAUTH_TOKEN?.trim());
}

export async function addTransfer(env: PutIoEnv, magnet: string): Promise<void> {
  if (!magnet.startsWith("magnet:?")) throw new Error("A valid magnet link is required.");
  await postForm(env, "/transfers/add", { url: magnet });
}

export async function listTransfers(env: PutIoEnv) {
  const root = await getJson(env, "/transfers/list");
  return (Array.isArray(root?.transfers) ? root.transfers : []).map((item: any) => ({
    id: Number(item.id),
    name: item.name || "Unnamed transfer",
    status: item.status || "UNKNOWN",
    percentDone: Number(item.percent_done) || 0,
    size: Number(item.size) || 0,
    fileId: item.file_id == null ? null : Number(item.file_id),
  }));
}

export async function cancelTransfer(env: PutIoEnv, id: number): Promise<void> {
  await postForm(env, "/transfers/cancel", { transfer_ids: String(id) });
}

export async function listFiles(env: PutIoEnv, parentId: number) {
  const root = await getJson(env, `/files/list?parent_id=${parentId}`);
  const files = (Array.isArray(root?.files) ? root.files : []).map((item: any) => {
    const contentType = String(item.content_type ?? "");
    return {
      id: Number(item.id),
      name: item.name || "Unnamed file",
      size: Number(item.size) || 0,
      contentType,
      directory:
        contentType === "application/x-directory" ||
        String(item.file_type ?? "").toUpperCase() === "FOLDER",
    };
  });
  files.sort((left: any, right: any) =>
    left.directory !== right.directory
      ? left.directory
        ? -1
        : 1
      : left.name.localeCompare(right.name),
  );
  return { parentId, files };
}

export async function renameFile(
  env: PutIoEnv,
  id: number,
  name: string,
): Promise<void> {
  const trimmed = name.trim();
  if (!trimmed || trimmed.length > 255 || /[\\/]/.test(trimmed)) {
    throw new Error("Enter a valid file name.");
  }
  await postForm(env, "/files/rename", { file_id: String(id), name: trimmed });
}

export async function deleteFile(env: PutIoEnv, id: number): Promise<void> {
  await postForm(env, "/files/delete", { file_ids: String(id) });
}

export async function streamFile(
  env: PutIoEnv,
  fileId: number,
  range: string | null,
): Promise<Response> {
  const oauthToken = token(env);
  const headers = new Headers({ Authorization: `Bearer ${oauthToken}`, Accept: "*/*" });
  if (range) headers.set("Range", range);
  const remote = await fetch(
    `${PUTIO_API}/files/${fileId}/download?oauth_token=${encodeURIComponent(oauthToken)}`,
    {
    headers,
    redirect: "follow",
    signal: AbortSignal.timeout(120_000),
    },
  );
  if (!remote.ok) throw new Error(`put.io media request returned HTTP ${remote.status}`);
  const responseHeaders = new Headers({
    "Content-Type": remote.headers.get("Content-Type") ?? "application/octet-stream",
    "Accept-Ranges": remote.headers.get("Accept-Ranges") ?? "bytes",
    "Cache-Control": "private, no-store",
  });
  for (const name of ["Content-Length", "Content-Range", "ETag", "Last-Modified"]) {
    const value = remote.headers.get(name);
    if (value) responseHeaders.set(name, value);
  }
  return new Response(remote.body, { status: remote.status, headers: responseHeaders });
}
