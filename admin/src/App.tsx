import { useState } from 'react';
import { DashboardPage } from './pages/DashboardPage';
import { CrawlHistoryPage } from './pages/CrawlHistoryPage';
import { ProductsPage } from './pages/ProductsPage';
import { StoreProductsPage } from './pages/StoreProductsPage';
import { MatchReviewPage } from './pages/MatchReviewPage';
import { AnomaliesPage } from './pages/AnomaliesPage';
import { DeliveryPage } from './pages/DeliveryPage';
import { LocationsPage } from './pages/LocationsPage';
import { CsvImportPage } from './pages/CsvImportPage';
import { SettingsPage } from './pages/SettingsPage';

const PAGES = {
  dashboard: { label: 'Քրոլերներ', component: DashboardPage },
  history: { label: 'Քրոլինգի պատմություն', component: CrawlHistoryPage },
  products: { label: 'Ընդհանուր ապրանքներ', component: ProductsPage },
  storeProducts: { label: 'Խանութների ապրանքներ և կապում', component: StoreProductsPage },
  review: { label: 'Համընկնումների ստուգում', component: MatchReviewPage },
  anomalies: { label: 'Կասկածելի գներ', component: AnomaliesPage },
  delivery: { label: 'Առաքման պայմաններ', component: DeliveryPage },
  locations: { label: 'Մասնաճյուղեր', component: LocationsPage },
  csv: { label: 'CSV ներմուծում', component: CsvImportPage },
  settings: { label: 'Կարգավորումներ', component: SettingsPage },
} as const;

type PageKey = keyof typeof PAGES;

export default function App() {
  const [page, setPage] = useState<PageKey>(() => (location.hash.slice(1) as PageKey) in PAGES ? (location.hash.slice(1) as PageKey) : 'dashboard');
  const Current = PAGES[page].component;
  const go = (p: PageKey) => {
    setPage(p);
    location.hash = p;
  };
  return (
    <div className="layout">
      <nav className="sidebar">
        <div className="brand">Որտե՞ղ է էժան<span>ադմին</span></div>
        {(Object.keys(PAGES) as PageKey[]).map((k) => (
          <button key={k} className={k === page ? 'nav active' : 'nav'} onClick={() => go(k)}>
            {PAGES[k].label}
          </button>
        ))}
      </nav>
      <main className="content">
        <Current />
      </main>
    </div>
  );
}
