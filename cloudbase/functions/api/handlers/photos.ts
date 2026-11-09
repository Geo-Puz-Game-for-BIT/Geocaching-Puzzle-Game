import { randomUUID } from "node:crypto";
import type { IncomingMessage } from "node:http";

import Busboy from "busboy";

import { insertRow, selectRows, updateRows } from "../db.ts";
import { ApiError } from "../errors.ts";
import { JPEG_MIME_TYPE, MAX_PHOTO_BYTES } from "../../../shared/photo.ts";
import {
  buildPhotoObjectKey,
  deletePhotoObject,
  deletePhotoObjectStrict,
  uploadPhotoObject,
  validateJpeg,
} from "../storage.ts";

async function readMultipartPhoto(request: IncomingMessage): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    let settled = false;
    let fileBytes: Buffer | undefined;

    const finish = (error?: Error, bytes?: Buffer) => {
      if (settled) {
        return;
      }
      settled = true;
      if (error) {
        reject(error);
      } else if (bytes) {
        resolve(bytes);
      } else {
        reject(new ApiError("PHOTO_REQUIRED", "A photo file is required"));
      }
    };

    let busboy: Busboy.Busboy;
    try {
      busboy = Busboy({
        headers: request.headers,
        limits: { files: 1, fileSize: MAX_PHOTO_BYTES },
      });
    } catch {
      finish(new ApiError("INVALID_REQUEST", "Invalid multipart request"));
      return;
    }

    busboy.on("file", (_fieldName, file, info) => {
      if (info.mimeType !== JPEG_MIME_TYPE) {
        file.resume();
        finish(new ApiError("PHOTO_INVALID", "Only image/jpeg is accepted"));
        return;
      }

      const chunks: Buffer[] = [];
      file.on("limit", () => {
        finish(new ApiError("PHOTO_INVALID", "Photo exceeds the 5 MiB limit"));
      });
      file.on("data", (chunk: Buffer) => {
        chunks.push(chunk);
      });
      file.on("end", () => {
        fileBytes = Buffer.concat(chunks);
      });
    });
    busboy.on("error", () => {
      finish(new ApiError("INVALID_REQUEST", "Unable to parse multipart request"));
    });
    busboy.on("finish", () => {
      finish(undefined, fileBytes);
    });

    request.pipe(busboy);
  });
}

async function readBinaryPhoto(request: IncomingMessage): Promise<Buffer> {
  const chunks: Buffer[] = [];
  let total = 0;
  for await (const chunk of request) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(String(chunk));
    total += buffer.length;
    if (total > MAX_PHOTO_BYTES) {
      throw new ApiError("PHOTO_INVALID", "Photo exceeds the 5 MiB limit");
    }
    chunks.push(buffer);
  }
  return Buffer.concat(chunks);
}

export async function storeCheckInPhoto(userId: string, request: IncomingMessage) {
  const contentType = request.headers["content-type"] ?? "";
  const challengeHeader = request.headers["x-challenge-id"];
  const attemptHeader = request.headers["x-attempt-id"];
  const challengeId = Array.isArray(challengeHeader) ? challengeHeader[0] : challengeHeader;
  const attemptId = Array.isArray(attemptHeader) ? attemptHeader[0] : attemptHeader;

  let bytes: Buffer;

  if (contentType.startsWith("multipart/form-data")) {
    bytes = await readMultipartPhoto(request);
  } else if (contentType.startsWith("image/jpeg")) {
    bytes = await readBinaryPhoto(request);
  } else {
    throw new ApiError("PHOTO_INVALID", "Content-Type must be multipart/form-data or image/jpeg");
  }

  validateJpeg(bytes);

  if (!challengeId || !attemptId) {
    throw new ApiError(
      "INVALID_REQUEST",
      "X-Challenge-Id and X-Attempt-Id headers are required",
    );
  }
  if (challengeId.length > 128 || attemptId.length > 128) {
    throw new ApiError("INVALID_REQUEST", "Challenge or attempt identifier is invalid");
  }

  const challenges = await selectRows<{
    id: string;
    user_id: string;
    used_at: string | null;
    expires_at: string;
    attempt_id: string | null;
  }>("challenges", {
    select: "id,user_id,used_at,expires_at,attempt_id",
    id: `eq.${challengeId}`,
    user_id: `eq.${userId}`,
    limit: "1",
  });
  const challenge = challenges[0];
  if (!challenge || challenge.attempt_id !== attemptId) {
    throw new ApiError(
      "INVALID_REQUEST",
      "Challenge does not belong to the supplied attempt",
    );
  }
  if (challenge.used_at || Date.parse(challenge.expires_at) <= Date.now()) {
    throw new ApiError("CHALLENGE_EXPIRED", "Challenge is expired or already used");
  }

  const photoId = `pho_${randomUUID()}`;
  const objectKey = buildPhotoObjectKey(userId, photoId);
  await uploadPhotoObject(objectKey, bytes);

  try {
    const result = await insertRow<{ uploaded_at: string }>("photos", {
      id: photoId,
      user_id: userId,
      object_key: objectKey,
      mime_type: JPEG_MIME_TYPE,
      size_bytes: bytes.length,
      challenge_id: challengeId,
      attempt_id: attemptId,
    });

    return {
      photoId,
      objectKey,
      uploadedAt: new Date(result.uploaded_at).toISOString(),
    };
  } catch (error) {
    await deletePhotoObject(objectKey);
    throw error;
  }
}

