import { useState } from 'react';
import { getToken, setToken } from '../api';
import { Card } from '../components';

export function SettingsPage() {
  const [token, setTok] = useState(getToken());
  const [saved, setSaved] = useState(false);
  return (
    <>
      <h1>Կարգավորումներ</h1>
      <Card title="Ադմինի թոքեն">
        <p className="muted">
          Ուղարկվում է <code>X-Admin-Token</code> վերնագրով և պետք է համընկնի backend-ի <code>ADMIN_TOKEN</code>-ի հետ։ Եթե
          backend-ը աշխատում է առանց ADMIN_TOKEN-ի, ադմինի հարցումներն ընդունվում են միայն localhost-ից, և թոքեն պետք չէ։
        </p>
        <div className="toolbar">
          <input type="password" value={token} onChange={(e) => { setTok(e.target.value); setSaved(false); }} placeholder="Ադմինի թոքեն" />
          <button className="primary" onClick={() => { setToken(token); setSaved(true); }}>Պահպանել</button>
          {saved && <span className="ok">Պահպանված է</span>}
        </div>
      </Card>
    </>
  );
}
