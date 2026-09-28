import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import AlertsPage from './AlertsPage'
vi.mock('../api/client', () => ({ api: vi.fn() }))
afterEach(() => { cleanup(); vi.resetAllMocks() })
function setup({fail = false, suppressed = null, available = true}: {fail?:boolean;suppressed?:string|null;available?:boolean} = {}) {
  let enabled = false
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (options?.method === 'PATCH') {
      if (fail) throw new Error('Could not save email preference')
      enabled = JSON.parse(String(options.body)).emailEnabled
      return {} as never
    }
    if (path.includes('email-settings')) return {recipient:'alice@example.com',timeZone:'America/New_York',localTime:'09:00',nextDigestAt:enabled?'2026-09-29T13:00:00Z':null,serviceAvailable:available,suppression:suppressed} as never
    return (path.includes('saved-searches')?[{id:'search-1',name:'Backend',enabled:true,emailEnabled:enabled}]:[]) as never
  })
  render(<BrowserRouter><AlertsPage/></BrowserRouter>)
}
describe('daily email digest preferences', () => {
  it('persists opt-in and displays the fixed Eastern schedule', async () => {
    setup()
    const checkbox = await screen.findByRole('checkbox', {name:'Include in my daily email digest'})
    expect(checkbox).not.toBeChecked()
    expect(screen.getByText('Daily at 9:00 AM Eastern Time.')).toBeVisible()
    expect(screen.getByText('Recipient: alice@example.com')).toBeVisible()
    fireEvent.click(checkbox)
    await waitFor(() => expect(checkbox).toBeChecked())
    expect(api).toHaveBeenCalledWith('/api/v1/saved-searches/search-1', {method:'PATCH',body:'{"emailEnabled":true}'})
    expect(await screen.findByText(/Next digest:.*Eastern Time/)).toBeVisible()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  })
  it('keeps the setting off when a save fails', async () => {
    setup({fail:true})
    const checkbox = await screen.findByRole('checkbox', {name:'Include in my daily email digest'})
    fireEvent.click(checkbox)
    expect(await screen.findByRole('status')).toHaveTextContent('Could not save email preference')
    expect(checkbox).not.toBeChecked()
  })
  it('shows disabled delivery and bounce suspension clearly', async () => {
    setup({available:false,suppressed:'BOUNCE'})
    expect(await screen.findByText(/Email delivery is currently disabled/)).toBeVisible()
    expect(screen.getByRole('status')).toHaveTextContent('address bounced')
  })
  it('offers retry when settings cannot load', async () => {
    vi.mocked(api).mockRejectedValue(new Error('Settings unavailable'))
    render(<BrowserRouter><AlertsPage/></BrowserRouter>)
    expect(await screen.findByText('Settings unavailable')).toBeVisible()
    expect(screen.getByRole('button', {name:/Try again/i})).toBeVisible()
  })
})
