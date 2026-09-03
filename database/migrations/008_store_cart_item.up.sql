CREATE TABLE tblCartItem (
    cart_item_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    product_id VARCHAR(32) NOT NULL,
    quantity INTEGER NOT NULL,
    added_at DATETIME NOT NULL,
    PRIMARY KEY (cart_item_id),
    CONSTRAINT uk_tblCartItem_user_product UNIQUE (user_id, product_id)
);

CREATE INDEX idx_tblCartItem_user ON tblCartItem(user_id);
CREATE INDEX idx_tblCartItem_product ON tblCartItem(product_id);
