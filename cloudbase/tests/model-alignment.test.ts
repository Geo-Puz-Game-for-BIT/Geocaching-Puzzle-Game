import { beforeEach, describe, expect, it, vi } from "vitest";

import { listNearbyLandmarks } from "../functions/api/handlers/landmarks.ts";
import { getPuzzleProgress } from "../functions/api/handlers/puzzles.ts";

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

beforeEach(() => {
  process.env.STORAGE_API_BASE = "https://test.example";
  process.env.CLOUDBASE_API_KEY = "test-key";

  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: string | URL | Request) => {
      const url = new URL(typeof input === "string" ? input : input.toString());
      const table = url.pathname.split("/").pop();

      if (table === "landmarks") {
        return jsonResponse([
          {
            id: "lmk_north_lake",
            name: "北湖",
            latitude: 39.7366,
            longitude: 116.1666,
            coordinate_system: "GCJ02",
            check_in_radius_meters: 55,
            campus_zone: "NORTH",
            piece_index: 0,
            fragment_id: "fragment_north_lake",
            coordinate_verified: false,
            check_in_area: { type: "circle", radiusMeters: 55 },
            difficulty: 1,
            terrain: 1,
            hint: "沿湖岸寻找取景点。",
            status: "active",
          },
        ]);
      }

      if (table === "tasks") {
        return jsonResponse([
          {
            id: "task_north_lake",
            landmark_id: "lmk_north_lake",
            type: "photo",
            instructions: "在北湖完成现场拍照并上传。",
            puzzle_id: "puzzle_liangxiang_campus",
            fragment_id: "fragment_north_lake",
          },
        ]);
      }

      if (table === "puzzles") {
        return jsonResponse([
          {
            id: "puzzle_liangxiang_campus",
            name: "良乡校区探索图",
            rows: 2,
            columns: 2,
            default_reveal_mode: "FOG_OF_WAR",
          },
        ]);
      }

      if (table === "puzzle_sections") {
        return jsonResponse([
          {
            id: "section_north",
            puzzle_id: "puzzle_liangxiang_campus",
            campus_zone: "NORTH",
            rows: 2,
            columns: 2,
            sort_order: 0,
          },
        ]);
      }

      if (table === "fragments") {
        return jsonResponse([
          {
            id: "fragment_north_lake",
            puzzle_id: "puzzle_liangxiang_campus",
            section_id: "section_north",
            landmark_id: "lmk_north_lake",
            title: "北湖",
            piece_index: 0,
            image_key: "puzzles/puzzle_liangxiang_campus/fragment_north_lake.png",
          },
          {
            id: "fragment_north_zongjiao",
            puzzle_id: "puzzle_liangxiang_campus",
            section_id: "section_north",
            landmark_id: "lmk_north_zongjiao",
            title: "综教",
            piece_index: 1,
            image_key: "puzzles/puzzle_liangxiang_campus/fragment_north_zongjiao.png",
          },
        ]);
      }

      if (table === "fragment_grants") {
        return jsonResponse([{ fragment_id: "fragment_north_lake" }]);
      }

      return jsonResponse([]);
    }),
  );
});

describe("B/C canonical campus model", () => {
  it("landmark response keeps zone, fragment and check-in area aligned with Android", async () => {
    const landmarks = await listNearbyLandmarks({});

    expect(landmarks[0]).toMatchObject({
      id: "lmk_north_lake",
      campusZone: "NORTH",
      fragmentId: "fragment_north_lake",
      pieceIndex: 0,
      coordinateVerified: false,
      checkInArea: { type: "circle", radiusMeters: 55 },
    });
    expect(landmarks[0]).not.toHaveProperty("checkInRadiusMeters");
  });

  it("puzzle response groups two-by-two fragments under campus sections", async () => {
    const puzzle = await getPuzzleProgress(
      "usr_demo01",
      "puzzle_liangxiang_campus",
    );

    expect(puzzle).toMatchObject({
      puzzleId: "puzzle_liangxiang_campus",
      title: "良乡校区探索图",
      defaultRevealMode: "FOG_OF_WAR",
      ownedFragmentIds: ["fragment_north_lake"],
      completed: false,
      sections: [
        {
          id: "section_north",
          campusZone: "NORTH",
          rows: 2,
          columns: 2,
          fragments: [
            {
              id: "fragment_north_lake",
              landmarkId: "lmk_north_lake",
              title: "北湖",
              pieceIndex: 0,
              owned: true,
            },
            {
              id: "fragment_north_zongjiao",
              landmarkId: "lmk_north_zongjiao",
              title: "综教",
              pieceIndex: 1,
              owned: false,
            },
          ],
        },
      ],
    });
  });
});
