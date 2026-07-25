"use client";

import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import {
  Anchor, Bookmark, Cast, Check, ChevronLeft, Copy, Download, File, Folder,
  KeyRound, LogOut, Menu, Play, Plus, RefreshCw, Search, Settings, Share2, Shield,
  Trash2, UserPlus, Users, X
} from "lucide-react";
import { api, ApiError, login, logout, primeCsrf } from "./api";
import type {
  Account, AuditRecord, InviteRecord, PutFile, SavedSearch, SearchOutcome, Source,
  Torrent, Transfer, UserRecord
} from "./types";

type Tab = "search" | "saved" | "transfers" | "files" | "sources" | "admin";

const tabs: { id: Tab; label: string; icon: typeof Search }[] = [
  { id: "search", label: "Search", icon: Search },
  { id: "saved", label: "Saved", icon: Bookmark },
  { id: "transfers", label: "Transfers", icon: Download },
  { id: "files", label: "Files", icon: Folder },
  { id: "sources", label: "Sources", icon: Settings }
];

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "Something went wrong.";
}

function formatBytes(value: number) {
  if (!value) return "—";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const power = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1);
  return `${(value / 1024 ** power).toFixed(power > 1 ? 1 : 0)} ${units[power]}`;
}

function useAsync<T>(loader: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setData(await loader());
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setLoading(false);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useEffect(() => { void load(); }, [load]);
  return { data, setData, error, loading, load };
}

function App() {
  const [account, setAccount] = useState<Account | null>(null);
  const [ready, setReady] = useState(false);
  const [registrationEnabled, setRegistrationEnabled] = useState(false);

  const checkSession = useCallback(async () => {
    try {
      const csrf = await primeCsrf();
      setRegistrationEnabled(csrf.registrationEnabled);
      setAccount(await api<Account>("/api/auth/me"));
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401) console.error(error);
      setAccount(null);
    } finally {
      setReady(true);
    }
  }, []);

  useEffect(() => { void checkSession(); }, [checkSession]);

  if (!ready) return <Splash />;
  if (!account) {
    return <AuthScreen registrationEnabled={registrationEnabled} onAuthenticated={checkSession} />;
  }
  return <Shell account={account} onSignedOut={() => setAccount(null)} />;
}

function Splash() {
  return <main className="splash"><img src="/pirate-penguin.png" alt="" /><span>Loading the ship…</span></main>;
}

