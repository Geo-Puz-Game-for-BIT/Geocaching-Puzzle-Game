import { ERROR_CODES, type ErrorCode } from "../../shared/error-codes.ts";

export class ApiError extends Error {
  readonly code: ErrorCode;
  readonly status: number;
  readonly retryable: boolean;
  readonly details?: unknown;

  constructor(
    code: ErrorCode,
    message: string,
    options: { retryable?: boolean; details?: unknown } = {},
  ) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = ERROR_CODES[code];
    this.retryable = options.retryable ?? false;
    this.details = options.details;
  }
}
