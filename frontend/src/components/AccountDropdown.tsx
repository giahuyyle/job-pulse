import { LogOut, UserRound } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'

export default function AccountDropdown({ name, email, picture, onSignOut }: {
  name?: string | null; email: string | null; picture?: string | null; onSignOut: () => Promise<void>
}) {
  const [open, setOpen] = useState(false)
  const [failedPicture, setFailedPicture] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const root = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const id = useId()
  const image = picture?.startsWith('https://') && picture !== failedPicture ? picture : null

  useEffect(() => {
    if (!open) return
    const dismiss = (event: Event) => {
      if (event.target instanceof Node && !root.current?.contains(event.target)) setOpen(false)
    }
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { setOpen(false); trigger.current?.focus() }
    }
    document.addEventListener('pointerdown', dismiss)
    document.addEventListener('focusin', dismiss)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('pointerdown', dismiss)
      document.removeEventListener('focusin', dismiss)
      document.removeEventListener('keydown', escape)
    }
  }, [open])

  async function logout() {
    setPending(true); setError('')
    try { await onSignOut() }
    catch { setError('Could not sign out. Please try again.') }
    finally { setPending(false) }
  }

  return <div className="account-dropdown" ref={root}>
    <button ref={trigger} className="account-avatar" aria-label="Account" aria-expanded={open}
      aria-controls={id} onClick={() => setOpen(!open)}>
      {image ? <img src={image} alt="" referrerPolicy="no-referrer" onError={() => setFailedPicture(image)}/>
        : <UserRound size={21} aria-hidden="true"/>}
    </button>
    {open && <div id={id} className="account-panel" role="region" aria-label="Account details">
      <p className="account-name">{name || 'Your account'}</p>
      <p className="account-email">{email}</p>
      <button className="account-signout" onClick={() => void logout()} disabled={pending}>
        <LogOut size={16} aria-hidden="true"/>{pending ? 'Signing out…' : 'Sign out'}
      </button>
      {error && <p className="account-error" role="alert">{error}</p>}
    </div>}
  </div>
}
