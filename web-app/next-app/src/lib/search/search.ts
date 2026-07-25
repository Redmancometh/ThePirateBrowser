import type {
  SearchOutcome,
  TorrentResult,
  TorrentSource,
} from "@/lib/search/types";

const USER_AGENT = "PirateBrowser-Web/2.0";
const HASH = /btih:([a-z0-9]{32,40})/i;

function integer(value: unknown): number {
  const number = typeof value === "number" ? value : Number.parseInt(String(value), 10);
  return Number.isFinite(number) ? number : 0;
}

function size(value: unknown): number {
  const number = typeof value === "number" ? value : Number.parseInt(String(value), 10);
  return Number.isSafeInteger(number) && number > 0 ? number : 0;
}

function magnet(hash: string, name: string): string {
  return `magnet:?xt=urn:btih:${encodeURIComponent(hash)}&dn=${encodeURIComponent(name)}`;
}

async function checkedFetch(
  input: string,
  init: RequestInit = {},
): Promise<Response> {
  const response = await fetch(input, {
    ...init,
    headers: {
      Accept: "application/json",
      "User-Agent": USER_AGENT,
      ...init.headers,
    },
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return response;
}

async function json(input: string, init?: RequestInit): Promise<any> {
  return (await checkedFetch(input, init)).json();
}

async function pirateBay(query: string): Promise<TorrentResult[]> {
  const items = await json(`https://apibay.org/q.php?q=${encodeURIComponent(query)}`);
  return (Array.isArray(items) ? items : [])
    .filter((item) => item?.id !== "0" && item?.info_hash)
    .map((item) => {
      const name = item.name || "Untitled torrent";
      return {
        name,
        source: "PIRATE_BAY" as const,
        magnet: magnet(item.info_hash, name),
        size: size(item.size),
        seeders: integer(item.seeders),
        leechers: integer(item.leechers),
      };
    });
}

async function knaben(query: string): Promise<TorrentResult[]> {
  const root = await json("https://api.knaben.org/v1", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      search_type: "100%",
      search_field: "title",
      query,
      order_by: "seeders",
      order_direction: "desc",
      from: 0,
      size: 150,
      hide_unsafe: true,
      hide_xxx: true,
    }),
  });
  return (Array.isArray(root?.hits) ? root.hits : [])
    .filter((item: any) => item?.hash)
    .map((item: any) => {
      const name = item.title || "Untitled torrent";
      return {
        name,
        source: "KNABEN" as const,
        magnet: magnet(item.hash, name),
        size: size(item.bytes),
        seeders: integer(item.seeders),
        leechers: integer(item.peers),
      };
    });
}

async function magnetz(query: string): Promise<TorrentResult[]> {
  const root = await json(
    `https://magnetz.eu/api/magnets/search?query=${encodeURIComponent(query)}&page=1`,
  );
  return (Array.isArray(root?.data) ? root.data : [])
    .filter((item: any) => item?.info_hash)
    .map((item: any) => {
      const name = item.name || "Untitled torrent";
      return {
        name,
        source: "MAGNETZ" as const,
        magnet: magnet(item.info_hash, name),
        size: size(item.size),
        seeders: integer(item.seeders),
        leechers: integer(item.leechers),
      };
    });
}

async function torrentsCsv(query: string): Promise<TorrentResult[]> {
  const root = await json(
    `https://torrents-csv.com/service/search?q=${encodeURIComponent(query)}&size=50&page=1`,
  );
  return (Array.isArray(root?.torrents) ? root.torrents : [])
    .filter((item: any) => item?.infohash)
    .map((item: any) => {
      const name = item.name || "Untitled torrent";
      return {
        name,
        source: "TORRENTS_CSV" as const,
        magnet: magnet(item.infohash, name),
        size: size(item.size_bytes),
        seeders: integer(item.seeders),
        leechers: integer(item.leechers),
      };
    });
}

function decodeHtml(value: string): string {
  return value
    .replaceAll("&amp;", "&")
    .replaceAll("&quot;", '"')
    .replaceAll("&#39;", "'")
    .replaceAll("&lt;", "<")
    .replaceAll("&gt;", ">");
}

function humanSize(value: string): number {
  const match = value.match(/([0-9.]+)\s*([kmgt]?i?b)/i);
  if (!match) return 0;
  const powers: Record<string, number> = {
    b: 0,
    kb: 1,
    kib: 1,
    mb: 2,
    mib: 2,
    gb: 3,
    gib: 3,
    tb: 4,
    tib: 4,
  };
  return Math.floor(Number.parseFloat(match[1]) * 1024 ** powers[match[2].toLowerCase()]);
}

