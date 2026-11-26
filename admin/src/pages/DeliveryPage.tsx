import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { adminApi, type StoreDelivery } from '../api';
import { Card, ErrorBox } from '../components';

const FIELDS = [
  ['fee', 'Delivery fee'],
  ['freeFrom', 'Free delivery from'],
  ['minOrder', 'Minimum order'],
  ['smallOrderBelow', 'Small order below'],
  ['smallOrderFee', 'Small order fee'],
] as const;
type Field = (typeof FIELDS)[number][0];

function StoreForm({ store }: { store: StoreDelivery }) {
  const qc = useQueryClient();
  const d = store.delivery;
  const [values, setValues] = useState<Record<Field, string>>(() => ({
    fee: d?.fee?.toString() ?? '', freeFrom: d?.freeFrom?.toString() ?? '', minOrder: d?.minOrder?.toString() ?? '',
    smallOrderBelow: d?.smallOrderBelow?.toString() ?? '', smallOrderFee: d?.smallOrderFee?.toString() ?? '',
  }));
  const [source, setSource] = useState(d?.source ?? '');
  const num = (v: string) => (v.trim() === '' ? null : Number(v));
  const save = useMutation({
    mutationFn: () => adminApi.setStoreDelivery(store.id, {
      fee: num(values.fee), freeFrom: num(values.freeFrom), minOrder: num(values.minOrder),
      smallOrderBelow: num(values.smallOrderBelow), smallOrderFee: num(values.smallOrderFee), source: source.trim() || null,
    }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['storeDelivery'] }),
  });
  return (
    <Card title={store.name}>
      <div className="small muted">
        {d?.checkedAt ? `Checked ${new Date(d.checkedAt).toLocaleDateString()}` : 'Unknown – never assumed in basket totals'}
        {' · '}<a href={store.websiteUrl} target="_blank" rel="noreferrer">{store.websiteUrl}</a>
      </div>
      <div className="delivery-grid">
        {FIELDS.map(([k, label]) => (
          <label key={k}>
            <span className="small">{label}, ֏</span>
            <input type="number" min={0} value={values[k]} onChange={(e) => setValues({ ...values, [k]: e.target.value })} />
          </label>
        ))}
      </div>
      <label className="block">
        <span className="small">Source (page URL and what it says) – required when any amount is set</span>
        <input value={source} onChange={(e) => setSource(e.target.value)} style={{ width: '100%' }} />
      </label>
      <ErrorBox error={save.error} />
      <div className="actions">
        <button className="primary" disabled={save.isPending} onClick={() => save.mutate()}>Save</button>
        {save.isSuccess && <span className="small muted">Saved</span>}
      </div>
    </Card>
  );
}

export function DeliveryPage() {
  const stores = useQuery({ queryKey: ['storeDelivery'], queryFn: adminApi.storeDelivery });
  return (
    <>
      <h1>Delivery terms</h1>
      <p className="muted">
        Enter only what the store publishes, with the page it came from. Empty = unknown: unknown fees are never assumed
        and the app says so. "Small order" is a tier where orders below an amount pay a different fee.
      </p>
      <ErrorBox error={stores.error} />
      {stores.data?.map((s) => <StoreForm key={s.id} store={s} />)}
    </>
  );
}
