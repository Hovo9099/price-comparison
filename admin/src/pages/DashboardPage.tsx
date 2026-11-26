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
      <h1>Քրոլերների վահանակ</h1>
      <ErrorBox error={dash.error ?? run.error ?? cancel.error} />
      {d && (
        <div className="stats">
          <div><b>{fmtNum(d.canonicalProducts)}</b>ընդհանուր ապրանք</div>
          <div><b>{fmtNum(d.storeProducts)}</b>խանութների ապրանք</div>
          <div><b>{fmtNum(d.currentPrices)}</b>ընթացիկ գին</div>
          <div><b>{fmtNum(d.pendingMatches)}</b>ստուգման սպասող համընկնում</div>
          <div><b>{fmtNum(d.unmatchedStoreProducts)}</b>չկապված</div>
        </div>
      )}
      <Card
        title="Խանութներ"
        actions={
          <label className="inline">
            Փորձնական սահմանաչափ&nbsp;
            <input type="number" min={1} value={devLimit} onChange={(e) => setDevLimit(Number(e.target.value))} style={{ width: 80 }} />
          </label>
        }
      >
        <table>
          <thead>
            <tr>
              <th>Խանութ</th><th>Քրոլեր</th><th>Կատալոգ</th><th>Վերջին քրոլինգ</th><th>Վերջին ամբողջական քրոլինգ</th>
              <th>Գտնված</th><th>Ներմուծված</th><th>Գնով</th><th>Սխալներ</th><th>Էջավորում</th><th>Գնի վերջին թարմացում</th><th></th>
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
                    {!s.implemented ? <StatusBadge status="NOT_IMPLEMENTED" /> : s.providerAvailable ? <span>միացված</span> : <span className="danger">անջատված</span>}
                    {s.unavailableReason && <div className="muted small wrap">{s.unavailableReason}</div>}
                  </td>
                  <td>
                    <StatusBadge status={s.catalogState} />
                    {s.catalogMessage && s.catalogState !== 'NOT_IMPLEMENTED' && <div className="muted small wrap">{s.catalogMessage}</div>}
                  </td>
                  <td className="small">
                    {s.running ? <StatusBadge status="RUNNING" /> : r ? <StatusBadge status={r.status} /> : <span className="muted">երբեք</span>}
                    {r && <div>{fmtDate(r.startedAt)} → {fmtDate(r.finishedAt)}</div>}
                    {r && <div className="muted">{full ? 'ամբողջ կատալոգ' : `սահմանաչափ՝ ${r.maxProducts}`}{r.errorMessage ? ` · ${r.errorMessage}` : ''}</div>}
                  </td>
                  <td className="small">{s.lastCompleteRun ? fmtDate(s.lastCompleteRun.finishedAt) : <span className="muted">չկա</span>}</td>
                  <td>{fmtNum(r?.productsDiscovered)}</td>
                  <td>{fmtNum(s.products)} <div className="muted small">{fmtNum(s.activeProducts)} ակտիվ</div></td>
                  <td>{fmtNum(s.pricedListings)}</td>
                  <td>
                    {r?.errors ? <span className="danger">{fmtNum(r.errors)}</span> : fmtNum(r?.errors)}
                  </td>
                  <td className="small">
                    {!r ? '—' : r.status === 'RUNNING' ? <span>ընթացքում · {fmtNum(r.pagesVisited)} էջ</span>
                      : !full ? <span className="muted">սահմանափակ գործարկում</span>
                      : r.status === 'SUCCESS' || r.status === 'PARTIAL'
                        ? (r.errorMessage ? <span className="danger">անավարտ</span> : <span>ավարտված · {fmtNum(r.pagesVisited)} էջ</span>)
                        : <span className="danger">չի հասել</span>}
                  </td>
                  <td className="small">{fmtDate(s.lastPriceUpdate)}</td>
                  <td className="actions">
                    {s.running && r ? (
                      <button onClick={() => cancel.mutate(r.id)}>Չեղարկել</button>
                    ) : !s.implemented ? (
                      <button disabled={run.isPending} onClick={() => run.mutate({ slug: s.slug, max: devLimit })} title="Կրկին ստուգում է՝ արդյոք կայքը կատալոգ ունի">
                        Ստուգել կայքը
                      </button>
                    ) : (
                      <>
                        <button disabled={!s.providerAvailable || run.isPending} onClick={() => run.mutate({ slug: s.slug, max: devLimit })}>
                          Գործարկել ({devLimit})
                        </button>
                        <button
                          className="primary"
                          disabled={!s.providerAvailable || run.isPending}
                          onClick={() => confirm(`Գործարկե՞լ ${s.name}-ի ամբողջական ներմուծումը։ Այն կարող է տևել մի քանի ժամ։`) && run.mutate({ slug: s.slug, max: 0 })}
                        >
                          Ամբողջական ներմուծում
                        </button>
                      </>
                    )}
                    {r && (
                      <button disabled={!r.errors} onClick={() => setErrorsOf(errorsOf === r.id ? null : r.id)}>
                        {errorsOf === r.id ? 'Թաքցնել սխալները' : 'Սխալներ'}
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
