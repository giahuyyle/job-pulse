import { Activity, Bell, BriefcaseBusiness, Command, Menu, X } from 'lucide-react'
import { useState } from 'react'
import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import JobsPage from './pages/JobsPage'
import AlertsPage from './pages/AlertsPage'
import AdminPage from './pages/AdminPage'

const links = [
  {to:'/jobs', label:'Find jobs', icon:BriefcaseBusiness},
  {to:'/alerts', label:'Alerts', icon:Bell},
  {to:'/admin', label:'Operations', icon:Activity},
]

export default function App() {
  const [open, setOpen] = useState(false)
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
        <div className="hidden items-center gap-2 text-xs font-bold text-pine/60 md:flex"><span className="status-dot bg-emerald-500 shadow-[0_0_0_4px_rgba(16,185,129,.12)]"/> Local workspace</div>
        <button className="md:hidden" onClick={()=>setOpen(!open)} aria-label="Toggle menu">{open?<X/>:<Menu/>}</button>
      </div>
      {open&&<nav className="space-y-1 border-t border-black/8 p-4 md:hidden">{links.map(({to,label})=><NavLink key={to} to={to} onClick={()=>setOpen(false)} className="block rounded-xl px-4 py-3 font-bold">{label}</NavLink>)}</nav>}
    </header>
    <main><Routes><Route path="/jobs" element={<JobsPage/>}/><Route path="/alerts" element={<AlertsPage/>}/><Route path="/admin" element={<AdminPage/>}/><Route path="*" element={<Navigate to="/jobs" replace/>}/></Routes></main>
  </div>
}