async function nyaa(query: string): Promise<TorrentResult[]> {
  const response = await checkedFetch(
    `https://nyaa.si/?f=0&c=0_0&q=${encodeURIComponent(query)}`,
    { headers: { Accept: "text/html" } },
  );
  const html = await response.text();
  const rows = html.match(/<tr[\s\S]*?<\/tr>/gi) ?? [];
  const results: TorrentResult[] = [];
  for (const row of rows) {
    const magnetMatch = row.match(/href="(magnet:[^"]+)"/i);
    const titleMatch =
      row.match(/<a[^>]+title="([^"]+)"[^>]*>/i) ??
      row.match(/<a[^>]+href="\/view\/[^"]+"[^>]*>([^<]+)<\/a>/i);
    if (!magnetMatch || !titleMatch) continue;
    const cells = [...row.matchAll(/<td[^>]*>([\s\S]*?)<\/td>/gi)].map((match) =>
      match[1].replace(/<[^>]+>/g, "").trim(),
    );
    results.push({
      name: decodeHtml(titleMatch[1]),
      source: "NYAA",
      magnet: decodeHtml(magnetMatch[1]),
      size: humanSize(cells[3] ?? ""),
      seeders: integer(cells[5]),
      leechers: integer(cells[6]),
    });
  }
  return results;
}

async function eztv(query: string): Promise<TorrentResult[]> {
  const root = await json("https://eztvx.to/api/get-torrents?limit=100&page=1");
  const normalized = query.toLocaleLowerCase();
  return (Array.isArray(root?.torrents) ? root.torrents : [])
    .filter((item: any) => String(item?.filename ?? "").toLocaleLowerCase().includes(normalized))
    .map((item: any) => ({
      name: item.filename,
      source: "EZTV" as const,
      magnet: item.magnet_url,
      size: size(item.size_bytes),
      seeders: integer(item.seeds),
      leechers: integer(item.peers),
    }));
}

async function yts(query: string): Promise<TorrentResult[]> {
  const root = await json(
    `https://movies-api.accel.li/api/v2/list_movies.json?limit=50&query_term=${encodeURIComponent(query)}`,
  );
  const results: TorrentResult[] = [];
  for (const movie of Array.isArray(root?.data?.movies) ? root.data.movies : []) {
    const title = movie.title_long || movie.title || "Untitled torrent";
    for (const torrent of Array.isArray(movie.torrents) ? movie.torrents : []) {
      if (!torrent.hash) continue;
      const name = torrent.quality ? `${title} ${torrent.quality}` : title;
      results.push({
        name,
        source: "YTS",
        magnet: magnet(torrent.hash, name),
        size: size(torrent.size_bytes),
        seeders: integer(torrent.seeds),
        leechers: integer(torrent.peers),
      });
    }
  }
  return results;
}

const SEARCHERS: Record<TorrentSource, (query: string) => Promise<TorrentResult[]>> = {
  PIRATE_BAY: pirateBay,
  KNABEN: knaben,
  MAGNETZ: magnetz,
  TORRENTS_CSV: torrentsCsv,
  NYAA: nyaa,
  EZTV: eztv,
  YTS: yts,
};

export async function searchTorrents(
  query: string,
  enabled: TorrentSource[],
): Promise<SearchOutcome> {
  if (enabled.length === 0) {
    return { results: [], failures: ["No torrent sources are enabled."] };
  }
  const settled = await Promise.allSettled(
    enabled.map(async (source) => ({ source, results: await SEARCHERS[source](query) })),
  );
  const failures: string[] = [];
  const unique = new Map<string, TorrentResult>();
  settled.forEach((outcome, index) => {
    if (outcome.status === "rejected") {
      const reason =
        outcome.reason instanceof Error ? outcome.reason.message : String(outcome.reason);
      failures.push(`${enabled[index]}: ${reason}`);
      return;
    }
    for (const result of outcome.value.results) {
      const identity = result.magnet.match(HASH)?.[1]?.toLowerCase() ?? result.magnet;
      const previous = unique.get(identity);
      if (!previous || result.seeders > previous.seeders) unique.set(identity, result);
    }
  });
  return {
    results: [...unique.values()].sort((left, right) => right.seeders - left.seeders),
    failures,
  };
}
