-- Parma (https://parma.am): public server-rendered catalog, robots.txt allows crawling. Crawled by ParmaProvider.
INSERT INTO stores (name, slug, website_url, active)
VALUES ('Parma', 'parma', 'https://parma.am', TRUE)
ON CONFLICT (slug) DO NOTHING;
