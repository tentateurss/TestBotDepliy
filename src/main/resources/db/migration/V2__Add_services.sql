-- Add services table and initialize default services

-- Table: services
CREATE TABLE IF NOT EXISTS services (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    price REAL NOT NULL,
    category TEXT NOT NULL,
    display_order INTEGER NOT NULL,
    active INTEGER DEFAULT 1,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT DEFAULT CURRENT_TIMESTAMP
);

-- Insert default services
INSERT INTO services (name, price, category, display_order, active) 
SELECT 'Классика', 1500.0, 'наращивание', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM services WHERE name = 'Классика');

INSERT INTO services (name, price, category, display_order, active) 
SELECT '2D объём', 1800.0, 'наращивание', 2, 1
WHERE NOT EXISTS (SELECT 1 FROM services WHERE name = '2D объём');

INSERT INTO services (name, price, category, display_order, active) 
SELECT '3D объём', 2100.0, 'наращивание', 3, 1
WHERE NOT EXISTS (SELECT 1 FROM services WHERE name = '3D объём');

INSERT INTO services (name, price, category, display_order, active) 
SELECT 'Голливуд', 2500.0, 'наращивание', 4, 1
WHERE NOT EXISTS (SELECT 1 FROM services WHERE name = 'Голливуд');

INSERT INTO services (name, price, category, display_order, active) 
SELECT 'Коррекция классика', 1000.0, 'коррекция', 5, 1
WHERE NOT EXISTS (SELECT 1 FROM services WHERE name = 'Коррекция классика');

INSERT INTO services (name, price, category, display_order, active) 
SELECT 'Коррекция объём', 1300.0, 'коррекция', 6, 1
WHERE NOT EXISTS (SELECT 1 FROM services WHERE name = 'Коррекция объём');

INSERT INTO services (name, price, category, display_order, active) 
SELECT 'Снятие', 400.0, 'снятие', 7, 1
WHERE NOT EXISTS (SELECT 1 FROM services WHERE name = 'Снятие');

INSERT INTO services (name, price, category, display_order, active) 
SELECT 'Окрашивание ресниц', 300.0, 'дополнительно', 8, 1
WHERE NOT EXISTS (SELECT 1 FROM services WHERE name = 'Окрашивание ресниц');

INSERT INTO services (name, price, category, display_order, active) 
SELECT 'Ламинирование ресниц', 1800.0, 'дополнительно', 9, 1
WHERE NOT EXISTS (SELECT 1 FROM services WHERE name = 'Ламинирование ресниц');

-- Create index for services
CREATE INDEX IF NOT EXISTS idx_services_category ON services(category);
CREATE INDEX IF NOT EXISTS idx_services_active ON services(active);
