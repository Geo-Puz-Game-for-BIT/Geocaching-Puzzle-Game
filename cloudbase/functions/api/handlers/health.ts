import { randomUUID } from "node:crypto";

import type { ApiSuccessResponse } from "../contracts/apiResponse.ts";
import type { HealthData } from "../contracts/health.ts";

export interface HealthHandlerOptions {
  now?: Date;
  environment?: string;
  version?: string;
  requestId?: string;
}

export function createHealthResponse(
  options: HealthHandlerOptions = {},
): ApiSuccessResponse<HealthData> {
  const now = options.now ?? new Date();
  const environment = options.environment ?? process.env.APP_ENV ?? "local";
  const version = options.version ?? process.env.SERVICE_VERSION ?? "0.1.0";
  const requestId = options.requestId ?? randomUUID();

  return {
    requestId,
    data: {
      status: "ok",
      service: "geotreasure-api",
      version,
      environment,
      timestamp: now.toISOString(),
    },
  };
}

export function healthHandler(options: HealthHandlerOptions = {}): ApiSuccessResponse<HealthData> {
  return createHealthResponse(options);
}
