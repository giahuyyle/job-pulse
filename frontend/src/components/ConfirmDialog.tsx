export interface Confirmation {
  title:string
  body:string
  confirmLabel?:string
}

export default function ConfirmDialog({confirmation,busy,onConfirm,onCancel}:{confirmation:Confirmation;busy:boolean;onConfirm:()=>void;onCancel:()=>void}) {
  return <div role="dialog" aria-modal="true" aria-labelledby="confirm-title" className="fixed inset-0 z-50 grid place-items-center bg-ink/45 p-5 backdrop-blur-sm">
    <div className="panel w-full max-w-md p-6">
      <h2 id="confirm-title" className="font-display text-xl font-extrabold">{confirmation.title}</h2>
      <p className="mt-3 text-sm leading-6 text-black/55">{confirmation.body}</p>
      <div className="mt-6 flex justify-end gap-2">
        <button className="btn-secondary" disabled={busy} onClick={onCancel}>Cancel</button>
        <button className="btn-primary" disabled={busy} onClick={onConfirm}>{busy?'Working…':confirmation.confirmLabel||'Confirm'}</button>
      </div>
    </div>
  </div>
}
