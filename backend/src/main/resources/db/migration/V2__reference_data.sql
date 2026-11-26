-- Reference data only: real Armenian supermarkets and the app's own category tree.
-- No product or price data is inserted here; prices come exclusively from crawlers / CSV imports.

INSERT INTO stores (name, slug, website_url, active) VALUES
    ('SAS Supermarket', 'sas', 'https://www.sas.am', TRUE),
    ('Yerevan City', 'yerevan-city', 'https://yerevan-city.am', TRUE),
    ('Carrefour Armenia', 'carrefour', 'https://carrefour.am', TRUE),
    ('Nor Zovq', 'nor-zovq', 'https://norzovq.am', FALSE),
    ('Supermarket.am', 'supermarket-am', 'https://supermarket.am', FALSE);

INSERT INTO categories (name, name_en, slug, icon, sort_order) VALUES
    ('Կաթնամթերք', 'Milk & Dairy', 'dairy', 'milk', 10),
    ('Ձու', 'Eggs', 'eggs', 'egg', 20),
    ('Միս և մսամթերք', 'Meat', 'meat', 'meat', 30),
    ('Ձուկ և ծովամթերք', 'Fish & Seafood', 'fish', 'fish', 40),
    ('Հաց և թխվածք', 'Bread & Bakery', 'bread', 'bread', 50),
    ('Մրգեր և բանջարեղեն', 'Fruits & Vegetables', 'produce', 'fruit', 60),
    ('Բրինձ, ձավար, մակարոն', 'Rice, Grains & Pasta', 'grains', 'grain', 70),
    ('Նպարեղեն', 'Pantry', 'pantry', 'pantry', 80),
    ('Քաղցրավենիք', 'Sweets & Snacks', 'sweets', 'candy', 90),
    ('Ըմպելիքներ', 'Drinks', 'drinks', 'drink', 100),
    ('Սուրճ և թեյ', 'Coffee & Tea', 'coffee-tea', 'coffee', 110),
    ('Ալկոհոլային խմիչքներ', 'Alcohol', 'alcohol', 'wine', 120),
    ('Սառեցված մթերք', 'Frozen Food', 'frozen', 'snow', 130),
    ('Մանկական ապրանքներ', 'Baby', 'baby', 'baby', 140),
    ('Կենցաղային քիմիա', 'Household', 'household', 'home', 150),
    ('Անձնական խնամք', 'Personal Care', 'personal-care', 'care', 160),
    ('Կենդանիների համար', 'Pets', 'pets', 'pet', 170),
    ('Այլ', 'Other', 'other', 'other', 999);
