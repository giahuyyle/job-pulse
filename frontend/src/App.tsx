import { Activity, Bell, BriefcaseBusiness, Command, Menu, X } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import { api, signOut } from './api/client'
import JobsPage from './pages/JobsPage'
import AlertsPage from './pages/AlertsPage'
import AdminPage from './pages/AdminPage'

type Session = {authenticated:boolean; email:string|null; subject:string|null; admin:boolean}

function SignInGate({session,admin,children}:{session:Session|null;admin?:boolean;children:ReactNode}){
  if(!session) return <div className="mx-auto max-w-2xl p-10">Checking your session…</div>
  if(!session.authenticated) return <div className="mx-auto max-w-2xl p-10"><div className="panel p-8"><h1 className="font-display text-2xl font-extrabold">Sign in to continue</h1><p className="mt-2 text-sm text-black/50">Your saved searches and alerts are private to your account.</p><a className="btn-primary mt-6 inline-flex" href="/oauth2/authorization/google">Sign in with Google</a></div></div>
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
    <header className="sticky top-0 z-40 border-b border-black/8 bg-canvas/90 backdrop-blur-xl">
      <div className="mx-auto flex h-18 max-w-[1440px] items-center justify-between px-5 lg:px-10">
        <NavLink to="/jobs" className="flex items-center gap-3" onClick={()=>setOpen(false)}>
          <span className="grid size-9 place-items-center rounded-xl bg-pine text-mint shadow-lg shadow-pine/15"><Command size={18}/></span>
          <span className="font-display text-lg font-extrabold tracking-[-.04em]">JobPulse</span>
        </NavLink>
        <nav className="hidden items-center gap-1 rounded-2xl border border-black/8 bg-white/75 p-1.5 md:flex">
          {links.map(({to,label,icon:Icon})=><NavLink key={to} to={to} className={({isActive})=>`flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-bold ${isActive?'bg-pine text-white shadow-sm':'text-ink/55 hover:text-ink'}`}><Icon size={15}/>{label}</NavLink>)}
        </nav>
        <div className="hidden items-center gap-2 text-xs font-bold text-pine/60 md:flex">{session?.authenticated?<><span>{session.email}</span><button className="btn-secondary" onClick={()=>void signOut()}>Sign out</button></>:<a className="btn-secondary" href="/oauth2/authorization/google">Sign in with Google</a>}</div>
        <button className="md:hidden" onClick={()=>setOpen(!open)} aria-label="Toggle menu">{open?<X/>:<Menu/>}</button>
      </div>
      {open&&<nav className="space-y-1 border-t border-black/8 p-4 md:hidden">{links.map(({to,label})=><NavLink key={to} to={to} onClick={()=>setOpen(false)} className="block rounded-xl px-4 py-3 font-bold">{label}</NavLink>)}{session?.authenticated?<button onClick={()=>void signOut()} className="block rounded-xl px-4 py-3 font-bold">Sign out</button>:<a href="/oauth2/authorization/google" className="block rounded-xl px-4 py-3 font-bold">Sign in with Google</a>}</nav>}
    </header>
    <main><Routes><Route path="/jobs" element={<JobsPage authenticated={!!session?.authenticated}/>}/><Route path="/alerts" element={<SignInGate session={session}><AlertsPage/></SignInGate>}/><Route path="/admin" element={<SignInGate session={session} admin><AdminPage/></SignInGate>}/><Route path="*" element={<Navigate to="/jobs" replace/>}/></Routes></main>
  </div>
}
