export type Account = {
  username: string;
  role: "ADMIN" | "USER";
  putIoConfigured: boolean;
  canary: string;
};

export type Torrent = {
  name: string;
  source: string;
  magnet: string;
  size: number;
  seeders: number;
  leechers: number;
};

export type SearchOutcome = { results: Torrent[]; failures: string[] };

export type Source = {
  id: string;
  name: string;
  summary: string;
  enabled: boolean;
};

export type SavedSearch = {
  id: string;
  name: string;
  query: string;
  minimumSeeders: number;
  enabled: boolean;
  lastCheckedAt: string | null;
  createdAt: string;
  knownResultCount: number;
};

export type Transfer = {
  id: number;
  name: string;
  status: string;
  percentDone: number;
  size: number;
  fileId: number | null;
};

export type PutFile = {
  id: number;
  name: string;
  size: number;
  contentType: string;
  directory: boolean;
};

export type UserRecord = {
  id: string;
  username: string;
  role: "ADMIN" | "USER";
  enabled: boolean;
  createdAt: string;
};

export type InviteRecord = {
  id: string;
  codeHint: string;
  label: string;
  createdBy: string;
  createdAt: string;
  expiresAt: string | null;
  usedBy: string | null;
  usedAt: string | null;
  revokedAt: string | null;
  status: "ACTIVE" | "USED" | "EXPIRED" | "REVOKED";
};

export type AuditRecord = {
  id: string;
  username: string;
  action: string;
  targetType: string;
  targetId: string | null;
  detail: string | null;
  createdAt: string;
};
