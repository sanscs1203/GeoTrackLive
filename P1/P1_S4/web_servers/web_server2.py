from flask import Flask, render_template, jsonify
import mysql.connector
from datetime import datetime

app = Flask(__name__)

# Database configuration for Machine 2
DB_CONFIG = {
    'host': 'localhost',
    'database': 'gps_tracking',
    'user': 'gps_app',
    'password': 'gps_password_123',
    'port': 3307
}

@app.route('/')
def index():
    """Serve the main HTML page"""
    return render_template('index.html')

@app.route('/api/location')
def get_location():
    """Get latest GPS data from database"""
    try:
        # Connect to database
        conn = mysql.connector.connect(**DB_CONFIG)
        cursor = conn.cursor()
        
        # Query for latest GPS data
        query = """
        SELECT latitude, longitude, altitude, timestamp 
        FROM location_data 
        ORDER BY timestamp DESC 
        LIMIT 1
        """
        
        cursor.execute(query)
        result = cursor.fetchone()
        
        if result:
            # Format the data
            data = {
                'latitude': f"{result[0]:.6f}",
                'longitude': f"{result[1]:.6f}",
                'altitude': f"{result[2]:.2f}",
                'datetime': result[3].strftime('%Y-%m-%d %H:%M:%S')
            }
        else:
            # Default values if no data
            data = {
                'latitude': "0.000000",
                'longitude': "0.000000",
                'altitude': "00.00",
                'datetime': "9999-12-31 23:59:59"
            }
        
        cursor.close()
        conn.close()
        
        return jsonify(data)
        
    except Exception as e:
        print(f"Database error: {e}")
        # Return default values on error
        return jsonify({
            'latitude': "0.000000",
            'longitude': "0.000000",
            'altitude': "00.00",
            'datetime': "9999-12-31 23:59:59"
        })

if __name__ == '__main__':
    print("Starting Web Server 2...")
    print("Access at: http://localhost:5002")
    app.run(host='0.0.0.0', port=5002, debug=True)