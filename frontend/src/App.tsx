import { Activity, Bell, BriefcaseBusiness, Menu, X } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import { api, signOut } from './api/client'
import JobsPage from './pages/JobsPage'
import AlertsPage from './pages/AlertsPage'
import AdminPage from './pages/AdminPage'

type Session = {authenticated:boolean; email:string|null; subject:string|null; admin:boolean}

function SignInGate({session,admin,children}:{session:Session|null;admin?:boolean;children:ReactNode}){
  if(!session) return <div className="mx-auto max-w-2xl p-10">Checking your session…</div>
  if(!session.authenticated) return <div className="mx-auto max-w-2xl p-10"><div className="panel p-8"><h1 className="font-display text-2xl font-extrabold">Sign in to continue</h1><p className="mt-2 text-sm text-black/50">Sign in to see your searches and alerts.</p><a className="btn-primary mt-6 inline-flex" href="/oauth2/authorization/google">Sign in with Google</a></div></div>
  if(admin&&!session.admin) return <div className="mx-auto max-w-2xl p-10"><div className="panel p-8"><h1 className="font-display text-2xl font-extrabold">Administrator access required</h1></div></div>
  return children
}

export default function App() {
  const [open, setOpen] = useState(false)
  const [session, setSession] = useState<Session|null>(null)
  useEffect(()=>{void api<Session>('/api/v1/auth/session').then(setSession)
    .catch(()=>setSession({authenticated:false,email:null,subject:null,admin:false}))},[])
  const links = [
    {to:'/jobs', label:'Find jobs', icon:BriefcaseBusiness},
    ...(session?.authenticated?[{to:'/alerts',label:'Alerts',icon:Bell}]:[]),
    ...(session?.admin?[{to:'/admin',label:'Operations',icon:Activity}]:[]),
  ]
  return <div className="min-h-screen">
    <a className="skip-link" href="#main-content">Skip to content</a>
    <header className="site-header sticky top-0 z-40 border-b border-black/8 bg-canvas/90 backdrop-blur-xl">
      <div className="mx-auto flex h-18 max-w-[1440px] items-center justify-between px-5 lg:px-10">
        <NavLink to="/jobs" className="flex items-center gap-2.5" onClick={()=>setOpen(false)} aria-label="JobPulse home">
          <span className="brand-mark" aria-hidden="true">j<span>.</span></span>
          <span className="font-display text-lg font-extrabold tracking-[-.04em]">jobpulse</span>
        </NavLink>
        <nav className="desktop-nav hidden items-center gap-1 md:flex" aria-label="Main navigation">
          {links.map(({to,label,icon:Icon})=><NavLink key={to} to={to} className={({isActive})=>`flex items-center gap-2 px-4 py-2 text-sm font-bold ${isActive?'active':'text-ink/55 hover:text-ink'}`}><Icon size={15}/>{label}</NavLink>)}
        </nav>
        <div className="hidden min-w-0 items-center gap-2 text-xs font-bold text-pine/60 md:flex">{session?.authenticated?<><span className="max-w-40 truncate">{session.email}</span><button className="btn-secondary" onClick={()=>void signOut()}>Sign out</button></>:<a className="btn-secondary" href="/oauth2/authorization/google">Sign in</a>}</div>
        <button className="menu-button md:hidden" onClick={()=>setOpen(!open)} aria-label={open?'Close menu':'Open menu'} aria-expanded={open} aria-controls="mobile-navigation">{open?<X/>:<Menu/>}</button>
      </div>
      {open&&<nav id="mobile-navigation" aria-label="Mobile navigation" className="mobile-nav space-y-1 border-t border-black/8 p-4 md:hidden">{links.map(({to,label})=><NavLink key={to} to={to} onClick={()=>setOpen(false)} className={({isActive})=>`block rounded-xl px-4 py-3 font-bold ${isActive?'bg-pine text-white':''}`}>{label}</NavLink>)}{session?.authenticated?<button onClick={()=>void signOut()} className="block w-full rounded-xl px-4 py-3 text-left font-bold">Sign out</button>:<a href="/oauth2/authorization/google" className="block rounded-xl px-4 py-3 font-bold">Sign in with Google</a>}</nav>}
    </header>
    <main id="main-content"><Routes><Route path="/jobs" element={<JobsPage authenticated={!!session?.authenticated}/>}/><Route path="/alerts" element={<SignInGate session={session}><AlertsPage/></SignInGate>}/><Route path="/admin" element={<SignInGate session={session} admin><AdminPage/></SignInGate>}/><Route path="*" element={<Navigate to="/jobs" replace/>}/></Routes></main>
  </div>
}
