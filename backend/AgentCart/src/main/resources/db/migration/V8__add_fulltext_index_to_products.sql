ALTER TABLE products ADD FULLTEXT INDEX ft_products_search (name, description, category, brand);
