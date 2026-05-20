ALTER TABLE products DROP INDEX ft_products_search;
ALTER TABLE products ADD FULLTEXT INDEX ft_products_search (name, description, category, brand) WITH PARSER ngram;
