import type { Problem } from './types'

export class ApiError extends Error {
  constructor(message:string, public status:number) { super(message) }
}

const baseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

function resolveUrl(path:string) {
  if (!baseUrl) return path
  const normalized = baseUrl.endsWith('/api/v1') && path.startsWith('/api/v1/')
    ? path.slice('/api/v1'.length)
    : path
  return `${baseUrl}${normalized}`
}

async function csrfHeader():Promise<Record<string,string>> {
  const response=await fetch(resolveUrl('/api/v1/auth/csrf'),{credentials:'same-origin'})
  if(!response.ok) throw new ApiError('Could not obtain CSRF token',response.status)
  const token=await response.json() as {headerName:string;token:string}
  return {[token.headerName]:token.token}
}

export async function api<T>(path:string, init?:RequestInit):Promise<T> {
  const method=(init?.method||'GET').toUpperCase()
  const unsafe=!['GET','HEAD','OPTIONS','TRACE'].includes(method)
  const csrf=unsafe ? await csrfHeader() : null
  const response = await fetch(resolveUrl(path), {
    ...init,
    credentials:'same-origin',
    headers: { ...(init?.body ? {'Content-Type':'application/json'} : {}),
      ...csrf, ...init?.headers },
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => ({} as Problem)) as Problem
    throw new ApiError(problem.detail || problem.title || `Request failed (${response.status})`, response.status)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export async function signOut():Promise<void> {
  const csrf=await csrfHeader()
  const response=await fetch('/logout',{method:'POST',credentials:'same-origin',
    headers:csrf})
  if(!response.ok) throw new ApiError('Could not sign out',response.status)
  window.location.assign('/jobs')
}

export const qs = (params:Record<string,string|number|boolean|null|undefined>) => {
  const value = new URLSearchParams()
  Object.entries(params).forEach(([key, entry]) => { if (entry !== '' && entry != null) value.set(key, String(entry)) })
  return value.toString()
}
