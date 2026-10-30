import { selectRows } from "../db.ts";
import { ApiError } from "../errors.ts";
import { distanceMeters, type CheckInArea } from "../geo.ts";

interface LandmarkRow {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  coordinate_system: string;
  check_in_radius_meters: number;
  campus_zone: string | null;
  piece_index: number | null;
  fragment_id: string | null;
  coordinate_verified: boolean;
  check_in_area: CheckInArea | null;
  difficulty: number;
  terrain: number;
  hint: string | null;
  status: string;
}

interface TaskRow {
  id: string;
  landmark_id: string;
  type: string;
  instructions: string;
  puzzle_id: string;
  fragment_id: string;
}

export interface LandmarkListQuery {
  latitude?: number;
  longitude?: number;
  radiusMeters?: number;
}

export async function listNearbyLandmarks(input: LandmarkListQuery) {
  const [landmarks, tasks] = await Promise.all([
    selectRows<LandmarkRow>("landmarks", {
      select:
        "id,name,latitude,longitude,coordinate_system,check_in_radius_meters,campus_zone,piece_index,fragment_id,coordinate_verified,check_in_area,difficulty,terrain,hint,status",
      status: "eq.active",
      order: "id.asc",
    }),
    selectRows<TaskRow>("tasks", {
      select: "id,landmark_id,type,instructions,puzzle_id,fragment_id",
      type: "eq.photo",
    }),
  ]);

  const taskByLandmark = new Map(tasks.map((task) => [task.landmark_id, task]));

  return landmarks
    .map((landmark) => {
      const task = taskByLandmark.get(landmark.id);
      return {
        id: landmark.id,
        name: landmark.name,
        point: {
          latitude: landmark.latitude,
          longitude: landmark.longitude,
        },
        coordinateSystem: landmark.coordinate_system,
        campusZone: landmark.campus_zone,
        fragmentId: landmark.fragment_id,
        pieceIndex: landmark.piece_index,
        coordinateVerified: landmark.coordinate_verified,
        checkInArea:
          landmark.check_in_area ?? {
            type: "circle" as const,
            radiusMeters: landmark.check_in_radius_meters,
          },
        difficulty: landmark.difficulty,
        terrain: landmark.terrain,
        hint: landmark.hint,
        task:
          task === undefined
            ? null
            : {
                id: task.id,
                type: task.type,
                instructions: task.instructions,
                puzzleId: task.puzzle_id,
                fragmentId: task.fragment_id,
              },
      };
    })
    .filter((landmark) => {
      if (input.latitude === undefined || input.longitude === undefined) {
        return true;
      }
      const distance = distanceMeters(
        { latitude: input.latitude, longitude: input.longitude },
        landmark.point,
      );
      return input.radiusMeters === undefined || distance <= input.radiusMeters;
    });
}

export async function requireActiveLandmark(landmarkId: string): Promise<LandmarkRow> {
  const rows = await selectRows<LandmarkRow>("landmarks", {
    select:
      "id,name,latitude,longitude,coordinate_system,check_in_radius_meters,campus_zone,piece_index,fragment_id,coordinate_verified,check_in_area,difficulty,terrain,hint,status",
    id: `eq.${landmarkId}`,
    status: "eq.active",
    limit: "1",
  });
  const landmark = rows[0];
  if (!landmark) {
    throw new ApiError("LANDMARK_NOT_FOUND", "Landmark does not exist");
  }
  return landmark;
}
