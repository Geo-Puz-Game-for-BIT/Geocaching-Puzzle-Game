import SwaggerParser from '@apidevtools/swagger-parser';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const openApiPath = fileURLToPath(new URL('../../docs/api/openapi.yaml', import.meta.url));
const fixturePath = fileURLToPath(new URL('../../docs/api/fixtures/campus-catalog.json', import.meta.url));
const androidCatalogPath = fileURLToPath(
  new URL(
    '../../android-app/app/src/main/java/com/bitse/geotreasure/core/model/LiangxiangTreasureCatalog.kt',
    import.meta.url,
  ),
);
const openApi = await SwaggerParser.dereference(openApiPath);
await SwaggerParser.validate(openApiPath);
const fixture = JSON.parse(readFileSync(fixturePath, 'utf8'));
const androidCatalog = readFileSync(androidCatalogPath, 'utf8');

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

assert(fixture.puzzleId === 'puzzle_liangxiang_campus', 'Unexpected canonical puzzle ID');
assert(fixture.sections.length === 3, 'Canonical puzzle must have exactly 3 sections');
assert(
  new Set(fixture.sections.map((section) => section.campusZone)).size === 3,
  'Campus zones must be unique',
);
assert(
  fixture.sections.map((section) => section.campusZone).join(',') === 'NORTH,EAST,SOUTH',
  'Campus sections must follow the Android CampusZone order',
);

const fragmentIds = new Set();
const landmarkIds = new Set();
for (const section of fixture.sections) {
  assert(section.rows === 2 && section.columns === 2, `${section.id} must be 2x2`);
  assert(section.fragments.length === 4, `${section.id} must contain four fragments`);
  assert(
    new Set(section.fragments.map((fragment) => fragment.pieceIndex)).size === 4 &&
      section.fragments.every((fragment) => fragment.pieceIndex >= 0 && fragment.pieceIndex <= 3),
    `${section.id} piece indexes must be unique 0..3`,
  );
  for (const fragment of section.fragments) {
    assert(!fragmentIds.has(fragment.id), `Duplicate fragment ID ${fragment.id}`);
    fragmentIds.add(fragment.id);
    assert(fragment.id === `fragment_${fragment.landmarkId.slice(4)}`, 'Fragment/landmark ID mismatch');
    assert(androidCatalog.includes(`landmark("${fragment.landmarkId.slice(4)}"`), 'Fragment is missing from Android catalog');
  }
}

for (const landmark of fixture.landmarks) {
  assert(!landmarkIds.has(landmark.id), `Duplicate landmark ID ${landmark.id}`);
  landmarkIds.add(landmark.id);
  assert(landmark.coordinateSystem === 'GCJ02', `${landmark.id} must use GCJ02`);
  assert(landmark.coordinateVerified === false, `${landmark.id} must remain unverified`);
  assert(
    landmark.checkInArea.type === 'circle' || landmark.checkInArea.type === 'polygon',
    `${landmark.id} has an invalid checkInArea`,
  );
}

assert(landmarkIds.size === 12, 'Canonical catalog must contain 12 landmarks');
assert(fragmentIds.size === 12, 'Canonical catalog must contain 12 fragments');

const puzzleProgress = openApi.components.schemas.PuzzleProgress;
const sectionItems = puzzleProgress.properties.sections;
assert(sectionItems.minItems === 3 && sectionItems.maxItems === 3, 'OpenAPI must require 3 sections');
assert(
  sectionItems.items.properties.rows.enum?.includes(2) &&
    sectionItems.items.properties.columns.enum?.includes(2),
  'OpenAPI must require 2x2 sections',
);
assert(
  sectionItems.items.properties.fragments.minItems === 4 &&
    sectionItems.items.properties.fragments.maxItems === 4,
  'OpenAPI must require four fragments per section',
);

console.log('OpenAPI contract and canonical campus fixture are valid.');
