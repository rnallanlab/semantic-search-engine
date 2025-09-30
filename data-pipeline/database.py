import psycopg2
from psycopg2.extras import RealDictCursor
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker
import logging
from config import DATABASE_CONFIG

logger = logging.getLogger(__name__)

class DatabaseManager:
    def __init__(self):
        self.connection_string = self._build_connection_string()
        self.engine = create_engine(self.connection_string)
        self.SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=self.engine)

    def _build_connection_string(self):
        return f"postgresql://{DATABASE_CONFIG['user']}:{DATABASE_CONFIG['password']}@{DATABASE_CONFIG['host']}:{DATABASE_CONFIG['port']}/{DATABASE_CONFIG['database']}"

    def create_tables(self):
        """Create necessary tables with vector support"""
        create_table_sql = """
        -- Enable pgvector extension
        CREATE EXTENSION IF NOT EXISTS vector;

        -- Products table with vector embeddings
        CREATE TABLE IF NOT EXISTS products (
            id SERIAL PRIMARY KEY,
            asin VARCHAR(20) UNIQUE NOT NULL,
            title TEXT,
            description TEXT,
            brand VARCHAR(255),
            category TEXT[],
            price DECIMAL(10,2),
            image_url TEXT,
            rating DECIMAL(3,2),
            review_count INTEGER,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

            -- Vector embeddings
            title_embedding vector(384),
            description_embedding vector(384),
            combined_embedding vector(384)
        );

        -- Create indexes for vector similarity search
        CREATE INDEX IF NOT EXISTS products_title_embedding_idx
        ON products USING ivfflat (title_embedding vector_cosine_ops) WITH (lists = 100);

        CREATE INDEX IF NOT EXISTS products_description_embedding_idx
        ON products USING ivfflat (description_embedding vector_cosine_ops) WITH (lists = 100);

        CREATE INDEX IF NOT EXISTS products_combined_embedding_idx
        ON products USING ivfflat (combined_embedding vector_cosine_ops) WITH (lists = 100);

        -- Create indexes for traditional search
        CREATE INDEX IF NOT EXISTS products_asin_idx ON products(asin);
        CREATE INDEX IF NOT EXISTS products_brand_idx ON products(brand);
        CREATE INDEX IF NOT EXISTS products_category_idx ON products USING GIN(category);
        CREATE INDEX IF NOT EXISTS products_title_text_idx ON products USING GIN(to_tsvector('english', title));

        -- Search logs table for analytics
        CREATE TABLE IF NOT EXISTS search_logs (
            id SERIAL PRIMARY KEY,
            query TEXT NOT NULL,
            results_count INTEGER,
            response_time_ms INTEGER,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        """

        try:
            with self.engine.begin() as conn:
                conn.execute(text(create_table_sql))
            logger.info("Database tables created successfully")
        except Exception as e:
            logger.error(f"Error creating tables: {e}")
            raise

    def get_connection(self):
        """Get raw database connection"""
        return psycopg2.connect(**DATABASE_CONFIG, cursor_factory=RealDictCursor)

    def get_session(self):
        """Get SQLAlchemy session"""
        return self.SessionLocal()

    def test_connection(self):
        """Test database connectivity"""
        try:
            with self.engine.connect() as conn:
                result = conn.execute(text("SELECT 1"))
                logger.info("Database connection test successful")
                return True
        except Exception as e:
            logger.error(f"Database connection test failed: {e}")
            return False