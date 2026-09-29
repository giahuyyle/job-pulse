import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import AccountDropdown from './AccountDropdown'
afterEach(cleanup)
const profile = {name:'Alice Example', email:'alice@example.com', picture:'https://example.com/avatar.jpg'}
it('reveals profile details and signs out through the dropdown', async () => {
  const user = userEvent.setup(); const logout = vi.fn().mockResolvedValue(undefined)
  render(<AccountDropdown {...profile} onSignOut={logout}/> )
  expect(screen.queryByText(profile.email)).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', {name:'Account'}))
  expect(screen.getByText(profile.name)).toBeVisible(); expect(screen.getByText(profile.email)).toBeVisible()
  await user.tab(); expect(screen.getByRole('button', {name:'Sign out'})).toHaveFocus()
  await user.keyboard('{Enter}'); expect(logout).toHaveBeenCalledOnce()
})
it('closes with Escape, outside pointer input, and focus leaving the dropdown', async () => {
  const user = userEvent.setup()
  render(<><AccountDropdown {...profile} onSignOut={vi.fn()}/><button>Outside</button></>)
  const trigger = screen.getByRole('button', {name:'Account'})
  await user.click(trigger); await user.keyboard('{Escape}')
  expect(trigger).toHaveFocus(); expect(trigger).toHaveAttribute('aria-expanded','false')
  await user.click(trigger); fireEvent.pointerDown(document.body)
  expect(screen.queryByRole('region')).not.toBeInTheDocument()
  await user.click(trigger); await user.tab(); await user.tab()
  expect(screen.getByRole('button',{name:'Outside'})).toHaveFocus()
  expect(screen.queryByRole('region')).not.toBeInTheDocument()
})
it('falls back when the avatar fails and reports sign-out errors', async () => {
  const user = userEvent.setup()
  render(<AccountDropdown {...profile} onSignOut={vi.fn().mockRejectedValue(new Error('network'))}/> )
  const image = screen.getByRole('button',{name:'Account'}).querySelector('img')!
  fireEvent.error(image); expect(screen.getByRole('button',{name:'Account'}).querySelector('img')).toBeNull()
  await user.click(screen.getByRole('button',{name:'Account'})); await user.click(screen.getByRole('button',{name:'Sign out'}))
  expect(await screen.findByRole('alert')).toHaveTextContent('Could not sign out')
  expect(screen.getByRole('button',{name:'Sign out'})).toBeEnabled()
})
