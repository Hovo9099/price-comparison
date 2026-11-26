-- norzovq.am (Cloudflare 525 in October 2026) now redirects to zovq.am, a public Shopware storefront crawled by
-- ZovqProvider (store code stays 'nor-zovq').
UPDATE stores SET website_url = 'https://zovq.am', active = TRUE, updated_at = now() WHERE slug = 'nor-zovq';
