import { randomUUID } from "node:crypto";
import type { IncomingMessage, ServerResponse } from "node:http";

import { ApiError } from "./errors.ts";
import type { ApiFailureResponse, ApiSuccessResponse } from "./contracts/apiResponse.ts";
import { ERROR_CODES, type ErrorCode } from "../../shared/error-codes.ts";

export const MAX_JSON_BYTES = 1_048_576;

export function getRequestId(request: IncomingMessage): string {
  const supplied = request.headers["x-request-id"];
  if (
    typeof supplied === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
      supplied,
    )
  ) {
    return supplied;
  }
  return randomUUID();
}

export function sendJson<T>(
  response: ServerResponse,
  statusCode: number,
  requestId: string,
  body: ApiSuccessResponse<T> | ApiFailureResponse,
): void {
  response.writeHead(statusCode, {
    "Cache-Control": "no-store",
    "Content-Type": "application/json; charset=utf-8",
    "X-Request-Id": requestId,
  });
  response.end(JSON.stringify(body));
}

export function sendError(response: ServerResponse, requestId: string, error: ApiError): void {
  sendJson(response, error.status, requestId, {
    requestId,
    error: {
      code: error.code,
      message: error.message,
      retryable: error.retryable,
      ...(error.details === undefined ? {} : { details: error.details }),
    },
  });
}

export function toApiError(error: unknown, requestId: string): ApiFailureResponse {
  if (error instanceof ApiError) {
    return {
      requestId,
      error: {
        code: error.code,
        message: error.message,
        retryable: error.retryable,
        ...(error.details === undefined ? {} : { details: error.details }),
      },
    };
  }

  console.error("Unhandled API error", { requestId, error });
  return {
    requestId,
    error: {
      code: "INTERNAL_ERROR",
      message: "Unexpected server error",
      retryable: true,
    },
  };
}

export function statusForErrorCode(code: string): number {
  return ERROR_CODES[code as ErrorCode] ?? 500;
}

export async function readJsonBody(request: IncomingMessage): Promise<unknown> {
  const length = Number(request.headers["content-length"] ?? 0);
  if (Number.isFinite(length) && length > MAX_JSON_BYTES) {
    throw new ApiError("INVALID_REQUEST", "Request body is too large");
  }

  const chunks: Buffer[] = [];
  let total = 0;
  for await (const chunk of request) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(String(chunk));
    total += buffer.length;
    if (total > MAX_JSON_BYTES) {
      throw new ApiError("INVALID_REQUEST", "Request body is too large");
    }
    chunks.push(buffer);
  }

  if (total === 0) {
    throw new ApiError("INVALID_REQUEST", "Request body is required");
  }

  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw new ApiError("INVALID_REQUEST", "Request body must be valid JSON");
  }
}
