import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { adminApi } from '../api';
import { Card, ErrorBox } from '../components';

const EXAMPLE = `store,externalProductId,name,brand,barcode,price,currency,available,capturedAt
nor-zovq,12345,<ապրանքի իրական անունը>,<բրենդ>,<բարկոդ կամ դատարկ>,<գին>,AMD,true,2026-10-08T10:00:00+04:00`;

export function CsvImportPage() {
  const [file, setFile] = useState<File | null>(null);
  const upload = useMutation({ mutationFn: (f: File) => adminApi.importCsv(f) });
  return (
    <>
      <h1>CSV ներմուծում</h1>
      <Card title="Բեռնել իրական գներ">
        <p className="muted">
          Այն խանութների համար, որոնց կայքը հնարավոր չէ քրոլ անել։ Միայն իրական, դիտարկված գներ։ Պարտադիր սյունակներ՝ store
          (slug կամ անուն), externalProductId, name, price, capturedAt։ Ոչ պարտադիր՝ brand, barcode, currency, available, nameEn,
          originalPrice, url, category, imageUrl։ Տողերն անցնում են նորմալացում, գների պատմություն և համընկնումների որոնում։
        </p>
        <pre>{EXAMPLE}</pre>
        <div className="toolbar">
          <input type="file" accept=".csv,text/csv" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          <button className="primary" disabled={!file || upload.isPending} onClick={() => file && upload.mutate(file)}>
            {upload.isPending ? 'Ներմուծվում է…' : 'Ներմուծել'}
          </button>
        </div>
        <ErrorBox error={upload.error} />
        {upload.data && (
          <div>
            <p>
              Տողեր՝ <b>{upload.data.rows}</b> · ներմուծված՝ <b>{upload.data.imported}</b> · փոխված գներ՝ <b>{upload.data.pricesChanged}</b> ·
              գործարկումներ՝ {upload.data.runIds.join(', ') || '—'}
            </p>
            {upload.data.errors.length > 0 && (
              <table>
                <thead><tr><th>Տող</th><th>Սխալ</th></tr></thead>
                <tbody>{upload.data.errors.map((e, i) => <tr key={i}><td>{e.line}</td><td>{e.message}</td></tr>)}</tbody>
              </table>
            )}
          </div>
        )}
      </Card>
    </>
  );
}
