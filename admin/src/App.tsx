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
  dashboard: { label: 'Crawlers', component: DashboardPage },
  history: { label: 'Crawl history', component: CrawlHistoryPage },
  products: { label: 'Canonical products', component: ProductsPage },
  storeProducts: { label: 'Store products & mapping', component: StoreProductsPage },
  review: { label: 'Match review', component: MatchReviewPage },
  anomalies: { label: 'Price anomalies', component: AnomaliesPage },
  delivery: { label: 'Delivery terms', component: DeliveryPage },
  locations: { label: 'Store branches', component: LocationsPage },
  csv: { label: 'CSV import', component: CsvImportPage },
  settings: { label: 'Settings', component: SettingsPage },
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
        <div className="brand">Where Is It Cheapest?<span>admin</span></div>
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
