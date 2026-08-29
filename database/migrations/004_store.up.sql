CREATE TABLE tblProduct (
    product_id VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    stock INTEGER NOT NULL,
    price DOUBLE NOT NULL,
    description VARCHAR(255),
    category VARCHAR(64) NOT NULL,
    PRIMARY KEY (product_id)
);

CREATE TABLE tblOrder (
    order_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    product_id VARCHAR(32) NOT NULL,
    quantity INTEGER NOT NULL,
    total_price DOUBLE NOT NULL,
    order_date DATETIME NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    unit_price DOUBLE NOT NULL,
    PRIMARY KEY (order_id)
);

CREATE INDEX idx_tblOrder_user ON tblOrder(user_id);
CREATE INDEX idx_tblOrder_product ON tblOrder(product_id);
