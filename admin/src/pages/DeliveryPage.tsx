import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { adminApi, fmtDay, type StoreDelivery } from '../api';
import { Card, ErrorBox } from '../components';

const FIELDS = [
  ['fee', 'Առաքման վճար'],
  ['freeFrom', 'Անվճար առաքում՝ սկսած'],
  ['minOrder', 'Նվազագույն պատվեր'],
  ['smallOrderBelow', 'Փոքր պատվեր՝ մինչև'],
  ['smallOrderFee', 'Փոքր պատվերի վճար'],
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
        {d?.checkedAt ? `Ստուգվել է ${fmtDay(d.checkedAt)}` : 'Անհայտ է․ զամբյուղի հաշվարկում երբեք չի ենթադրվում'}
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
        <span className="small">Աղբյուր (էջի URL և ինչ է գրված այնտեղ)․ պարտադիր է, եթե որևէ գումար նշված է</span>
        <input value={source} onChange={(e) => setSource(e.target.value)} style={{ width: '100%' }} />
      </label>
      <ErrorBox error={save.error} />
      <div className="actions">
        <button className="primary" disabled={save.isPending} onClick={() => save.mutate()}>Պահպանել</button>
        {save.isSuccess && <span className="small muted">Պահպանված է</span>}
      </div>
    </Card>
  );
}

export function DeliveryPage() {
  const stores = useQuery({ queryKey: ['storeDelivery'], queryFn: adminApi.storeDelivery });
  return (
    <>
      <h1>Առաքման պայմաններ</h1>
      <p className="muted">
        Մուտքագրեք միայն այն, ինչ հրապարակում է խանութը, նշելով էջը։ Դատարկ = անհայտ․ անհայտ վճարները երբեք չեն ենթադրվում,
        և հավելվածը դա նշում է։ «Փոքր պատվեր»՝ որոշակի գումարից պակաս պատվերների համար այլ վճար։
      </p>
      <ErrorBox error={stores.error} />
      {stores.data?.map((s) => <StoreForm key={s.id} store={s} />)}
    </>
  );
}
