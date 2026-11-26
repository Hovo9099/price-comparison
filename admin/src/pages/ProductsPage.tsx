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
    <Card title={`Canonical product #${id}`} actions={<button onClick={onClose}>Close</button>}>
      <ErrorBox error={detail.error ?? save.error} />
      <div className="form-grid">
        {field('name', 'Name (hy)')}
        {field('nameEn', 'Name (en)')}
        {field('brand', 'Brand')}
        {field('barcode', 'Barcode')}
        {field('sizeValue', 'Size value (ml / g / pcs)', 'number')}
        <label>
          Size unit
          <select value={form.sizeUnit ?? ''} onChange={(e) => setForm({ ...form, sizeUnit: e.target.value || null })}>
            <option value="">—</option><option value="ml">ml</option><option value="g">g</option><option value="pcs">pcs</option>
          </select>
        </label>
        {field('variant', 'Variant (e.g. 3.2%)')}
        <label>
          Category
          <select value={form.categoryId ?? ''} onChange={(e) => setForm({ ...form, categoryId: e.target.value ? Number(e.target.value) : null })}>
            <option value="">—</option>
            {categories.data?.map((c) => <option key={c.id} value={c.id}>{c.name} / {c.nameEn}</option>)}
          </select>
        </label>
        {field('imageUrl', 'Image URL')}
        <label className="checkbox">
          <input type="checkbox" checked={form.active ?? true} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Active
        </label>
      </div>
      <button className="primary" disabled={save.isPending} onClick={() => save.mutate()}>Save</button>
      {save.isSuccess && <span className="ok"> Saved</span>}
      <h3>Store products</h3>
      <table>
        <thead><tr><th>Store</th><th>External name</th><th>Barcode</th><th>Price</th><th>Captured</th></tr></thead>
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
      <h1>Canonical products</h1>
      <form className="toolbar" onSubmit={(e) => { e.preventDefault(); setPage(0); setQuery(q); }}>
        <input placeholder="Search name or barcode" value={q} onChange={(e) => setQ(e.target.value)} />
        <button type="submit">Search</button>
      </form>
      <ErrorBox error={list.error} />
      {editing !== null && <EditProduct id={editing} onClose={() => setEditing(null)} />}
      <Card>
        <table>
          <thead><tr><th>#</th><th>Name</th><th>Brand</th><th>Size</th><th>Barcode</th><th>Stores</th><th>Active</th><th></th></tr></thead>
          <tbody>
            {list.data?.items.map((p) => (
              <tr key={p.id}>
                <td>{p.id}</td>
                <td>{p.name}{p.nameEn && <div className="muted small">{p.nameEn}</div>}</td>
                <td>{p.brand ?? '—'}</td>
                <td>{p.soldByWeight ? 'per kg' : p.sizeValue ? `${p.sizeValue} ${p.sizeUnit}` : '—'}{p.variant ? ` · ${p.variant}` : ''}</td>
                <td>{p.barcode ?? '—'}</td>
                <td>{p.storeProductCount}</td>
                <td>{p.active ? 'yes' : 'no'}</td>
                <td><button onClick={() => setEditing(p.id)}>Edit</button></td>
              </tr>
            ))}
          </tbody>
        </table>
        {list.data && <Pager page={page} size={30} total={list.data.total} onPage={setPage} />}
      </Card>
    </>
  );
}
