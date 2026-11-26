import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { adminApi, fmtDate, fmtPrice } from '../api';
import { Card, ErrorBox, Pager } from '../components';
import { CanonicalPicker } from './CanonicalPicker';

export function StoreProductsPage() {
  const qc = useQueryClient();
  const dash = useQuery({ queryKey: ['dashboard'], queryFn: adminApi.dashboard });
  const [storeId, setStoreId] = useState<number | undefined>();
  const [unmatched, setUnmatched] = useState(false);
  const [q, setQ] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [mapping, setMapping] = useState<{ id: number; name: string } | null>(null);
  const list = useQuery({
    queryKey: ['storeProducts', storeId, unmatched, query, page],
    queryFn: () => adminApi.storeProducts({ storeId, unmatched, q: query, page }),
  });
  const map = useMutation({
    mutationFn: ({ id, canonicalId }: { id: number; canonicalId: number }) => adminApi.mapStoreProduct(id, canonicalId),
    onSuccess: () => {
      setMapping(null);
      qc.invalidateQueries({ queryKey: ['storeProducts'] });
    },
  });
  return (
    <>
      <h1>Խանութների ապրանքներ և կապում</h1>
      <form className="toolbar" onSubmit={(e) => { e.preventDefault(); setPage(0); setQuery(q); }}>
        <select value={storeId ?? ''} onChange={(e) => { setPage(0); setStoreId(e.target.value ? Number(e.target.value) : undefined); }}>
          <option value="">Բոլոր խանութները</option>
          {dash.data?.stores.map((s) => <option key={s.storeId} value={s.storeId}>{s.name}</option>)}
        </select>
        <label className="checkbox"><input type="checkbox" checked={unmatched} onChange={(e) => { setPage(0); setUnmatched(e.target.checked); }} /> Միայն չկապվածները</label>
        <input placeholder="Որոնել անունով" value={q} onChange={(e) => setQ(e.target.value)} />
        <button type="submit">Որոնել</button>
      </form>
      <ErrorBox error={list.error ?? map.error} />
      {mapping && (
        <Card title={`Կապել՝ ${mapping.name}`}>
          <CanonicalPicker initialQuery={mapping.name.split(' ').slice(0, 3).join(' ')} onCancel={() => setMapping(null)}
            onPick={(canonicalId) => map.mutate({ id: mapping.id, canonicalId })} />
        </Card>
      )}
      <Card>
        <table>
          <thead><tr><th>Խանութ</th><th>Արտաքին id / SKU</th><th>Անուն</th><th>Բրենդ</th><th>Բարկոդ</th><th>Գին</th><th>Վերջին անգամ տեսնվել է</th><th>Ընդհանուր ապրանք</th><th></th></tr></thead>
          <tbody>
            {list.data?.items.map((sp) => (
              <tr key={sp.id} className={sp.active ? '' : 'inactive'}>
                <td>{sp.storeName}</td>
                <td className="small">{sp.externalProductId}{sp.externalSku && <div className="muted">SKU {sp.externalSku}</div>}</td>
                <td>{sp.externalUrl ? <a href={sp.externalUrl} target="_blank" rel="noreferrer">{sp.externalName}</a> : sp.externalName}
                  {sp.externalNameEn && <div className="muted small">{sp.externalNameEn}</div>}</td>
                <td>{sp.brand ?? '—'}</td>
                <td className="small">{sp.barcode ?? '—'}</td>
                <td>{fmtPrice(sp.currentPrice)}</td>
                <td className="small">{fmtDate(sp.lastSeenAt)}</td>
                <td className="small">{sp.canonicalProductId ? `#${sp.canonicalProductId}` : <span className="danger">չկա</span>}</td>
                <td><button onClick={() => setMapping({ id: sp.id, name: sp.externalNameEn ?? sp.externalName })}>Կապել…</button></td>
              </tr>
            ))}
          </tbody>
        </table>
        {list.data && <Pager page={page} size={30} total={list.data.total} onPage={setPage} />}
      </Card>
    </>
  );
}
