import { ApiError } from "./errors.ts";
import { requiredEnv } from "./config.ts";
import { JPEG_MIME_TYPE, MAX_PHOTO_BYTES } from "../../shared/photo.ts";

export function buildPhotoObjectKey(userId: string, photoId: string): string {
  return `checkins/${userId}/${photoId}.jpg`;
}

function objectUrl(objectKey: string): string {
  const base = requiredEnv("STORAGE_API_BASE").replace(/\/+$/, "");
  const bucket = requiredEnv("STORAGE_BUCKET");
  const encodedKey = objectKey
    .split("/")
    .map((segment) => encodeURIComponent(segment))
    .join("/");
  return `${base}/v1/storages/object/${encodeURIComponent(bucket)}/${encodedKey}`;
}

export function validateJpeg(bytes: Buffer): void {
  if (bytes.length === 0) {
    throw new ApiError("PHOTO_REQUIRED", "Photo is required");
  }
  if (bytes.length > MAX_PHOTO_BYTES) {
    throw new ApiError("PHOTO_INVALID", "Photo exceeds the 5 MiB limit");
  }
  if (bytes.length < 3 || bytes[0] !== 0xff || bytes[1] !== 0xd8 || bytes[2] !== 0xff) {
    throw new ApiError("PHOTO_INVALID", "Only JPEG photos are accepted");
  }
}

export async function uploadPhotoObject(objectKey: string, bytes: Buffer): Promise<void> {
  const response = await fetch(objectUrl(objectKey), {
    method: "POST",
    headers: {
      Authorization: `Bearer ${requiredEnv("CLOUDBASE_API_KEY")}`,
      "Content-Type": JPEG_MIME_TYPE,
    },
    body: new Uint8Array(bytes).buffer,
  });

  if (!response.ok) {
    const message = await response.text();
    console.error("Storage upload failed", response.status, message);
    throw new ApiError("INTERNAL_ERROR", "Unable to store the photo", { retryable: true });
  }
}

export async function deletePhotoObject(objectKey: string): Promise<void> {
  const response = await fetch(objectUrl(objectKey), {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${requiredEnv("CLOUDBASE_API_KEY")}`,
    },
  });
  if (!response.ok) {
    console.error("Storage cleanup failed", response.status, await response.text());
  }
}

export async function deletePhotoObjectStrict(objectKey: string): Promise<void> {
  const authorization = `Bearer ${requiredEnv("CLOUDBASE_API_KEY")}`;
  const deleted = await fetch(objectUrl(objectKey), {
    method: "DELETE",
    headers: { Authorization: authorization },
  });
  if (!deleted.ok && deleted.status !== 404) {
    throw new Error(`Storage delete failed with ${String(deleted.status)}`);
  }

  let probe = await fetch(objectUrl(objectKey), {
    method: "HEAD",
    headers: { Authorization: authorization },
  });
  if (probe.status === 405) {
    probe = await fetch(objectUrl(objectKey), {
      method: "GET",
      headers: { Authorization: authorization },
    });
  }
  if (probe.status === 404) {
    return;
  }
  if (probe.ok) {
    throw new Error("Storage object still exists after deletion");
  }
  throw new Error(`Unable to verify storage object is absent: ${String(probe.status)}`);
}
