import { requiredEnv } from "./config.ts";

export class PostgrestError extends Error {
  readonly status: number;

  constructor(
    message: string,
    status: number,
  ) {
    super(message);
    this.name = "PostgrestError";
    this.status = status;
  }
}

interface RestRequestOptions {
  method?: "GET" | "POST" | "PATCH" | "DELETE";
  query?: Record<string, string>;
  body?: unknown;
  prefer?: string;
}

function restUrl(path: string, query: Record<string, string> = {}): string {
  const base = requiredEnv("STORAGE_API_BASE").replace(/\/+$/, "");
  const url = new URL(`${base}/v1/rdb/rest/${path}`);
  for (const [key, value] of Object.entries(query)) {
    url.searchParams.set(key, value);
  }
  return url.toString();
}

async function restRequest<T>(path: string, options: RestRequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {
    Authorization: `Bearer ${requiredEnv("CLOUDBASE_API_KEY")}`,
    Accept: "application/json",
  };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (options.prefer) {
    headers.Prefer = options.prefer;
  }

  const response = await fetch(restUrl(path, options.query), {
    method: options.method ?? "GET",
    headers,
    ...(options.body === undefined ? {} : { body: JSON.stringify(options.body) }),
  });

  const text = await response.text();
  if (!response.ok) {
    let message = text || `PostgREST request failed with ${String(response.status)}`;
    try {
      const parsed = JSON.parse(text) as { message?: string };
      message = parsed.message ?? message;
    } catch {
      // Keep the raw response text when the body is not JSON.
    }
    throw new PostgrestError(message, response.status);
  }

  return (text ? JSON.parse(text) : undefined) as T;
}

export async function selectRows<T>(table: string, query: Record<string, string>): Promise<T[]> {
  return restRequest<T[]>(table, { query });
}

export async function insertRow<T>(table: string, body: unknown): Promise<T> {
  const rows = await restRequest<T[]>(table, {
    method: "POST",
    body,
    prefer: "return=representation",
  });
  const row = rows[0];
  if (!row) {
    throw new Error(`Insert into ${table} returned no row`);
  }
  return row;
}

export async function updateRows<T>(
  table: string,
  query: Record<string, string>,
  body: unknown,
): Promise<T[]> {
  return restRequest<T[]>(table, {
    method: "PATCH",
    query,
    body,
    prefer: "return=representation",
  });
}

export async function deleteRows(
  table: string,
  query: Record<string, string>,
): Promise<void> {
  await restRequest(table, {
    method: "DELETE",
    query,
  });
}

export async function insertRowIfAbsent<T>(table: string, body: unknown): Promise<T | null> {
  try {
    return await insertRow<T>(table, body);
  } catch (error) {
    if (error instanceof PostgrestError && error.status === 409) {
      return null;
    }
    throw error;
  }
}
