import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { adminApi, fmtPrice, type MatchCandidate } from '../api';
import { Card, ErrorBox, Pager } from '../components';
import { CanonicalPicker } from './CanonicalPicker';

function CandidateCard({ c }: { c: MatchCandidate }) {
  const qc = useQueryClient();
  const [searching, setSearching] = useState(false);
  const done = () => qc.invalidateQueries({ queryKey: ['candidates'] });
  const approve = useMutation({ mutationFn: () => adminApi.approve(c.id), onSuccess: done });
  const reject = useMutation({ mutationFn: () => adminApi.reject(c.id), onSuccess: done });
  const map = useMutation({
    mutationFn: async (canonicalId: number) => {
      await adminApi.mapStoreProduct(c.storeProduct.id, canonicalId);
      await adminApi.reject(c.id);
    },
    onSuccess: done,
  });
  const sp = c.storeProduct;
  const cand = c.candidate;
  return (
    <div className="candidate">
      <div className="candidate-cols">
        <div>
          <div className="label">Store product · {sp.storeName}</div>
          <div className="name">{sp.externalName}</div>
          {sp.externalNameEn && <div className="muted">{sp.externalNameEn}</div>}
          <div className="small muted">
            {sp.brand ?? 'no brand'} · {sp.packageSize ? `${sp.packageSize} ${sp.packageUnit}` : 'no size'} · {sp.barcode ?? 'no barcode'} · {fmtPrice(sp.currentPrice)}
          </div>
          {sp.externalUrl && <a className="small" href={sp.externalUrl} target="_blank" rel="noreferrer">open in store</a>}
        </div>
        <div className="confidence">
          <div className="pct">{Math.round(c.confidence * 100)}%</div>
          <div className="small muted">{c.reason}</div>
        </div>
        <div>
          <div className="label">Candidate canonical #{cand.id}</div>
          <div className="name">{cand.name}</div>
          {cand.nameEn && <div className="muted">{cand.nameEn}</div>}
          <div className="small muted">
            {cand.brand ?? 'no brand'} · {cand.sizeValue ? `${cand.sizeValue} ${cand.sizeUnit}` : 'no size'} · {cand.barcode ?? 'no barcode'}
          </div>
        </div>
      </div>
      <ErrorBox error={approve.error ?? reject.error ?? map.error} />
      <div className="actions">
        <button className="primary" disabled={approve.isPending} onClick={() => approve.mutate()}>Approve</button>
        <button className="danger-btn" disabled={reject.isPending} onClick={() => reject.mutate()}>Reject</button>
        <button onClick={() => setSearching(!searching)}>Search product</button>
      </div>
      {searching && (
        <CanonicalPicker initialQuery={(sp.externalNameEn ?? sp.externalName).split(' ').slice(0, 3).join(' ')}
          onCancel={() => setSearching(false)} onPick={(id) => map.mutate(id)} />
      )}
    </div>
  );
}

export function MatchReviewPage() {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState('PENDING');
  const list = useQuery({ queryKey: ['candidates', status, page], queryFn: () => adminApi.candidates(status, page) });
  return (
    <>
      <h1>Match review</h1>
      <p className="muted">
        Uncertain matches (60–90% confidence). Approve to merge the store product into the candidate canonical product,
        reject to keep them separate, or search for the right product.
      </p>
      <div className="toolbar">
        <select value={status} onChange={(e) => { setPage(0); setStatus(e.target.value); }}>
          {['PENDING', 'APPROVED', 'REJECTED', 'AUTO_APPROVED'].map((s) => <option key={s}>{s}</option>)}
        </select>
      </div>
      <ErrorBox error={list.error} />
      <Card>
        {list.data?.items.length === 0 && <p className="muted">Nothing to review.</p>}
        {list.data?.items.map((c) => <CandidateCard key={c.id} c={c} />)}
        {list.data && <Pager page={page} size={20} total={list.data.total} onPage={setPage} />}
      </Card>
    </>
  );
}
