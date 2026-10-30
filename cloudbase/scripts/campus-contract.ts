export interface CampusFragmentFixture {
  id: string;
  landmarkId: string;
  taskId: string;
  title: string;
  pieceIndex: number;
  imageKey: string;
}

export interface CampusSectionFixture {
  id: string;
  campusZone: string;
  rows: number;
  columns: number;
  fragments: CampusFragmentFixture[];
}

export interface CampusLandmarkFixture {
  id: string;
  name: string;
  sectionId: string;
  pieceIndex: number;
  latitude: number;
  longitude: number;
  coordinateSystem: string;
  coordinateVerified: boolean;
  checkInArea: { type: string };
}

export interface CampusCatalogFixture {
  puzzleId: string;
  sections: CampusSectionFixture[];
  landmarks: CampusLandmarkFixture[];
}

interface AndroidLandmark {
  suffix: string;
  name: string;
  campusZone: string;
  pieceIndex: number;
  latitude: number;
  longitude: number;
}

function parseAndroidCatalog(source: string): AndroidLandmark[] {
  const pattern =
    /landmark\("([^"]+)",\s*"([^"]+)",\s*CampusZone\.([A-Z]+),\s*(\d+),\s*([0-9.]+),\s*([0-9.]+),/g;
  return [...source.matchAll(pattern)].map((match) => ({
    suffix: match[1]!,
    name: match[2]!,
    campusZone: match[3]!,
    pieceIndex: Number(match[4]),
    latitude: Number(match[5]),
    longitude: Number(match[6]),
  }));
}

export function validateCampusContract(
  fixture: CampusCatalogFixture,
  androidSource: string,
): void {
  const android = parseAndroidCatalog(androidSource);
  if (android.length !== 12) {
    throw new Error(`Android baseline must contain 12 landmarks, got ${android.length}`);
  }

  if (fixture.puzzleId !== "puzzle_liangxiang_campus") {
    throw new Error("Unexpected puzzleId");
  }
  if (fixture.sections.length !== 3) {
    throw new Error("Canonical puzzle must contain exactly three sections");
  }

  const zones = fixture.sections.map((section) => section.campusZone);
  if (new Set(zones).size !== 3 || zones.join(",") !== "NORTH,EAST,SOUTH") {
    throw new Error("campusZone must be NORTH, EAST, SOUTH exactly once");
  }

  const fragmentToSection = new Map<string, CampusSectionFixture>();
  for (const section of fixture.sections) {
    if (
      section.id !== `section_${section.campusZone.toLowerCase()}` ||
      section.rows !== 2 ||
      section.columns !== 2 ||
      section.fragments.length !== 4
    ) {
      throw new Error(`Invalid section layout: ${section.id}`);
    }
    const indexes = section.fragments.map((fragment) => fragment.pieceIndex);
    if (
      new Set(indexes).size !== 4 ||
      indexes.some((index) => index < 0 || index > 3)
    ) {
      throw new Error(`pieceIndex must be unique 0..3 in ${section.id}`);
    }
    for (const fragment of section.fragments) {
      const suffix = fragment.landmarkId.startsWith("lmk_")
        ? fragment.landmarkId.slice(4)
        : "";
      if (
        !suffix ||
        fragment.id !== `fragment_${suffix}` ||
        fragment.taskId !== `task_${suffix}`
      ) {
        throw new Error(`Mismatched landmarkId, fragmentId, or taskId: ${fragment.landmarkId}`);
      }
      fragmentToSection.set(fragment.id, section);
    }
  }

  if (fixture.landmarks.length !== 12 || fragmentToSection.size !== 12) {
    throw new Error("Canonical fixture must contain 12 landmarks and 12 fragments");
  }

  const androidBySuffix = new Map(android.map((entry) => [entry.suffix, entry]));
  const fixtureIds = new Set<string>();
  for (const landmark of fixture.landmarks) {
    if (!landmark.id.startsWith("lmk_")) {
      throw new Error(`Invalid landmarkId ${landmark.id}`);
    }
    const suffix = landmark.id.slice(4);
    const expected = androidBySuffix.get(suffix);
    if (!expected) {
      throw new Error(`Landmark ${landmark.id} is missing from the Android baseline`);
    }
    const fragment = [...fragmentToSection.entries()].find(
      ([, section]) =>
        section.campusZone === expected.campusZone &&
        section.fragments.some(
          (item) =>
            item.landmarkId === landmark.id &&
            item.pieceIndex === expected.pieceIndex,
        ),
    )?.[0];
    if (!fragment) {
      throw new Error(`landmarkId, campusZone, or pieceIndex mismatch for ${landmark.id}`);
    }
    const section = fragmentToSection.get(fragment)!;
    if (
      landmark.sectionId !== section.id ||
      landmark.pieceIndex !== expected.pieceIndex ||
      landmark.name !== expected.name
    ) {
      throw new Error(`sectionId or pieceIndex mismatch for ${landmark.id}`);
    }
    if (
      Math.abs(landmark.latitude - expected.latitude) > 1e-9 ||
      Math.abs(landmark.longitude - expected.longitude) > 1e-9
    ) {
      throw new Error(`latitude or longitude mismatch for ${landmark.id}`);
    }
    if (
      landmark.coordinateSystem !== "GCJ02" ||
      landmark.coordinateVerified !== false
    ) {
      throw new Error(`Coordinate metadata mismatch for ${landmark.id}`);
    }
    if (!["circle", "polygon"].includes(landmark.checkInArea.type)) {
      throw new Error(`Invalid checkInArea for ${landmark.id}`);
    }
    fixtureIds.add(landmark.id);
  }

  if (fixtureIds.size !== androidBySuffix.size) {
    throw new Error("Fixture and Android landmark sets differ");
  }
}
