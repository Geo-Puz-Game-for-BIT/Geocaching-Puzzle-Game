import SwaggerParser from '@apidevtools/swagger-parser';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { validateCampusContract } from './campus-contract.ts';

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

validateCampusContract(fixture, androidCatalog);

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
