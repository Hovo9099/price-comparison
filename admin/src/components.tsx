import type { ReactNode } from 'react';
import { ApiError, type CrawlStatus } from './api';

export function StatusBadge({ status }: { status: CrawlStatus | string }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{status}</span>;
}

export function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null;
  const msg = error instanceof ApiError && error.status === 401
    ? 'Unauthorized: set the admin token in Settings (or run the backend locally without ADMIN_TOKEN).'
    : error instanceof Error ? error.message : String(error);
  return <div className="error">{msg}</div>;
}

export function Pager({ page, size, total, onPage }: { page: number; size: number; total: number; onPage: (p: number) => void }) {
  const pages = Math.max(1, Math.ceil(total / size));
  return (
    <div className="pager">
      <button disabled={page === 0} onClick={() => onPage(page - 1)}>‹ Prev</button>
      <span>Page {page + 1} / {pages} · {total} total</span>
      <button disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>Next ›</button>
    </div>
  );
}

export function Card({ title, children, actions }: { title?: string; children: ReactNode; actions?: ReactNode }) {
  return (
    <section className="card">
      {(title || actions) && (
        <div className="card-header">
          {title && <h2>{title}</h2>}
          <div>{actions}</div>
        </div>
      )}
      {children}
    </section>
  );
}
