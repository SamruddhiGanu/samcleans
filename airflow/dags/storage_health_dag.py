from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.http.operators.http import SimpleHttpOperator
from datetime import datetime, timedelta
import requests
import json

default_args = {
    'owner': 'storage-health',
    'depends_on_past': False,
    'email_on_failure': False,
    'email_on_retry': False,
    'retries': 1,
    'retry_delay': timedelta(minutes=5),
}

with DAG(
    'storage_health_nightly_analysis',
    default_args=default_args,
    description='Nightly DAG to trigger agentic AI storage analysis',
    schedule_interval=timedelta(days=1),
    start_date=datetime(2026, 1, 1),
    catchup=False,
) as dag:

    def check_backend_health():
        response = requests.get('http://host.docker.internal:8080/actuator/health')
        response.raise_for_status()
        print("Backend is healthy")

    health_check = PythonOperator(
        task_id='check_backend_health',
        python_callable=check_backend_health,
    )

    # Trigger the duplicate detection endpoint for the most recent scan session
    # In a fully event-driven setup, this could be triggered via Kafka, but for orchestrating
    # existing endpoints, Airflow HTTP operators are useful.
    trigger_duplicate_detection = SimpleHttpOperator(
        task_id='trigger_duplicate_detection',
        http_conn_id='backend_api', # Define this connection in Airflow UI: http://host.docker.internal:8080
        endpoint='/api/duplicates/detect/1', # Hardcoded session 1 for demonstration
        method='POST',
        headers={"Content-Type": "application/json"},
    )

    health_check >> trigger_duplicate_detection
