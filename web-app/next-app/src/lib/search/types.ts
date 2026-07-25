export const TORRENT_SOURCES = [
  "PIRATE_BAY",
  "KNABEN",
  "MAGNETZ",
  "TORRENTS_CSV",
  "NYAA",
  "EZTV",
  "YTS",
] as const;

export type TorrentSource = (typeof TORRENT_SOURCES)[number];

export type TorrentResult = {
  name: string;
  source: TorrentSource;
  magnet: string;
  size: number;
  seeders: number;
  leechers: number;
};

export type SearchOutcome = {
  results: TorrentResult[];
  failures: string[];
};

export const SOURCE_DETAILS: Record<
  TorrentSource,
  { name: string; summary: string }
> = {
  PIRATE_BAY: {
    name: "The Pirate Bay",
    summary: "General-purpose torrent index.",
  },
  KNABEN: {
    name: "Knaben",
    summary: "Broad metasearch with safety filtering.",
  },
  MAGNETZ: { name: "Magnetz", summary: "Fast general magnet search." },
  TORRENTS_CSV: {
    name: "Torrents.csv",
    summary: "Open torrent database with broad coverage.",
  },
  NYAA: { name: "Nyaa", summary: "Anime-focused torrent search." },
  EZTV: { name: "EZTV", summary: "Recent television releases." },
  YTS: { name: "YTS", summary: "Movie releases in compact formats." },
};
