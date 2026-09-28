import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import JobsPage from './JobsPage'
vi.mock('../api/client', () => ({api:vi.fn()}))
afterEach(() => { cleanup(); vi.resetAllMocks() })
it('saves current filters with explicit email opt-in', async () => {
  vi.mocked(api).mockResolvedValue({content:[],page:0,size:20,totalElements:0,totalPages:0})
  render(<MemoryRouter initialEntries={['/jobs?query=backend&remotePolicy=REMOTE']}><JobsPage authenticated/></MemoryRouter>)
  const checkbox = await screen.findByRole('checkbox', {name:'Include in my daily email digest'})
  expect(checkbox).not.toBeChecked()
  fireEvent.change(screen.getByLabelText('Search name'), {target:{value:'Remote backend'}})
  fireEvent.click(checkbox)
  fireEvent.click(screen.getByRole('button', {name:/Save search/}))
  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/v1/saved-searches', expect.objectContaining({method:'POST'})))
  const call = vi.mocked(api).mock.calls.find(([path]) => path === '/api/v1/saved-searches')
  expect(JSON.parse(String(call?.[1]?.body))).toMatchObject({name:'Remote backend',query:'backend',remotePolicy:'REMOTE',emailEnabled:true})
  expect(await screen.findByRole('status')).toHaveTextContent('daily 9 AM Eastern email digest')
})
