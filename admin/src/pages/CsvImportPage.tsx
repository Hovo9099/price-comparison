import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { adminApi } from '../api';
import { Card, ErrorBox } from '../components';

const EXAMPLE = `store,externalProductId,name,brand,barcode,price,currency,available,capturedAt
nor-zovq,12345,<real product name>,<brand>,<barcode or empty>,<price>,AMD,true,2026-10-08T10:00:00+04:00`;

export function CsvImportPage() {
  const [file, setFile] = useState<File | null>(null);
  const upload = useMutation({ mutationFn: (f: File) => adminApi.importCsv(f) });
  return (
    <>
      <h1>CSV import</h1>
      <Card title="Upload real prices">
        <p className="muted">
          For stores that cannot be crawled. Only real, observed prices. Required columns: store (slug or name),
          externalProductId, name, price, capturedAt. Optional: brand, barcode, currency, available, nameEn, originalPrice,
          url, category, imageUrl. Rows go through normalization, price history and matching.
        </p>
        <pre>{EXAMPLE}</pre>
        <div className="toolbar">
          <input type="file" accept=".csv,text/csv" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          <button className="primary" disabled={!file || upload.isPending} onClick={() => file && upload.mutate(file)}>
            {upload.isPending ? 'Importing…' : 'Import'}
          </button>
        </div>
        <ErrorBox error={upload.error} />
        {upload.data && (
          <div>
            <p>
              Rows: <b>{upload.data.rows}</b> · imported: <b>{upload.data.imported}</b> · prices changed: <b>{upload.data.pricesChanged}</b> ·
              runs: {upload.data.runIds.join(', ') || '—'}
            </p>
            {upload.data.errors.length > 0 && (
              <table>
                <thead><tr><th>Line</th><th>Error</th></tr></thead>
                <tbody>{upload.data.errors.map((e, i) => <tr key={i}><td>{e.line}</td><td>{e.message}</td></tr>)}</tbody>
              </table>
            )}
          </div>
        )}
      </Card>
    </>
  );
}
