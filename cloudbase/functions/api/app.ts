import { createServer, type IncomingMessage, type ServerResponse } from "node:http";

import { requireUserId } from "./auth.ts";
import { ApiError } from "./errors.ts";
import { healthHandler } from "./handlers/health.ts";
import { createCheckInChallenge } from "./handlers/challenges.ts";
import { queryCheckInAttempt, submitCheckIn } from "./handlers/checkins.ts";
import { listNearbyLandmarks } from "./handlers/landmarks.ts";
import { deleteCheckInPhoto, storeCheckInPhoto } from "./handlers/photos.ts";
import { getPuzzleProgress } from "./handlers/puzzles.ts";
import { getRequestId, readJsonBody, sendJson, statusForErrorCode, toApiError } from "./http.ts";

function optionalNumber(value: string | null): number | undefined {
  if (value === null || value === "") {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function headerValue(request: IncomingMessage, name: string): string | undefined {
  const value = request.headers[name];
  return typeof value === "string" ? value : undefined;
}

async function handleRequest(request: IncomingMessage, response: ServerResponse): Promise<void> {
  const requestId = getRequestId(request);
  const url = new URL(request.url ?? "/", `http://${request.headers.host ?? "localhost"}`);
  const pathname = url.pathname.startsWith("/api/v1")
    ? url.pathname
    : `/api/v1${url.pathname.startsWith("/") ? url.pathname : `/${url.pathname}`}`;

  try {
    if (pathname === "/api/v1/health") {
      if (request.method !== "GET") {
        response.writeHead(405, { Allow: "GET" });
        response.end("Method Not Allowed");
        return;
      }
      sendJson(response, 200, requestId, healthHandler({ requestId }));
      return;
    }

    const userId = requireUserId(request);

    if (pathname === "/api/v1/landmarks") {
      if (request.method !== "GET") {
        response.writeHead(405, { Allow: "GET" });
        response.end("Method Not Allowed");
        return;
      }

      const latitude = optionalNumber(url.searchParams.get("latitude"));
      const longitude = optionalNumber(url.searchParams.get("longitude"));
      if ((latitude === undefined) !== (longitude === undefined)) {
        throw new ApiError("INVALID_REQUEST", "latitude and longitude must be provided together");
      }

      const radiusMeters = optionalNumber(url.searchParams.get("radiusMeters"));
      const data = await listNearbyLandmarks({
        ...(latitude === undefined ? {} : { latitude }),
        ...(longitude === undefined ? {} : { longitude }),
        ...(radiusMeters === undefined ? {} : { radiusMeters }),
      });
      sendJson(response, 200, requestId, { requestId, data });
      return;
    }

    if (pathname === "/api/v1/checkin-challenges") {
      if (request.method !== "POST") {
        response.writeHead(405, { Allow: "POST" });
        response.end("Method Not Allowed");
        return;
      }
      const attemptId = headerValue(request, "x-attempt-id");
      if (!attemptId) {
        throw new ApiError("INVALID_REQUEST", "X-Attempt-Id header is required");
      }
      const data = await createCheckInChallenge(
        userId,
        attemptId,
        await readJsonBody(request),
      );
      sendJson(response, 200, requestId, { requestId, data });
      return;
    }

    if (pathname === "/api/v1/checkin-photos") {
      if (request.method !== "POST") {
        response.writeHead(405, { Allow: "POST" });
        response.end("Method Not Allowed");
        return;
      }
      const data = await storeCheckInPhoto(userId, request);
      sendJson(response, 200, requestId, { requestId, data });
      return;
    }

    const photoDeleteMatch = /^\/api\/v1\/checkin-photos\/([^/]+)$/.exec(pathname);
    if (photoDeleteMatch) {
      if (request.method !== "DELETE") {
        response.writeHead(405, { Allow: "DELETE" });
        response.end("Method Not Allowed");
        return;
      }
      const photoId = decodeURIComponent(photoDeleteMatch[1] ?? "");
      const data = await deleteCheckInPhoto(userId, photoId, requestId);
      sendJson(response, data.storageCleanup === "PENDING" ? 202 : 200, requestId, {
        requestId,
        data,
      });
      return;
    }

    if (pathname === "/api/v1/checkins") {
      if (request.method === "GET") {
        const attemptId = url.searchParams.get("attemptId");
        if (!attemptId) {
          throw new ApiError("INVALID_REQUEST", "attemptId query parameter is required");
        }
        const result = await queryCheckInAttempt(userId, attemptId, requestId);
        if (result.kind === "completed") {
          sendJson(response, 200, requestId, { requestId, data: result.receipt });
          return;
        }
        if (result.kind === "processing") {
          sendJson(response, 202, requestId, {
            requestId,
            data: { attemptId, status: "PROCESSING" },
          });
          return;
        }
        throw new ApiError("CHECKIN_NOT_FOUND", "No successful check-in exists for this attempt", {
          details: { attemptId },
        });
      }

      if (request.method !== "POST") {
        response.writeHead(405, { Allow: "GET, POST" });
        response.end("Method Not Allowed");
        return;
      }
      const idempotencyKey = headerValue(request, "idempotency-key");
      if (!idempotencyKey) {
        throw new ApiError("INVALID_REQUEST", "Idempotency-Key header is required");
      }
      const data = await submitCheckIn(
        userId,
        requestId,
        idempotencyKey,
        await readJsonBody(request),
      );
      sendJson(response, 200, requestId, { requestId, data });
      return;
    }

    const puzzleMatch = /^\/api\/v1\/puzzles\/([^/]+)$/.exec(pathname);
    if (puzzleMatch) {
      if (request.method !== "GET") {
        response.writeHead(405, { Allow: "GET" });
        response.end("Method Not Allowed");
        return;
      }
      const puzzleId = decodeURIComponent(puzzleMatch[1] ?? "");
      const data = await getPuzzleProgress(userId, puzzleId);
      sendJson(response, 200, requestId, { requestId, data });
      return;
    }

    response.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
    response.end("Not Found");
  } catch (error) {
    const failure = toApiError(error, requestId);
    sendJson(response, statusForErrorCode(failure.error.code), requestId, failure);
  }
}

export function createApp() {
  return createServer((request, response) => {
    void handleRequest(request, response);
  });
}
