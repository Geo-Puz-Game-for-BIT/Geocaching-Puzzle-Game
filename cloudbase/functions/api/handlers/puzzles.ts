import { selectRows } from "../db.ts";
import { ApiError } from "../errors.ts";

interface PuzzleRow {
  id: string;
  name: string;
  rows: number;
  columns: number;
  default_reveal_mode: "GRID" | "FOG_OF_WAR";
}

interface SectionRow {
  id: string;
  puzzle_id: string;
  campus_zone: "NORTH" | "EAST" | "SOUTH";
  rows: number;
  columns: number;
  sort_order: number;
}

interface FragmentRow {
  id: string;
  section_id: string;
  landmark_id: string;
  title: string;
  piece_index: number;
  image_key: string;
}

interface GrantRow {
  fragment_id: string;
}

export async function getPuzzleProgress(userId: string, puzzleId: string) {
  const puzzles = await selectRows<PuzzleRow>("puzzles", {
    select: "id,name,rows,columns,default_reveal_mode",
    id: `eq.${puzzleId}`,
    limit: "1",
  });
  const puzzle = puzzles[0];
  if (!puzzle) {
    throw new ApiError("PUZZLE_NOT_FOUND", "Puzzle does not exist");
  }

  const [sections, fragments, grants] = await Promise.all([
    selectRows<SectionRow>("puzzle_sections", {
      select: "id,puzzle_id,campus_zone,rows,columns,sort_order",
      puzzle_id: `eq.${puzzleId}`,
      order: "sort_order.asc",
    }),
    selectRows<FragmentRow>("fragments", {
      select: "id,section_id,landmark_id,title,piece_index,image_key",
      puzzle_id: `eq.${puzzleId}`,
      order: "piece_index.asc",
    }),
    selectRows<GrantRow>("fragment_grants", {
      select: "fragment_id",
      user_id: `eq.${userId}`,
    }),
  ]);

  const owned = new Set(grants.map((grant) => grant.fragment_id));
  const sectionsWithProgress = sections.map((section) => ({
    id: section.id,
    campusZone: section.campus_zone,
    rows: section.rows,
    columns: section.columns,
    fragments: fragments
      .filter((fragment) => fragment.section_id === section.id)
      .map((fragment) => ({
        id: fragment.id,
        landmarkId: fragment.landmark_id,
        title: fragment.title,
        pieceIndex: fragment.piece_index,
        imageKey: fragment.image_key,
        owned: owned.has(fragment.id),
      })),
  }));
  const ownedFragmentIds = fragments
    .filter((fragment) => owned.has(fragment.id))
    .map((fragment) => fragment.id);

  return {
    puzzleId: puzzle.id,
    title: puzzle.name,
    defaultRevealMode: puzzle.default_reveal_mode,
    sections: sectionsWithProgress,
    ownedFragmentIds,
    completed: fragments.length > 0 && ownedFragmentIds.length === fragments.length,
  };
}
