-- OPTIONAL. Run manually in your chosen application database only when wanted.
-- No stock or purchase history is created. This is a normal, editable customer.
INSERT INTO customers(name)
SELECT 'Walk-in Customer'
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE name='Walk-in Customer');
