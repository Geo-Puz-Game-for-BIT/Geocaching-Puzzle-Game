export interface ApiErrorBody {
  code: string;
  message: string;
  retryable: boolean;
  details?: unknown;
}

export interface ApiSuccessResponse<T> {
  requestId: string;
  data: T;
}

export interface ApiFailureResponse {
  requestId: string;
  error: ApiErrorBody;
}

export type ApiResponse<T> = ApiSuccessResponse<T> | ApiFailureResponse;
