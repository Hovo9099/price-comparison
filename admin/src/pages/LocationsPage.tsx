import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { adminApi } from '../api';
import { Card, ErrorBox } from '../components';

export function LocationsPage() {
  const qc = useQueryClient();
  const stores = useQuery({ queryKey: ['storeDelivery'], queryFn: adminApi.storeDelivery });
  const [storeId, setStoreId] = useState<number | null>(null);
  const current = storeId ?? stores.data?.[0]?.id ?? null;
  const locations = useQuery({
    queryKey: ['locations', current],
    queryFn: () => adminApi.locations(current!),
    enabled: current !== null,
  });
  const [form, setForm] = useState({ name: '', address: '', latitude: '', longitude: '', source: '' });
  const done = () => qc.invalidateQueries({ queryKey: ['locations', current] });
  const add = useMutation({
    mutationFn: () => adminApi.addLocation(current!, {
      name: form.name, address: form.address || null, latitude: Number(form.latitude), longitude: Number(form.longitude),
      source: form.source,
    }),
    onSuccess: () => { setForm({ ...form, name: '', address: '', latitude: '', longitude: '' }); done(); },
  });
  const remove = useMutation({ mutationFn: (id: number) => adminApi.deactivateLocation(current!, id), onSuccess: done });

  return (
    <>
      <h1>Մասնաճյուղեր</h1>
      <p className="muted">
        Օգտագործվում է հավելվածի «Մոտակա խանութներ» բաժնում։ Քրոլ արված մասնաճյուղերը (Yerevan City-ն հրապարակում է իր
        ցուցակը) թարմացնում է քրոլերը, իսկ մյուս խանութների համար ավելացրեք մասնաճյուղերը խանութի հասցեների էջից։
        Կոորդինատներ՝ քարտեզում աջ սեղմեք վայրի վրա և պատճենեք «lat, lon»-ը։
      </p>
      <div className="toolbar">
        <select value={current ?? ''} onChange={(e) => setStoreId(Number(e.target.value))}>
          {stores.data?.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
      </div>
      <ErrorBox error={stores.error ?? locations.error} />
      <Card title="Ավելացնել մասնաճյուղ">
        <div className="delivery-grid">
          {([['name', 'Անվանում'], ['address', 'Հասցե'], ['latitude', 'Լայնություն'], ['longitude', 'Երկայնություն']] as const).map(([k, label]) => (
            <label key={k}>
              <span className="small">{label}</span>
              <input value={form[k]} onChange={(e) => setForm({ ...form, [k]: e.target.value })}
                onPaste={k === 'latitude' ? (e) => {
                  const m = e.clipboardData.getData('text').match(/(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)/);
                  if (m) { e.preventDefault(); setForm({ ...form, latitude: m[1], longitude: m[2] }); }
                } : undefined} />
            </label>
          ))}
        </div>
        <label className="block">
          <span className="small">Աղբյուր (խանութի էջը, որտեղ նշված է այս մասնաճյուղը)</span>
          <input value={form.source} onChange={(e) => setForm({ ...form, source: e.target.value })} />
        </label>
        <ErrorBox error={add.error} />
        <button className="primary" disabled={add.isPending || current === null} onClick={() => add.mutate()}>Ավելացնել</button>
      </Card>
      <Card title={`Մասնաճյուղեր (${locations.data?.filter((l) => l.active).length ?? 0} ակտիվ)`}>
        <table>
          <thead><tr><th>Անվանում</th><th>Հասցե</th><th>Կոորդինատներ</th><th>Աղբյուր</th><th /></tr></thead>
          <tbody>
            {locations.data?.map((l) => (
              <tr key={l.id} className={l.active ? '' : 'muted'}>
                <td>{l.name}</td>
                <td>{l.address}</td>
                <td className="small">{l.latitude != null ? `${l.latitude}, ${l.longitude}` : '—'}</td>
                <td className="small">{l.manual ? <span title={l.source ?? ''}>ձեռքով</span> : 'քրոլեր'}{!l.active && ' · ոչ ակտիվ'}</td>
                <td>{l.manual && l.active && <button className="danger-btn" onClick={() => remove.mutate(l.id)}>Հեռացնել</button>}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <ErrorBox error={remove.error} />
      </Card>
    </>
  );
}
