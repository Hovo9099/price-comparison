import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { adminApi, fmtDate, fmtPrice, type AnomalyStatus, type PriceAnomaly } from '../api';
import { Card, ErrorBox, Pager } from '../components';
import { statusLabel } from '../labels';

const KIND: Record<PriceAnomaly['kind'], string> = {
  SUDDEN_DROP: 'Կտրուկ անկում՝ իր նախորդ գնի համեմատ',
  SUDDEN_RISE: 'Կտրուկ աճ՝ իր նախորդ գնի համեմատ',
  BELOW_MARKET: 'Շատ ցածր այլ խանութներից (մեդիան)',
  ABOVE_MARKET: 'Շատ բարձր այլ խանութներից (մեդիան)',
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
          {a.storeName} · խանութի ապրանք #{a.storeProductId}
          {a.productUrl && <> · <a href={a.productUrl} target="_blank" rel="noreferrer">բացել խանութում</a></>}
        </div>
      </td>
      <td>{KIND[a.kind]}</td>
      <td><strong>{fmtPrice(a.newPrice)}</strong> <span className="muted">համեմատ՝ {fmtPrice(a.referencePrice)}{ratio !== null && ` (${ratio}%)`}</span></td>
      <td className="small">{fmtDate(a.detectedAt)}</td>
      <td>
        {a.status === 'PENDING' ? (
          <div className="actions">
            <button className="primary" disabled={confirm.isPending} onClick={() => confirm.mutate()} title="Գինն իրական է">Գինն իրական է</button>
            <button className="danger-btn" disabled={reject.isPending} onClick={() => reject.mutate()} title="Շարունակել բացառել">Սխալ գին</button>
          </div>
        ) : (
          <span className="muted">{statusLabel(a.status)}</span>
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
      <h1>Կասկածելի գներ</h1>
      <p className="muted">
        Գներ, որոնք անհավանական են փոխվել (ապրանքի վերջին վստահելի գնի ≤ 40%-ը կամ ≥ 2.5 անգամը) կամ շատ են տարբերվում այլ
        խանութներից (նրանց մեդիանի ≤ 25%-ը կամ ≥ 4 անգամը)։ Դրանք պահվում են այնպես, ինչպես գրանցվել են, բայց չեն
        օգտագործվում որպես ամենաէժան գին, զամբյուղներում, զեղչերում կամ ծանուցումներում, քանի դեռ չեք հաստատել։ Խանութի
        ավելի նոր գինը փոխարինում է սպասողին։
      </p>
      <div className="toolbar">
        <select value={status} onChange={(e) => { setPage(0); setStatus(e.target.value as AnomalyStatus); }}>
          {(['PENDING', 'CONFIRMED', 'REJECTED', 'SUPERSEDED'] as const).map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
        </select>
      </div>
      <ErrorBox error={list.error} />
      <Card>
        {list.data?.items.length === 0 && <p className="muted">Այստեղ ոչինչ չկա։</p>}
        {!!list.data?.items.length && (
          <table>
            <thead><tr><th>Ապրանք</th><th>Պատճառ</th><th>Գին</th><th>Հայտնաբերվել է</th><th /></tr></thead>
            <tbody>{list.data.items.map((a) => <Row key={a.id} a={a} />)}</tbody>
          </table>
        )}
        {list.data && <Pager page={page} size={50} total={list.data.total} onPage={setPage} />}
      </Card>
    </>
  );
}
