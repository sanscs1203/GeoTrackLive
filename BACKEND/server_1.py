import socket
import json
import pymysql
from datetime import datetime

# Database configuration localhost
DB_CONFIG = {
    'host': '[ENDPOINT RDS]',
    'database': 'gps_tracking',
    'user': 'gps_app',
    'password': 'gps_password_123',
    'port': 3306,
}

# Receiver ID for the sniffer/machine
RECEIVER_ID = 1

def connect_to_database():
    """Create database connection"""
    try:
        conn = pymysql.connect(**DB_CONFIG)
        print("✅ Connected to database")
        return conn
    except Exception as e:
        print(f"❌ Database connection failed: {e}")
        return None
    
def is_connected(conn):
    """Check if pymysql connection is alive"""
    try:
        conn.ping(reconnect=False)
        return True
    except:
        return False  

def insert_location_data(conn, gps_data):
    """Insert GPS data into location_data_table"""
    try:
        cursor = conn.cursor()
        
        # Convert timestamp string to datetime object
        timestamp = datetime.strptime(gps_data['timestamp'], '%Y-%m-%d %H:%M:%S')
        
        # Insert query matching your table structure
        query = """
        INSERT INTO location_data (latitude, longitude, timestamp, receiver_id)
        VALUE (%s, %s, %s, %s)
        """
        
        values = (
            gps_data['latitude'],
            gps_data['longitude'],
            timestamp,
            RECEIVER_ID
        )
        
        cursor.execute(query, values)
        conn.commit()
        cursor.close()
        
        print("  ✅ Data saved to database")
        return True
    
    except pymysql.IntegrityError as e:
        # This handles duplicate entries (your UNIQUE constraint)
        print("  ⚠️  Duplicate entry - already in database")
        return True
    except Exception as e:
        print(f"  ❌ Database error: {e}")
        conn.rollback()
        return False
        
# Connect to database
conn = connect_to_database()

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind(('0.0.0.0', 4665))    # Puerto que se encuentre abierto

print("GPS Sniffer - Port 4665\n")
            
try:
    while True:
        data, addr = sock.recvfrom(4096)
        
        try:
            gps = json.loads(data.decode('utf-8'))
            
            # Display formatted data
            print(f"  📍 Latitude:  {gps['latitude']}")
            print(f"  📍 Longitude: {gps['longitude']}")
            print(f"  📍 Timestamp: {gps['timestamp']}")
            
            # Save to database
            if conn and is_connected(conn):
                insert_location_data(conn, gps)
            else:
                # Try to reconnect
                print("  ⚠️  Reconnecting to database...")
                conn = connect_to_database()
                if conn:
                    insert_location_data(conn, gps)
            
            print("-" * 50)
            
            
        except json.JSONDecodeError:
            print(f"Error parsing: {data}")
        except Exception as e:
            print(f"Error: {e}")
    
except KeyboardInterrupt:
    print("\n\nShutting down...")
finally:
    sock.close()
    if conn and conn.is_connected():
        conn.close()
        print("Database connection closed")
       