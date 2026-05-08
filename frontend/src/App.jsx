import { NavLink, Navigate, Route, Routes, useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { forwardRef, useEffect, useMemo, useRef, useState } from "react";
import { api, apiDownload, apiForm } from "./services/api";
import { downloadReportPdf } from "./utils/downloadReportPdf";
import { MapContainer, Marker, TileLayer, useMapEvents } from "react-leaflet";

function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [fullName, setFullName] = useState("");
  const [password, setPassword] = useState("");
  const [isRegister, setIsRegister] = useState(false);
  const [error, setError] = useState("");

  const submit = async (e) => {
    e.preventDefault();
    setError("");
    try {
      const endpoint = isRegister ? "/auth/register" : "/auth/login";
      const data = await api(endpoint, {
        method: "POST",
        body: JSON.stringify({ email, password, fullName }),
      });
      localStorage.setItem("token", data.token);
      localStorage.setItem("email", data.email);
      localStorage.setItem("fullName", data.fullName || "Земјоделец");
      navigate("/dashboard");
    } catch (err) {
      setError("Грешка при најава/регистрација.");
    }
  };

  return (
    <div className="auth-wrap">
      <div className="auth-overlay" />
      <form className="card auth-card" onSubmit={submit}>
        <div className="auth-logo">🌱</div>
        <h1>Agro AI</h1>
        <p>Најавете се за да продолжите</p>
        <label>Е-пошта</label>
        <input placeholder="vaseto.ime@email.com" value={email} onChange={(e) => setEmail(e.target.value)} />
        {isRegister && (
          <>
            <label>Име и презиме</label>
            <input placeholder="Марко Тодоровски" value={fullName} onChange={(e) => setFullName(e.target.value)} />
          </>
        )}
        <label>Лозинка</label>
        <input type="password" placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} />
        {error && <div className="error">{error}</div>}
        <button>{isRegister ? "Регистрирај се" : "Најави се"}</button>
        <button type="button" className="link-btn auth-switch" onClick={() => setIsRegister(!isRegister)}>
          {isRegister ? "Имате профил? Најави се" : "Немате профил? Регистрирај се"}
        </button>
      </form>
    </div>
  );
}

function AppShell({ children, title, subtitle, profile, centerPage }) {
  const navigate = useNavigate();
  const displayName = profile?.fullName || "Земјоделец";
  const initial = (displayName.trim().charAt(0) || "З").toUpperCase();
  const occ = (profile?.occupation && String(profile.occupation).trim()) || "Земјоделец";
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div>
          <div className="brand">Agro AI</div>
          <div className="tagline">Паметно земјоделство</div>
        </div>
        <nav className="side-nav">
          <NavLink to="/dashboard" end>Почетна</NavLink>
          <NavLink to="/parcels" end>Парцели</NavLink>
          <NavLink to="/history">Историја</NavLink>
          <NavLink to="/reports">Извештаи</NavLink>
          <NavLink to="/profile">Профил</NavLink>
        </nav>
        <div className="profile-mini">
          <NavLink to="/profile" className="sidebar-avatar-link" title="Профил">
            <div className="avatar">
              {profile?.avatarData ? <img className="avatar-img" src={profile.avatarData} alt="" /> : initial}
            </div>
          </NavLink>
          <div>
            <strong>{displayName}</strong>
            <div className="muted profile-mini-occ">{occ}</div>
          </div>
          <button className="logout-btn" onClick={() => { localStorage.clear(); navigate("/login"); }}>Одјави се</button>
        </div>
      </aside>
      <main className="content">
        <header className={centerPage ? "page-header page-header--center" : "page-header"}>
          <h2>{title}</h2>
          {subtitle ? <p>{subtitle}</p> : null}
        </header>
        {children}
      </main>
    </div>
  );
}

function sumParcelHectares(list) {
  return list.reduce((s, p) => s + (Number(p?.areaHa) || 0), 0);
}

/** WGS84: дозволува и запирка наместо точка; не враќа NaN. */
function parseCoordInput(raw) {
  if (raw == null) return null;
  const t = String(raw).trim();
  if (t === "") return null;
  const n = parseFloat(t.replace(",", "."), 10);
  return Number.isFinite(n) ? n : null;
}

function MapClickMarker({ lat, lon, onPick }) {
  useMapEvents({
    click(e) {
      onPick?.(e.latlng?.lat, e.latlng?.lng);
    },
  });
  if (lat == null || lon == null) return null;
  return <Marker position={[lat, lon]} />;
}

function ParcelMapPicker({ lat, lon, onPick }) {
  const has = lat != null && lon != null;
  const center = has ? [lat, lon] : [41.9981, 21.4254]; // Skopje as sensible default
  return (
    <div className="parcel-map">
      <MapContainer center={center} zoom={has ? 13 : 9} scrollWheelZoom className="parcel-map__inner">
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <MapClickMarker lat={lat} lon={lon} onPick={onPick} />
      </MapContainer>
      <div className="muted parcel-map__hint">Кликнете на мапата за да се постави маркер и автоматски да се пополнат координатите.</div>
    </div>
  );
}

/** history е по createdAt desc — прва појава по parcelId = најнова анализа за таа парцела. */
function latestRecByParcelId(history) {
  const m = new Map();
  (history || []).forEach((r) => {
    if (r?.parcelId != null && !m.has(r.parcelId)) m.set(r.parcelId, r);
  });
  return m;
}

function IconEye() {
  return <svg className="table-ico" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden><path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7Z" /><circle cx="12" cy="12" r="3" /></svg>;
}
function IconPencil() {
  return <svg className="table-ico" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden><path d="M12 20h9" /><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" /></svg>;
}
function IconTrash() {
  return <svg className="table-ico" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden><path d="M3 6h18" /><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" /><path d="M10 11v6" /><path d="M14 11v6" /></svg>;
}
function IconCalendar() {
  return <svg className="analysis-ico" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden><rect x="3" y="4" width="18" height="18" rx="2" /><path d="M3 10h18M8 2v4M16 2v4" /></svg>;
}
function IconStatParcels() {
  return (
    <svg className="stat-ico" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" aria-hidden>
      <path d="M3 3h8v8H3zM13 3h8v8h-8zM3 13h8v8H3zM13 13h8v8h-8z" />
    </svg>
  );
}
function IconStatArea() {
  return (
    <svg className="stat-ico" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
      <path d="M3 7V5a2 2 0 0 1 2-2h2" />
      <path d="M17 3h2a2 2 0 0 1 2 2v2" />
      <path d="M21 17v2a2 2 0 0 1-2 2h-2" />
      <path d="M7 21H5a2 2 0 0 1-2-2v-2" />
      <rect x="7" y="7" width="10" height="10" rx="1.5" />
    </svg>
  );
}
function IconTrend() {
  return <svg className="analysis-ico" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden><path d="M3 17l6-6 4 4 7-7" /><path d="M14 8h7v7" /></svg>;
}
function IconCheckCircle() {
  return <svg className="analysis-ico analysis-ico--ok" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden><path d="M12 22c5.5 0 10-4.5 10-10S17.5 2 12 2 2 6.5 2 12s4.5 10 10 10Z" /><path d="m8 12 2.5 2.5L16 9" /></svg>;
}
function IconSparkle() {
  return (
    <svg className="icon-sparkle" width="32" height="32" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M12 1.5l1.4 4.1 4.1 1.4-4.1 1.4L12 12.5l-1.4-4.1-4.1-1.4 4.1-1.4L12 1.5zm7 8.2l.9 2.6 2.6.9-2.6.9-.9 2.6-.9-2.6-2.6-.9 2.6-.9.9-2.6L19 9.7z" />
    </svg>
  );
}
function IconDroplet() {
  return <svg className="section-ico" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden><path d="M12 2.7c3 4 6 6.5 6 10a6 6 0 0 1-12 0c0-3.5 3-6 6-10Z" /></svg>;
}
function IconCloudRain() {
  return (
    <svg className="weather-head-icon" width="30" height="30" viewBox="0 0 24 24" fill="none" aria-hidden>
      <path
        d="M7 18a4.5 4.5 0 0 1-1.1-8.8A5.5 5.5 0 0 1 17.5 9a3.5 3.5 0 0 1-1.5 6.6"
        stroke="#2563eb"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M8 21v-2M12 21v-1M16 21v-2" stroke="#0ea5e9" strokeWidth="1.5" strokeLinecap="round" />
      <path d="M6 20v-1" stroke="#38bdf8" strokeWidth="1.3" strokeLinecap="round" />
    </svg>
  );
}
function IconPin() {
  return <svg className="section-ico" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden><path d="M12 21s7-4.4 7-10a7 7 0 0 0-14 0c0 5.6 7 10 7 10Z" /><circle cx="12" cy="11" r="2.5" /></svg>;
}

function riskLabelMk(level) {
  if (level === "LOW") return "Низок ризик";
  if (level === "MEDIUM") return "Среден ризик";
  if (level === "HIGH") return "Висок ризик";
  return level || "—";
}

