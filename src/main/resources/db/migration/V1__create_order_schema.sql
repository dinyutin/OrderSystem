CREATE TABLE products (
    product_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    stock INT NOT NULL,
    PRIMARY KEY (product_id),
    CONSTRAINT uk_products_name UNIQUE (name),
    CONSTRAINT chk_products_stock CHECK (stock >= 0)
);

CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id VARCHAR(36) NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_orders_order_id UNIQUE (order_id),
    CONSTRAINT fk_orders_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT chk_orders_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_orders_product_id ON orders (product_id);
