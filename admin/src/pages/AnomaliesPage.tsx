import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { adminApi, fmtPrice, type AnomalyStatus, type PriceAnomaly } from '../api';
import { Card, ErrorBox, Pager } from '../components';

const KIND: Record<PriceAnomaly['kind'], string> = {
  SUDDEN_DROP: 'Sudden drop vs. own last price',
  SUDDEN_RISE: 'Sudden rise vs. own last price',
  BELOW_MARKET: 'Far below other stores (median)',
  ABOVE_MARKET: 'Far above other stores (median)',
};

function Row({ a }: { a: PriceAnomaly }) {
  const qc = useQueryClient();
  const done = () => qc.invalidateQueries({ queryKey: ['anomalies'] });
  const confirm = useMutation({ mutationFn: () => adminApi.confirmAnomaly(a.id), onSuccess: done });
  const reject = useMutation({ mutationFn: () => adminApi.rejectAnomaly(a.id), onSuccess: done });
  const ratio = a.referencePrice ? Math.round((a.newPrice / a.referencePrice) * 100) : null;
  return (
    <tr>
      <td>
        <div>{a.productName}</div>
        <div className="small muted">
          {a.storeName} · store product #{a.storeProductId}
          {a.productUrl && <> · <a href={a.productUrl} target="_blank" rel="noreferrer">open in store</a></>}
        </div>
      </td>
      <td>{KIND[a.kind]}</td>
      <td><strong>{fmtPrice(a.newPrice)}</strong> <span className="muted">vs {fmtPrice(a.referencePrice)}{ratio !== null && ` (${ratio}%)`}</span></td>
      <td className="small">{new Date(a.detectedAt).toLocaleString()}</td>
      <td>
        {a.status === 'PENDING' ? (
          <div className="actions">
            <button className="primary" disabled={confirm.isPending} onClick={() => confirm.mutate()} title="The price is real">Price is real</button>
            <button className="danger-btn" disabled={reject.isPending} onClick={() => reject.mutate()} title="Keep it excluded">Wrong price</button>
          </div>
        ) : (
          <span className="muted">{a.status}</span>
        )}
        <ErrorBox error={confirm.error ?? reject.error} />
      </td>
    </tr>
  );
}

export function AnomaliesPage() {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<AnomalyStatus>('PENDING');
  const list = useQuery({ queryKey: ['anomalies', status, page], queryFn: () => adminApi.anomalies(status, page) });
  return (
    <>
      <h1>Price anomalies</h1>
      <p className="muted">
        Prices that changed implausibly (≤ 40 % or ≥ 2.5 × the listing's last trusted price) or sit far from other stores
        (≤ 25 % or ≥ 4 × their median). They are stored as observed but never used as the cheapest price, in baskets,
        deals or notifications until you confirm them. A newer price from the store supersedes a pending one.
      </p>
      <div className="toolbar">
        <select value={status} onChange={(e) => { setPage(0); setStatus(e.target.value as AnomalyStatus); }}>
          {(['PENDING', 'CONFIRMED', 'REJECTED', 'SUPERSEDED'] as const).map((s) => <option key={s}>{s}</option>)}
        </select>
      </div>
      <ErrorBox error={list.error} />
      <Card>
        {list.data?.items.length === 0 && <p className="muted">Nothing here.</p>}
        {!!list.data?.items.length && (
          <table>
            <thead><tr><th>Product</th><th>Why</th><th>Price</th><th>Detected</th><th /></tr></thead>
            <tbody>{list.data.items.map((a) => <Row key={a.id} a={a} />)}</tbody>
          </table>
        )}
        {list.data && <Pager page={page} size={50} total={list.data.total} onPage={setPage} />}
      </Card>
    </>
  );
}