function riskHeadlineFromLevel(level) {
  if (level === "LOW") return "Низок ризик од суша и оптимални услови за засејување";
  if (level === "MEDIUM") return "Умерен ризик: следете ги наводнувањето и времето";
  if (level === "HIGH") return "Повисок ризик: потребно е повеќе внимавање и планирање";
  return "Препорака според внесените податоци";
}

function riskAssessmentBodyFromLevel(level) {
  if (level === "LOW") {
    return "Врз основа на анализата на климатските податоци, влажноста на почвата и прогнозираните временски услови, "
      + "условите се оптимални за одгледување на препорачаните култури. Балансот помеѓу врнежите и "
      + "евапотранспирацијата е позитивен, што минимизира потреба од дополнително наводнување.";
  }
  if (level === "MEDIUM") {
    return "Врз основа на климатските податоци и влажноста на почвата, условите се умерени. "
      + "Следете ги препораките за наводнување и планирајте го засејувањето со поголема внимателност.";
  }
  if (level === "HIGH") {
    return "Показателите на сушење или неповолен воден биланс укажуваат на поголем ризик. "
      + "Препорачуваме консултација со агроном и внимателно планирање на наводнување пред засејвање.";
  }
  return "";
}

/** Содржина на печатен/прикажан агрономски извештај; `rec` од GET /recommendations/:id. */
const ReportDocument = forwardRef(function ReportDocument({ rec }, ref) {
  const top = (rec.topCrops && rec.topCrops.length) ? rec.topCrops : [];
  const climate = rec.climate;
  const mo = climate?.soilMoisture;
  const moPct = mo != null && mo <= 1.5 ? Math.round(mo * 100) : (mo != null ? Math.round(mo) : null);
  const dateStr = (() => {
    const d = new Date(rec.createdAt);
    if (Number.isNaN(d.getTime())) return "—";
    return d.toLocaleDateString("mk-MK", { year: "numeric", month: "long", day: "numeric" });
  })();
  const parcelLine = [rec.parcelName, rec.parcelLocation].filter(Boolean).join(" - ");

  return (
    <div className="report-document" ref={ref}>
      <header className="report-doc-header">
        <div className="report-brand">Агро AI</div>
        <h1 className="report-doc-title">Извештај за препорака на култури</h1>
        <p className="report-doc-sub">{parcelLine || rec.parcelName}</p>
        <p className="report-doc-date">{dateStr}</p>
      </header>

      <section className="report-section">
        <h2 className="report-section__h"><IconPin /> Информации за парцелата</h2>
        <div className="report-info-grid">
          <div><span>Име на парцела</span><strong>{rec.parcelName || "—"}</strong></div>
          <div><span>Локација</span><strong>{rec.parcelLocation || "—"}</strong></div>
          <div><span>Површина</span><strong>{rec.parcelAreaHa != null ? `${rec.parcelAreaHa} хектари` : "—"}</strong></div>
          <div><span>Тип на почва</span><strong>{rec.parcelSoilType || "—"}</strong></div>
          <div><span>Географска ширина</span><strong>{rec.parcelLatitude != null ? `${Number(rec.parcelLatitude).toFixed(4)}°N` : "—"}</strong></div>
          <div><span>Географска должина</span><strong>{rec.parcelLongitude != null ? `${Number(rec.parcelLongitude).toFixed(4)}°E` : "—"}</strong></div>
        </div>
      </section>

      <section className="report-section">
        <h2 className="report-section__h"><IconDroplet /> Временски услови и влажност</h2>
        <div className="report-weather-row">
          <div className="report-weather-box">
            <h3>Прогноза (7 дена)</h3>
            {climate ? (
              <ul className="report-stat-list">
                <li><span>Просечна температура</span><strong>{climate.temperatureC.toFixed(1)}°C</strong></li>
                <li><span>Очекувани врнежи (сума 7д)</span><strong>{climate.precipitation7dMm.toFixed(1)} мм</strong></li>
                <li><span>Евапотранспирација (ден)</span><strong>{climate.evapotranspirationMmPerDay.toFixed(1)} мм</strong></li>
              </ul>
            ) : <p className="muted">Нема вчитани климатски податоци за оваа анализа.</p>}
          </div>
          <div className="report-weather-box report-weather-box--moist">
            <h3>Влажност на почва</h3>
            {moPct != null ? (
              <>
                <div className="moist-big"><span className="moist-num">{moPct}%</span> {climate?.soilMoistureLabel || rec.soilMoistureStatus || "—"}</div>
                <p className="muted small-print">{rec.irrigationAdvice}</p>
              </>
            ) : <p className="muted">—</p>}
          </div>
        </div>
      </section>

      <section className="report-section report-section--ai">
        <div className="report-ai-head" role="group" aria-label="AI препораки">
          <IconSparkle />
          <div className="report-ai-head__text">
            <h2 className="report-ai-title">AI Препораки за култури</h2>
            <p className="report-ai-sub">Базирано на податоци за почва, временски услови и AI анализа</p>
          </div>
        </div>
        <div className="report-crops">
          {top.length === 0 && (
            <div className="crop-rec-card">
              <div className="crop-rec-card__head">
                <span className="crop-rec-rank">1</span>
                <strong>{rec.suggestedCrop}</strong>
                <span className={`risk-badge risk-badge--${(rec.riskLevel || "").toLowerCase()}`}>{riskLabelMk(rec.riskLevel)}</span>
              </div>
              <p>Очекуван принос: {Number(rec.expectedYieldTonPerHa).toFixed(1)} т/ха</p>
            </div>
          )}
          {top.map((c) => (
            <div key={c.rank} className="crop-rec-card">
              <div className="crop-rec-card__head">
                <span className="crop-rec-rank">{c.rank}</span>
                <strong>{c.cropName}</strong>
                <span className={`risk-badge risk-badge--${(c.riskLevel || "").toLowerCase()}`}>{riskLabelMk(c.riskLevel)}</span>
              </div>
              <p className="crop-yield">Очекуван принос: {Number(c.expectedYieldTonPerHa).toFixed(1)} т/ха</p>
              <div className="suit-row">
                <span>Погодност</span>
                <div className="suit-bar-wrap" role="img" aria-label={`Погодност ${c.suitabilityPercent}%`}>
                  <div className="suit-bar" style={{ width: `${c.suitabilityPercent}%` }} />
                </div>
                <strong className="suit-pct">{c.suitabilityPercent}%</strong>
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="report-section report-risk">
        <h2 className="report-section__h report-section__h--centered">Ризична оцена</h2>
        <div className="risk-summary-box">
          <IconCheckCircle />
          <div>
            <h3 className="risk-headline">{riskHeadlineFromLevel(rec.riskLevel)}</h3>
            <p className="risk-explain-text">{riskAssessmentBodyFromLevel(rec.riskLevel)}</p>
          </div>
        </div>
      </section>

      <footer className="report-doc-footer">
        <p>Овој извештај е генериран автоматски со агрономска AI-анализа и податоци за време (Open-Meteo).</p>
        <p>За дополнителни прашања, консултирајте агроном. © {new Date().getFullYear()}</p>
      </footer>
    </div>
  );
});

function ShareReportDialog({ open, onClose, pageUrl }) {
  const [copyFeedback, setCopyFeedback] = useState("");

  useEffect(() => {
    if (open) setCopyFeedback("");
  }, [open]);

  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  const copyShareLink = async () => {
    const u = pageUrl;
    setCopyFeedback("");
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(u);
      } else {
        const ta = document.createElement("textarea");
        ta.value = u;
        ta.setAttribute("readonly", "");
        ta.style.position = "fixed";
        ta.style.left = "-9999px";
        document.body.appendChild(ta);
        ta.select();
        document.execCommand("copy");
        document.body.removeChild(ta);
      }
      setCopyFeedback("Линкот е копиран.");
    } catch {
      setCopyFeedback("Не можеше автоматски копирање. Избери го текстот и копирај рачно.");
    }
  };

  const nativeShare = async () => {
    if (!navigator.share) return;
    try {
      await navigator.share({
        title: "Agro AI — извештај",
        text: "Погледни го агрономскиот извештај",
        url: pageUrl,
      });
      onClose();
    } catch (e) {
      if (e?.name !== "AbortError") {
        setCopyFeedback("Споделувањето не успеа. Користи „Копирај линк“.");
      }
    }
  };

  if (!open) return null;

  return (
    <div
      className="share-modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="share-dialog-title"
    >
      <button
        type="button"
        className="share-modal__backdrop"
        aria-label="Затвори"
        onClick={onClose}
      />
      <div className="share-modal__panel">
        <h3 className="share-modal__title" id="share-dialog-title">Сподели извештај</h3>
        <p className="share-modal__hint muted">Копирај го линкот или сподели преку друга апликација</p>
        <label className="share-modal__label" htmlFor="share-url-input">Линк до овој извештај</label>
        <div className="share-modal__row">
          <input
            id="share-url-input"
            className="share-modal__url"
            readOnly
            value={pageUrl}
            onFocus={(e) => e.target.select()}
          />
          <button type="button" className="analysis-btn-primary" onClick={() => { void copyShareLink(); }}>
            Копирај
          </button>
        </div>
        {copyFeedback && <p className="share-modal__feedback" role="status">{copyFeedback}</p>}
        {typeof navigator !== "undefined" && typeof navigator.share === "function" && (
          <button type="button" className="share-modal__system" onClick={() => { void nativeShare(); }}>
            Сподели преку уредот…
          </button>
        )}
        <button type="button" className="share-modal__dismiss" onClick={onClose}>
          Затвори
        </button>
      </div>
    </div>
  );
}

function DashboardPage({ summary, parcels, history, onGenerate, onRefresh, profile, onAddParcelClick, onDeleteParcel }) {
  const navigate = useNavigate();
  const totalHaDisplay = summary != null && Number(summary.totalAreaHa) > 0 ? Number(summary.totalAreaHa) : sumParcelHectares(parcels);
  const lastParcelById = [...parcels].sort((a, b) => (b.id ?? 0) - (a.id ?? 0))[0];
  const latestByParcel = useMemo(() => latestRecByParcelId(history), [history]);
  const weatherLabel =
    summary?.weatherLocation ||
    (lastParcelById
      ? [lastParcelById.name, lastParcelById.location?.trim()].filter(Boolean).join(" — ") ||
        (lastParcelById.latitude != null && lastParcelById.longitude != null
          ? `${Number(lastParcelById.latitude).toFixed(4)}°, ${Number(lastParcelById.longitude).toFixed(4)}°`
          : "—")
      : "—");

  const lastAnalysisDateText = useMemo(() => {
    const t = summary?.latestAnalysisAt || history?.[0]?.createdAt;
    if (!t) return "Нема";
    const s = formatMkDate(t);
    return s === "—" ? "Нема" : s;
  }, [summary?.latestAnalysisAt, history]);
  const lastAnalysisPending = lastAnalysisDateText === "Нема";

  return (
    <AppShell title={`Добредојдовте назад, ${summary?.fullName || profile?.fullName || "Земјоделец"}!`} subtitle="Еве го прегледот на вашите парцели и анализи." profile={profile}>
      <section className="stats-grid">
        <div className="stat-card stat-card--dash stat-card--parcels">
          <div className="stat-card__body">
            <span className="stat-card__label">Вкупно парцели</span>
            <strong className="stat-card__value">{summary?.totalParcels ?? parcels.length}</strong>
          </div>
          <div className="stat-card__ico" aria-hidden><IconStatParcels /></div>
        </div>
        <div
          className={
            `stat-card stat-card--dash stat-card--analysis ${lastAnalysisPending ? "stat-card--analysis-pending" : "stat-card--analysis-ok"}`
          }
        >
          <div className="stat-card__body">
            <span className="stat-card__label">Последна анализа</span>
            <strong className="stat-card__value">{lastAnalysisDateText}</strong>
          </div>
          <div className="stat-card__ico" aria-hidden><IconCalendar /></div>
        </div>
        <div className="stat-card stat-card--dash stat-card--hectares">
          <div className="stat-card__body">
            <span className="stat-card__label">Вкупна површина</span>
            <strong className="stat-card__value">
              {totalHaDisplay.toFixed(1)} <span className="stat-card__unit">ха</span>
            </strong>
          </div>
          <div className="stat-card__ico" aria-hidden><IconStatArea /></div>
        </div>
      </section>
      <section className="weather-card weather-card--dashboard" aria-labelledby="weather-dash-title">
        <div className="weather-card__head">
          <IconCloudRain />
          <h3 className="weather-card__title" id="weather-dash-title">
            Временски услови{weatherLabel && weatherLabel !== "—" ? ` — ${weatherLabel}` : ""}
          </h3>
        </div>
        <div className="weather-stat-tiles">
          <div className="weather-tile weather-tile--air">
            <span className="weather-tile__label">Температура (воздух, просек)</span>
            <strong className="weather-tile__value">{(summary?.avgTemperature || 0).toFixed(1)}°C</strong>
          </div>
          <div className="weather-tile weather-tile--rain">
            <span className="weather-tile__label">Врнежи (7 дена, просек/ден)</span>
            <strong className="weather-tile__value">{(summary?.avgPrecipitation || 0).toFixed(1)} mm</strong>
          </div>
          <div className="weather-tile weather-tile--moist">
            <span className="weather-tile__label">Влажност на почва (0–9 cm)</span>
            <strong className="weather-tile__value weather-tile__value--text">
              <span className="weather-ico-inline" aria-hidden>💧</span> {summary?.soilStatus || "Непознато"}
            </strong>
          </div>
          <div className="weather-tile weather-tile--et0">
            <span className="weather-tile__label">ET0 (евапотранспирација)</span>
            <strong className="weather-tile__value">{(summary?.avgEt0 || 0).toFixed(1)} mm/ден</strong>
          </div>
          <div className="weather-tile weather-tile--soil">
            <span className="weather-tile__label">Темп. на почва (0 cm)</span>
            <strong className="weather-tile__value">{(summary?.avgSoilTemperature0cm ?? 0).toFixed(1)}°C</strong>
          </div>
        </div>
      </section>
      <section className="two-col">
        <div className="card">
          <div className="section-title">
            <h3>Мои парцели</h3>
            <div className="row-actions">
              <button onClick={onRefresh}>Освежи</button>
              <button type="button" onClick={onAddParcelClick}>+ Додади парцела</button>
            </div>
          </div>
          {parcels.length === 0 && <div className="muted">Нема внесени парцели. Додај нова парцела за да се прикажува овде.</div>}
          {parcels.map((p) => {
            const hasAnalysis = latestByParcel.has(p.id);
            return (
              <div key={p.id} className={hasAnalysis ? "parcel-item" : "parcel-item parcel-item--awaiting"}>
                <div><strong>{p.name}</strong><div className="muted">{p.location} • {p.areaHa} ха</div></div>
                <div className="parcel-item-actions">
                  <button type="button" className="icon-action" title="Преглед" onClick={() => void navigate(`/parcels/${p.id}`)}><IconEye /></button>
                  <button
                    type="button"
                    className="icon-action icon-action--danger"
                    title="Избриши"
                    onClick={() => {
                      if (!window.confirm(`Да се избрише „${p.name || p.id}“?`)) return;
                      void onDeleteParcel(p.id);
                    }}
                  >
                    <IconTrash />
                  </button>
                  <button
                    type="button"
                    className={hasAnalysis ? "btn-parcel-cta" : "btn-parcel-cta btn-parcel-cta--orange"}
                    onClick={() => onGenerate(p.id)}
                  >
                    {hasAnalysis ? "Повторна анализа" : "Чека анализа"}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
        <div className="card">
          <h3>Скорешни анализи</h3>
          {history.slice(0, 5).map((r) => (
            <div key={r.id} className="history-item">
              <strong>{r.parcelName}</strong>
              <div>Препорака: {r.suggestedCrop}</div>
              <div className="muted">{new Date(r.createdAt).toLocaleDateString("mk-MK")}</div>
            </div>
          ))}
        </div>
      </section>
    </AppShell>
  );
}

function formatMkDate(iso) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleDateString("mk-MK", { year: "numeric", month: "2-digit", day: "2-digit" });
}

function ParcelDetailPage({ profile }) {
  const { id } = useParams();
  const navigate = useNavigate();
  const [parcel, setParcel] = useState(null);
  const [history, setHistory] = useState([]);
  const [err, setErr] = useState("");

  useEffect(() => {
    let ok = true;
    (async () => {
      setErr("");
      try {
        const [p, h] = await Promise.all([api(`/parcels/${id}`), api("/recommendations/history")]);
        if (ok) {
          setParcel(p);
          setHistory(h || []);
        }
      } catch (e) {
        if (ok) setErr("Парцелата не е пронајдена или немате пристап.");
      }
    })();
    return () => { ok = false; };
  }, [id]);

  const forParcel = useMemo(() => (history || []).filter((r) => r.parcelId === Number(id)), [history, id]);
  const last = forParcel[0];

  if (err) {
    return (
      <AppShell title="Парцела" subtitle="" profile={profile}>
        <div className="card"><p className="error">{err}</p><button type="button" onClick={() => void navigate("/parcels")}>Назад на листа</button></div>
      </AppShell>
    );
  }
  if (!parcel) {
    return <AppShell title="Парцела" subtitle="Се вчитува…" profile={profile}><div className="muted">Се вчитува…</div></AppShell>;
  }

  return (
    <AppShell title={parcel.name || "Парцела"} subtitle="Преглед на деталите" profile={profile}>
      <div className="parcel-actions-bar">
        <button type="button" className="secondary-btn" onClick={() => void navigate("/parcels")}>← Назад</button>
        <div className="row-actions">
          <button type="button" onClick={() => void navigate(`/parcels/${id}/edit`)}>Уреди</button>
        </div>
      </div>
      <div className="card parcel-detail-grid">
        <div>
          <h3>Основни</h3>
          <p><span className="label-k">Локација</span> {parcel.location || "—"}</p>
          <p><span className="label-k">WGS84</span> {parcel.latitude != null && parcel.longitude != null ? `${Number(parcel.latitude).toFixed(5)}, ${Number(parcel.longitude).toFixed(5)}` : "—"}</p>
          <p><span className="label-k">Површина</span> {parcel.areaHa} ха</p>
          <p><span className="label-k">Почва</span> {parcel.soilType || "—"}</p>
        </div>
        <div>
          <h3>Анализа</h3>
          {last ? (
            <>
              <p><span className="label-k">Последна</span> {formatMkDate(last.createdAt)}</p>
              <p><span className="label-k">Препорака</span> {last.suggestedCrop}</p>
              <p><span className="label-k">Ризик</span> {last.riskLevel}</p>
              <p className="muted small-block">{last.explanation}</p>
            </>
          ) : <p className="muted">Нема анализа — на почетната дашборд притиснете „Чека анализа“ за оваа парцела.</p>}
        </div>
      </div>
      {parcel.previousCrops && parcel.previousCrops.length > 0 && (
        <div className="card" style={{ marginTop: 14 }}>
          <h3>Претходни култури</h3>
          <div className="chips">{parcel.previousCrops.map((c) => <span key={c}>{c}</span>)}</div>
        </div>
      )}
    </AppShell>
  );
}

function ParcelEditPage({ profile, updateParcel, savingParcel }) {
  const { id } = useParams();
  const navigate = useNavigate();
  const [form, setForm] = useState(null);
  const [newCrop, setNewCrop] = useState("");
  const [loadErr, setLoadErr] = useState("");

  useEffect(() => {
    let ok = true;
    (async () => {
      try {
        const p = await api(`/parcels/${id}`);
        if (!ok) return;
        setForm({
          name: p.name || "",
          location: p.location || "",
          soilType: p.soilType || "Глинеста",
          areaHa: p.areaHa ?? 2.5,
          latitude: p.latitude != null ? String(p.latitude) : "",
          longitude: p.longitude != null ? String(p.longitude) : "",
          previousCrops: p.previousCrops && p.previousCrops.length ? [...p.previousCrops] : [],
        });
      } catch {
        if (ok) setLoadErr("Не е вчитана парцелата.");
      }
    })();
    return () => { ok = false; };
  }, [id]);

  const addPreviousCrop = () => {
    if (!newCrop.trim() || !form) return;
    setForm((prev) => ({ ...prev, previousCrops: [...prev.previousCrops, newCrop.trim()] }));
    setNewCrop("");
  };

  const submit = async (e) => {
    e.preventDefault();
    if (!form) return;
    const lat = parseCoordInput(form.latitude);
    const lon = parseCoordInput(form.longitude);
    if (lat == null || lon == null) {
      window.alert("Внесете валидни WGS84 координати.");
      return;
    }
    const ok = await updateParcel(id, {
      name: form.name,
      location: form.location,
      soilType: form.soilType,
      areaHa: form.areaHa,
      latitude: lat,
      longitude: lon,
      previousCrops: form.previousCrops,
    });
    if (ok) void navigate(`/parcels/${id}`);
  };

  if (loadErr) {
    return <AppShell title="Уреди" subtitle="" profile={profile}><div className="error">{loadErr}</div></AppShell>;
  }
  if (!form) {
    return <AppShell title="Уреди парцела" subtitle="Се вчитува…" profile={profile}><p className="muted">Се вчитува…</p></AppShell>;
  }

  return (
    <AppShell title="Уреди парцела" subtitle="Зачувај ги измените" profile={profile}>
      <form onSubmit={submit} className="add-parcel-page">
        <section className="card add-section">
          <h3>Основни информации</h3>
          <div className="parcel-form">
            <label>Име *<input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
            <label>Локација *<input required value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} /></label>
            <label>Географска ширина
              <input type="text" inputMode="decimal" required value={form.latitude} onChange={(e) => setForm({ ...form, latitude: e.target.value })} />
            </label>
            <label>Географска должина
              <input type="text" inputMode="decimal" required value={form.longitude} onChange={(e) => setForm({ ...form, longitude: e.target.value })} />
            </label>
          </div>
          <ParcelMapPicker
            lat={parseCoordInput(form.latitude)}
            lon={parseCoordInput(form.longitude)}
            onPick={(la, lo) => {
              setForm((prev) => ({ ...prev, latitude: String(la), longitude: String(lo) }));
            }}
          />
        </section>
        <section className="card add-section">
          <h3>Детали</h3>
          <div className="parcel-form">
            <label>Површина (ха) *<input required type="number" min="0.1" step="0.1" value={form.areaHa} onChange={(e) => setForm({ ...form, areaHa: Number(e.target.value) })} /></label>
            <label>Тип на почва
              <select value={form.soilType} onChange={(e) => setForm({ ...form, soilType: e.target.value })}>
                <option>Глинеста</option>
                <option>Песоклива</option>
                <option>Иловеста</option>
                <option>Црница</option>
              </select>
            </label>
          </div>
          <label>Претходни култури</label>
          <div className="crop-row">
            <input value={newCrop} onChange={(e) => setNewCrop(e.target.value)} placeholder="Култура" />
            <button type="button" className="dark-btn" onClick={addPreviousCrop}>Додади</button>
          </div>
          {form.previousCrops.length > 0 && <div className="chips">{form.previousCrops.map((c) => <span key={c}>{c}</span>)}</div>}
        </section>
        <div className="actions-row">
          <button type="button" className="secondary-btn" onClick={() => void navigate(`/parcels/${id}`)}>Откажи</button>
          <button type="submit" disabled={savingParcel}>{savingParcel ? "Се зачувува…" : "Зачувај"}</button>
        </div>
      </form>
    </AppShell>
  );
}

function ParcelsPage({ parcels, history, addParcel, deleteParcel, savingParcel, profile }) {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [showAddForm, setShowAddForm] = useState(false);
  const [searchTerm, setSearchTerm] = useState("");
  const [newCrop, setNewCrop] = useState("");
  const [importFile, setImportFile] = useState(null);
  const [importPreview, setImportPreview] = useState(null);
  const [importErr, setImportErr] = useState("");
  const [importBusy, setImportBusy] = useState(false);
  const [importCommitResult, setImportCommitResult] = useState(null);
  const [form, setForm] = useState({
    name: "",
    location: "",
    soilType: "",
    areaHa: 2.5,
    latitude: "42.0123",
    longitude: "21.4567",
    previousCrops: [],
    notes: "",
    importFileName: "",
  });

  useEffect(() => {
    const add = searchParams.get("add");
    if (add === "1" || add === "true") {
      setShowAddForm(true);
      setSearchParams({}, { replace: true });
    }
  }, [searchParams, setSearchParams]);

  const latestByParcel = useMemo(() => latestRecByParcelId(history), [history]);

  const filteredParcels = parcels.filter((p) => {
    const q = searchTerm.toLowerCase();
    const name = (p.name ?? "").toLowerCase();
    const loc = (p.location ?? "").toLowerCase();
    return name.includes(q) || loc.includes(q);
  });

  const handleDelete = async (p) => {
    if (!window.confirm(`Да се избрише парцелата „${p.name || p.id}“? Ова не може да се врати.`)) return;
    await deleteParcel(p.id);
  };

  const submitParcel = async (e) => {
    e.preventDefault();
    const lat = parseCoordInput(form.latitude);
    const lon = parseCoordInput(form.longitude);
    if (lat == null || lon == null) {
      // eslint-disable-next-line no-alert
      window.alert("Внесете исправни WGS84 координати (десим. точка/запирка, на пр. 42,0045 и 21,4123).");
      return;
    }
    const ok = await addParcel({
      name: form.name,
      location: form.location,
      soilType: form.soilType,
      areaHa: form.areaHa,
      latitude: lat,
      longitude: lon,
      previousCrops: form.previousCrops,
    });
    if (!ok) return;
    void navigate("/dashboard", { replace: true });
    setForm({
      name: "",
      location: "",
      soilType: "",
      areaHa: 2.5,
      latitude: "42.0123",
      longitude: "21.4567",
      previousCrops: [],
      notes: "",
      importFileName: "",
    });
    setShowAddForm(false);
  };

  const addPreviousCrop = () => {
    if (!newCrop.trim()) return;
    setForm((prev) => ({ ...prev, previousCrops: [...prev.previousCrops, newCrop.trim()] }));
    setNewCrop("");
  };

  const onImportFile = async (f) => {
    setImportErr("");
    setImportCommitResult(null);
    setImportPreview(null);
    setImportFile(null);
    if (!f) return;
    if (f.size > 10 * 1024 * 1024) {
      setImportErr("Датотеката е преголема (макс. 10MB).");
      return;
    }
    setImportFile(f);
    setImportBusy(true);
    try {
      const fd = new FormData();
      fd.append("file", f);
      const res = await apiForm("/parcels/import/preview", fd);
      setImportPreview(res);
    } catch (e) {
      setImportErr(e?.message || "Не може да се увезе датотеката.");
    } finally {
      setImportBusy(false);
    }
  };

  const commitImport = async () => {
    setImportErr("");
    setImportCommitResult(null);
    if (!importPreview?.rows?.length) {
      setImportErr("Нема редови за увоз.");
      return;
    }
    const validDrafts = (importPreview.rows || [])
      .filter((r) => !(r?.errors && r.errors.length))
      .map((r) => r.parsed)
      .filter(Boolean);
    if (validDrafts.length === 0) {
      setImportErr("Нема валидни редови. Поправете ја датотеката и прикачете повторно.");
      return;
    }
    setImportBusy(true);
    try {
      const res = await api("/parcels/import/commit", { method: "POST", body: JSON.stringify({ rows: validDrafts }) });
      setImportCommitResult(res);
      void navigate("/dashboard", { replace: true });
    } catch (e) {
      setImportErr(e?.message || "Увозот не успеа.");
    } finally {
      setImportBusy(false);
    }
  };

  if (showAddForm) {
    return (
      <AppShell title="Додади нова парцела" subtitle="Внесете ги деталите за новата парцела" profile={profile}>
        <form onSubmit={submitParcel} className="add-parcel-page">
          <section className="card add-section">
            <h3>Основни информации</h3>
            <p>Општи податоци за парцелата</p>
            <div className="parcel-form">
              <label>Име на парцела *<input required placeholder="пр. Парцела Север" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
              <label>Локација *<input required placeholder="пр. Скопје, Петровец" value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} /></label>
              <label className="coord-hint">Географска ширина (WGS84°, -90..90)
                <input
                  required
                  type="text"
                  inputMode="decimal"
                  autoComplete="off"
                  placeholder="42,0045 или 42.0045"
                  value={form.latitude == null || form.latitude === "" ? "" : String(form.latitude)}
                  onChange={(e) => setForm({ ...form, latitude: e.target.value })}
                />
                <small className="muted">Десим. точка или запирка (MK тастатура). Open-Meteo: само WGS84.</small>
              </label>
              <label className="coord-hint">Географска должина (WGS84°, -180..180)
                <input
                  required
                  type="text"
                  inputMode="decimal"
                  autoComplete="off"
                  placeholder="21,4123 или 21.4123"
                  value={form.longitude == null || form.longitude === "" ? "" : String(form.longitude)}
                  onChange={(e) => setForm({ ...form, longitude: e.target.value })}
                />
                <small className="muted">Скопје-регион: ширина ∼40–42°, должина ∼20–23° (од Google Maps).</small>
              </label>
            </div>
            <ParcelMapPicker
              lat={parseCoordInput(form.latitude)}
              lon={parseCoordInput(form.longitude)}
              onPick={(la, lo) => {
                setForm((prev) => ({ ...prev, latitude: String(la), longitude: String(lo) }));
              }}
            />
          </section>

          <section className="card add-section">
            <h3>Детали за парцелата</h3>
            <p>Карактеристики на почвата и површина</p>
            <div className="parcel-form">
              <label>Површина (хектари) *<input required type="number" step="0.1" min="0.1" value={form.areaHa} onChange={(e) => setForm({ ...form, areaHa: Number(e.target.value) })} /></label>
              <label>Тип на почва *<select required value={form.soilType} onChange={(e) => setForm({ ...form, soilType: e.target.value })}>
                <option value="">Изберете тип на почва</option>
                <option value="Глинеста">Глинеста</option>
                <option value="Песоклива">Песоклива</option>
                <option value="Иловеста">Иловеста</option>
                <option value="Црница">Црница</option>
              </select></label>
            </div>
            <label>Претходно одгледувани култури</label>
            <div className="crop-row">
              <input placeholder="Внесете култура и притиснете Додади" value={newCrop} onChange={(e) => setNewCrop(e.target.value)} />
              <button type="button" className="dark-btn" onClick={addPreviousCrop}>Додади</button>
            </div>
            {form.previousCrops.length > 0 && <div className="chips">{form.previousCrops.map((c) => <span key={c}>{c}</span>)}</div>}
            <label>Забелешки (опционално)</label>
            <textarea placeholder="Дополнителни информации за парцелата..." value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
          </section>

          <section className="card add-section">
            <h3>Увоз на податоци (опционално)</h3>
            <p>Прикачете CSV или Excel датотека за повеќе парцели (преглед пред зачувување)</p>
            <div className="import-actions-row">
              <button
                type="button"
                className="analysis-btn-secondary"
                onClick={() => {
                  void apiDownload("/parcels/import/template.csv", "parcel-import-template.csv")
                    .catch((e) => window.alert(e?.message || "Не може да се превземе CSV шаблон."));
                }}
              >
                Превземи шаблон (CSV)
              </button>
              <button
                type="button"
                className="analysis-btn-secondary"
                onClick={() => {
                  void apiDownload("/parcels/import/template.xlsx", "parcel-import-template.xlsx")
                    .catch((e) => window.alert(e?.message || "Не може да се превземе Excel шаблон."));
                }}
              >
                Превземи шаблон (Excel)
              </button>
              <small className="muted">Колони: name, location, latitude, longitude, area, soil_type, previous_crop, current_crop, notes</small>
            </div>

            <div className="import-preview-table-wrap" style={{ marginTop: 10 }}>
              <table className="parcels-table import-preview-table">
                <thead>
                  <tr>
                    <th>name</th>
                    <th>location</th>
                    <th>latitude</th>
                    <th>longitude</th>
                    <th>area</th>
                    <th>soil_type</th>
                    <th>previous_crop</th>
                    <th>current_crop</th>
                    <th>notes</th>
                  </tr>
                </thead>
                <tbody>
                  <tr className="row-valid">
                    <td>Парцелa Север</td>
                    <td>Скопје</td>
                    <td>41.9981</td>
                    <td>21.4254</td>
                    <td>2.5</td>
                    <td>глинеста</td>
                    <td>пченица</td>
                    <td>пченка</td>
                    <td>пример парцела</td>
                  </tr>
                  <tr className="row-valid">
                    <td>Парцелa Исток</td>
                    <td>Велес</td>
                    <td>41.7156</td>
                    <td>21.7756</td>
                    <td>3.2</td>
                    <td>песоклива</td>
                    <td>сончоглед</td>
                    <td>пченица</td>
                    <td>потребна проверка</td>
                  </tr>
                </tbody>
              </table>
              <div className="muted" style={{ marginTop: 8 }}>
                Ова е пример како треба да изгледа датотеката. Може да ги користите овие колони и ист редослед.
              </div>
            </div>
            <label className="upload-box">
              <input
                type="file"
                accept=".csv,.xls,.xlsx"
                onChange={(e) => {
                  const f = e.target.files?.[0] || null;
                  setForm({ ...form, importFileName: f?.name || "" });
                  void onImportFile(f);
                }}
              />
              <div>Повлечете датотека овде или кликнете за да изберете</div>
              <small>CSV, XLSX (max. 10MB)</small>
              {form.importFileName && <strong>{form.importFileName}</strong>}
            </label>
            {importErr && <div className="error" style={{ marginTop: 10 }}>{importErr}</div>}
            {importBusy && <div className="muted" style={{ marginTop: 10 }}>Се обработува датотеката…</div>}
            {importPreview && (
              <div className="import-preview" style={{ marginTop: 12 }}>
                <div className="import-preview-head">
                  <strong>Преглед</strong>
                  <span className="muted">
                    {importPreview.validRows} валидни • {importPreview.invalidRows} невалидни • прикажани до {importPreview.rows?.length || 0}
                  </span>
                </div>
                <div className="import-preview-table-wrap">
                  <table className="parcels-table import-preview-table">
                    <thead>
                      <tr>
                        <th>#</th>
                        <th>Име</th>
                        <th>Локација</th>
                        <th>Почва</th>
                        <th>Површина</th>
                        <th>Lat</th>
                        <th>Lon</th>
                        <th>Претходна</th>
                        <th>Тековна</th>
                        <th>Грешки</th>
                      </tr>
                    </thead>
                    <tbody>
                      {(importPreview.rows || []).slice(0, 20).map((r) => (
                        <tr key={r.rowNumber} className={r.errors?.length ? "row-invalid" : "row-valid"}>
                          <td>{r.rowNumber}</td>
                          <td>{r.parsed?.name || "—"}</td>
                          <td>{r.parsed?.location || "—"}</td>
                          <td>{r.parsed?.soilType || "—"}</td>
                          <td>{r.parsed?.areaHa ?? "—"}</td>
                          <td>{r.parsed?.latitude ?? "—"}</td>
                          <td>{r.parsed?.longitude ?? "—"}</td>
                          <td>{r.parsed?.previousCrop || "—"}</td>
                          <td>{r.parsed?.currentCrop || "—"}</td>
                          <td>
                            {r.errors?.length
                              ? <ul className="import-errors">{r.errors.map((er, i) => <li key={i}><strong>{er.field}</strong>: {er.message}</li>)}</ul>
                              : <span className="muted">—</span>}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {(importPreview.rows || []).length > 20 && (
                    <div className="muted" style={{ marginTop: 8 }}>Прикажани се првите 20 редови. Поправете ја датотеката и прикачете повторно ако има грешки.</div>
                  )}
                </div>
                <div className="actions-row" style={{ marginTop: 12 }}>
                  <button
                    type="button"
                    className="secondary-btn"
                    disabled={importBusy}
                    onClick={() => {
                      setImportFile(null);
                      setImportPreview(null);
                      setImportCommitResult(null);
                      setImportErr("");
                      setForm((prev) => ({ ...prev, importFileName: "" }));
                    }}
                  >
                    Исчисти
                  </button>
                  <button
                    type="button"
                    disabled={importBusy || (importPreview.validRows || 0) === 0}
                    onClick={() => { void commitImport(); }}
                  >
                    {importBusy ? "Се увезува…" : `Увези ${importPreview.validRows || 0} валидни парцели`}
                  </button>
                </div>
                {importCommitResult?.created != null && (
                  <div className="muted" style={{ marginTop: 10 }}>
                    Успешно се креирани {importCommitResult.created} парцели.
                  </div>
                )}
              </div>
            )}
          </section>

          <div className="actions-row">
            <button type="button" className="secondary-btn" onClick={() => setShowAddForm(false)}>Откажи</button>
            <button type="submit" disabled={savingParcel}>{savingParcel ? "Се зачувува..." : "Додади парцела"}</button>
          </div>
        </form>
      </AppShell>
    );
  }

  return (
    <AppShell title="Управување со парцели" subtitle="Преглед и управување со сите ваши парцели" profile={profile}>
      <div className="parcel-toolbar">
        <input placeholder="Барај парцела по име или локација..." value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} />
        <button type="button" onClick={() => void navigate("/parcels?add=1")}>+ Додади нова парцела</button>
      </div>
      <div className="card parcels-table-card parcels-table-wrap">
        <table className="parcels-table">
          <thead>
            <tr>
              <th>Име на парцела</th>
              <th>Локација</th>
              <th>Тип на почва</th>
              <th>Површина (ха)</th>
              <th>Последна анализа</th>
              <th>Статус (анализа)</th>
              <th>Акции</th>
            </tr>
          </thead>
          <tbody>
            {filteredParcels.map((p) => {
              const rec = latestByParcel.get(p.id);
              return (
                <tr key={p.id}>
                  <td>{p.name}</td>
                  <td>{p.location}</td>
                  <td>{p.soilType}</td>
                  <td>{p.areaHa}</td>
                  <td>{rec ? formatMkDate(rec.createdAt) : "—"}</td>
                  <td>
                    {rec
                      ? <span className="status-pill status-pill--done">Анализирано</span>
                      : <span className="status-pill status-pill--pending">Чека анализа</span>}
                  </td>
                  <td>
                    <div className="table-row-actions" role="group" aria-label="Акции">
                      <button type="button" className="icon-action" title="Преглед" onClick={() => void navigate(`/parcels/${p.id}`)}><IconEye /></button>
                      <button type="button" className="icon-action" title="Уреди" onClick={() => void navigate(`/parcels/${p.id}/edit`)}><IconPencil /></button>
                      <button type="button" className="icon-action icon-action--danger" title="Избриши" onClick={() => { void handleDelete(p); }}><IconTrash /></button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </AppShell>
  );
}

function AnalysisListPage({ title, subtitle, history, profile }) {
  const navigate = useNavigate();
  const [filterParcelId, setFilterParcelId] = useState("all");

  const parcelOptions = useMemo(() => {
    const m = new Map();
    (history || []).forEach((r) => {
      if (r?.parcelId != null && !m.has(r.parcelId)) m.set(r.parcelId, r.parcelName || `Парцела #${r.parcelId}`);
    });
    return [...m.entries()].sort((a, b) => String(a[1]).localeCompare(String(b[1]), "mk"));
  }, [history]);

  const filtered = useMemo(() => {
    if (filterParcelId === "all") return history || [];
    const id = Number(filterParcelId);
    return (history || []).filter((r) => r.parcelId === id);
  }, [history, filterParcelId]);

  return (
    <AppShell title={title} subtitle={subtitle} profile={profile}>
      <div className="analysis-list-toolbar">
        <label className="sr-only" htmlFor="analysis-parcel-filter">Филтер</label>
        <select
          id="analysis-parcel-filter"
          className="analysis-filter-select"
          value={filterParcelId}
          onChange={(e) => setFilterParcelId(e.target.value)}
        >
          <option value="all">Сите парцели</option>
          {parcelOptions.map(([id, name]) => (
            <option key={id} value={String(id)}>{name}</option>
          ))}
        </select>
      </div>
      {filtered.length === 0 && (
        <div className="card analysis-empty">Нема анализи. Генерирајте анализа од почетната или од парцелите.</div>
      )}
      {filtered.map((r) => {
        const top = (r.topCrops && r.topCrops.length) ? r.topCrops : [];
        const suit = r.primarySuitabilityPercent ?? (top[0]?.suitabilityPercent) ?? 0;
        return (
          <div key={r.id} className="analysis-card">
            <div className="analysis-card__date">
              <IconCalendar />
              <span>{formatMkDate(r.createdAt)}</span>
            </div>
            <div className="analysis-card__body">
              <div className="analysis-card__title-row">
                <strong className="analysis-card__parcel">{r.parcelName}</strong>
                {suit > 0 && (
                  <span className="suit-pill">
                    <IconTrend />
                    {suit}% погодност
                  </span>
                )}
              </div>
              <p className="analysis-card__main">Главна препорака: {r.suggestedCrop || "—"}</p>
              <div className="top3-pills" aria-label="Топ 3 култури">
                {top.length > 0
                  ? top.map((c) => (
                    <span key={c.rank} className="top3-pill">{c.rank}. {c.cropName}</span>
                  ))
                  : <span className="top3-pill">1. {r.suggestedCrop || "—"}</span>}
              </div>
            </div>
            <div className="analysis-card__actions">
              <button type="button" className="analysis-btn-secondary" onClick={() => void navigate(`/reports/${r.id}`)}>Избери</button>
              <button type="button" className="icon-action analysis-eye" title="Преглед" onClick={() => void navigate(`/reports/${r.id}`)}><IconEye /></button>
            </div>
          </div>
        );
      })}
    </AppShell>
  );
}

function ReportDetailPage({ profile }) {
  const { recommendationId } = useParams();
  const navigate = useNavigate();
  const reportRef = useRef(null);
  const [rec, setRec] = useState(null);
  const [err, setErr] = useState("");
  const [shareOpen, setShareOpen] = useState(false);
  const [pdfWorking, setPdfWorking] = useState(false);

  const sharePageUrl = useMemo(() => {
    if (typeof window === "undefined") return "";
    return `${window.location.origin}${window.location.pathname}${window.location.search}${window.location.hash}`;
  }, [recommendationId]);

  useEffect(() => {
    let ok = true;
    (async () => {
      setErr("");
      try {
        const r = await api(`/recommendations/${recommendationId}`);
        if (ok) setRec(r);
      } catch {
        if (ok) setErr("Извештајот не е пронајден.");
      }
    })();
    return () => { ok = false; };
  }, [recommendationId]);

  if (err) {
    return (
      <AppShell title="Извештај" subtitle="" profile={profile}>
        <div className="card"><p className="error">{err}</p><button type="button" onClick={() => void navigate("/reports")}>Назад</button></div>
      </AppShell>
    );
  }
  if (!rec) {
    return <AppShell title="Извештај - Анализа" subtitle="Се вчитува…" profile={profile}><p className="muted">Се вчитува…</p></AppShell>;
  }

  return (
    <AppShell title="Извештај - Анализа" subtitle="Преглед на генериран извештај" profile={profile}>
      <div className="report-page-toolbar">
        <button type="button" className="secondary-btn" onClick={() => void navigate("/reports")}>← Назад</button>
        <div className="report-page-actions">
          <button type="button" className="analysis-btn-secondary" onClick={() => setShareOpen(true)}>Сподели</button>
          <button
            type="button"
            className="analysis-btn-primary"
            disabled={pdfWorking}
            onClick={() => {
              setPdfWorking(true);
              const name = (rec.parcelName || "parcels").replace(/[^\w\u0400-\u04FF\- ]/g, "").trim().replace(/\s+/g, "-") || "parcels";
              void downloadReportPdf(reportRef.current, `izvestaj-${rec.id}-${name}.pdf`)
                .catch((e) => {
                  window.alert(e?.message || "PDF не можеше да се создаде. Пробајте повторно.");
                })
                .finally(() => setPdfWorking(false));
            }}
          >
            {pdfWorking ? "Се создава PDF…" : "Превземи PDF"}
          </button>
        </div>
      </div>
      <ReportDocument ref={reportRef} rec={rec} />
      <ShareReportDialog open={shareOpen} onClose={() => setShareOpen(false)} pageUrl={sharePageUrl} />
    </AppShell>
  );
}

function HistoryPage({ history, profile }) {
  return (
    <AnalysisListPage
      title="Историја на анализи"
      subtitle="Преглед на сите претходни AI препораки"
      history={history}
      profile={profile}
    />
  );
}

function ReportsPage({ history, parcels, profile }) {
  const navigate = useNavigate();
  const [rec, setRec] = useState(null);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState("");
  const [shareOpen, setShareOpen] = useState(false);
  const [pdfWorking, setPdfWorking] = useState(false);
  const reportRef = useRef(null);

  const latestByParcel = useMemo(() => latestRecByParcelId(history), [history]);
  const lastOverall = history && history.length ? history[0] : null;
  const sortedParcels = useMemo(
    () => [...(parcels || [])].sort((a, b) => String(a.name || "").localeCompare(String(b.name || ""), "mk")),
    [parcels],
  );

  /** Ако има анализи: парцелата од најновата. Инаку прва во списокот (по име). */
  const defaultFilterKey = useMemo(() => {
    if (lastOverall?.parcelId != null) return String(lastOverall.parcelId);
    if (sortedParcels.length) return String(sortedParcels[0].id);
    return "";
  }, [lastOverall, sortedParcels]);

  const [parcelOverride, setParcelOverride] = useState(null);
  const filterKey = parcelOverride != null ? parcelOverride : defaultFilterKey;

  const selectedRecommendationId = useMemo(() => {
    if (!filterKey) return null;
    const pid = Number(filterKey);
    if (Number.isNaN(pid)) return null;
    return latestByParcel.get(pid)?.id ?? null;
  }, [filterKey, latestByParcel]);

  useEffect(() => {
    let ok = true;
    if (selectedRecommendationId == null) {
      setRec(null);
      setErr("");
      setLoading(false);
      return () => { ok = false; };
    }
    setLoading(true);
    setErr("");
    (async () => {
      try {
        const r = await api(`/recommendations/${selectedRecommendationId}`);
        if (ok) {
          setRec(r);
          setErr("");
        }
      } catch {
        if (ok) {
          setRec(null);
          setErr("Извештајот не можеше да се вчита.");
        }
      } finally {
        if (ok) setLoading(false);
      }
    })();
    return () => { ok = false; };
  }, [selectedRecommendationId]);

  const sharePageUrl = useMemo(() => {
    if (typeof window === "undefined" || !rec?.id) return "";
    return `${window.location.origin}/reports/${rec.id}`;
  }, [rec]);

  return (
    <AppShell title="Извештаи" profile={profile}>
      <div className="reports-page-bar">
        <div className="reports-page-filter-row">
          <label className="reports-page-filter-label" htmlFor="reports-parcel-select">Парцела</label>
          <select
            id="reports-parcel-select"
            className="analysis-filter-select"
            value={filterKey}
            onChange={(e) => setParcelOverride(e.target.value)}
            disabled={sortedParcels.length === 0}
          >
            {sortedParcels.length === 0 ? (
              <option value="">—</option>
            ) : (
              sortedParcels.map((p) => (
                <option key={p.id} value={String(p.id)}>{p.name || `Парцела #${p.id}`}</option>
              ))
            )}
          </select>
        </div>
        <p className="reports-page-hint">
          <span className="reports-page-hint-text">
            Сите извештаи со датуми и хронолошка листа:{" "}
          </span>
          <NavLink to="/history" className="reports-page-history-link">Историја на анализи</NavLink>
        </p>
      </div>

      {loading && <p className="muted">Се вчитува…</p>}
      {err && <div className="card analysis-empty"><p className="error">{err}</p></div>}

      {!loading && !err && filterKey && selectedRecommendationId == null && (
        <div className="card analysis-empty">
          За оваа парцела сè уште нема извештај. Генерирај анализа од Почетна или од парцелата.
        </div>
      )}

      {!loading && !err && !filterKey && (
        <div className="card analysis-empty">Додадете барем една парцела за да видите извештаи.</div>
      )}

      {rec && !err && (
        <>
          <div className="report-page-toolbar report-page-toolbar--index">
            <div className="report-page-actions">
              <button type="button" className="analysis-btn-secondary" onClick={() => setShareOpen(true)}>Сподели</button>
              <button type="button" className="analysis-btn-secondary" onClick={() => void navigate(`/reports/${rec.id}`)}>Поглед на цел екран</button>
              <button
                type="button"
                className="analysis-btn-primary"
                disabled={pdfWorking}
                onClick={() => {
                  setPdfWorking(true);
                  const name = (rec.parcelName || "parcels").replace(/[^\w\u0400-\u04FF\- ]/g, "").trim().replace(/\s+/g, "-") || "parcels";
                  void downloadReportPdf(reportRef.current, `izvestaj-${rec.id}-${name}.pdf`)
                    .catch((e) => {
                      window.alert(e?.message || "PDF не можеше да се создаде. Пробајте повторно.");
                    })
                    .finally(() => setPdfWorking(false));
                }}
              >
                {pdfWorking ? "Се создава PDF…" : "Превземи PDF"}
              </button>
            </div>
          </div>
          <ReportDocument ref={reportRef} rec={rec} />
          <ShareReportDialog open={shareOpen} onClose={() => setShareOpen(false)} pageUrl={sharePageUrl} />
        </>
      )}
    </AppShell>
  );
}

function ProfilePage({ profile, setProfile }) {
  const navigate = useNavigate();
  const [name, setName] = useState(profile?.fullName || "");
  const [occupation, setOccupation] = useState(profile?.occupation || "Земјоделец");
  const [localAvatar, setLocalAvatar] = useState(null);
  const [removePhoto, setRemovePhoto] = useState(false);
  const [savedProfile, setSavedProfile] = useState("");
  const [profileError, setProfileError] = useState("");
  const [savedPassword, setSavedPassword] = useState("");
  const [savedPrefs, setSavedPrefs] = useState("");
  const [passwordForm, setPasswordForm] = useState({ current: "", next: "", confirm: "" });
  const [preferences, setPreferences] = useState({ language: "Македонски", units: "Империјал (°F, in, yd)" });

  useEffect(() => {
    try {
      const raw = localStorage.getItem("preferences");
      if (raw) {
        const p = JSON.parse(raw);
        if (p && typeof p === "object") {
          setPreferences((prev) => ({
            language: typeof p.language === "string" ? p.language : prev.language,
            units: typeof p.units === "string" ? p.units : prev.units,
          }));
        }
      }
    } catch {
      /* ignore */
    }
  }, []);

  useEffect(() => {
    setName(profile?.fullName || "");
    const o = profile?.occupation;
    setOccupation(o != null && String(o).trim() !== "" ? String(o).trim() : "Земјоделец");
  }, [profile?.email, profile?.fullName, profile?.occupation]);

  useEffect(() => {
    setLocalAvatar(null);
    setRemovePhoto(false);
  }, [profile?.email]);

  const displayAvatar = removePhoto ? null : (localAvatar ?? profile?.avatarData);
  const previewInitial = (name || "З").trim().charAt(0).toUpperCase() || "З";

  const onAvatarFile = (e) => {
    const f = e.target.files?.[0];
    e.target.value = "";
    if (!f) return;
    if (!f.type.startsWith("image/")) {
      return;
    }
    if (f.size > 450 * 1024) {
      window.alert("Сликата е преголема (макс. околу 450 KB).");
      return;
    }
    const r = new FileReader();
    r.onload = () => {
      const data = r.result;
      if (typeof data === "string") {
        if (data.length > 1_400_000) {
          window.alert("Сликата е преголема (по конверзија). Изберете помала (макс. ~450 KB).");
          return;
        }
        setLocalAvatar(data);
        setRemovePhoto(false);
        setProfileError("");
      }
    };
    r.readAsDataURL(f);
  };

  const saveProfile = async (e) => {
    e.preventDefault();
    setProfileError("");
    setSavedProfile("");
    const occ = (typeof occupation === "string" ? occupation : "").trim() || "Земјоделец";
    const payload = {
      fullName: (name && String(name).trim()) || (profile?.fullName || "Земјоделец"),
      occupation: occ,
    };
    if (removePhoto) {
      payload.clearAvatar = true;
    } else if (localAvatar != null) {
      payload.avatarData = localAvatar;
    }
    try {
      const res = await api("/profile", { method: "PUT", body: JSON.stringify(payload) });
      setProfile(res);
      localStorage.setItem("fullName", res.fullName);
      if (res.occupation) localStorage.setItem("occupation", res.occupation);
      setLocalAvatar(null);
      setRemovePhoto(false);
      setSavedProfile("Промените се зачувани.");
    } catch (err) {
      const msg = err?.message || String(err);
      setProfileError(msg.includes("413") || /Payload Too Large|преголема/i.test(msg) ? "Сликата е преголема за серверот. Изберете помала." : `Не е зачувано: ${msg}`);
    }
  };

  const changePassword = (e) => {
    e.preventDefault();
    if (!passwordForm.current || !passwordForm.next || !passwordForm.confirm) return;
    if (passwordForm.next !== passwordForm.confirm) {
      setSavedPassword("Новата лозинка и потврдата не се совпаѓаат.");
      return;
    }
    setSavedPassword("Лозинката е успешно променета.");
    setPasswordForm({ current: "", next: "", confirm: "" });
  };

  const savePreferences = (e) => {
    e.preventDefault();
    localStorage.setItem("preferences", JSON.stringify(preferences));
    setSavedPrefs("Преференциите се зачувани.");
  };

  const deleteProfile = async () => {
    if (!window.confirm("Да се избрише трајно вашиот профил? Ќе бидат избришани сите парцели и анализи. Ова не може да се врати.")) {
      return;
    }
    try {
      await api("/profile/delete-account", { method: "POST" });
      localStorage.clear();
      void navigate("/login", { replace: true });
    } catch (err) {
      const d = err?.message || String(err);
      window.alert(`Профилот не е избришан: ${d}`);
    }
  };

  return (
    <AppShell
      centerPage
      title="Профил и нагодувања"
      subtitle="Управувајте со вашите лични информации и преференции"
      profile={profile}
    >
      <div className="profile-page-center">
        <div className="profile-layout">
          <form className="card profile-section" onSubmit={saveProfile}>
            <h3>Лични информации</h3>
            <p>Ажурирајте ги вашите основни податоци</p>
            <div className="profile-avatar-row">
              <div className="profile-avatar-wrap">
                {displayAvatar
                  ? <img className="profile-avatar-preview" src={displayAvatar} alt="" />
                  : <span className="profile-avatar-initial" aria-hidden>{previewInitial}</span>}
              </div>
              <div className="profile-avatar-actions">
                <label className="btn-file">
                  Избери слика
                  <input type="file" accept="image/jpeg,image/png,image/webp" className="sr-only" onChange={onAvatarFile} />
                </label>
                {(profile?.avatarData || localAvatar) && !removePhoto && (
                  <button type="button" className="linkish" onClick={() => { setRemovePhoto(true); setLocalAvatar(null); }}>
                    Отстрани ја сликата
                  </button>
                )}
              </div>
            </div>
            <label>Име и презиме<input value={name} onChange={(e) => setName(e.target.value)} /></label>
            <label>Дејност
              <input
                value={occupation}
                onChange={(e) => setOccupation(e.target.value)}
                placeholder="Земјоделец"
              />
            </label>
            <label>Е-пошта<input value={profile?.email || ""} disabled /></label>
            <div className="profile-actions"><button>Зачувај промени</button></div>
            {profileError && <div className="error">{profileError}</div>}
            {savedProfile && <div className="muted">{savedProfile}</div>}
          </form>

        <form className="card profile-section" onSubmit={changePassword}>
          <h3>Промена на лозинка</h3>
          <p>Обезбедете го вашиот профил со силна лозинка</p>
          <label>Тековна лозинка<input type="password" value={passwordForm.current} onChange={(e) => setPasswordForm({ ...passwordForm, current: e.target.value })} /></label>
          <label>Нова лозинка<input type="password" value={passwordForm.next} onChange={(e) => setPasswordForm({ ...passwordForm, next: e.target.value })} /></label>
          <label>Потврди нова лозинка<input type="password" value={passwordForm.confirm} onChange={(e) => setPasswordForm({ ...passwordForm, confirm: e.target.value })} /></label>
          <div className="profile-actions"><button>Промени лозинка</button></div>
          {savedPassword && <div className="muted">{savedPassword}</div>}
        </form>

        <form className="card profile-section" onSubmit={savePreferences}>
          <h3>Преференции</h3>
          <p>Прилагодете ја апликацијата според вашите потреби</p>
          <label>Јазик
            <select value={preferences.language} onChange={(e) => setPreferences({ ...preferences, language: e.target.value })}>
              <option>Македонски</option>
              <option>English</option>
            </select>
          </label>
          <label>Единици мерка
            <select value={preferences.units} onChange={(e) => setPreferences({ ...preferences, units: e.target.value })}>
              <option>Империјал (°F, in, yd)</option>
              <option>Метрички (°C, mm, km)</option>
            </select>
          </label>
          <div className="profile-actions"><button>Зачувај преференции</button></div>
          {savedPrefs && <div className="muted">{savedPrefs}</div>}
        </form>

          <section className="card profile-danger">
            <h3>Зона на опасност</h3>
            <p>Трајно бришење на профил.</p>
            <small>Штом го избришете вашиот профил, нема враќање назад. Ве молиме бидете сигурни.</small>
            <button type="button" className="danger" onClick={() => { void deleteProfile(); }}>Избриши го мојот профил</button>
          </section>
        </div>
      </div>
    </AppShell>
  );
}

function Dashboard() {
  const location = useLocation();
  const navigate = useNavigate();
  const [summary, setSummary] = useState(null);
  const [profile, setProfile] = useState({
    fullName: localStorage.getItem("fullName") || "Земјоделец",
    email: localStorage.getItem("email") || "",
    role: "FARMER",
    occupation: localStorage.getItem("occupation") || "Земјоделец",
    avatarData: null,
  });
  const [parcels, setParcels] = useState([]);
  const [history, setHistory] = useState([]);
  const [error, setError] = useState("");
  const [savingParcel, setSavingParcel] = useState(false);

  const load = async () => {
    setError("");
    const [pRes, hRes, sRes, prRes] = await Promise.allSettled([
      api("/parcels"),
      api("/recommendations/history"),
      api("/dashboard/summary"),
      api("/profile"),
    ]);
    if (pRes.status === "fulfilled") setParcels(pRes.value);
    else {
      setParcels([]);
    }
    if (hRes.status === "fulfilled") setHistory(hRes.value);
    else setHistory([]);
    if (sRes.status === "fulfilled") setSummary(sRes.value);
    else setSummary(null);
    if (prRes.status === "fulfilled" && prRes.value) {
      setProfile(prRes.value);
      if (prRes.value.fullName) localStorage.setItem("fullName", prRes.value.fullName);
      if (prRes.value.occupation) localStorage.setItem("occupation", prRes.value.occupation);
    }
    if (pRes.status === "rejected") {
      setError("Не може да се вчитаат парцелите. Провери дали backend е на http://localhost:8080 и си најавен.");
    } else if (sRes.status === "rejected") {
      setError("Списокот на парцели е вчитан, но прегледот (време/вкупно) не. Рестартирај го backend по ажурирање и притисни „Освежи“.");
    }
  };

  useEffect(() => { load(); }, [location.pathname]);

  const addParcel = async (payload) => {
    setSavingParcel(true);
    try {
      await api("/parcels", { method: "POST", body: JSON.stringify(payload) });
      await load();
      setError("");
      return true;
    } catch (err) {
      const detail = err?.message || String(err);
      setError(
        /401|403/.test(detail)
          ? "Најави се повторно (токенот е застарен или невалиден)."
          : `Парцелата не е зачувана: ${detail}`,
      );
      return false;
    } finally {
      setSavingParcel(false);
    }
  };

  const generate = async (parcelId) => {
    await api(`/recommendations/generate/${parcelId}`, { method: "POST" });
    load();
  };

  const deleteParcel = async (parcelId) => {
    try {
      await api(`/parcels/${parcelId}`, { method: "DELETE" });
      await load();
      setError("");
    } catch (err) {
      const detail = err?.message || String(err);
      setError(`Парцелата не е избришана: ${detail}`);
    }
  };

  const updateParcel = async (parcelId, payload) => {
    setSavingParcel(true);
    try {
      await api(`/parcels/${parcelId}`, { method: "PUT", body: JSON.stringify(payload) });
      await load();
      setError("");
      return true;
    } catch (err) {
      const detail = err?.message || String(err);
      setError(
        /401|403/.test(detail)
          ? "Најави се повторно (токенот е застарен или невалиден)."
          : `Промените не се зачувани: ${detail}`,
      );
      return false;
    } finally {
      setSavingParcel(false);
    }
  };

  return (
    <>
      {error && <div className="error global-error">{error}</div>}
      <Routes>
        <Route
          path="/dashboard"
          element={(
            <DashboardPage
              summary={summary}
              parcels={parcels}
              history={history}
              onGenerate={generate}
              onRefresh={load}
              onAddParcelClick={() => void navigate("/parcels?add=1")}
              onDeleteParcel={deleteParcel}
              profile={profile}
            />
          )}
        />
        <Route
          path="/parcels/:id/edit"
          element={(
            <ParcelEditPage profile={profile} updateParcel={updateParcel} savingParcel={savingParcel} />
          )}
        />
        <Route
          path="/parcels/:id"
          element={<ParcelDetailPage profile={profile} />}
        />
        <Route
          path="/parcels"
          element={(
            <ParcelsPage
              parcels={parcels}
              history={history}
              addParcel={addParcel}
              deleteParcel={deleteParcel}
              savingParcel={savingParcel}
              profile={profile}
            />
          )}
        />
        <Route path="/history" element={<HistoryPage history={history} profile={profile} />} />
        <Route path="/reports/:recommendationId" element={<ReportDetailPage profile={profile} />} />
        <Route path="/reports" element={<ReportsPage history={history} parcels={parcels} profile={profile} />} />
        <Route path="/profile" element={<ProfilePage profile={profile} setProfile={setProfile} />} />
        <Route path="*" element={<Navigate to="/dashboard" />} />
      </Routes>
    </>
  );
}

function PrivateRoute({ children }) {
  return localStorage.getItem("token") ? children : <Navigate to="/login" />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/*" element={<PrivateRoute><Dashboard /></PrivateRoute>} />
    </Routes>
  );
}
