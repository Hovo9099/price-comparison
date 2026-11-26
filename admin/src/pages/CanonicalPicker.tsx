import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { adminApi } from '../api';

/** Search canonical products and pick one (used for manual mapping). */
export function CanonicalPicker({ initialQuery, onPick, onCancel }: { initialQuery: string; onPick: (id: number) => void; onCancel: () => void }) {
  const [q, setQ] = useState(initialQuery);
  const [query, setQuery] = useState(initialQuery);
  const results = useQuery({ queryKey: ['picker', query], queryFn: () => adminApi.canonical(query, 0), enabled: query.length > 1 });
  return (
    <div className="picker">
      <form className="toolbar" onSubmit={(e) => { e.preventDefault(); setQuery(q); }}>
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search canonical product" autoFocus />
        <button type="submit">Search</button>
        <button type="button" onClick={onCancel}>Cancel</button>
      </form>
      <ul>
        {results.data?.items.map((c) => (
          <li key={c.id}>
            <button onClick={() => onPick(c.id)}>Map to #{c.id}</button> {c.name}
            {c.nameEn && <span className="muted"> · {c.nameEn}</span>}
            <span className="muted"> · {c.soldByWeight ? 'per kg' : c.sizeValue ? `${c.sizeValue}${c.sizeUnit}` : ''} · {c.storeProductCount} store(s)</span>
          </li>
        ))}
        {results.data?.items.length === 0 && <li className="muted">No results</li>}
      </ul>
    </div>
  );
}