interface DeletablePhotoRow {
  id: string;
  user_id: string;
  object_key: string;
  attempt_id: string | null;
  deleted_at: string | null;
}

export async function deleteCheckInPhoto(
  userId: string,
  photoId: string,
  _requestId: string,
) {
  const rows = await selectRows<DeletablePhotoRow>("photos", {
    select: "id,user_id,object_key,attempt_id,deleted_at",
    id: `eq.${photoId}`,
    user_id: `eq.${userId}`,
    limit: "1",
  });
  const photo = rows[0];
  if (!photo) {
    throw new ApiError("PHOTO_NOT_FOUND", "Photo does not exist");
  }

  if (photo.attempt_id) {
    const attempts = await selectRows<{ id: string; status: string }>(
      "checkin_attempts",
      {
        select: "id,status",
        id: `eq.${photo.attempt_id}`,
        user_id: `eq.${userId}`,
        limit: "1",
      },
    );
    if (attempts[0]?.status === "PROCESSING") {
      throw new ApiError(
        "CHECKIN_RESULT_UNKNOWN",
        "Resolve the active check-in attempt before deleting its only photo",
        { retryable: true, details: { attemptId: photo.attempt_id } },
      );
    }
  }

  const deletedAt = photo.deleted_at ? new Date(photo.deleted_at) : new Date();
  const deleteAfter = new Date(deletedAt.getTime() + 24 * 60 * 60 * 1000);
  if (!photo.deleted_at) {
    await updateRows("photos", {
      id: `eq.${photoId}`,
      user_id: `eq.${userId}`,
      deleted_at: "is.null",
    }, {
      deleted_at: deletedAt.toISOString(),
      delete_after: deleteAfter.toISOString(),
    });
  }

  try {
    await deletePhotoObjectStrict(photo.object_key);
    await updateRows("photos", {
      id: `eq.${photoId}`,
      user_id: `eq.${userId}`,
    }, {
      storage_deleted_at: new Date().toISOString(),
    });
    return {
      photoId,
      status: "DELETED",
      storageCleanup: "COMPLETED",
      deleteAfter: deleteAfter.toISOString(),
    };
  } catch (error) {
    console.error("Photo object cleanup is pending", { photoId, error });
    return {
      photoId,
      status: "DELETED",
      storageCleanup: "PENDING",
      deleteAfter: deleteAfter.toISOString(),
    };
  }
}

export async function cleanupPendingPhotoDeletions() {
  const rows = await selectRows<{
    id: string;
    user_id: string;
    object_key: string;
  }>("photos", {
    select: "id,user_id,object_key",
    deleted_at: "not.is.null",
    storage_deleted_at: "is.null",
    order: "deleted_at.asc",
    limit: "50",
  });

  let cleaned = 0;
  let failed = 0;
  for (const photo of rows) {
    try {
      await deletePhotoObjectStrict(photo.object_key);
      await updateRows("photos", {
        id: `eq.${photo.id}`,
        user_id: `eq.${photo.user_id}`,
      }, {
        storage_deleted_at: new Date().toISOString(),
      });
      cleaned += 1;
    } catch (error) {
      failed += 1;
      console.error("Pending photo cleanup failed", { photoId: photo.id, error });
    }
  }
  return { scanned: rows.length, cleaned, failed };
}
