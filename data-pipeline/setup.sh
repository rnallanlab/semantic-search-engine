
  cd data-pipeline

  # Create virtual environment
  python -m venv venv

  # Activate virtual environment
  source venv/bin/activate  # On macOS/Linux

  # Install dependencies
  pip install -r requirements.txt

  # Then run the data ingestion
  python data_ingestion.py