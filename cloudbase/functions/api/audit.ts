import { insertRow } from "./db.ts";

export interface AuditLogInput {
  requestId: string;
  eventType: string;
  userId: string;
  entityId: string;
  metadata: Record<string, unknown>;
}

export async function writeAuditLog(input: AuditLogInput): Promise<void> {
  try {
    await insertRow("audit_logs", {
      request_id: input.requestId,
      event_type: input.eventType,
      user_id: input.userId,
      entity_id: input.entityId,
      metadata: input.metadata,
    });
  } catch (error) {
    console.error("Audit log write failed", { requestId: input.requestId, error });
  }
}
