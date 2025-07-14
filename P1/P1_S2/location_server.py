import socket
import json
import threading
from datetime import datetime

def handle_client(client_socket, client_address):
    """
    Handle individual TCP client connection
    """
    try:
        # Receive data (up to 1024 bytes)
        data = client_socket.recv(1024)
        
        if data:
            # Decode bytes to string
            message = data.decode('utf-8').strip()
            
            # Get current time
            current_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            
            print(f"\n[{current_time}] TCP connection from {client_address[0]}:{client_address[1]}")
            
            try:
                # Parse JSON data
                location_data = json.loads(message)
                
                # Display formatted data
                print(f"  📍 Latitude:  {location_data['latitude']}")
                print(f"  📍 Longitude: {location_data['longitude']}")
                print(f"  📍 Altitude:  {location_data['altitude']} m")
                print(f"  📍 Timestamp: {location_data['timestamp']}")
                print("-" * 50)
                
                # Optional: Save to file
                with open('tcp_locations.log', 'a') as f:
                    f.write(f"{current_time} | {client_address[0]} | {message}\n")
                    
            except json.JSONDecodeError:
                print(f"  ⚠️  Invalid JSON: {message}")
    
    except Exception as e:
        print(f"Error handling client {client_address}: {e}")
    
    finally:
        client_socket.close()

def start_tcp_server(host='0.0.0.0', port=4665):
    """
    TCP Server to receive location data from Android app
    """
    # Create TCP socket
    tcp_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    
    # Allow socket reuse
    tcp_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    
    # Bind to address and port
    tcp_socket.bind((host, port))
    
    # Listen for connections (max 5 queued)
    tcp_socket.listen(5)
    
    print(f"TCP Server listening on {host}:{port}")
    print("Waiting for location data...")
    print("-" * 50)
    
    try:
        while True:
            # Accept incoming connection
            client_socket, client_address = tcp_socket.accept()
            
            # Handle client in a new thread
            client_thread = threading.Thread(
                target=handle_client,
                args=(client_socket, client_address)
            )
            client_thread.start()
    
    except KeyboardInterrupt:
        print("\n\nServer stopped by user")
    finally:
        tcp_socket.close()

if __name__ == "__main__":
    # Change the IP to your computer's IP address
    # Use '0.0.0.0' to listen on all network interfaces
    start_tcp_server('0.0.0.0', 4665)