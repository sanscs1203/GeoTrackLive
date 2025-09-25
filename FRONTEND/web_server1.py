from flask import Flask, render_template, jsonify, request
import pymysql
from datetime import datetime
import os
from dotenv import load_dotenv

load_dotenv()

app = Flask(__name__)

# Database configuration for Machine 1
DB_CONFIG = {
    'host': os.getenv('DB_HOST'),
    'database': os.getenv('DB_NAME'),
    'user': os.getenv('DB_USER'),
    'password': os.getenv('DB_PASSWORD'),
    'port': int(os.getenv('DB_PORT', 3306))
}


@app.route('/')
def index():
    """Serve the main HTML page"""
    app_title = os.getenv('NAME_TAB')
    print(app_title)
    return render_template('index.html', page_title=app_title or 'GeoTrackLive')

@app.route('/api/location')
def get_location():
    """Get latest GPS data from database"""
    try:
        # Connect to database
        conn = pymysql.connect(**DB_CONFIG)
        cursor = conn.cursor()
        
        # Query for latest GPS data
        query = """
        SELECT latitude, longitude, timestamp 
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
                'datetime': result[2].strftime('%Y-%m-%d %H:%M:%S')
            }
        else:
            # Default values if no data
            data = {
                'latitude': "Esperando GPS...",
                'longitude': "Esperando GPS...",
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
            'datetime': "Esperando GPS..."
        })

@app.route('/api/location/history')
def get_location_history():
    """Get GPS data history within a date range"""
    try:
        # Get date parameters from query string
        start_date = request.args.get('start')
        end_date = request.args.get('end')
        
        if not start_date or not end_date:
            return jsonify({'error': 'Se requieren fecha inicial y final'}), 400
        
        # Convert string dates to datetime objects
        # Handle the format from datetime-local input (YYYY-MM-DDTHH:MM)
        start_dt = datetime.fromisoformat(start_date.replace('T', ' '))
        end_dt = datetime.fromisoformat(end_date.replace('T', ' '))
        
        # Connect to database
        conn = pymysql.connect(**DB_CONFIG)
        cursor = conn.cursor()
        
        # Query for GPS data in the date range
        query = """
        SELECT latitude, longitude, timestamp 
        FROM location_data 
        WHERE timestamp BETWEEN %s AND %s
        ORDER BY timestamp ASC
        """
        
        cursor.execute(query, (start_dt, end_dt))
        results = cursor.fetchall()
        
        # Format the results
        locations = []
        for result in results:
            locations.append({
                'latitude': f"{result[0]:.6f}",
                'longitude': f"{result[1]:.6f}",
                'datetime': result[2].strftime('%Y-%m-%d %H:%M:%S')
            })
        
        cursor.close()
        conn.close()
        
        # Return the data
        return jsonify({'locations': locations})
    
    except ValueError as e:
        print(f"Date parsing error: {e}")
        return jsonify({'error': 'Formato de fecha inválido'}), 400
    
    except Exception as e:
        print(f"Database error: {e}")
        return jsonify({'error': 'Error al obtener datos históricos'}), 500

if __name__ == '__main__':
    print("Starting Web Server 1...")
    print(f"Access at: {os.getenv('DNS')}")  
    app.run(host='0.0.0.0', port=int(os.getenv('DNS_PORT', 5001)), debug=True)
