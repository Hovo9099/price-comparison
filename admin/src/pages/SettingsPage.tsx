import { useState } from 'react';
import { getToken, setToken } from '../api';
import { Card } from '../components';

export function SettingsPage() {
  const [token, setTok] = useState(getToken());
  const [saved, setSaved] = useState(false);
  return (
    <>
      <h1>Settings</h1>
      <Card title="Admin token">
        <p className="muted">
          Sent as <code>X-Admin-Token</code>. Must equal the backend's <code>ADMIN_TOKEN</code>. When the backend runs without
          ADMIN_TOKEN, admin endpoints accept requests from localhost only and no token is needed.
        </p>
        <div className="toolbar">
          <input type="password" value={token} onChange={(e) => { setTok(e.target.value); setSaved(false); }} placeholder="Admin token" />
          <button className="primary" onClick={() => { setToken(token); setSaved(true); }}>Save</button>
          {saved && <span className="ok">Saved</span>}
        </div>
      </Card>
    </>
  );
}