function AuthScreen({
  registrationEnabled,
  onAuthenticated
}: {
  registrationEnabled: boolean;
  onAuthenticated: () => Promise<void>;
}) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [canary, setCanary] = useState("WEB-LOCAL");

  useEffect(() => {
    api<{ canary: string }>("/api/meta").then(data => setCanary(data.canary.toUpperCase())).catch(() => {});
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const form = new FormData(event.currentTarget);
    const username = String(form.get("username") || "");
    const password = String(form.get("password") || "");
    const passwordConfirmation = String(form.get("passwordConfirmation") || "");
    if (mode === "register" && password !== passwordConfirmation) {
      setError("Passwords do not match.");
      setBusy(false);
      return;
    }
    try {
      if (mode === "register") {
        await api("/api/auth/register", {
          method: "POST",
          body: { username, password, inviteCode: String(form.get("inviteCode") || "") }
        });
      }
      await login(username, password);
      await onAuthenticated();
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-brand">
        <div className="auth-emblem"><img src="/pirate-penguin.png" alt="Pirate penguin" /></div>
        <p className="eyebrow">Private search deck</p>
        <h1>The Pirate<br />Browser</h1>
        <p>Search across independent indexes, save discoveries, and steer put.io from one secure harbor.</p>
        <span className="canary">{canary}</span>
      </section>
      <section className="auth-panel">
        <div className="auth-card">
          <span className="mini-logo"><Anchor size={18} /> Members only</span>
          <h2>{mode === "login" ? "Welcome aboard" : "Accept your invitation"}</h2>
          <p>{mode === "login" ? "Sign in to open your private dashboard." : "Choose your credentials. Passwords must be at least 12 characters."}</p>
          {error && <div className="notice error">{error}</div>}
          <form onSubmit={submit}>
            <label>Username<input name="username" autoComplete="username" required maxLength={40} /></label>
            <label>Password<input name="password" type="password" autoComplete={mode === "login" ? "current-password" : "new-password"} required minLength={12} /></label>
            {mode === "register" && (
              <>
                <label>Confirm password<input name="passwordConfirmation" type="password" autoComplete="new-password" required minLength={12} /></label>
                <label>Invitation code<input name="inviteCode" autoComplete="off" required /></label>
              </>
            )}
            <button className="button primary full" disabled={busy}>
              {busy && <RefreshCw size={17} className="spin" />}
              {mode === "login" ? "Sign in" : "Create account"}
            </button>
          </form>
          {registrationEnabled && (
            <button className="text-button" onClick={() => { setMode(mode === "login" ? "register" : "login"); setError(""); }}>
              {mode === "login" ? "Have an invitation? Create an account" : "Already have an account? Sign in"}
            </button>
          )}
        </div>
      </section>
    </main>
  );
}

function Shell({ account, onSignedOut }: { account: Account; onSignedOut: () => void }) {
  const [tab, setTab] = useState<Tab>("search");
  const [menu, setMenu] = useState(false);
  const navigation = account.role === "ADMIN"
    ? [...tabs, { id: "admin" as Tab, label: "Admin", icon: Shield }]
    : tabs;
  async function signOut() {
    await logout();
    onSignedOut();
  }
  const title = navigation.find(item => item.id === tab)?.label || "Search";
  return (
    <div className="app-shell">
      <aside className={menu ? "sidebar open" : "sidebar"}>
        <div className="brand"><img src="/pirate-penguin.png" alt="" /><span>The Pirate<br /><b>Browser</b></span></div>
        <nav>
          {navigation.map(item => <NavButton key={item.id} item={item} active={tab === item.id} onClick={() => { setTab(item.id); setMenu(false); }} />)}
        </nav>
        <div className="sidebar-foot">
          <div className="user-chip"><span>{account.username.slice(0, 1).toUpperCase()}</span><div><b>{account.username}</b><small>{account.role.toLowerCase()}</small></div></div>
          <button className="icon-button" aria-label="Sign out" onClick={signOut}><LogOut size={19} /></button>
          <span className="canary">{account.canary}</span>
        </div>
      </aside>
      {menu && <button className="scrim" aria-label="Close menu" onClick={() => setMenu(false)} />}
      <main className="workspace">
        <header className="topbar">
          <button className="menu-button" onClick={() => setMenu(true)}><Menu /><span>Menu</span></button>
          <div><p className="eyebrow">Private dashboard</p><h1>{title}</h1></div>
          <div className={`status-dot ${account.putIoConfigured ? "online" : ""}`}>
            <span /> put.io {account.putIoConfigured ? "ready" : "offline"}
          </div>
        </header>
        <div className="content">
          {tab === "search" && <SearchView onNavigate={setTab} putIoConfigured={account.putIoConfigured} />}
          {tab === "saved" && <SavedView />}
          {tab === "transfers" && <TransfersView configured={account.putIoConfigured} />}
          {tab === "files" && <FilesView configured={account.putIoConfigured} />}
          {tab === "sources" && <SourcesView />}
          {tab === "admin" && <AdminView />}
        </div>
      </main>
      <nav className="bottom-nav">
        {navigation.slice(0, 5).map(item => <NavButton key={item.id} item={item} active={tab === item.id} onClick={() => setTab(item.id)} />)}
      </nav>
    </div>
  );
}

function NavButton({ item, active, onClick }: {
  item: { id: Tab; label: string; icon: typeof Search }; active: boolean; onClick: () => void
}) {
  const Icon = item.icon;
  return <button className={active ? "nav-item active" : "nav-item"} onClick={onClick}><Icon size={20} /><span>{item.label}</span></button>;
}

function SearchView({ onNavigate, putIoConfigured }: { onNavigate: (tab: Tab) => void; putIoConfigured: boolean }) {
  const [query, setQuery] = useState("");
  const [minimumSeeders, setMinimumSeeders] = useState(0);
  const [outcome, setOutcome] = useState<SearchOutcome | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  async function runSearch(event: FormEvent) {
    event.preventDefault();
    if (!query.trim()) return;
    setLoading(true); setError(""); setMessage("");
    try {
      setOutcome(await api(`/api/search?q=${encodeURIComponent(query)}&minimumSeeders=${minimumSeeders}`));
    } catch (reason) { setError(errorMessage(reason)); }
    finally { setLoading(false); }
  }
  async function add(magnet: string) {
    try {
      await api("/api/putio/transfers", { method: "POST", body: { magnet } });
      setMessage("Transfer added to put.io.");
    } catch (reason) { setError(errorMessage(reason)); }
  }
  async function save() {
    try {
      await api("/api/saved-searches", {
        method: "POST",
        body: { name: query, query, minimumSeeders, enabled: true, knownMagnets: outcome?.results.map(item => item.magnet) || [] }
      });
      setMessage("Search saved.");
    } catch (reason) { setError(errorMessage(reason)); }
  }
  return (
    <>
      <section className="hero-card">
        <div><p className="eyebrow">Seven sources. One clean manifest.</p><h2>What are we hunting?</h2><p>Results are merged and deduplicated without exposing your put.io credential.</p></div>
        <form className="search-form" onSubmit={runSearch}>
          <div className="search-box"><Search /><input value={query} onChange={event => setQuery(event.target.value)} placeholder="Search torrents…" aria-label="Search torrents" maxLength={250} /><button disabled={loading}>{loading ? <RefreshCw className="spin" /> : "Search"}</button></div>
          <label className="seed-filter">Minimum seeders <input type="number" min="0" max="100000" value={minimumSeeders} onChange={event => setMinimumSeeders(Number(event.target.value))} /></label>
        </form>
      </section>
      {error && <div className="notice error">{error}</div>}
      {message && <div className="notice success"><Check size={17} />{message}<button onClick={() => onNavigate("transfers")}>View transfers</button></div>}
      {outcome && (
        <section>
          <div className="section-heading"><div><p className="eyebrow">Manifest</p><h2>{outcome.results.length} results</h2></div><button className="button secondary" onClick={save}><Bookmark size={17} /> Save search</button></div>
          {outcome.failures.length > 0 && <div className="notice warning">Some sources did not answer: {outcome.failures.join(", ")}</div>}
          <div className="result-list">
            {outcome.results.length === 0 ? <Empty icon={Search} title="No matching cargo" text="Try a broader phrase or reduce the minimum seeder count." /> :
              outcome.results.map(result => <TorrentRow key={`${result.source}-${result.magnet}`} result={result} configured={putIoConfigured} onAdd={add} />)}
          </div>
        </section>
      )}
      {!outcome && !loading && <Empty icon={Anchor} title="The manifest is empty" text="Run a search to query every enabled source at once." />}
    </>
  );
}

function TorrentRow({ result, configured, onAdd }: { result: Torrent; configured: boolean; onAdd: (magnet: string) => void }) {
  async function share() {
    if (navigator.share) await navigator.share({ title: result.name, text: result.magnet });
    else await navigator.clipboard.writeText(result.magnet);
  }
  return (
    <article className="result-row">
      <div className="result-main"><span className="source-badge">{result.source}</span><h3>{result.name}</h3><p>{formatBytes(result.size)} · <b>{result.seeders}</b> seeders · {result.leechers} leechers</p></div>
      <div className="row-actions">
        <button className="button ghost" onClick={share}><Share2 size={16} /> Share</button>
        <button className="button primary" disabled={!configured} onClick={() => onAdd(result.magnet)}><Plus size={16} /> put.io</button>
      </div>
    </article>
  );
}

function SavedView() {
  const saved = useAsync(() => api<SavedSearch[]>("/api/saved-searches"), []);
  const [checking, setChecking] = useState("");
  const [message, setMessage] = useState("");
  async function check(id: string) {
    setChecking(id); setMessage("");
    try {
      const result = await api<{ newCount: number; results: Torrent[] }>(`/api/saved-searches/${id}/check`, { method: "POST" });
      setMessage(`${result.newCount} new result${result.newCount === 1 ? "" : "s"} found.`);
      await saved.load();
    } catch (reason) { setMessage(errorMessage(reason)); }
    finally { setChecking(""); }
  }
  async function toggle(item: SavedSearch) {
    await api(`/api/saved-searches/${item.id}`, { method: "PUT", body: { ...item, enabled: !item.enabled } });
    await saved.load();
  }
  async function remove(item: SavedSearch) {
    if (!confirm(`Delete “${item.name}”?`)) return;
    await api(`/api/saved-searches/${item.id}`, { method: "DELETE" });
    await saved.load();
  }
  return (
    <PanelState {...saved}>
      <div className="section-heading"><div><p className="eyebrow">Watch list</p><h2>Your saved searches</h2></div><button className="button secondary" onClick={saved.load}><RefreshCw size={17} /> Refresh</button></div>
      {message && <div className="notice">{message}</div>}
      <div className="card-grid">
        {saved.data?.map(item => (
          <article className="data-card" key={item.id}>
            <div className="card-title"><Bookmark /><div><h3>{item.name}</h3><p>“{item.query}”</p></div><button className={`toggle ${item.enabled ? "on" : ""}`} aria-label="Toggle saved search" onClick={() => toggle(item)}><span /></button></div>
            <div className="stat-row"><span>Minimum seeders <b>{item.minimumSeeders}</b></span><span>Known results <b>{item.knownResultCount}</b></span></div>
            <p className="muted">Last checked: {item.lastCheckedAt ? new Date(item.lastCheckedAt).toLocaleString() : "Never"}</p>
            <div className="row-actions"><button className="button primary" disabled={checking === item.id} onClick={() => check(item.id)}>{checking === item.id ? <RefreshCw className="spin" size={16} /> : <Search size={16} />} Check now</button><button className="icon-button danger" aria-label="Delete saved search" onClick={() => remove(item)}><Trash2 size={17} /></button></div>
          </article>
        ))}
      </div>
      {saved.data?.length === 0 && <Empty icon={Bookmark} title="No saved searches" text="Save any search from the Search tab and it will appear here." />}
    </PanelState>
  );
}

function TransfersView({ configured }: { configured: boolean }) {
  const transfers = useAsync(() => configured ? api<Transfer[]>("/api/putio/transfers") : Promise.resolve([]), [configured]);
  useEffect(() => {
    if (!configured) return;
    const timer = window.setInterval(() => void transfers.load(), 3000);
    return () => window.clearInterval(timer);
  }, [configured, transfers.load]);
  async function cancel(id: number) {
    if (!confirm("Cancel this transfer?")) return;
    await api(`/api/putio/transfers/${id}`, { method: "DELETE" });
    await transfers.load();
  }
  if (!configured) return <Unavailable />;
  return (
    <PanelState {...transfers}>
      <div className="section-heading"><div><p className="eyebrow">Live queue · 3 second updates</p><h2>put.io transfers</h2></div><button className="button secondary" onClick={transfers.load}><RefreshCw size={17} /> Refresh</button></div>
      <div className="result-list">{transfers.data?.map(item => (
        <article className="result-row transfer" key={item.id}>
          <div className="result-main"><span className="source-badge">{item.status}</span><h3>{item.name}</h3><div className="progress"><span style={{ width: `${Math.max(0, Math.min(100, item.percentDone))}%` }} /></div><p>{item.percentDone.toFixed(1)}% · {formatBytes(item.size)}</p></div>
          <button className="icon-button danger" aria-label="Cancel transfer" onClick={() => cancel(item.id)}><X /></button>
        </article>
      ))}</div>
      {transfers.data?.length === 0 && <Empty icon={Download} title="No active transfers" text="Add a result from Search and its progress will appear here." />}
    </PanelState>
  );
}

function FilesView({ configured }: { configured: boolean }) {
  const [stack, setStack] = useState<{ id: number; name: string }[]>([{ id: 0, name: "Files" }]);
  const parent = stack[stack.length - 1];
  const files = useAsync(() => configured ? api<{ files: PutFile[] }>(`/api/putio/files/${parent.id}`) : Promise.resolve({ files: [] }), [configured, parent.id]);
  const [playing, setPlaying] = useState<PutFile | null>(null);
  async function remove(item: PutFile) {
    if (!confirm(`Delete “${item.name}” from the shared put.io account?`)) return;
    await api(`/api/putio/files/${item.id}`, { method: "DELETE" });
    await files.load();
  }
  async function rename(item: PutFile) {
    const name = prompt("New file name", item.name);
    if (!name || name === item.name) return;
    await api(`/api/putio/files/${item.id}`, { method: "PATCH", body: { name, parentId: parent.id } });
    await files.load();
  }
  if (!configured) return <Unavailable />;
  return (
    <PanelState {...files} data={files.data?.files || null}>
      <div className="section-heading"><div><p className="eyebrow">Shared library</p><h2>{parent.name}</h2></div><div className="row-actions">{stack.length > 1 && <button className="button ghost" onClick={() => setStack(stack.slice(0, -1))}><ChevronLeft size={17} /> Back</button>}<button className="button secondary" onClick={files.load}><RefreshCw size={17} /> Refresh</button></div></div>
      <div className="file-grid">{files.data?.files.map(item => (
        <article className="file-card" key={item.id}>
          <button className="file-open" onClick={() => item.directory ? setStack([...stack, { id: item.id, name: item.name }]) : setPlaying(item)}>
            {item.directory ? <Folder /> : <File />}<span><b>{item.name}</b><small>{item.directory ? "Folder" : formatBytes(item.size)}</small></span>
          </button>
          <div className="file-actions"><button onClick={() => rename(item)}>Rename</button><button className="danger-text" onClick={() => remove(item)}>Delete</button></div>
        </article>
      ))}</div>
      {files.data?.files.length === 0 && <Empty icon={Folder} title="This folder is empty" text="Completed transfers will be visible here." />}
      {playing && <Player file={playing} onClose={() => setPlaying(null)} />}
    </PanelState>
  );
}

function Player({ file, onClose }: { file: PutFile; onClose: () => void }) {
  const mediaRef = useRef<HTMLVideoElement>(null);
  const [castUrl, setCastUrl] = useState("");
  const [castError, setCastError] = useState("");
  useEffect(() => {
    api<{ url: string }>(`/api/putio/files/${file.id}/cast`, { method: "POST" })
      .then(grant => setCastUrl(grant.url))
      .catch(reason => setCastError(errorMessage(reason)));
  }, [file.id]);
  async function cast() {
    const media = mediaRef.current as (HTMLVideoElement & { remote?: { prompt(): Promise<void> } }) | null;
    if (!media?.remote) return alert("Native casting is not available in this browser. Try Chrome with a Cast device on your network.");
    try { await media.remote.prompt(); } catch { /* The user may cancel the chooser. */ }
  }
  async function share() {
    const url = `${location.origin}/api/putio/files/${file.id}/content`;
    if (navigator.share) await navigator.share({ title: file.name, url });
    else await navigator.clipboard.writeText(url);
  }
  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-label={`Playing ${file.name}`}>
      <div className="player-modal">
        <div className="modal-head"><div><p className="eyebrow">Now playing</p><h2>{file.name}</h2></div><button className="icon-button" onClick={onClose}><X /></button></div>
        <video ref={mediaRef} controls autoPlay src={castUrl || `/api/putio/files/${file.id}/content`} />
        {castError && <div className="notice warning">{castError}</div>}
        <div className="player-actions"><button className="button secondary" onClick={share}><Share2 size={17} /> Share</button><button className="button primary" disabled={!castUrl} onClick={cast}><Cast size={17} /> Cast</button></div>
      </div>
    </div>
  );
}

