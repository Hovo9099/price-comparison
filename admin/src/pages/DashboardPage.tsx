import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { adminApi, fmtDate, fmtNum } from '../api';
import { Card, ErrorBox, StatusBadge } from '../components';
import { RunErrors } from './CrawlHistoryPage';

export function DashboardPage() {
  const qc = useQueryClient();
  const [devLimit, setDevLimit] = useState(100);
  const [errorsOf, setErrorsOf] = useState<number | null>(null);
  const dash = useQuery({
    queryKey: ['dashboard'],
    queryFn: adminApi.dashboard,
    refetchInterval: (q) => (q.state.data?.stores.some((s) => s.running) ? 5000 : 30000),
  });
  const run = useMutation({
    mutationFn: ({ slug, max }: { slug: string; max?: number }) => adminApi.runCrawler(slug, max),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['dashboard'] }),
  });
  const cancel = useMutation({
    mutationFn: (id: number) => adminApi.cancelRun(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['dashboard'] }),
  });

  const d = dash.data;
  return (
    <>
      <h1>Crawler dashboard</h1>
      <ErrorBox error={dash.error ?? run.error ?? cancel.error} />
      {d && (
        <div className="stats">
          <div><b>{fmtNum(d.canonicalProducts)}</b>canonical products</div>
          <div><b>{fmtNum(d.storeProducts)}</b>store products</div>
          <div><b>{fmtNum(d.currentPrices)}</b>current prices</div>
          <div><b>{fmtNum(d.pendingMatches)}</b>matches to review</div>
          <div><b>{fmtNum(d.unmatchedStoreProducts)}</b>unmatched</div>
        </div>
      )}
      <Card
        title="Stores"
        actions={
          <label className="inline">
            Dev limit&nbsp;
            <input type="number" min={1} value={devLimit} onChange={(e) => setDevLimit(Number(e.target.value))} style={{ width: 80 }} />
          </label>
        }
      >
        <table>
          <thead>
            <tr>
              <th>Store</th><th>Provider</th><th>Catalog</th><th>Last crawl</th><th>Last complete crawl</th>
              <th>Discovered</th><th>Imported</th><th>Available</th><th>Errors</th><th>Pagination</th><th>Last price update</th><th></th>
            </tr>
          </thead>
          <tbody>
            {d?.stores.map((s) => {
              const r = s.lastRun;
              const full = r && (r.maxProducts === 0 || r.maxProducts === null);
              return (
                <tr key={s.storeId}>
                  <td>
                    <b>{s.name}</b>
                    <div className="muted"><a href={s.websiteUrl} target="_blank" rel="noreferrer">{s.websiteUrl}</a></div>
                  </td>
                  <td>
                    {!s.implemented ? <StatusBadge status="NOT_IMPLEMENTED" /> : s.providerAvailable ? <span>enabled</span> : <span className="danger">disabled</span>}
                    {s.unavailableReason && <div className="muted small wrap">{s.unavailableReason}</div>}
                  </td>
                  <td>
                    <StatusBadge status={s.catalogState} />
                    {s.catalogMessage && s.catalogState !== 'NOT_IMPLEMENTED' && <div className="muted small wrap">{s.catalogMessage}</div>}
                  </td>
                  <td className="small">
                    {s.running ? <StatusBadge status="RUNNING" /> : r ? <StatusBadge status={r.status} /> : <span className="muted">never</span>}
                    {r && <div>{fmtDate(r.startedAt)} → {fmtDate(r.finishedAt)}</div>}
                    {r && <div className="muted">{full ? 'full catalog' : `limit ${r.maxProducts}`}{r.errorMessage ? ` · ${r.errorMessage}` : ''}</div>}
                  </td>
                  <td className="small">{s.lastCompleteRun ? fmtDate(s.lastCompleteRun.finishedAt) : <span className="muted">none</span>}</td>
                  <td>{fmtNum(r?.productsDiscovered)}</td>
                  <td>{fmtNum(s.products)} <div className="muted small">{fmtNum(s.activeProducts)} active</div></td>
                  <td>{fmtNum(s.pricedListings)}</td>
                  <td>
                    {r?.errors ? <span className="danger">{fmtNum(r.errors)}</span> : fmtNum(r?.errors)}
                  </td>
                  <td className="small">
                    {!r ? '—' : r.status === 'RUNNING' ? <span>in progress · {fmtNum(r.pagesVisited)} pages</span>
                      : !full ? <span className="muted">limited run</span>
                      : r.status === 'SUCCESS' || r.status === 'PARTIAL'
                        ? (r.errorMessage ? <span className="danger">incomplete</span> : <span>complete · {fmtNum(r.pagesVisited)} pages</span>)
                        : <span className="danger">not reached</span>}
                  </td>
                  <td className="small">{fmtDate(s.lastPriceUpdate)}</td>
                  <td className="actions">
                    {s.running && r ? (
                      <button onClick={() => cancel.mutate(r.id)}>Cancel</button>
                    ) : !s.implemented ? (
                      <button disabled={run.isPending} onClick={() => run.mutate({ slug: s.slug, max: devLimit })} title="Re-checks whether the site serves a catalog">
                        Check site
                      </button>
                    ) : (
                      <>
                        <button disabled={!s.providerAvailable || run.isPending} onClick={() => run.mutate({ slug: s.slug, max: devLimit })}>
                          Run ({devLimit})
                        </button>
                        <button
                          className="primary"
                          disabled={!s.providerAvailable || run.isPending}
                          onClick={() => confirm(`Run a full catalog import for ${s.name}? This can take hours.`) && run.mutate({ slug: s.slug, max: 0 })}
                        >
                          Full import
                        </button>
                      </>
                    )}
                    {r && (
                      <button disabled={!r.errors} onClick={() => setErrorsOf(errorsOf === r.id ? null : r.id)}>
                        {errorsOf === r.id ? 'Hide errors' : 'Errors'}
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </Card>
      {errorsOf !== null && <RunErrors runId={errorsOf} />}
    </>
  );
}
