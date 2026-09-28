import { ArrowRight, Bell, BellRing, Check, MapPin, Power, PowerOff } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import type { Alert, Search, EmailSettings } from '../api/types'
import { Empty, Failure, Loading, Pill } from '../components/States'

export default function AlertsPage() {
  const [searches, setSearches] = useState<Search[] | null>(null)
  const [alerts, setAlerts] = useState<Alert[] | null>(null)
  const [emailSettings, setEmailSettings] = useState<EmailSettings | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [unreadOnly, setUnreadOnly] = useState(true)
  const [busyId, setBusyId] = useState<string | null>(null)

  const load = useCallback(async () => {
    setError('')
    try {
      const [settings, savedSearches, newAlerts] = await Promise.all([
        api<EmailSettings>('/api/v1/email-settings'),
        api<Search[]>('/api/v1/saved-searches'),
        api<Alert[]>(`/api/v1/alerts?unread=${unreadOnly}`),
      ])
      setEmailSettings(settings)
      setSearches(savedSearches)
      setAlerts(newAlerts)
    } catch (e) { setError(e instanceof Error ? e.message : 'Request failed') }
  }, [unreadOnly])
  useEffect(() => { void load() }, [load])

  const update = async (id: string, path: string, body?: unknown) => {
    setBusyId(id)
    setNotice('')
    try {
      await api(path, { method: 'PATCH', ...(body ? { body: JSON.stringify(body) } : {}) })
      await load()
    } catch (e) { setNotice(e instanceof Error ? e.message : 'Could not update alerts') }
    finally { setBusyId(null) }
  }

  return <div className="alerts-page mx-auto max-w-[1200px] px-5 pb-20 pt-10 lg:px-10 lg:pt-16">
    <header className="alerts-hero"><div><h1>Job <span>alerts.</span></h1><p>New jobs matching your saved searches.</p></div><span className="alerts-hero-icon" aria-hidden="true"><BellRing size={36}/></span></header>
    {emailSettings && <section className="email-summary" aria-labelledby="email-summary-heading"><h2 id="email-summary-heading">Daily email digest</h2><p>Daily at 9:00 AM Eastern Time.</p><p>Recipient: {emailSettings.recipient || 'Your verified Google sign-in email'}</p><p>{!emailSettings.serviceAvailable ? 'Email delivery is currently disabled. You can still save your preferences.' : emailSettings.nextDigestAt ? `Next digest: ${new Intl.DateTimeFormat('en-US', { timeZone: 'America/New_York', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(emailSettings.nextDigestAt))} Eastern Time` : 'Enable email on a saved search to receive new matches.'}</p>{emailSettings.suppression && <p role="status">{emailSettings.suppression === 'BOUNCE' ? 'Email delivery is suspended because the address bounced. Sign in with an updated verified address to resume.' : emailSettings.suppression === 'PROVIDER' ? 'The email provider has suppressed this address. Contact the administrator to review delivery.' : emailSettings.suppression === 'COMPLAINT' ? 'Email delivery is suspended after a spam complaint. Opt in again to request resubscription.' : 'You have unsubscribed from job emails. Opt in on a saved search to resume.'}</p>}</section>}
    {notice && <p role="status" className="alerts-notice">{notice}</p>}
    {error ? <div className="panel mt-8"><Failure message={error} retry={load}/></div> : !searches || !alerts ? <div className="panel mt-8"><Loading label="Loading your alerts"/></div> :
      <div className="alerts-layout">
        <aside aria-labelledby="saved-searches-heading"><div className="alerts-section-heading"><div><h2 id="saved-searches-heading">Saved searches <span>{searches.length}</span></h2></div></div>
          <div className="saved-search-list">{searches.length === 0 ? <div className="panel"><Empty title="No saved searches" body="Save a job search to get alerts."/><Link to="/jobs" className="btn-primary mx-5 mb-5">Find jobs<ArrowRight size={15}/></Link></div> : searches.map(search => <article key={search.id} className={`saved-search-item ${!search.enabled ? 'is-disabled' : ''}`}><div><h3>{search.name}</h3><p>{[search.query, search.company, search.location, search.remotePolicy, search.source].filter(Boolean).join(' · ') || 'All jobs'}</p><span className="saved-search-status">{search.enabled ? 'Active' : 'Paused'}</span><label className="digest-opt-in"><input type="checkbox" checked={search.emailEnabled ?? false} disabled={busyId === search.id} onChange={event => void update(search.id, `/api/v1/saved-searches/${search.id}`, { emailEnabled: event.target.checked })}/>Include in my daily email digest</label></div><button disabled={busyId === search.id} onClick={() => void update(search.id, `/api/v1/saved-searches/${search.id}`, { enabled: !search.enabled })} aria-label={`${search.enabled ? 'Pause' : 'Resume'} ${search.name}`} title={search.enabled ? 'Pause search' : 'Resume search'}>{search.enabled ? <Power size={17}/> : <PowerOff size={17}/>}</button></article>)}</div>
          <Link to="/jobs" className="add-search-link">New search <ArrowRight size={15}/></Link>
        </aside>
        <section aria-labelledby="alerts-heading"><div className="alerts-section-heading"><div><h2 id="alerts-heading">Inbox <span>{alerts.length}</span></h2></div><button onClick={() => setUnreadOnly(!unreadOnly)} className="btn-secondary" aria-pressed={unreadOnly}>{unreadOnly ? <BellRing size={15}/> : <Bell size={15}/>} {unreadOnly ? 'Unread only' : 'All alerts'}</button></div>
          <div className="alerts-list">{alerts.length === 0 ? <Empty title={unreadOnly ? 'No unread alerts' : 'No alerts yet'} body="New matches will appear here."/> : alerts.map(alert => <article key={alert.id} className={`alert-item ${alert.readAt ? 'is-read' : ''}`}><span className="alert-indicator" aria-hidden="true"/><div className="alert-item-body"><div className="alert-item-top"><div><h3>{alert.job.title}</h3><p>{alert.job.company}</p></div><time dateTime={alert.createdAt}>{new Date(alert.createdAt).toLocaleDateString()}</time></div><p className="alert-location"><MapPin size={14}/>{alert.job.location || 'Location flexible'}</p><div className="alert-actions"><Pill>{alert.job.remotePolicy === 'UNSPECIFIED' ? 'Workplace not listed' : alert.job.remotePolicy}</Pill><a className="btn-secondary" href={alert.job.applyUrl} target="_blank" rel="noreferrer">View role<ArrowRight size={14}/></a>{!alert.readAt && <button disabled={busyId === alert.id} onClick={() => void update(alert.id, `/api/v1/alerts/${alert.id}/read`)} className="btn-primary"><Check size={15}/>Mark read</button>}</div></div></article>)}</div>
        </section>
      </div>}
  </div>
}
