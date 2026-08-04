import {
  dutyLogOperations,
  type DutyLogOperationId,
  type DutyLogOperationRequest,
  type DutyLogOperationResponse,
} from "@/generated/dutylog-api";
import {
  createDutyLogHttpClient,
  type DutyLogHttpClientOptions,
  type DutyLogRequestOptions,
} from "./httpClient";

export type DutyLogPathParameters = Record<string, string | number>;
export type DutyLogQueryParameters = Record<string, string | number | boolean | null | undefined>;

export interface DutyLogGeneratedRequestOptions<TBody> {
  path?: DutyLogPathParameters;
  query?: DutyLogQueryParameters;
  body?: TBody;
  signal?: AbortSignal;
}

function interpolatePath(template: string, parameters: DutyLogPathParameters = {}): string {
  return template.replace(/\{([^}]+)\}/g, (_match, key: string) => {
    const value = parameters[key];
    if (value === undefined || value === null || value === "") {
      throw new Error(`Missing OpenAPI path parameter: ${key}`);
    }
    return encodeURIComponent(String(value));
  });
}

function appendQuery(path: string, parameters: DutyLogQueryParameters = {}): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(parameters)) {
    if (value !== undefined && value !== null) query.append(key, String(value));
  }
  const encoded = query.toString();
  return encoded ? `${path}?${encoded}` : path;
}

export function createGeneratedDutyLogApiClient(httpOptions: DutyLogHttpClientOptions = {}) {
  const requestJson = createDutyLogHttpClient(httpOptions);
  return Object.freeze({
    async request<T extends DutyLogOperationId>(
      operationId: T,
      options: DutyLogGeneratedRequestOptions<DutyLogOperationRequest<T>> = {},
    ): Promise<DutyLogOperationResponse<T> | null> {
      const operation = dutyLogOperations[operationId];
      const path = appendQuery(interpolatePath(operation.path, options.path), options.query);
      const request: DutyLogRequestOptions<DutyLogOperationRequest<T>> = { method: operation.method };
      if (Object.prototype.hasOwnProperty.call(options, "body")) request.body = options.body as DutyLogOperationRequest<T>;
      if (options.signal) request.signal = options.signal;
      return requestJson<DutyLogOperationResponse<T>, DutyLogOperationRequest<T>>(path, request);
    },
  });
}

export type DutyLogGeneratedApiClient = ReturnType<typeof createGeneratedDutyLogApiClient>;
