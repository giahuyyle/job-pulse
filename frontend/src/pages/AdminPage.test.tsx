import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdminPage from './AdminPage'

vi.mock('recharts', () => ({
  ResponsiveContainer: ({children}:{children:React.ReactNode}) => <div>{children}</div>,
  BarChart: ({children}:{children:React.ReactNode}) => <div>{children}</div>,
  Bar: () => null, CartesianGrid: () => null, Tooltip: () => null,
  XAxis: () => null, YAxis: () => null,
}))

const emptyPage = {content:[],page:0,size:50,totalElements:0,totalPages:0}
const overview = {totalBoards:0,activeBoards:0,totalPostings:0,openPostings:0,failedRunsLast24Hours:0,unpublishedEvents:0,deadLetterMessages:0,lastSuccessfulRunAt:null}
const summary = {enabledTargets:0,disabledTargets:0,dueTargets:0,lastIngestionAt:null,pendingIngestionRequests:0,runningIngestionRequests:0,successfulRuns:0,failedRuns:0,failedRunsLast24h:0,postingsCreated:0,postingsUpdated:0,postingsUnchanged:0,postingsClosed:0,rabbitQueueDepth:0,deadLetterMessages:0,unpublishedJobEvents:0,kafkaPublishingFailures:0,oldestUnpublishedEventAt:null}

function json(value:unknown,status=200){return new Response(JSON.stringify(value),{status,headers:{'Content-Type':'application/json'}})}
function fixtureFetch({boards=[],runs=[]}:{boards?:unknown[];runs?:unknown[]}={}){
  return vi.fn(async(input:RequestInfo|URL,init?:RequestInit)=>{const url=String(input)
    if(url.includes('/auth/csrf'))return json({headerName:'X-CSRF-TOKEN',token:'test-token'})
    if(init?.method&&init.method!=='GET')return json({})
    if(url.includes('/overview'))return json(overview)
    if(url.includes('/summary'))return json(summary)
    if(url.includes('/boards'))return json({...emptyPage,content:boards,totalElements:boards.length,totalPages:boards.length?1:0})
    if(url.includes('/ingestion-runs'))return json({...emptyPage,content:runs,totalElements:runs.length,totalPages:Math.ceil(runs.length/10)})
    if(url.includes('/discovery'))return json([])
    return json(emptyPage)
  })
}
function renderPage(){const client=new QueryClient({defaultOptions:{queries:{retry:false,staleTime:Infinity}}});return render(<QueryClientProvider client={client}><BrowserRouter><AdminPage/></BrowserRouter></QueryClientProvider>)}
const board={id:'board-1',company:'Stripe',provider:'GREENHOUSE',sourceAccount:'stripe',enabled:true,pollingIntervalMinutes:15,lastSuccessfulRunAt:null,lastRunStatus:'FAILED'}
const run=(index:number,status='SUCCEEDED')=>({id:`run-${index}`,source:'GREENHOUSE',sourceAccount:'stripe',status,startedAt:'2026-09-20T08:00:00Z',completedAt:'2026-09-20T08:01:00Z',discovered:10,created:1,updated:1,unchanged:8,closed:0,failureMessage:status==='FAILED'?'Provider failed':null,durationMs:60000,throughputPerSecond:0.16,retryCount:0,correlationId:`00000000-0000-0000-0000-${String(index).padStart(12,'0')}`})

afterEach(()=>{cleanup();vi.restoreAllMocks()})

describe('AdminPage',()=>{
  it('shows loading and API error states',async()=>{globalThis.fetch=vi.fn(()=>Promise.reject(new Error('offline')));renderPage();expect(screen.getByText(/Reading operational state/i)).toBeInTheDocument();expect(await screen.findByText(/Couldn’t load this view/i)).toBeInTheDocument();expect(screen.getByText('offline')).toBeInTheDocument()})
  it('shows the empty boards state',async()=>{globalThis.fetch=fixtureFetch();renderPage();await userEvent.click(await screen.findByRole('button',{name:'boards'}));expect(await screen.findByText('No boards configured')).toBeInTheDocument()})
  it('confirms enable or disable before sending the mutation',async()=>{const fetchMock=fixtureFetch({boards:[board]});globalThis.fetch=fetchMock;renderPage();await userEvent.click(await screen.findByRole('button',{name:'boards'}));await userEvent.click(screen.getByRole('button',{name:'Disable'}));expect(screen.getByRole('dialog')).toHaveTextContent('Disable Stripe?');await userEvent.click(screen.getByRole('button',{name:'Confirm'}));await waitFor(()=>expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/boards/board-1/enabled'),expect.objectContaining({method:'PATCH',headers:expect.objectContaining({'X-CSRF-TOKEN':'test-token'})})))})
  it('requires confirmation before manual ingestion',async()=>{globalThis.fetch=fixtureFetch({boards:[board]});renderPage();await userEvent.click(await screen.findByRole('button',{name:'boards'}));await userEvent.click(screen.getByRole('button',{name:'Run now'}));expect(screen.getByRole('dialog')).toHaveTextContent('Run ingestion for Stripe?')})
  it('confirms failed-run retry and explains immutable history',async()=>{globalThis.fetch=fixtureFetch({runs:[run(1,'FAILED')]});renderPage();await userEvent.click(await screen.findByRole('button',{name:'runs'}));await userEvent.click(screen.getByRole('button',{name:'Retry'}));expect(screen.getByRole('dialog')).toHaveTextContent('original run remains unchanged')})
  it('supports run filtering and pagination',async()=>{globalThis.fetch=fixtureFetch({runs:Array.from({length:11},(_,i)=>run(i,i===10?'FAILED':'SUCCEEDED'))});renderPage();await userEvent.click(await screen.findByRole('button',{name:'runs'}));expect(screen.getByText('Page 1 of 2')).toBeInTheDocument();await userEvent.click(screen.getByRole('button',{name:'Next'}));expect(screen.getByText('Page 2 of 2')).toBeInTheDocument();fireEvent.change(screen.getByDisplayValue('All statuses'),{target:{value:'FAILED'}});expect(screen.getByText('Page 1 of 1')).toBeInTheDocument();expect(screen.getByText('Provider failed')).toBeInTheDocument()})
})
