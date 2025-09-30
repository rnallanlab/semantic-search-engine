import json
import requests
import pandas as pd
import csv
from sentence_transformers import SentenceTransformer
import numpy as np
from typing import List, Dict, Optional
import logging
from tqdm import tqdm
import psycopg2.extras
from database import DatabaseManager
from config import DATA_CONFIG, MODEL_CONFIG

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class DataIngestionPipeline:
    def __init__(self):
        self.db_manager = DatabaseManager()
        self.model = SentenceTransformer(MODEL_CONFIG['model_name'])
        logger.info(f"Loaded model: {MODEL_CONFIG['model_name']}")

    def download_dataset(self, url: str, local_path: str) -> bool:
        """Download Amazon dataset"""
        try:
            logger.info(f"Downloading dataset from {url}")
            response = requests.get(url, stream=True)
            response.raise_for_status()

            with open(local_path, 'wb') as f:
                for chunk in response.iter_content(chunk_size=8192):
                    f.write(chunk)

            logger.info(f"Dataset downloaded to {local_path}")
            return True
        except Exception as e:
            logger.error(f"Error downloading dataset: {e}")
            return False

    def parse_amazon_data(self, file_path: str) -> List[Dict]:
        """Parse Amazon dataset from CSV file"""
        products = []
        try:
            logger.info(f"Reading CSV file: {file_path}")
            df = pd.read_csv(file_path)

            logger.info(f"CSV shape: {df.shape}")
            logger.info(f"CSV columns: {list(df.columns)}")

            # Limit records for prototype
            max_records = min(DATA_CONFIG['max_records'], len(df))
            df = df.head(max_records)

            for i, row in df.iterrows():
                try:
                    # Map CSV columns to our schema based on actual column structure
                    # Use direct column access instead of .get() to avoid pandas issues
                    asin_val = row['asin'] if 'asin' in row and pd.notna(row['asin']) else (row['input_asin'] if 'input_asin' in row and pd.notna(row['input_asin']) else f'ASIN_{i}')
                    title_val = row['title'] if 'title' in row and pd.notna(row['title']) else ''
                    description_val = row['description'] if 'description' in row and pd.notna(row['description']) else ''
                    brand_val = row['brand'] if 'brand' in row and pd.notna(row['brand']) else (row['manufacturer'] if 'manufacturer' in row and pd.notna(row['manufacturer']) else '')
                    category_val = row['categories'] if 'categories' in row and pd.notna(row['categories']) else ''
                    price_val = row['final_price'] if 'final_price' in row and pd.notna(row['final_price']) else (row['initial_price'] if 'initial_price' in row and pd.notna(row['initial_price']) else '')
                    image_url_val = row['image_url'] if 'image_url' in row and pd.notna(row['image_url']) else ''
                    rating_val = row['rating'] if 'rating' in row and pd.notna(row['rating']) else 0
                    review_count_val = row['reviews_count'] if 'reviews_count' in row and pd.notna(row['reviews_count']) else 0

                    parsed_product = {
                        'asin': str(asin_val),
                        'title': str(title_val),
                        'description': str(description_val),
                        'brand': str(brand_val),
                        'category': self._parse_category(str(category_val)),
                        'price': self._parse_price(str(price_val)),
                        'image_url': str(image_url_val),
                        'rating': self._parse_rating(rating_val),
                        'review_count': self._parse_review_count(review_count_val)
                    }

                    # Clean up None values
                    for key, value in parsed_product.items():
                        # Handle different types to avoid pandas array issues
                        is_na = False
                        try:
                            is_na = pd.isna(value) or str(value) == 'nan'
                        except (ValueError, TypeError):
                            # If pd.isna fails (e.g., on lists), just check for None and 'nan' string
                            is_na = value is None or str(value) == 'nan'

                        if is_na:
                            if key in ['category']:
                                parsed_product[key] = []
                            elif key in ['price', 'rating']:
                                parsed_product[key] = None
                            elif key in ['review_count']:
                                parsed_product[key] = 0
                            else:
                                parsed_product[key] = ''

                    # Only include products with meaningful content
                    title_str = str(parsed_product['title'])
                    if title_str and title_str != 'nan' and len(title_str.strip()) > 3:
                        products.append(parsed_product)

                    if (i + 1) % 1000 == 0:
                        logger.info(f"Processed {i + 1} records")

                except Exception as e:
                    logger.warning(f"Error parsing row {i}: {e}")
                    continue

            logger.info(f"Parsed {len(products)} valid products from CSV")
            return products

        except Exception as e:
            logger.error(f"Error parsing CSV data: {e}")
            return []

    def _parse_price(self, price_str: str) -> Optional[float]:
        """Parse price string to float"""
        if not price_str or str(price_str) == 'nan' or pd.isna(price_str):
            return None

        try:
            # Remove currency symbols and extract number
            price_clean = ''.join(c for c in str(price_str) if c.isdigit() or c == '.')
            return float(price_clean) if price_clean else None
        except:
            return None

    def _parse_category(self, category_str: str) -> List[str]:
        """Parse category string to list"""
        if not category_str or str(category_str) == 'nan' or pd.isna(category_str):
            return []

        try:
            # If it's already a list-like string, try to parse it
            if category_str.startswith('[') and category_str.endswith(']'):
                # Remove brackets and split by comma
                category_str = category_str.strip('[]').replace("'", "").replace('"', '')
                categories = [cat.strip() for cat in category_str.split(',') if cat.strip()]
            else:
                # Split by common delimiters
                categories = [cat.strip() for cat in category_str.replace('|', ',').replace('>', ',').split(',') if cat.strip()]

            return categories[:5]  # Limit to 5 categories
        except:
            return [str(category_str)] if category_str else []

    def _parse_rating(self, rating_val) -> Optional[float]:
        """Parse rating value to float"""
        if pd.isna(rating_val) or str(rating_val) == 'nan':
            return None

        try:
            rating = float(rating_val)
            # Ensure rating is within reasonable bounds
            return max(0.0, min(5.0, rating))
        except:
            return None

    def _parse_review_count(self, review_count_val) -> int:
        """Parse review count to integer"""
        if pd.isna(review_count_val) or str(review_count_val) == 'nan':
            return 0

        try:
            # Handle strings like "1,234" or "1.2K"
            count_str = str(review_count_val).replace(',', '')
            if 'K' in count_str.upper():
                return int(float(count_str.upper().replace('K', '')) * 1000)
            elif 'M' in count_str.upper():
                return int(float(count_str.upper().replace('M', '')) * 1000000)
            else:
                return int(float(count_str))
        except:
            return 0

    def generate_embeddings(self, products: List[Dict]) -> List[Dict]:
        """Generate vector embeddings for products"""
        logger.info("Generating embeddings...")

        # Prepare text for embedding
        titles = [p['title'] for p in products]
        descriptions = [p['description'] if p['description'] else p['title'] for p in products]
        combined_texts = [f"{p['title']} {p['description'] if p['description'] else ''} {p['brand'] if p['brand'] else ''}".strip()
                         for p in products]

        # Generate embeddings in batches
        batch_size = MODEL_CONFIG['batch_size']

        title_embeddings = []
        description_embeddings = []
        combined_embeddings = []

        for i in tqdm(range(0, len(products), batch_size), desc="Generating embeddings"):
            batch_end = min(i + batch_size, len(products))

            title_batch = titles[i:batch_end]
            desc_batch = descriptions[i:batch_end]
            combined_batch = combined_texts[i:batch_end]

            title_emb = self.model.encode(title_batch)
            desc_emb = self.model.encode(desc_batch)
            combined_emb = self.model.encode(combined_batch)

            title_embeddings.extend(title_emb.tolist())
            description_embeddings.extend(desc_emb.tolist())
            combined_embeddings.extend(combined_emb.tolist())

        # Add embeddings to products
        for i, product in enumerate(products):
            product['title_embedding'] = title_embeddings[i]
            product['description_embedding'] = description_embeddings[i]
            product['combined_embedding'] = combined_embeddings[i]

        logger.info("Embeddings generated successfully")
        return products

    def insert_products(self, products: List[Dict]):
        """Insert products into database"""
        logger.info(f"Inserting {len(products)} products into database...")

        insert_sql = """
        INSERT INTO products (
            asin, title, description, brand, category, price,
            image_url, rating, review_count,
            title_embedding, description_embedding, combined_embedding
        ) VALUES %s
        ON CONFLICT (asin) DO UPDATE SET
            title = EXCLUDED.title,
            description = EXCLUDED.description,
            brand = EXCLUDED.brand,
            category = EXCLUDED.category,
            price = EXCLUDED.price,
            image_url = EXCLUDED.image_url,
            rating = EXCLUDED.rating,
            review_count = EXCLUDED.review_count,
            title_embedding = EXCLUDED.title_embedding,
            description_embedding = EXCLUDED.description_embedding,
            combined_embedding = EXCLUDED.combined_embedding,
            updated_at = CURRENT_TIMESTAMP
        """

        try:
            conn = self.db_manager.get_connection()
            with conn.cursor() as cursor:
                # Prepare data for insertion
                values = []
                for product in products:
                    values.append((
                        product['asin'],
                        product['title'],
                        product['description'],
                        product['brand'],
                        product['category'],
                        product['price'],
                        product['image_url'],
                        product['rating'],
                        product['review_count'],
                        product['title_embedding'],
                        product['description_embedding'],
                        product['combined_embedding']
                    ))

                # Batch insert
                psycopg2.extras.execute_values(
                    cursor, insert_sql, values, template=None, page_size=100
                )
                conn.commit()

            conn.close()
            logger.info("Products inserted successfully")

        except Exception as e:
            logger.error(f"Error inserting products: {e}")
            raise

    def run_pipeline(self):
        """Run the complete data ingestion pipeline"""
        logger.info("Starting data ingestion pipeline...")

        # Test database connection
        if not self.db_manager.test_connection():
            raise Exception("Database connection failed")

        # Create tables
        self.db_manager.create_tables()

        # Download and process data
        dataset_file = 'amazon_products.csv'

        if not self.download_dataset(DATA_CONFIG['dataset_url'], dataset_file):
            raise Exception("Failed to download dataset")

        # Parse data
        products = self.parse_amazon_data(dataset_file)
        if not products:
            raise Exception("No valid products found in dataset")

        # Generate embeddings
        products_with_embeddings = self.generate_embeddings(products)

        # Insert into database
        self.insert_products(products_with_embeddings)

        logger.info("Data ingestion pipeline completed successfully!")
        logger.info(f"Processed {len(products)} products")

if __name__ == "__main__":
    pipeline = DataIngestionPipeline()
    pipeline.run_pipeline()