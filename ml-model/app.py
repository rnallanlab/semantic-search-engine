from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer
import numpy as np
from typing import List, Union
import logging
import os

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Initialize FastAPI app
app = FastAPI(
    title="Semantic Search Embedding Service",
    description="HuggingFace embedding model API for semantic search",
    version="1.0.0"
)

# Global model variable
model = None
MODEL_NAME = os.getenv("MODEL_NAME", "sentence-transformers/all-MiniLM-L6-v2")

class EmbeddingRequest(BaseModel):
    texts: Union[str, List[str]]
    normalize: bool = True

class EmbeddingResponse(BaseModel):
    embeddings: List[List[float]]
    model_name: str
    dimension: int

class HealthResponse(BaseModel):
    status: str
    model_loaded: bool
    model_name: str

@app.on_event("startup")
async def load_model():
    """Load the sentence transformer model on startup"""
    global model
    try:
        logger.info(f"Loading model: {MODEL_NAME}")
        model = SentenceTransformer(MODEL_NAME)
        logger.info("Model loaded successfully")
    except Exception as e:
        logger.error(f"Failed to load model: {e}")
        raise

@app.get("/health", response_model=HealthResponse)
async def health_check():
    """Health check endpoint"""
    return HealthResponse(
        status="healthy" if model is not None else "unhealthy",
        model_loaded=model is not None,
        model_name=MODEL_NAME
    )

@app.post("/embed", response_model=EmbeddingResponse)
async def generate_embeddings(request: EmbeddingRequest):
    """Generate embeddings for input text(s)"""
    if model is None:
        raise HTTPException(status_code=503, detail="Model not loaded")

    try:
        # Handle both single string and list of strings
        texts = request.texts if isinstance(request.texts, list) else [request.texts]

        # Generate embeddings
        embeddings = model.encode(
            texts,
            normalize_embeddings=request.normalize,
            convert_to_tensor=False
        )

        # Convert to list of lists
        if isinstance(embeddings, np.ndarray):
            embeddings_list = embeddings.tolist()
        else:
            embeddings_list = [emb.tolist() for emb in embeddings]

        return EmbeddingResponse(
            embeddings=embeddings_list,
            model_name=MODEL_NAME,
            dimension=len(embeddings_list[0]) if embeddings_list else 0
        )

    except Exception as e:
        logger.error(f"Error generating embeddings: {e}")
        raise HTTPException(status_code=500, detail=f"Error generating embeddings: {str(e)}")

@app.get("/")
async def root():
    """Root endpoint"""
    return {
        "service": "Semantic Search Embedding Service",
        "model": MODEL_NAME,
        "status": "running"
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)