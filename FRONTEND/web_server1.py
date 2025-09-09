from flask import Flask, render_template, jsonify
import pymysql
from datetime import datetime

app = Flask(__name__)

# Databse configuration for Machine 1
DB_CONFIG = {
    'host': 'geotracklive-db.cav40om2if7t.us-east-1.rds.amazonaws.com',
    'database': 'gps_tracking',
    'user': 'gps_app',
    'password': 'gps_password_123',
    'port': 3306
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
        conn = pymysql.connect(**DB_CONFIG)
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
                'latitude': "Esperando GPS...",
                'longitude': "Esperando GPS...",
                'altitude': "Esperando GPS...",
                'datetime': "Esperando GPS..."
            }
        
        cursor.close()
        conn.close()
        
        return jsonify(data)
    
    except Exception as e:
        print(f"Database error: {e}")
        # Return default values on error
        return jsonify({
            'latitude': "Esperando GPS...",
            'longitude': "Esperando GPS...",
            'altitude': "Esperando GPS...",
            'datetime': "Esperando GPS..."
        })

if __name__ == '__main__':
    print("Starting Web Server 1...")
    print("Access at: [DNS]:5001")  
    app.run(host='0.0.0.0', port=5001, debug=True)