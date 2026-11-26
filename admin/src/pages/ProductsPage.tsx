import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { adminApi, fmtDate, fmtPrice, type CanonicalProduct } from '../api';
import { Card, ErrorBox, Pager } from '../components';

function EditProduct({ id, onClose }: { id: number; onClose: () => void }) {
  const qc = useQueryClient();
  const detail = useQuery({ queryKey: ['canonical', id], queryFn: () => adminApi.canonicalDetail(id) });
  const categories = useQuery({ queryKey: ['categories'], queryFn: adminApi.categories });
  const [form, setForm] = useState<Partial<CanonicalProduct>>({});
  useEffect(() => {
    if (detail.data) setForm(detail.data.product);
  }, [detail.data]);
  const save = useMutation({
    mutationFn: () => adminApi.updateCanonical(id, form),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['canonical'] });
      qc.invalidateQueries({ queryKey: ['canonicalList'] });
    },
  });
  const field = (key: keyof CanonicalProduct, label: string, type = 'text') => (
    <label>
      {label}
      <input
        type={type}
        value={(form[key] as string | number | null | undefined) ?? ''}
        onChange={(e) => setForm({ ...form, [key]: type === 'number' ? (e.target.value === '' ? null : Number(e.target.value)) : e.target.value })}
      />
    </label>
  );
  return (
    <Card title={`Ընդհանուր ապրանք #${id}`} actions={<button onClick={onClose}>Փակել</button>}>
      <ErrorBox error={detail.error ?? save.error} />
      <div className="form-grid">
        {field('name', 'Անուն (հայ.)')}
        {field('nameEn', 'Անուն (անգլ.)')}
        {field('brand', 'Բրենդ')}
        {field('barcode', 'Բարկոդ')}
        {field('sizeValue', 'Չափ (մլ / գ / հատ)', 'number')}
        <label>
          Չափի միավոր
          <select value={form.sizeUnit ?? ''} onChange={(e) => setForm({ ...form, sizeUnit: e.target.value || null })}>
            <option value="">—</option><option value="ml">մլ</option><option value="g">գ</option><option value="pcs">հատ</option>
          </select>
        </label>
        {field('variant', 'Տարբերակ (օր.՝ 3.2%)')}
        <label>
          Կատեգորիա
          <select value={form.categoryId ?? ''} onChange={(e) => setForm({ ...form, categoryId: e.target.value ? Number(e.target.value) : null })}>
            <option value="">—</option>
            {categories.data?.map((c) => <option key={c.id} value={c.id}>{c.name} / {c.nameEn}</option>)}
          </select>
        </label>
        {field('imageUrl', 'Նկարի URL')}
        <label className="checkbox">
          <input type="checkbox" checked={form.active ?? true} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Ակտիվ
        </label>
      </div>
      <button className="primary" disabled={save.isPending} onClick={() => save.mutate()}>Պահպանել</button>
      {save.isSuccess && <span className="ok"> Պահպանված է</span>}
      <h3>Խանութների ապրանքներ</h3>
      <table>
        <thead><tr><th>Խանութ</th><th>Խանութի անվանում</th><th>Բարկոդ</th><th>Գին</th><th>Գրանցվել է</th></tr></thead>
        <tbody>
          {detail.data?.storeProducts.map((sp) => (
            <tr key={sp.id}>
              <td>{sp.storeName}</td>
              <td>{sp.externalUrl ? <a href={sp.externalUrl} target="_blank" rel="noreferrer">{sp.externalName}</a> : sp.externalName}
                {sp.externalNameEn && <div className="muted small">{sp.externalNameEn}</div>}</td>
              <td>{sp.barcode ?? '—'}</td>
              <td>{fmtPrice(sp.currentPrice)}</td>
              <td className="small">{fmtDate(sp.capturedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  );
}

export function ProductsPage() {
  const [q, setQ] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<number | null>(null);
  const list = useQuery({ queryKey: ['canonicalList', query, page], queryFn: () => adminApi.canonical(query, page) });
  return (
    <>
      <h1>Ընդհանուր ապրանքներ</h1>
      <form className="toolbar" onSubmit={(e) => { e.preventDefault(); setPage(0); setQuery(q); }}>
        <input placeholder="Որոնել անունով կամ բարկոդով" value={q} onChange={(e) => setQ(e.target.value)} />
        <button type="submit">Որոնել</button>
      </form>
      <ErrorBox error={list.error} />
      {editing !== null && <EditProduct id={editing} onClose={() => setEditing(null)} />}
      <Card>
        <table>
          <thead><tr><th>#</th><th>Անուն</th><th>Բրենդ</th><th>Չափ</th><th>Բարկոդ</th><th>Խանութներ</th><th>Ակտիվ</th><th></th></tr></thead>
          <tbody>
            {list.data?.items.map((p) => (
              <tr key={p.id}>
                <td>{p.id}</td>
                <td>{p.name}{p.nameEn && <div className="muted small">{p.nameEn}</div>}</td>
                <td>{p.brand ?? '—'}</td>
                <td>{p.soldByWeight ? '1 կգ-ի գին' : p.sizeValue ? `${p.sizeValue} ${p.sizeUnit}` : '—'}{p.variant ? ` · ${p.variant}` : ''}</td>
                <td>{p.barcode ?? '—'}</td>
                <td>{p.storeProductCount}</td>
                <td>{p.active ? 'այո' : 'ոչ'}</td>
                <td><button onClick={() => setEditing(p.id)}>Խմբագրել</button></td>
              </tr>
            ))}
          </tbody>
        </table>
        {list.data && <Pager page={page} size={30} total={list.data.total} onPage={setPage} />}
      </Card>
    </>
  );
}
