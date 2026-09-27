import { ArrowLeft, ArrowRight, BellPlus, Building2, ExternalLink, MapPin, Search, SlidersHorizontal, X } from 'lucide-react'
import { type FormEvent, useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import type { Job, JobSummary, Page } from '../api/types'
import { Empty, Failure, Loading, Pill } from '../components/States'

const relative = (date: string | null) => {
  if (!date) return 'Recently added'
  const days = Math.max(0, Math.round((Date.now() - new Date(date).getTime()) / 86400000))
  return days === 0 ? 'Today' : new Intl.RelativeTimeFormat('en', { numeric: 'auto' }).format(-days, 'day')
}
const workplace = (value: string) => value === 'ONSITE' ? 'On site' : value === 'UNSPECIFIED' ? 'Workplace not listed' : value
const sourceName = (value: string) => value.charAt(0) + value.slice(1).toLowerCase()
const readableDescription = (value: string | null) => {
  if (!value) return 'No description was provided by the source.'
  let text = value
  for (let pass = 0; pass < 3; pass++) {
    if (!/<[a-z][\s\S]*>/i.test(text) && !/&(?:lt|gt|amp|nbsp|#\d+);/i.test(text)) break
    const doc = new DOMParser().parseFromString(text.replace(/<br\s*\/?\s*>|<\/(?:p|div|li|h[1-6])>/gi, '$&\n'), 'text/html')
    doc.querySelectorAll('script, style, iframe, template').forEach(node => node.remove())
    const next = doc.body.textContent || ''
    if (next === text) break
    text = next
  }
  return text.replace(/\n[\t ]+/g, '\n').replace(/\n{3,}/g, '\n\n').trim()
}

export default function JobsPage({ authenticated }: { authenticated: boolean }) {
  const [params, setParams] = useSearchParams()
  const [data, setData] = useState<Page<JobSummary> | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<Job | null>(null)
  const [detailError, setDetailError] = useState('')
  const [detailLoading, setDetailLoading] = useState(false)
  const [saveName, setSaveName] = useState('')
  const [notice, setNotice] = useState('')
  const [saving, setSaving] = useState(false)
  const [filtersOpen, setFiltersOpen] = useState(() => !!(params.get('source') || params.get('remotePolicy') || (params.get('sort') && params.get('sort') !== 'newest')))
  const closeButton = useRef<HTMLButtonElement>(null)
  const returnFocus = useRef<HTMLElement | null>(null)
  const detailRequest = useRef(0)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try { setData(await api(`/api/v1/jobs?${params.toString()}`)) }
    catch (e) { setError(e instanceof Error ? e.message : 'Request failed') }
    finally { setLoading(false) }
  }, [params])
  useEffect(() => { void load() }, [load])

  const closeDetail = () => {
    detailRequest.current += 1
    setSelected(null)
    setDetailError('')
    setDetailLoading(false)
    returnFocus.current?.focus()
  }
  const detailOpen = !!selected || !!detailError || detailLoading
  useEffect(() => {
    if (!detailOpen) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    closeButton.current?.focus()
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeDetail()
      if (event.key !== 'Tab') return
      const focusable = Array.from(document.querySelectorAll<HTMLElement>('.detail-panel button, .detail-panel a[href]'))
      if (!focusable.length) return
      const first = focusable[0], last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => { document.body.style.overflow = previousOverflow; window.removeEventListener('keydown', onKeyDown) }
  }, [detailOpen])

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const next = new URLSearchParams()
    for (const [key, value] of new FormData(event.currentTarget)) if (String(value).trim()) next.set(key, String(value).trim())
    next.set('page', '0')
    setParams(next)
  }
  const open = async (id: string) => {
    const request = ++detailRequest.current
    returnFocus.current = document.activeElement as HTMLElement
    setDetailError('')
    setDetailLoading(true)
    try { const job = await api<Job>(`/api/v1/jobs/${id}`); if (request === detailRequest.current) setSelected(job) }
    catch (e) { if (request === detailRequest.current) setDetailError(e instanceof Error ? e.message : 'Could not load job') }
    finally { if (request === detailRequest.current) setDetailLoading(false) }
  }
  const save = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!authenticated) { window.location.assign('/oauth2/authorization/google'); return }
    if (!saveName.trim()) return
    setSaving(true)
    setNotice('')
    try {
      await api('/api/v1/saved-searches', { method: 'POST', body: JSON.stringify({ name: saveName.trim(), ...Object.fromEntries(['query', 'company', 'source', 'remotePolicy', 'location'].map(key => [key, params.get(key) || null])) }) })
      setSaveName('')
      setNotice('Search saved. New matches will appear in Alerts.')
    } catch (e) { setNotice(e instanceof Error ? e.message : 'Could not save search') }
    finally { setSaving(false) }
  }
  const page = Number(params.get('page') || 0)
  const go = (nextPage: number) => { const next = new URLSearchParams(params); next.set('page', String(nextPage)); setParams(next); document.getElementById('results')?.scrollIntoView({ behavior: 'smooth' }) }
  const activeFilters = ['query', 'location', 'company', 'source', 'remotePolicy'].filter(key => params.get(key)).length
  const advancedFilters = ['source', 'remotePolicy'].filter(key => params.get(key)).length + (params.get('sort') && params.get('sort') !== 'newest' ? 1 : 0)

  return <div className="jobs-page mx-auto max-w-[1440px] px-5 pb-20 pt-10 lg:px-10 lg:pt-16">
    <section className="jobs-intro">
      <div className="jobs-intro-copy">
        <p className="eyebrow">Jobs from company boards</p>
        <h1>Find your <em>next role.</em></h1>
        <p className="jobs-intro-description">Search listings and apply on company sites.</p>
      </div>
      <div className="jobs-intro-aside">
        <span className="source-kicker">Sources</span>
        <div className="source-stack"><span>Greenhouse</span><span>Lever</span><span>Ashby</span></div>
      </div>
    </section>

    <form key={params.toString()} onSubmit={submit} className="search-workspace" role="search">
      <div className="search-main-fields">
        <label className="search-field"><span>What</span><span className="search-input-wrap"><Search size={18}/><input name="query" defaultValue={params.get('query') || ''} placeholder="Job title, skill, or keyword"/></span></label>
        <label className="search-field"><span>Where</span><span className="search-input-wrap"><MapPin size={18}/><input name="location" defaultValue={params.get('location') || ''} placeholder="City or region"/></span></label>
        <label className="search-field"><span>Company</span><span className="search-input-wrap"><Building2 size={18}/><input name="company" defaultValue={params.get('company') || ''} placeholder="Any company"/></span></label>
        <button className="btn-primary search-submit" type="submit">Search jobs <ArrowRight size={17}/></button>
      </div>
      <button type="button" className="mobile-filter-toggle" aria-expanded={filtersOpen} aria-controls="search-filters" onClick={() => setFiltersOpen(!filtersOpen)}><SlidersHorizontal size={16}/> Filters &amp; sort {advancedFilters > 0 && <span>{advancedFilters}</span>}</button>
      <div id="search-filters" className={`search-filter-row ${filtersOpen ? 'is-open' : ''}`}><span className="search-filter-label"><SlidersHorizontal size={15}/> Refine results</span>
        <label><span className="sr-only">Source</span><select name="source" defaultValue={params.get('source') || ''}><option value="">All sources</option><option value="GREENHOUSE">Greenhouse</option><option value="LEVER">Lever</option><option value="ASHBY">Ashby</option></select></label>
        <label><span className="sr-only">Workplace</span><select name="remotePolicy" defaultValue={params.get('remotePolicy') || ''}><option value="">Any workplace</option><option value="REMOTE">Remote</option><option value="HYBRID">Hybrid</option><option value="ONSITE">On site</option><option value="UNSPECIFIED">Unspecified</option></select></label>
        <label><span className="sr-only">Sort order</span><select name="sort" defaultValue={params.get('sort') || 'newest'}><option value="newest">Newest first</option><option value="relevance">Most relevant</option></select></label>
        {activeFilters > 0 && <button className="clear-filters" type="button" onClick={() => setParams({})}>Clear filters</button>}
      </div>
    </form>

    <div className="jobs-content" id="results">
      <section aria-labelledby="results-heading" className="min-w-0">
        <div className="results-heading"><div><p className="eyebrow">Results</p><h2 id="results-heading" aria-live="polite">{loading ? 'Searching' : error ? 'Search results' : `${(data?.totalElements || 0).toLocaleString()} roles`}</h2></div><p>{activeFilters > 0 ? `${activeFilters} ${activeFilters === 1 ? 'filter' : 'filters'}` : params.get('sort') === 'relevance' ? 'Most relevant first' : 'Newest first'}</p></div>
        <div className="job-list">{loading ? <Loading label="Searching"/> : error ? <Failure message={error} retry={load}/> : !data?.content.length ? <Empty title="No matching roles" body="Try fewer filters or a broader keyword."/> : data.content.map(job => <button key={job.id} onClick={() => void open(job.id)} className="job-row"><span className="job-monogram" aria-hidden="true">{job.company.slice(0, 2).toUpperCase()}</span><span className="job-row-body"><span className="job-title">{job.title}</span><span className="job-company">{job.company}<span aria-hidden="true"> · </span>{job.location || 'Location flexible'}</span><span className="job-tags"><Pill>{workplace(job.remotePolicy)}</Pill><Pill>{sourceName(job.source)}</Pill></span></span><span className="job-row-end"><span>{relative(job.postedAt || job.firstSeenAt)}</span><ArrowRight size={18} aria-hidden="true"/></span></button>)}</div>
        {!loading && !error && data && data.totalPages > 1 && <nav className="results-pagination" aria-label="Results pages"><button className="btn-secondary" disabled={page === 0} onClick={() => go(page - 1)}><ArrowLeft size={15}/>Previous</button><span>Page {page + 1} of {data.totalPages}</span><button className="btn-secondary" disabled={page + 1 >= data.totalPages} onClick={() => go(page + 1)}>Next<ArrowRight size={15}/></button></nav>}
      </section>
      <aside className="jobs-sidebar" aria-label="Saved search"><div className="save-search-card"><span className="save-search-icon"><BellPlus size={21}/></span><h2>Save this search</h2><p>Get new matches in Alerts.</p><form onSubmit={save}><label htmlFor="search-name">Search name</label><input id="search-name" className="field" value={saveName} onChange={event => setSaveName(event.target.value)} placeholder="e.g. Remote design roles" required={authenticated}/><button className="btn-primary" disabled={saving}>{saving ? 'Saving…' : authenticated ? 'Save search' : 'Sign in to save'}<ArrowRight size={15}/></button></form>{notice && <p className="save-notice" role="status">{notice}</p>}</div><p className="source-note"><ExternalLink size={16}/> Apply on the company site.</p></aside>
    </div>

    {detailOpen && <div className="detail-backdrop" onMouseDown={closeDetail}><article role="dialog" aria-modal="true" aria-label="Job details" onMouseDown={event => event.stopPropagation()} className="detail-panel"><button ref={closeButton} className="detail-close" onClick={closeDetail} aria-label="Close job details"><X size={19}/></button>{detailLoading ? <Loading label="Loading role"/> : detailError ? <Failure message={detailError}/> : selected && <><p className="eyebrow">{selected.company}</p><h2 id="job-detail-title">{selected.title}</h2><div className="detail-tags"><Pill>{workplace(selected.remotePolicy)}</Pill><Pill>{sourceName(selected.source)}</Pill>{selected.employmentType && <Pill>{selected.employmentType}</Pill>}</div><p className="detail-location"><MapPin size={17}/>{selected.location || 'Location flexible'}</p><a href={selected.applyUrl} target="_blank" rel="noreferrer" className="btn-primary detail-apply">Apply on company site<ExternalLink size={16}/></a><div className="detail-description"><p className="eyebrow">About the role</p><div>{readableDescription(selected.description)}</div></div></>}</article></div>}
  </div>
}
