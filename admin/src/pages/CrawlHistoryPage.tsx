import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { adminApi, fmtDate, fmtNum } from '../api';
import { Card, ErrorBox, Pager, StatusBadge } from '../components';

export function RunErrors({ runId }: { runId: number }) {
  const [page, setPage] = useState(0);
  const errors = useQuery({ queryKey: ['runErrors', runId, page], queryFn: () => adminApi.runErrors(runId, page) });
  return (
    <Card title={`Errors of run #${runId}`}>
      <ErrorBox error={errors.error} />
      {errors.data && errors.data.total === 0 && <p className="muted">No errors.</p>}
      {errors.data && errors.data.total > 0 && (
        <>
          <table>
            <thead><tr><th>Time</th><th>External id</th><th>URL</th><th>Message</th></tr></thead>
            <tbody>
              {errors.data.items.map((e) => (
                <tr key={e.id}>
                  <td className="small">{fmtDate(e.createdAt)}</td>
                  <td>{e.externalId ?? '—'}</td>
                  <td className="small wrap">{e.url?.startsWith('http') ? <a href={e.url} target="_blank" rel="noreferrer">{e.url}</a> : e.url}</td>
                  <td className="wrap">{e.message}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={page} size={50} total={errors.data.total} onPage={setPage} />
        </>
      )}
    </Card>
  );
}

export function CrawlHistoryPage() {
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<number | null>(null);
  const runs = useQuery({ queryKey: ['runs', page], queryFn: () => adminApi.runs(undefined, page), refetchInterval: 10000 });
  return (
    <>
      <h1>Crawl history</h1>
      <ErrorBox error={runs.error} />
      <Card>
        <table>
          <thead>
            <tr><th>#</th><th>Store</th><th>Type</th><th>Status</th><th>Started</th><th>Finished</th><th>Limit</th><th>Pages</th><th>Products</th><th>Prices changed</th><th>Errors</th><th></th></tr>
          </thead>
          <tbody>
            {runs.data?.items.map((r) => (
              <tr key={r.id} className={selected === r.id ? 'selected' : ''}>
                <td>{r.id}</td>
                <td>{r.storeName}</td>
                <td>{r.runType}</td>
                <td><StatusBadge status={r.status} />{r.errorMessage && <div className="small muted wrap">{r.errorMessage}</div>}</td>
                <td className="small">{fmtDate(r.startedAt)}</td>
                <td className="small">{fmtDate(r.finishedAt)}</td>
                <td>{r.maxProducts ? r.maxProducts : 'full'}</td>
                <td>{fmtNum(r.pagesVisited)}</td>
                <td>{fmtNum(r.productsDiscovered)}</td>
                <td>{fmtNum(r.pricesUpdated)}</td>
                <td>{fmtNum(r.errors)}</td>
                <td><button onClick={() => setSelected(r.id)}>Errors</button></td>
              </tr>
            ))}
          </tbody>
        </table>
        {runs.data && <Pager page={page} size={30} total={runs.data.total} onPage={setPage} />}
      </Card>
      {selected !== null && <RunErrors runId={selected} />}
    </>
  );
}
