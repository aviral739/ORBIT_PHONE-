import json
import time
import sys

if sys.stdout.encoding != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8')

def run_simulation():
    print("Starting Event Simulator...")
    # Simulate processing
    time.sleep(1)
    
    total_events = 30
    dropped = 5
    compressed = 10
    
    drop_pct = (dropped / total_events) * 100
    compress_pct = (compressed / total_events) * 100
    latency_ms = 45.2
    
    print(f"Processed Events: {total_events}")
    print(f"Drop Rate: {drop_pct:.1f}%")
    print(f"Compress Rate: {compress_pct:.1f}%")
    print(f"Average Latency: {latency_ms}ms")
    print("Cloud Cost: ₹0.00")

if __name__ == "__main__":
    run_simulation()
