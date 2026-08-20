CREATE TABLE admin_users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_users_username UNIQUE (username)
);

CREATE TABLE customers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(150) /*!80000 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_customers_email UNIQUE (email),
    CONSTRAINT uk_customers_phone UNIQUE (phone)
);