function SourcesView() {
  const sources = useAsync(() => api<Source[]>("/api/sources"), []);
  async function toggle(source: Source) {
    if (!sources.data) return;
    const enabled = sources.data.filter(item => item.id !== source.id && item.enabled).map(item => item.id);
    if (!source.enabled) enabled.push(source.id);
    sources.setData(await api("/api/sources", { method: "PUT", body: { enabled } }));
  }
  return (
    <PanelState {...sources}>
      <div className="section-heading"><div><p className="eyebrow">Search fleet</p><h2>Choose your sources</h2></div></div>
      <div className="card-grid">{sources.data?.map(source => (
        <button className={`source-card ${source.enabled ? "enabled" : ""}`} key={source.id} onClick={() => toggle(source)}>
          <span className="source-check">{source.enabled && <Check size={16} />}</span><div><h3>{source.name}</h3><p>{source.summary}</p></div>
        </button>
      ))}</div>
    </PanelState>
  );
}

function AdminView() {
  const users = useAsync(() => api<UserRecord[]>("/api/admin/users"), []);
  const invites = useAsync(() => api<InviteRecord[]>("/api/admin/invites"), []);
  const audit = useAsync(() => api<AuditRecord[]>("/api/admin/audit"), []);
  const [accountError, setAccountError] = useState("");
  const [inviteError, setInviteError] = useState("");
  const [generatedCode, setGeneratedCode] = useState("");
  const [copied, setCopied] = useState(false);
  const [busy, setBusy] = useState<"account" | "invite" | "">("");

  async function createAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const password = String(form.get("password") || "");
    if (password !== String(form.get("passwordConfirmation") || "")) {
      setAccountError("Passwords do not match.");
      return;
    }
    setBusy("account");
    setAccountError("");
    try {
      await api("/api/admin/users", {
        method: "POST",
        body: { username: form.get("username"), password, role: form.get("role") }
      });
      formElement.reset();
      await users.load();
      await audit.load();
    } catch (reason) {
      setAccountError(errorMessage(reason));
    } finally {
      setBusy("");
    }
  }

  async function generateInvite(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setBusy("invite");
    setInviteError("");
    setGeneratedCode("");
    setCopied(false);
    try {
      const created = await api<{ code: string; invite: InviteRecord }>("/api/admin/invites", {
        method: "POST",
        body: { label: form.get("label"), expiryDays: form.get("expiryDays") }
      });
      setGeneratedCode(created.code);
      formElement.reset();
      await invites.load();
      await audit.load();
    } catch (reason) {
      setInviteError(errorMessage(reason));
    } finally {
      setBusy("");
    }
  }

  async function copyInvite() {
    await navigator.clipboard.writeText(generatedCode);
    setCopied(true);
  }

  async function revokeInvite(invite: InviteRecord) {
    if (!window.confirm(`Revoke the invitation "${invite.label}"?`)) return;
    setInviteError("");
    try {
      await api("/api/admin/invites", { method: "DELETE", body: { id: invite.id } });
      await invites.load();
      await audit.load();
    } catch (reason) {
      setInviteError(errorMessage(reason));
    }
  }

  async function toggle(user: UserRecord) {
    setAccountError("");
    try {
      await api("/api/admin/users/enabled", { method: "PATCH", body: { id: user.id, enabled: !user.enabled } });
      await users.load();
      await audit.load();
    } catch (reason) {
      setAccountError(errorMessage(reason));
    }
  }

  const activeInvites = invites.data?.filter(invite => invite.status === "ACTIVE").length ?? 0;
  const enabledUsers = users.data?.filter(user => user.enabled).length ?? 0;
  return (
    <>
      <section className="admin-hero">
        <div>
          <p className="eyebrow">Captain&apos;s controls</p>
          <h2>Admin console</h2>
          <p>Invite friends, manage access, and review account activity from one place.</p>
        </div>
        <div className="admin-stats">
          <span><b>{enabledUsers}</b> active accounts</span>
          <span><b>{activeInvites}</b> ready invites</span>
          <span><b>{audit.data?.length ?? 0}</b> recent events</span>
        </div>
      </section>

      <section className="admin-grid">
        <div className="admin-card invite-card">
          <div className="admin-card-head">
            <span><KeyRound /></span>
            <div>
              <p className="eyebrow">Recommended</p>
              <h2>Generate an invitation</h2>
              <p>Create a one-time code, copy it, and send it directly to your friend.</p>
            </div>
          </div>
          {inviteError && <div className="notice error">{inviteError}</div>}
          <form className="invite-form" onSubmit={generateInvite}>
            <label>Who is this for?<input name="label" placeholder="e.g. Sam" maxLength={80} /></label>
            <label>Expires in
              <select name="expiryDays" defaultValue="30">
                <option value="1">1 day</option>
                <option value="7">7 days</option>
                <option value="30">30 days</option>
                <option value="90">90 days</option>
                <option value="never">Never</option>
              </select>
            </label>
            <button className="button primary" disabled={busy === "invite"}>
              {busy === "invite" ? <RefreshCw size={17} className="spin" /> : <Plus size={17} />}
              Generate invite
            </button>
          </form>
          {generatedCode && (
            <div className="generated-invite" role="status">
              <div><small>New invitation code</small><code>{generatedCode}</code></div>
              <button className="button secondary" type="button" onClick={copyInvite}>
                {copied ? <Check size={17} /> : <Copy size={17} />}
                {copied ? "Copied" : "Copy"}
              </button>
              <p>This is shown once. Copy it before leaving this page.</p>
            </div>
          )}
          <div className="invite-list">
            <div className="subsection-title"><h3>Recent invitations</h3><button className="text-button compact" onClick={invites.load}>Refresh</button></div>
            {invites.loading && !invites.data && <p className="muted">Loading invitations…</p>}
            {invites.error && <div className="notice error">{invites.error}</div>}
            {invites.data?.length === 0 && <p className="muted">No generated invitations yet.</p>}
            {invites.data?.map(invite => (
              <article className="invite-row" key={invite.id}>
                <div>
                  <b>{invite.label}</b>
                  <small>••••{invite.codeHint} · created {new Date(invite.createdAt).toLocaleDateString()}</small>
                </div>
                <span className={`status-pill ${invite.status.toLowerCase()}`}>{invite.status.toLowerCase()}</span>
                {invite.status === "ACTIVE" && (
                  <button className="button ghost small danger-text" type="button" onClick={() => revokeInvite(invite)}>Revoke</button>
                )}
              </article>
            ))}
          </div>
        </div>

        <form className="admin-card account-card" onSubmit={createAccount}>
          <div className="admin-card-head">
            <span><UserPlus /></span>
            <div>
              <p className="eyebrow">Direct access</p>
              <h2>Create an account</h2>
              <p>Use this when you want to choose the credentials yourself.</p>
            </div>
          </div>
          {accountError && <div className="notice error">{accountError}</div>}
          <label>Username<input name="username" autoComplete="off" required maxLength={40} /></label>
          <label>Temporary password<input name="password" type="password" autoComplete="new-password" minLength={12} required /></label>
          <label>Confirm password<input name="passwordConfirmation" type="password" autoComplete="new-password" minLength={12} required /></label>
          <label>Role<select name="role"><option value="USER">Standard user</option><option value="ADMIN">Administrator</option></select></label>
          <button className="button primary" disabled={busy === "account"}>
            {busy === "account" ? <RefreshCw size={17} className="spin" /> : <Users size={17} />}
            Create account
          </button>
        </form>
      </section>

      <section>
        <div className="section-heading"><div><p className="eyebrow">Access control</p><h2>Accounts</h2></div><button className="button ghost" onClick={users.load}><RefreshCw size={17} /> Refresh</button></div>
        {users.error && <div className="notice error">{users.error}</div>}
        {accountError && <div className="notice error">{accountError}</div>}
        <div className="user-list">{users.data?.map(user => (
          <article key={user.id}>
            <div className="user-chip"><span>{user.username[0].toUpperCase()}</span><div><b>{user.username}</b><small>{user.role.toLowerCase()} · joined {new Date(user.createdAt).toLocaleDateString()}</small></div></div>
            <div className="account-status"><span className={`status-pill ${user.enabled ? "active" : "revoked"}`}>{user.enabled ? "active" : "disabled"}</span><button type="button" aria-label={`${user.enabled ? "Disable" : "Enable"} ${user.username}`} className={`toggle ${user.enabled ? "on" : ""}`} onClick={() => toggle(user)}><span /></button></div>
          </article>
        ))}</div>
      </section>
      <section>
        <div className="section-heading"><div><p className="eyebrow">Latest 200 events</p><h2>Audit log</h2></div><button className="button secondary" onClick={audit.load}><RefreshCw size={17} /> Refresh</button></div>
        <div className="audit-table">{audit.data?.map(event => <article key={event.id}><time>{new Date(event.createdAt).toLocaleString()}</time><b>{event.username}</b><span>{event.action.replaceAll("_", " ")}</span><small>{event.targetType}{event.targetId ? ` · ${event.targetId}` : ""}</small></article>)}</div>
      </section>
    </>
  );
}

function PanelState<T>({ loading, error, data, children }: { loading: boolean; error: string; data: T | null; children: React.ReactNode; [key: string]: unknown }) {
  if (loading && !data) return <Loading />;
  if (error && !data) return <Empty icon={X} title="Could not load this deck" text={error} />;
  return <>{children}</>;
}

function Loading() {
  return <div className="loading-state"><RefreshCw className="spin" /><span>Loading…</span></div>;
}

function Empty({ icon: Icon, title, text }: { icon: typeof Search; title: string; text: string }) {
  return <div className="empty-state"><span><Icon /></span><h2>{title}</h2><p>{text}</p></div>;
}

function Unavailable() {
  return <Empty icon={Anchor} title="put.io is not configured" text="The server owner needs to set PUTIO_OAUTH_TOKEN and restart the service. The credential stays on the backend." />;
}

export default App;
