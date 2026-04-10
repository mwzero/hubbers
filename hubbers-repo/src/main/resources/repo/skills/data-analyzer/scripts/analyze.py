#!/usr/bin/env python3
"""
Data Analyzer Script
Analyzes structured data and returns statistical insights.
"""
import json
import sys
from statistics import mean, median, stdev

def analyze_data(data, analysis_type="summary"):
    """Analyze the provided data."""
    if not data:
        return {"error": "No data provided"}
    
    # Extract numeric values
    values = []
    for item in data:
        if isinstance(item, dict) and 'value' in item:
            values.append(item['value'])
        elif isinstance(item, (int, float)):
            values.append(item)
    
    if not values:
        return {"error": "No numeric values found in data"}
    
    # Calculate statistics
    stats = {
        "count": len(values),
        "mean": round(mean(values), 2),
        "median": median(values),
        "min": min(values),
        "max": max(values)
    }
    
    if len(values) > 1:
        stats["std_dev"] = round(stdev(values), 2)
    
    # Generate insights
    insights = []
    if stats["std_dev"] > stats["mean"] * 0.5:
        insights.append("High variability detected in data")
    if stats["max"] > stats["mean"] + 2 * stats.get("std_dev", 0):
        insights.append("Potential outliers detected")
    
    return {
        "statistics": stats,
        "insights": insights,
        "analysis_type": analysis_type
    }

if __name__ == "__main__":
    try:
        # Read input from command line argument
        input_json = sys.argv[1] if len(sys.argv) > 1 else "{}"
        input_data = json.loads(input_json)
        
        data = input_data.get("data", [])
        analysis_type = input_data.get("analysis_type", "summary")
        
        result = analyze_data(data, analysis_type)
        print(json.dumps(result, indent=2))
        
    except Exception as e:
        print(json.dumps({"error": str(e)}), file=sys.stderr)
        sys.exit(1)
