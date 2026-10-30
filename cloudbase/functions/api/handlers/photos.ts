import { randomUUID } from "node:crypto";
import type { IncomingMessage } from "node:http";

import Busboy from "busboy";

import { insertRow } from "../db.ts";
import { ApiError } from "../errors.ts";
import { JPEG_MIME_TYPE, MAX_PHOTO_BYTES } from "../../../shared/photo.ts";
import {
  buildPhotoObjectKey,
  deletePhotoObject,
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
  let bytes: Buffer;

  if (contentType.startsWith("multipart/form-data")) {
    bytes = await readMultipartPhoto(request);
  } else if (contentType.startsWith("image/jpeg")) {
    bytes = await readBinaryPhoto(request);
  } else {
    throw new ApiError("PHOTO_INVALID", "Content-Type must be multipart/form-data or image/jpeg");
  }

  validateJpeg(bytes);

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
