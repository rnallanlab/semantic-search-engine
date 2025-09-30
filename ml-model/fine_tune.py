#!/usr/bin/env python3
"""
Model Fine-tuning Pipeline for E-commerce Semantic Search

This script fine-tunes a sentence-transformer model on e-commerce specific data
to improve search relevancy for product queries.
"""

import json
import logging
import os
import random
from typing import List, Tuple, Dict
import pandas as pd
import torch
from sentence_transformers import SentenceTransformer, InputExample, losses
from sentence_transformers.evaluation import EmbeddingSimilarityEvaluator
from torch.utils.data import DataLoader
from sklearn.model_selection import train_test_split
import numpy as np

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class EcommerceFinetuner:
    """Fine-tune sentence transformers for e-commerce search relevancy"""

    def __init__(self, base_model: str = "sentence-transformers/all-MiniLM-L6-v2"):
        self.base_model = base_model
        self.model = None
        self.training_data = []
        self.evaluation_data = []

    def load_base_model(self):
        """Load the pre-trained sentence transformer model"""
        logger.info(f"Loading base model: {self.base_model}")
        self.model = SentenceTransformer(self.base_model)

    def generate_training_data(self, products: List[Dict]) -> List[InputExample]:
        """
        Generate training data from product catalog for fine-tuning.
        Creates positive and negative pairs for contrastive learning.
        """
        logger.info("Generating training data from product catalog...")

        training_examples = []

        # 1. Positive pairs: queries that should match products
        positive_pairs = self._create_positive_pairs(products)

        # 2. Hard negative pairs: similar products that shouldn't match specific queries
        negative_pairs = self._create_negative_pairs(products)

        # 3. Convert to InputExample format
        for query, product_text, label in positive_pairs + negative_pairs:
            training_examples.append(InputExample(
                texts=[query, product_text],
                label=float(label)
            ))

        logger.info(f"Generated {len(training_examples)} training examples")
        return training_examples

    def _create_positive_pairs(self, products: List[Dict]) -> List[Tuple[str, str, int]]:
        """Create positive training pairs (query, matching product, 1)"""
        positive_pairs = []

        for product in products:
            title = product.get('title', '')
            description = product.get('description', '')
            brand = product.get('brand', '')

            if not title:
                continue

            # Generate various types of queries that should match this product
            queries = []

            # 1. Extract key terms from title
            title_words = title.lower().split()
            if len(title_words) >= 2:
                # Partial title matches
                queries.append(' '.join(title_words[:2]))
                queries.append(' '.join(title_words[-2:]))

            # 2. Brand + product type combinations
            if brand:
                brand_lower = brand.lower()
                # Extract product type (common patterns)
                for keyword in ['headphones', 'speaker', 'phone', 'laptop', 'tablet',
                               'watch', 'camera', 'tv', 'monitor', 'keyboard', 'mouse']:
                    if keyword in title.lower():
                        queries.append(f"{brand_lower} {keyword}")

            # 3. Feature-based queries from description
            if description:
                desc_lower = description.lower()
                feature_queries = []

                # Common e-commerce features
                features = {
                    'wireless': ['wireless', 'bluetooth', 'cordless'],
                    'portable': ['portable', 'compact', 'travel'],
                    'gaming': ['gaming', 'gamer', 'game'],
                    'professional': ['professional', 'pro', 'business'],
                    'waterproof': ['waterproof', 'water resistant', 'splash proof']
                }

                for feature, keywords in features.items():
                    if any(kw in desc_lower for kw in keywords):
                        feature_queries.append(feature)

                queries.extend(feature_queries)

            # 4. Synonyms and variations
            synonym_map = {
                'headphones': ['earphones', 'earbuds', 'headset'],
                'smartphone': ['phone', 'mobile', 'cell phone'],
                'laptop': ['notebook', 'computer', 'pc'],
                'television': ['tv', 'smart tv', 'display']
            }

            for canonical, synonyms in synonym_map.items():
                if canonical in title.lower():
                    queries.extend(synonyms)

            # Create positive pairs
            product_text = f"{title} {description}".strip()
            for query in queries[:5]:  # Limit to avoid too many duplicates
                if query and len(query.strip()) > 2:
                    positive_pairs.append((query.strip(), product_text, 1))

        return positive_pairs

    def _create_negative_pairs(self, products: List[Dict]) -> List[Tuple[str, str, int]]:
        """Create negative training pairs (query, non-matching product, 0)"""
        negative_pairs = []

        # Group products by category for better negative sampling
        category_groups = {}
        for product in products:
            categories = product.get('category', [])
            if categories:
                main_category = categories[0] if isinstance(categories, list) else str(categories)
                if main_category not in category_groups:
                    category_groups[main_category] = []
                category_groups[main_category].append(product)

        # Create negative pairs by mixing categories
        categories = list(category_groups.keys())

        for i, category1 in enumerate(categories):
            for j, category2 in enumerate(categories):
                if i != j and len(category_groups[category1]) > 0 and len(category_groups[category2]) > 0:
                    # Sample products from different categories
                    prod1 = random.choice(category_groups[category1])
                    prod2 = random.choice(category_groups[category2])

                    # Create query from prod1, pair with prod2 (should not match)
                    query_terms = prod1.get('title', '').split()[:2]
                    if len(query_terms) >= 2:
                        query = ' '.join(query_terms).lower()
                        product_text = f"{prod2.get('title', '')} {prod2.get('description', '')}".strip()

                        if query and product_text:
                            negative_pairs.append((query, product_text, 0))

                    # Limit negative pairs to avoid overwhelming positives
                    if len(negative_pairs) >= len(products) // 2:
                        break

                if len(negative_pairs) >= len(products) // 2:
                    break

        return negative_pairs

    def prepare_evaluation_data(self, products: List[Dict]) -> List[InputExample]:
        """Prepare evaluation dataset for measuring fine-tuning performance"""
        logger.info("Preparing evaluation dataset...")

        # Use a subset of products for evaluation
        eval_products = random.sample(products, min(500, len(products) // 4))

        eval_examples = []

        for product in eval_products:
            title = product.get('title', '')
            description = product.get('description', '')

            if not title:
                continue

            # Create evaluation queries
            title_words = title.lower().split()

            # Perfect match (should score high)
            if len(title_words) >= 3:
                perfect_query = ' '.join(title_words[:3])
                product_text = f"{title} {description}".strip()
                eval_examples.append(InputExample(
                    texts=[perfect_query, product_text],
                    label=1.0
                ))

            # Partial match (should score medium)
            if len(title_words) >= 2:
                partial_query = ' '.join(title_words[:2])
                eval_examples.append(InputExample(
                    texts=[partial_query, product_text],
                    label=0.7
                ))

        logger.info(f"Created {len(eval_examples)} evaluation examples")
        return eval_examples

    def fine_tune(self,
                  training_examples: List[InputExample],
                  evaluation_examples: List[InputExample],
                  output_path: str = "./fine_tuned_model",
                  epochs: int = 3,
                  batch_size: int = 16):
        """Fine-tune the model using contrastive learning"""

        if not self.model:
            self.load_base_model()

        logger.info(f"Starting fine-tuning with {len(training_examples)} examples...")

        # Split training data
        train_examples, val_examples = train_test_split(
            training_examples, test_size=0.2, random_state=42
        )

        # Create DataLoader
        train_dataloader = DataLoader(train_examples, shuffle=True, batch_size=batch_size)

        # Define loss function (Contrastive Loss for binary classification)
        train_loss = losses.ContrastiveLoss(model=self.model)

        # Create evaluator
        evaluator = EmbeddingSimilarityEvaluator.from_input_examples(
            evaluation_examples, name='eval'
        )

        # Fine-tune
        warmup_steps = int(len(train_dataloader) * epochs * 0.1)

        self.model.fit(
            train_objectives=[(train_dataloader, train_loss)],
            epochs=epochs,
            warmup_steps=warmup_steps,
            evaluator=evaluator,
            evaluation_steps=500,
            output_path=output_path,
            save_best_model=True,
            show_progress_bar=True
        )

        logger.info(f"Fine-tuning completed. Model saved to: {output_path}")

        # Evaluate final model
        final_score = evaluator(self.model)
        logger.info(f"Final evaluation score: {final_score}")

        return final_score

    def evaluate_improvement(self,
                           original_model: SentenceTransformer,
                           fine_tuned_model: SentenceTransformer,
                           test_queries: List[Tuple[str, str, float]]) -> Dict:
        """
        Evaluate improvement in search relevancy after fine-tuning

        Args:
            test_queries: List of (query, product_text, expected_similarity)
        """
        logger.info("Evaluating model improvement...")

        original_scores = []
        fine_tuned_scores = []

        for query, product_text, expected in test_queries:
            # Original model
            orig_embeddings = original_model.encode([query, product_text])
            orig_similarity = float(np.dot(orig_embeddings[0], orig_embeddings[1]))
            original_scores.append(abs(orig_similarity - expected))

            # Fine-tuned model
            ft_embeddings = fine_tuned_model.encode([query, product_text])
            ft_similarity = float(np.dot(ft_embeddings[0], ft_embeddings[1]))
            fine_tuned_scores.append(abs(ft_similarity - expected))

        # Calculate metrics
        orig_mae = np.mean(original_scores)
        ft_mae = np.mean(fine_tuned_scores)
        improvement = ((orig_mae - ft_mae) / orig_mae) * 100

        results = {
            'original_mae': orig_mae,
            'fine_tuned_mae': ft_mae,
            'improvement_percentage': improvement,
            'test_cases': len(test_queries)
        }

        logger.info(f"Evaluation Results:")
        logger.info(f"  Original MAE: {orig_mae:.4f}")
        logger.info(f"  Fine-tuned MAE: {ft_mae:.4f}")
        logger.info(f"  Improvement: {improvement:.2f}%")

        return results

def load_products_from_db():
    """Load products from database for fine-tuning"""
    import psycopg2
    from data_pipeline.config import DATABASE_CONFIG

    try:
        conn = psycopg2.connect(**DATABASE_CONFIG)
        cursor = conn.cursor()

        cursor.execute("""
            SELECT asin, title, description, brand, category
            FROM products
            WHERE title IS NOT NULL
            LIMIT 10000
        """)

        products = []
        for row in cursor.fetchall():
            products.append({
                'asin': row[0],
                'title': row[1],
                'description': row[2] or '',
                'brand': row[3] or '',
                'category': row[4] or []
            })

        cursor.close()
        conn.close()

        logger.info(f"Loaded {len(products)} products from database")
        return products

    except Exception as e:
        logger.error(f"Error loading products: {e}")
        return []

def main():
    """Main fine-tuning pipeline"""

    # Configuration
    BASE_MODEL = "sentence-transformers/all-MiniLM-L6-v2"
    OUTPUT_PATH = "./fine_tuned_ecommerce_model"
    EPOCHS = 4
    BATCH_SIZE = 32

    # Initialize fine-tuner
    finetuner = EcommerceFinetuner(BASE_MODEL)
    finetuner.load_base_model()

    # Load product data
    products = load_products_from_db()
    if not products:
        logger.error("No products loaded. Cannot proceed with fine-tuning.")
        return

    # Generate training data
    training_examples = finetuner.generate_training_data(products)
    evaluation_examples = finetuner.prepare_evaluation_data(products)

    if len(training_examples) < 100:
        logger.warning("Very few training examples. Consider improving data generation.")

    # Fine-tune model
    final_score = finetuner.fine_tune(
        training_examples=training_examples,
        evaluation_examples=evaluation_examples,
        output_path=OUTPUT_PATH,
        epochs=EPOCHS,
        batch_size=BATCH_SIZE
    )

    # Load fine-tuned model for comparison
    fine_tuned_model = SentenceTransformer(OUTPUT_PATH)

    # Create test cases for improvement evaluation
    test_queries = [
        ("wireless headphones", "Sony WH-1000XM4 Wireless Noise Canceling Headphones", 0.8),
        ("bluetooth speaker", "JBL Charge 4 Portable Bluetooth Speaker", 0.9),
        ("gaming laptop", "ASUS ROG Strix Gaming Laptop", 0.85),
        ("smartphone", "iPhone 13 Pro Max", 0.7),
        ("kitchen blender", "Vitamix High-Performance Blender", 0.8)
    ]

    # Evaluate improvement
    improvement_results = finetuner.evaluate_improvement(
        original_model=SentenceTransformer(BASE_MODEL),
        fine_tuned_model=fine_tuned_model,
        test_queries=test_queries
    )

    # Save results
    results = {
        'base_model': BASE_MODEL,
        'fine_tuned_model_path': OUTPUT_PATH,
        'training_examples': len(training_examples),
        'evaluation_examples': len(evaluation_examples),
        'final_evaluation_score': final_score,
        'improvement_metrics': improvement_results,
        'hyperparameters': {
            'epochs': EPOCHS,
            'batch_size': BATCH_SIZE
        }
    }

    with open('fine_tuning_results.json', 'w') as f:
        json.dump(results, f, indent=2)

    logger.info("Fine-tuning completed successfully!")
    logger.info(f"Results saved to: fine_tuning_results.json")
    logger.info(f"Fine-tuned model saved to: {OUTPUT_PATH}")

if __name__ == "__main__":
    main()