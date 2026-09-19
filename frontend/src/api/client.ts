import type { Problem } from './types'

export class ApiError extends Error {
  constructor(message:string, public status:number) { super(message) }
}

export async function api<T>(path:string, init?:RequestInit):Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { ...(init?.body ? {'Content-Type':'application/json'} : {}), ...init?.headers },
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => ({} as Problem)) as Problem
    throw new ApiError(problem.detail || problem.title || `Request failed (${response.status})`, response.status)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const qs = (params:Record<string,string|number|boolean|null|undefined>) => {
  const value = new URLSearchParams()
  Object.entries(params).forEach(([key, entry]) => { if (entry !== '' && entry != null) value.set(key, String(entry)) })
  return value.toString()
}
