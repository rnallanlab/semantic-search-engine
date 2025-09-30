-- Test data for Search API integration tests

INSERT INTO products (asin, title, description, brand, category, price, image_url, rating, review_count,
                      title_embedding, description_embedding, combined_embedding) VALUES
('B001', 'Wireless Bluetooth Headphones', 'High-quality wireless headphones with noise cancellation and 20-hour battery life', 'AudioTech', '["Electronics","Audio"]', 199.99, 'http://example.com/headphones1.jpg', 4.5, 1250,
 '[0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0]', '[0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0,0.1]', '[0.15,0.25,0.35,0.45,0.55,0.65,0.75,0.85,0.95,0.55]'),

('B002', 'iPhone 14 Pro Max', 'Latest iPhone with A16 Bionic chip, Pro camera system, and 1TB storage', 'Apple', '["Electronics","Smartphones"]', 1399.99, 'http://example.com/iphone14.jpg', 4.8, 2100,
 '[0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5]', '[0.6,0.6,0.6,0.6,0.6,0.6,0.6,0.6,0.6,0.6]', '[0.55,0.55,0.55,0.55,0.55,0.55,0.55,0.55,0.55,0.55]'),

('B003', 'Portable Power Bank 20000mAh', 'Portable external battery charger with fast charging support for smartphones and tablets', 'PowerPlus', '["Electronics","Accessories"]', 49.99, 'http://example.com/powerbank.jpg', 4.2, 890,
 '[0.3,0.7,0.1,0.9,0.4,0.6,0.2,0.8,0.5,0.0]', '[0.4,0.8,0.2,1.0,0.5,0.7,0.3,0.9,0.6,0.1]', '[0.35,0.75,0.15,0.95,0.45,0.65,0.25,0.85,0.55,0.05]'),

('B004', 'Samsung Galaxy S23 Ultra', 'Premium Android smartphone with S Pen, 200MP camera, and 5G connectivity', 'Samsung', '["Electronics","Smartphones"]', 1199.99, 'http://example.com/galaxy23.jpg', 4.6, 1560,
 '[0.2,0.4,0.6,0.8,1.0,0.9,0.7,0.5,0.3,0.1]', '[0.3,0.5,0.7,0.9,0.1,0.8,0.6,0.4,0.2,0.0]', '[0.25,0.45,0.65,0.85,0.55,0.85,0.65,0.45,0.25,0.05]'),

('B005', 'Gaming Mechanical Keyboard', 'RGB backlit mechanical keyboard with Cherry MX switches for gaming', 'GameTech', '["Electronics","Gaming"]', 129.99, 'http://example.com/keyboard.jpg', 4.4, 750,
 '[0.8,0.2,0.6,0.4,0.9,0.1,0.7,0.3,0.5,0.0]', '[0.9,0.3,0.7,0.5,1.0,0.2,0.8,0.4,0.6,0.1]', '[0.85,0.25,0.65,0.45,0.95,0.15,0.75,0.35,0.55,0.05]'),

('B006', 'Wireless Mouse Ergonomic', 'Ergonomic wireless mouse with precision tracking and long battery life', 'ComputerPro', '["Electronics","Accessories"]', 39.99, 'http://example.com/mouse.jpg', 4.1, 340,
 '[0.4,0.6,0.2,0.8,0.3,0.7,0.1,0.9,0.5,0.0]', '[0.5,0.7,0.3,0.9,0.4,0.8,0.2,1.0,0.6,0.1]', '[0.45,0.65,0.25,0.85,0.35,0.75,0.15,0.95,0.55,0.05]');

-- Additional test data for edge cases
INSERT INTO products (asin, title, description, brand, category, price, image_url, rating, review_count,
                      title_embedding, description_embedding, combined_embedding) VALUES
('B007', 'Budget Earbuds', 'Affordable wired earbuds for everyday use', 'BasicAudio', '["Electronics","Audio"]', 15.99, 'http://example.com/earbuds.jpg', 3.5, 120,
 '[0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1]', '[0.2,0.2,0.2,0.2,0.2,0.2,0.2,0.2,0.2,0.2]', '[0.15,0.15,0.15,0.15,0.15,0.15,0.15,0.15,0.15,0.15]'),

('B008', 'Premium Tablet', NULL, 'TechCorp', '["Electronics","Tablets"]', 799.99, NULL, 4.7, 890,
 NULL, NULL, '[0.7,0.7,0.7,0.7,0.7,0.7,0.7,0.7,0.7,0.7]');