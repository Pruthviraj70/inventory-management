-- Select a NEW database before sourcing this file. No database creation or deletion here.
-- IF NOT EXISTS preserves rows, but is not a migration for incompatible existing tables.
CREATE TABLE IF NOT EXISTS products (
 item_id INT PRIMARY KEY AUTO_INCREMENT,
 name VARCHAR(150) NOT NULL,
 stock INT NOT NULL DEFAULT 0,
 CONSTRAINT ck_product_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
 CONSTRAINT ck_product_stock CHECK (stock >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS suppliers (
 supplier_id INT PRIMARY KEY AUTO_INCREMENT,
 name VARCHAR(150) NOT NULL,
 supply_time_days INT NOT NULL DEFAULT 0,
 min_order_total DECIMAL(14,2) NOT NULL DEFAULT 0,
 CONSTRAINT ck_supplier_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
 CONSTRAINT ck_supply_days CHECK (supply_time_days >= 0),
 CONSTRAINT ck_supplier_min CHECK (min_order_total >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS supplier_products (
 item_id INT NOT NULL,
 supplier_id INT NOT NULL,
 supplier_specific_stock INT NOT NULL DEFAULT 0,
 PRIMARY KEY (item_id, supplier_id),
 CONSTRAINT fk_sp_product FOREIGN KEY (item_id) REFERENCES products(item_id) ON DELETE RESTRICT,
 CONSTRAINT fk_sp_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id) ON DELETE RESTRICT,
 CONSTRAINT ck_sp_stock CHECK (supplier_specific_stock >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS purchase_orders (
 order_id INT PRIMARY KEY AUTO_INCREMENT,
 supplier_id INT NOT NULL,
 status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
 order_datetime DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 arrived_datetime DATETIME(6) NULL,
 UNIQUE KEY uq_order_supplier (order_id, supplier_id),
 CONSTRAINT fk_po_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id) ON DELETE RESTRICT,
 CONSTRAINT ck_order_status CHECK (status IN ('PENDING','RECEIVED','CANCELLED')),
 CONSTRAINT ck_arrival CHECK ((status = 'RECEIVED' AND arrived_datetime IS NOT NULL AND arrived_datetime >= order_datetime) OR (status <> 'RECEIVED' AND arrived_datetime IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS purchase_order_items (
 order_id INT NOT NULL,
 item_id INT NOT NULL,
 supplier_id INT NOT NULL,
 quantity INT NOT NULL,
 order_price DECIMAL(14,2) NOT NULL,
 landed_unit_cost DECIMAL(14,2) NULL,
 remaining_quantity INT NOT NULL DEFAULT 0,
 PRIMARY KEY (order_id,item_id,supplier_id),
 KEY ix_purchase_fifo (item_id, remaining_quantity, order_id),
 CONSTRAINT fk_poi_order_supplier FOREIGN KEY (order_id,supplier_id) REFERENCES purchase_orders(order_id,supplier_id) ON DELETE RESTRICT,
 CONSTRAINT fk_poi_pair FOREIGN KEY (item_id,supplier_id) REFERENCES supplier_products(item_id,supplier_id) ON DELETE RESTRICT,
 CONSTRAINT ck_poi_quantity CHECK (quantity > 0),
 CONSTRAINT ck_order_price CHECK (order_price >= 0),
 CONSTRAINT ck_landed CHECK (landed_unit_cost IS NULL OR landed_unit_cost >= order_price),
 CONSTRAINT ck_remaining CHECK (remaining_quantity BETWEEN 0 AND quantity),
 CONSTRAINT ck_unreceived CHECK (landed_unit_cost IS NOT NULL OR remaining_quantity = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS customers (
 consumer_id INT PRIMARY KEY AUTO_INCREMENT,
 name VARCHAR(150) NOT NULL,
 CONSTRAINT ck_customer_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS sales (
 transaction_id INT PRIMARY KEY AUTO_INCREMENT,
 consumer_id INT NOT NULL,
 sale_datetime DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 markup_percent DECIMAL(7,2) NOT NULL,
 cart_total DECIMAL(14,2) NOT NULL,
 CONSTRAINT fk_sales_customer FOREIGN KEY (consumer_id) REFERENCES customers(consumer_id) ON DELETE RESTRICT,
 CONSTRAINT ck_markup CHECK (markup_percent >= 0),
 CONSTRAINT ck_cart_total CHECK (cart_total >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS sale_items (
 transaction_id INT NOT NULL,
 item_id INT NOT NULL,
 supplier_id INT NOT NULL,
 quantity INT NOT NULL,
 price_per_unit DECIMAL(14,2) NOT NULL,
 PRIMARY KEY (transaction_id,item_id,supplier_id),
 CONSTRAINT fk_si_sale FOREIGN KEY (transaction_id) REFERENCES sales(transaction_id) ON DELETE RESTRICT,
 CONSTRAINT fk_si_pair FOREIGN KEY (item_id,supplier_id) REFERENCES supplier_products(item_id,supplier_id) ON DELETE RESTRICT,
 CONSTRAINT ck_si_quantity CHECK (quantity > 0),
 CONSTRAINT ck_si_price CHECK (price_per_unit >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
